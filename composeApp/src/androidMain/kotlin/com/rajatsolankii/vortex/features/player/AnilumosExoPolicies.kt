package com.rajatsolankii.vortex.features.player

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Ported from Anilumos (`StreamRefusalLoadErrorPolicy`):
 * Gives up immediately on a CDN that answered 401/403 instead of burning 15–30s on retries,
 * and stops retrying dead hosts after two zero-byte failures so source failover runs promptly.
 */
@UnstableApi
internal class AnilumosStreamRefusalLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val responseCode = (loadErrorInfo.exception as? InvalidResponseCodeException)?.responseCode
        if (responseCode == 401 || responseCode == 403) return C.TIME_UNSET
        val isNetworkTimeoutOrRefusal = generateSequence<Throwable>(loadErrorInfo.exception) { it.cause }
            .take(6)
            .any { cause ->
                cause is java.net.SocketTimeoutException ||
                    cause is java.net.UnknownHostException ||
                    cause is java.net.ConnectException ||
                    cause is javax.net.ssl.SSLHandshakeException
            }
        if (isNetworkTimeoutOrRefusal &&
            (loadErrorInfo.loadEventInfo.bytesLoaded == 0L || loadErrorInfo.errorCount >= 2)
        ) {
            return C.TIME_UNSET
        }
        if (loadErrorInfo.loadEventInfo.bytesLoaded == 0L && loadErrorInfo.errorCount >= 2) {
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        val responseCode = (loadErrorInfo.exception as? InvalidResponseCodeException)?.responseCode
        val hasOtherServer =
            fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION)
        if ((responseCode == 401 || responseCode == 403) && !hasOtherServer) return null
        return super.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)
    }
}

/**
 * Ported from Anilumos (`PlaybackService` ResolvingDataSource):
 * Ensures every HTTP request (manifests, segments, keys, sidecar subtitles) carries the active
 * Referer, Origin, and User-Agent when the DataSpec doesn't already specify them.
 */
@UnstableApi
internal fun DataSource.Factory.withAnilumosRequestHeaders(
    headers: Map<String, String>,
): DataSource.Factory {
    if (headers.isEmpty()) return this
    return ResolvingDataSource.Factory(this) { dataSpec ->
        val scheme = dataSpec.uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return@Factory dataSpec
        val merged = LinkedHashMap<String, String>(headers.size + dataSpec.httpRequestHeaders.size)
        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank() && !key.equals("Range", ignoreCase = true)) {
                merged[key] = value
            }
        }
        dataSpec.httpRequestHeaders.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                merged[key] = value
            }
        }
        dataSpec.buildUpon().setHttpRequestHeaders(merged).build()
    }
}

/**
 * Ported from Anilumos (`PlaybackBuffer`):
 * Builds a TV/low-RAM-aware `DefaultLoadControl` when custom buffer settings aren't forced,
 * starting playback at 1.5s on constrained devices and keeping safe back-buffer limits.
 */
@UnstableApi
internal object AnilumosBufferPolicy {
    fun buildDefaultLoadControl(context: Context, isProgressive: Boolean = false): DefaultLoadControl {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        val memoryClassMb = am?.memoryClass ?: 256
        val isTv = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val constrained = isTv || isLowRam || memoryClassMb <= 256

        return if (constrained) {
            val minMs = if (isProgressive) 18_000 else 12_000
            val maxMs = if (isProgressive) 40_000 else 28_000
            DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                .setBufferDurationsMs(minMs, maxMs, 1_200, 2_500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(10_000, true)
                .build()
        } else {
            val minMs = if (isProgressive) 20_000 else 15_000
            val maxMs = if (isProgressive) 50_000 else 35_000
            DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                .setBufferDurationsMs(minMs, maxMs, 1_200, 2_500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(15_000, true)
                .build()
        }
    }
}
