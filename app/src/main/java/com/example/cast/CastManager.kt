package com.example.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Cast session lifecycle, remote media loading, and controls.
 * Safely initializes Google Play Services CastContext without crashing on non-GMS devices.
 */
class CastManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "VortexCastManager"

        @Volatile
        private var instance: CastManager? = null

        fun getInstance(context: Context): CastManager {
            return instance ?: synchronized(this) {
                instance ?: CastManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var castContext: CastContext? = null
    private var castSession: CastSession? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentStreamPositionMs = MutableStateFlow(0L)
    val currentStreamPositionMs: StateFlow<Long> = _currentStreamPositionMs.asStateFlow()

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {}

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            onApplicationConnected(session)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            onApplicationDisconnected()
        }

        override fun onSessionEnding(session: CastSession) {}

        override fun onSessionEnded(session: CastSession, error: Int) {
            onApplicationDisconnected()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {}

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            onApplicationConnected(session)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            onApplicationDisconnected()
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _isConnected.value = false
        }
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = castSession?.remoteMediaClient ?: return
            _isPlaying.value = client.isPlaying
            _currentStreamPositionMs.value = client.approximateStreamPosition
        }
    }

    init {
        initializeSafely()
    }

    private fun initializeSafely() {
        try {
            castContext = CastContext.getSharedInstance(appContext)
            val sessionManager = castContext?.sessionManager
            sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)

            val currentSession = sessionManager?.currentCastSession
            if (currentSession != null && currentSession.isConnected) {
                onApplicationConnected(currentSession)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Cast not available on this device: ${e.message}")
            castContext = null
        }
    }

    private fun onApplicationConnected(session: CastSession) {
        castSession = session
        _isConnected.value = true
        _connectedDeviceName.value = session.castDevice?.friendlyName ?: "Google Cast Receiver"

        session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)
    }

    private fun onApplicationDisconnected() {
        castSession?.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        castSession = null
        _isConnected.value = false
        _connectedDeviceName.value = null
        _isPlaying.value = false
        _currentStreamPositionMs.value = 0L
    }

    /**
     * Check whether Google Cast framework is available and supported on this device.
     */
    fun isCastSupported(): Boolean {
        return castContext != null
    }

    /**
     * Load a direct media stream on the connected Cast receiver.
     */
    fun loadMedia(
        title: String,
        mediaUrl: String,
        posterUrl: String? = null,
        mimeType: String = "video/mp4",
        startPositionMs: Long = 0L,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val session = castSession
        if (session == null || !session.isConnected) {
            onError("No active Cast connection. Please connect to a Cast device first.")
            return
        }

        val remoteMediaClient = session.remoteMediaClient
        if (remoteMediaClient == null) {
            onError("Cast media player client is unavailable.")
            return
        }

        try {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, title)
                putString(MediaMetadata.KEY_SUBTITLE, "Playing from Vortex")
                if (!posterUrl.isNullOrEmpty()) {
                    addImage(WebImage(Uri.parse(posterUrl)))
                }
            }

            val resolvedMimeType = if (mimeType.isNotEmpty() && mimeType != "text/html") {
                mimeType
            } else {
                MediaSourceClassifier.resolveMimeType(mediaUrl)
            }

            val mediaInfo = MediaInfo.Builder(mediaUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(resolvedMimeType)
                .setMetadata(metadata)
                .build()

            val options = MediaLoadOptions.Builder()
                .setAutoplay(true)
                .setPlayPosition(startPositionMs)
                .build()

            val request = remoteMediaClient.load(mediaInfo, options)
            request.setResultCallback { result ->
                if (result.status.isSuccess) {
                    onSuccess()
                } else {
                    onError("Failed to start playback on TV: ${result.status.statusMessage ?: "Error code ${result.status.statusCode}"}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading media on Cast device", e)
            onError(e.message ?: "Unknown Cast error occurred")
        }
    }

    fun play() {
        castSession?.remoteMediaClient?.play()
    }

    fun pause() {
        castSession?.remoteMediaClient?.pause()
    }

    fun togglePlayPause() {
        val client = castSession?.remoteMediaClient ?: return
        if (client.isPlaying) {
            client.pause()
        } else {
            client.play()
        }
    }

    fun seekTo(positionMs: Long) {
        castSession?.remoteMediaClient?.seek(positionMs)
    }

    fun disconnect() {
        try {
            castContext?.sessionManager?.endCurrentSession(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to end Cast session: ${e.message}")
        }
    }
}
