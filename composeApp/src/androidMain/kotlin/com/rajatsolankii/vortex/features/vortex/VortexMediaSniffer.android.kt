package com.rajatsolankii.vortex.features.vortex

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rajatsolankii.vortex.features.player.PlayerPlaybackNetworking
import com.rajatsolankii.vortex.features.player.PlayerSettingsStorage
import com.rajatsolankii.vortex.features.streams.StreamSubtitle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Silent off-screen Android WebView network sniffer for Vortex embed pages.
 *
 * Loads the embed URL in a headless WebView with [VortexAdShield] active and audio muted,
 * captures the real media manifest/stream (.m3u8 / .mpd / .mp4 / #EXTM3U .txt or extensionless
 * URLs) along with its exact Referer / Origin / User-Agent / Cookie headers and any .vtt/.srt
 * subtitle tracks, then destroys the WebView so playback happens in Media3 ExoPlayer with the
 * app's own native controls.
 */
internal actual object VortexPlatformSniffer {
    private val sniffMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appContextRef = AtomicReference<Context?>(null)

    fun initialize(context: Context) {
        appContextRef.compareAndSet(null, context.applicationContext)
    }

    private fun resolveContext(): Context? =
        appContextRef.get() ?: PlayerSettingsStorage.applicationContext()

    private data class CandidateEntry(
        val media: VortexExtractedMedia,
        val isMasterPlaylist: Boolean,
    )

    @SuppressLint("SetJavaScriptEnabled")
    actual suspend fun sniff(embedUrl: String, timeoutMs: Long): VortexExtractedMedia? = sniffMutex.withLock {
        val context = resolveContext() ?: return@withLock null
        val embedHost = VortexAdShield.hostOf(embedUrl)
        val resultDeferred = CompletableDeferred<VortexExtractedMedia?>()
        val finished = AtomicBoolean(false)
        val capturedSubtitles = CopyOnWriteArrayList<StreamSubtitle>()
        val bestCandidate = AtomicReference<CandidateEntry?>(null)
        val graceScheduled = AtomicBoolean(false)
        val probedUrls = Collections.synchronizedSet(HashSet<String>())
        val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val callbackToken = Any()

        var webView: WebView? = null

        fun cleanup() {
            probeScope.cancel()
            mainHandler.removeCallbacksAndMessages(callbackToken)
            mainHandler.post {
                webView?.let { view ->
                    runCatching {
                        view.evaluateJavascript(
                            "(function(){try{document.querySelectorAll('video,audio').forEach(function(e){e.muted=true;e.volume=0;e.pause();});}catch(_){}})();",
                            null,
                        )
                        view.stopLoading()
                        view.removeJavascriptInterface("VortexSnifferBridge")
                        view.clearHistory()
                        view.loadUrl("about:blank")
                        view.removeAllViews()
                        view.destroy()
                    }
                }
                webView = null
            }
        }

        fun completeWith(media: VortexExtractedMedia?) {
            if (!finished.compareAndSet(false, true)) return
            val finalMedia = media?.copy(
                subtitles = (media.subtitles + capturedSubtitles).distinctBy { it.url },
            )
            cleanup()
            resultDeferred.complete(finalMedia)
        }

        fun buildHeaders(
            requestHeaders: Map<String, String>,
            userAgent: String,
            targetUrl: String,
        ): Map<String, String> {
            val origin = VortexMediaExtractor.originOf(embedUrl)
            val cookie = runCatching { CookieManager.getInstance().getCookie(targetUrl) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
            return buildMap {
                requestHeaders.forEach { (k, v) ->
                    if (k.isNotBlank() && v.isNotBlank() && !k.equals("Range", ignoreCase = true)) {
                        put(k, v)
                    }
                }
                if (keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                    put("User-Agent", userAgent)
                }
                if (keys.none { it.equals("Referer", ignoreCase = true) }) {
                    put("Referer", origin?.let { "$it/" } ?: embedUrl)
                }
                if (keys.none { it.equals("Origin", ignoreCase = true) } && origin != null) {
                    put("Origin", origin)
                }
                if (cookie != null && keys.none { it.equals("Cookie", ignoreCase = true) }) {
                    put("Cookie", cookie)
                }
            }
        }

        fun recordMediaCandidate(
            rawUrl: String,
            requestHeaders: Map<String, String>,
            userAgent: String,
            explicitStreamType: String? = null,
            explicitMaster: Boolean = false,
            allowNonExtensionMedia: Boolean = false,
        ) {
            if (finished.get()) return
            val url = VortexMediaExtractor.normalizeExtractedUrl(embedUrl, rawUrl) ?: return
            if (VortexAdShield.isAdUrl(url) || isSegmentOrAssetUrl(url)) return
            if (!allowNonExtensionMedia && !VortexMediaExtractor.isDirectMediaUrl(url)) return

            val inferredType = explicitStreamType ?: VortexMediaExtractor.inferStreamType(url)
            val candidate = VortexExtractedMedia(
                url = url,
                headers = buildHeaders(requestHeaders, userAgent, url),
                subtitles = emptyList(),
                streamType = inferredType,
                embedUrl = embedUrl,
            )
            val isMaster = explicitMaster ||
                "master" in url.lowercase() ||
                "playlist" in url.lowercase()

            bestCandidate.updateAndGet { current ->
                if (current == null) {
                    CandidateEntry(candidate, isMaster)
                } else {
                    val newScore = candidateScore(candidate, isMaster)
                    val curScore = candidateScore(current.media, current.isMasterPlaylist)
                    if (newScore > curScore) CandidateEntry(candidate, isMaster) else current
                }
            }

            if (graceScheduled.compareAndSet(false, true)) {
                mainHandler.postAtTime(
                    { completeWith(bestCandidate.get()?.media) },
                    callbackToken,
                    SystemClock.uptimeMillis() + SUBTITLE_GRACE_MS,
                )
            }
        }

        fun recordSubtitleCandidate(
            rawUrl: String,
            requestHeaders: Map<String, String>,
            userAgent: String,
            allowProbeMatched: Boolean = false,
        ) {
            if (finished.get()) return
            val url = VortexMediaExtractor.normalizeExtractedUrl(embedUrl, rawUrl) ?: return
            if (VortexAdShield.isAdUrl(url)) return
            if (!allowProbeMatched && !VortexMediaExtractor.isSubtitleUrl(url)) return
            if (capturedSubtitles.any { it.url == url }) return
            val origin = VortexMediaExtractor.originOf(embedUrl)
            val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
            val headers = buildMap {
                requestHeaders.forEach { (k, v) ->
                    if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                }
                if (keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                    put("User-Agent", userAgent)
                }
                if (keys.none { it.equals("Referer", ignoreCase = true) }) {
                    put("Referer", origin?.let { "$it/" } ?: embedUrl)
                }
                if (cookie != null && keys.none { it.equals("Cookie", ignoreCase = true) }) {
                    put("Cookie", cookie)
                }
            }
            capturedSubtitles += StreamSubtitle(
                url = url,
                language = "en",
                name = "Subtitle",
                headers = headers,
            )
        }

        fun scheduleResponseProbe(
            reqUrl: String,
            headers: Map<String, String>,
            ua: String,
        ) {
            if (finished.get()) return
            if (!shouldProbeAmbiguousUrl(reqUrl, headers)) return
            if (probedUrls.size >= MAX_PROBE_REQUESTS || !probedUrls.add(reqUrl)) return

            probeScope.launch {
                val probed = probeResponseHead(
                    targetUrl = reqUrl,
                    headers = buildHeaders(headers, ua, reqUrl),
                ) ?: return@launch
                when (probed.kind) {
                    ProbeKind.MEDIA -> recordMediaCandidate(
                        rawUrl = reqUrl,
                        requestHeaders = headers,
                        userAgent = ua,
                        explicitStreamType = probed.streamType,
                        explicitMaster = probed.isMasterPlaylist,
                        allowNonExtensionMedia = true,
                    )
                    ProbeKind.SUBTITLE -> recordSubtitleCandidate(
                        rawUrl = reqUrl,
                        requestHeaders = headers,
                        userAgent = ua,
                        allowProbeMatched = true,
                    )
                }
            }
        }

        withContext(Dispatchers.Main.immediate) {
            val view = WebView(context)
            webView = view
            val ua = view.settings.userAgentString
            // Give headless WebView real 720p viewport dimensions so embed scripts & IntersectionObserver initialize immediately.
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 1280, 720)
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Skip useless poster images/favicons in headless extraction to save RAM & network.
                loadsImagesAutomatically = false
                blockNetworkImage = true
                // Allow autoplay inside embeds + cross-origin iframes (audio is muted by SNIFFER_JS_HOOK).
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                allowFileAccess = false
                allowContentAccess = false
            }

            fun dispatchSyntheticCenterTap() {
                if (finished.get()) return
                val targetView = webView ?: return
                runCatching {
                    val now = SystemClock.uptimeMillis()
                    val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 640f, 360f, 0)
                    val up = MotionEvent.obtain(now, now + 45L, MotionEvent.ACTION_UP, 640f, 360f, 0)
                    targetView.dispatchTouchEvent(down)
                    targetView.dispatchTouchEvent(up)
                    down.recycle()
                    up.recycle()
                }
            }

            view.addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onMediaUrl(url: String?) {
                        if (!url.isNullOrBlank()) {
                            recordMediaCandidate(url, emptyMap(), ua)
                        }
                    }

                    @JavascriptInterface
                    fun onMediaUrlWithType(url: String?, streamType: String?, isMaster: Boolean) {
                        if (!url.isNullOrBlank()) {
                            recordMediaCandidate(
                                rawUrl = url,
                                requestHeaders = emptyMap(),
                                userAgent = ua,
                                explicitStreamType = streamType?.takeIf { it.isNotBlank() },
                                explicitMaster = isMaster,
                                allowNonExtensionMedia = true,
                            )
                        }
                    }

                    @JavascriptInterface
                    fun onSubtitleUrl(url: String?) {
                        if (!url.isNullOrBlank()) {
                            recordSubtitleCandidate(url, emptyMap(), ua, allowProbeMatched = true)
                        }
                    }
                },
                "VortexSnifferBridge",
            )

            view.webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean = false

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.confirm()
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.confirm()
                    return true
                }
            }

            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url?.toString() ?: return false
                    return if (request.isForMainFrame) {
                        VortexAdShield.shouldBlockNavigation(target, embedHost)
                    } else {
                        VortexAdShield.shouldBlockSubframeNavigation(target)
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null
                    if (VortexAdShield.isAdUrl(reqUrl)) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    val headers = request.requestHeaders.orEmpty()
                    if (VortexMediaExtractor.isSubtitleUrl(reqUrl)) {
                        recordSubtitleCandidate(reqUrl, headers, ua)
                    } else if (VortexMediaExtractor.isDirectMediaUrl(reqUrl)) {
                        recordMediaCandidate(reqUrl, headers, ua)
                    } else if (
                        request.method.equals("GET", ignoreCase = true) &&
                        isHtmlVideoStreamRequest(headers)
                    ) {
                        recordMediaCandidate(
                            rawUrl = reqUrl,
                            requestHeaders = headers,
                            userAgent = ua,
                            explicitStreamType = "mp4",
                            allowNonExtensionMedia = true,
                        )
                    } else if (isBlockingStaticAsset(reqUrl, headers)) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    } else if (!request.isForMainFrame && request.method.equals("GET", ignoreCase = true)) {
                        scheduleResponseProbe(reqUrl, headers, ua)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.evaluateJavascript(SNIFFER_JS_HOOK, null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(SNIFFER_JS_HOOK, null)
                    view?.evaluateJavascript(AUTO_TRIGGER_PLAY_JS, null)
                    dispatchSyntheticCenterTap()
                }
            }

            mainHandler.postAtTime(
                {
                    if (!finished.get()) {
                        webView?.evaluateJavascript(AUTO_TRIGGER_PLAY_JS, null)
                        dispatchSyntheticCenterTap()
                    }
                },
                callbackToken,
                SystemClock.uptimeMillis() + 450L,
            )
            mainHandler.postAtTime(
                {
                    if (!finished.get()) {
                        dispatchSyntheticCenterTap()
                    }
                },
                callbackToken,
                SystemClock.uptimeMillis() + 1_150L,
            )

            val refererHeader = VortexMediaExtractor.originOf(embedUrl)?.let { mapOf("Referer" to "$it/") }.orEmpty()
            view.loadUrl(embedUrl, refererHeader)
        }

        return@withLock try {
            val awaited = withTimeoutOrNull(timeoutMs) {
                resultDeferred.await()
            }
            if (awaited != null) {
                awaited
            } else {
                val fallback = bestCandidate.get()?.media
                if (fallback != null) {
                    completeWith(fallback)
                    resultDeferred.getCompleted()
                } else {
                    null
                }
            }
        } finally {
            if (!finished.get()) {
                completeWith(null)
            }
        }
    }

    private enum class ProbeKind { MEDIA, SUBTITLE }
    private data class ProbeResult(
        val kind: ProbeKind,
        val streamType: String? = null,
        val isMasterPlaylist: Boolean = false,
    )

    private fun candidateScore(media: VortexExtractedMedia, isMaster: Boolean): Int {
        var score = 0
        if (media.streamType == "hls") score += 100
        else if (media.streamType == "dash") score += 70
        else if (media.streamType == "mp4") score += 50
        if (isMaster) score += 40
        if (".m3u8" in media.url.lowercase()) score += 10
        return score
    }

    private fun isHtmlVideoStreamRequest(headers: Map<String, String>): Boolean {
        val secDest = headers.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", ignoreCase = true) }?.value
        return secDest.equals("video", ignoreCase = true)
    }

    private fun isBlockingStaticAsset(url: String, headers: Map<String, String>): Boolean {
        val lower = url.lowercase()
        val path = lower.substringBefore('?').substringBefore('#')
        // Keep stylesheets loaded so player overlays/play buttons stay properly sized for JS & tap triggers;
        // block heavy images, icons, webfonts, and sourcemaps only.
        if (
            path.endsWith(".woff") || path.endsWith(".woff2") ||
            path.endsWith(".ttf") || path.endsWith(".otf") || path.endsWith(".png") ||
            path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp") ||
            path.endsWith(".gif") || path.endsWith(".svg") || path.endsWith(".ico") ||
            path.endsWith(".map") || "fonts.googleapis" in lower || "fonts.gstatic" in lower
        ) {
            return true
        }
        val secDest = headers.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", ignoreCase = true) }?.value?.lowercase()
        return secDest == "image" || secDest == "font"
    }

    private fun isSegmentOrAssetUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".ts") || path.endsWith(".m4s") || path.endsWith(".m4a") ||
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
            path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".png") ||
            path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp") ||
            path.endsWith(".gif") || path.endsWith(".svg") || path.endsWith(".ico") ||
            path.endsWith(".wasm") || path.endsWith(".map") || "/cdn-cgi/" in url.lowercase()
    }

    private fun shouldProbeAmbiguousUrl(url: String, headers: Map<String, String>): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (isSegmentOrAssetUrl(lower) || VortexAdShield.isAdUrl(lower)) return false
        val path = lower.substringBefore('?').substringBefore('#')
        if (path.endsWith(".html") || path.endsWith(".htm") || "/embed/" in path) return false
        val secDest = headers.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", ignoreCase = true) }?.value?.lowercase()
        if (secDest == "document" || secDest == "iframe" || secDest == "script" || secDest == "style" || secDest == "image" || secDest == "font") {
            return false
        }
        if ("/seg-" in path || "/chunk" in path || "/init-" in path || "beacon" in lower || "analytics" in lower || "fonts.googleapis" in lower) {
            return false
        }
        return true
    }

    private fun probeResponseHead(
        targetUrl: String,
        headers: Map<String, String>,
    ): ProbeResult? = runCatching {
        val conn = PlayerPlaybackNetworking.openConnection(
            url = targetUrl,
            headers = headers,
            method = "GET",
            connectTimeoutMs = PROBE_TIMEOUT_MS,
            readTimeoutMs = PROBE_TIMEOUT_MS,
        )
        conn.setRequestProperty("Accept", "*/*")
        try {
            val code = conn.responseCode
            if (code !in 200..299) return@runCatching null
            val contentType = conn.contentType?.lowercase().orEmpty()
            if ("text/html" in contentType || "javascript" in contentType || "css" in contentType || "image/" in contentType) {
                return@runCatching null
            }
            if ("mpegurl" in contentType || "m3u8" in contentType) {
                val head = readHeadString(conn)
                val isMaster = "#EXT-X-STREAM-INF" in head
                return@runCatching ProbeResult(ProbeKind.MEDIA, streamType = "hls", isMasterPlaylist = isMaster)
            }
            if ("dash+xml" in contentType) {
                return@runCatching ProbeResult(ProbeKind.MEDIA, streamType = "dash", isMasterPlaylist = true)
            }
            if ("text/vtt" in contentType || "subrip" in contentType || "x-ass" in contentType) {
                return@runCatching ProbeResult(ProbeKind.SUBTITLE)
            }
            if (contentType.startsWith("video/")) {
                return@runCatching ProbeResult(ProbeKind.MEDIA, streamType = "mp4", isMasterPlaylist = false)
            }
            val head = readHeadString(conn)
            val trimmed = head.trimStart()
            when {
                trimmed.startsWith("#EXTM3U") -> ProbeResult(
                    kind = ProbeKind.MEDIA,
                    streamType = "hls",
                    isMasterPlaylist = "#EXT-X-STREAM-INF" in head,
                )
                trimmed.startsWith("<MPD") || ("<?xml" in trimmed && "<MPD" in head) -> ProbeResult(
                    kind = ProbeKind.MEDIA,
                    streamType = "dash",
                    isMasterPlaylist = true,
                )
                trimmed.startsWith("WEBVTT") -> ProbeResult(kind = ProbeKind.SUBTITLE)
                else -> null
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun readHeadString(conn: HttpURLConnection): String {
        val stream = conn.errorStream ?: conn.inputStream ?: return ""
        val buf = ByteArray(2048)
        var total = 0
        while (total < buf.size) {
            val n = stream.read(buf, total, buf.size - total)
            if (n <= 0) break
            total += n
        }
        return String(buf, 0, total, Charsets.UTF_8)
    }

    private const val SUBTITLE_GRACE_MS = 40L
    private const val PROBE_TIMEOUT_MS = 1_100
    private const val MAX_PROBE_REQUESTS = 8

    private const val SNIFFER_JS_HOOK = """
        (function() {
          function installInWindow(win) {
            try {
              if (!win || win.__vortexSniffed) return;
              win.__vortexSniffed = true;
              win.open = function() { return null; };

              // Mute all HTML5 media elements and WebAudio to eliminate background audio leak.
              var proto = win.HTMLMediaElement && win.HTMLMediaElement.prototype;
              if (proto) {
                try {
                  var origMutedDesc = Object.getOwnPropertyDescriptor(proto, 'muted');
                  Object.defineProperty(proto, 'muted', {
                    get: function() { return true; },
                    set: function(_) {
                      try { if (origMutedDesc && origMutedDesc.set) origMutedDesc.set.call(this, true); } catch(e) {}
                    },
                    configurable: true
                  });
                } catch(e) {}
                try {
                  var origVolDesc = Object.getOwnPropertyDescriptor(proto, 'volume');
                  Object.defineProperty(proto, 'volume', {
                    get: function() { return 0; },
                    set: function(_) {
                      try { if (origVolDesc && origVolDesc.set) origVolDesc.set.call(this, 0); } catch(e) {}
                    },
                    configurable: true
                  });
                } catch(e) {}
                var origPlay = proto.play;
                if (origPlay) {
                  proto.play = function() {
                    try { this.muted = true; this.volume = 0; } catch(e) {}
                    return origPlay.apply(this, arguments);
                  };
                }
              }
              try {
                if (win.AudioContext && win.AudioContext.prototype && win.AudioContext.prototype.resume) {
                  win.AudioContext.prototype.resume = function() { return Promise.resolve(); };
                }
                if (win.webkitAudioContext && win.webkitAudioContext.prototype && win.webkitAudioContext.prototype.resume) {
                  win.webkitAudioContext.prototype.resume = function() { return Promise.resolve(); };
                }
              } catch(e) {}

              function muteExisting() {
                try {
                  win.document.querySelectorAll('video, audio').forEach(function(el) {
                    try { el.muted = true; el.volume = 0; } catch(e) {}
                  });
                } catch(e) {}
              }

              function inspectUrl(u) {
                if (!u || typeof u !== 'string') return;
                var l = u.toLowerCase();
                if (l.indexOf('.m3u8') !== -1 || l.indexOf('/master.txt') !== -1 || l.indexOf('/playlist.txt') !== -1 || l.indexOf('.mpd') !== -1 || l.indexOf('.mp4') !== -1) {
                  VortexSnifferBridge.onMediaUrl(u);
                } else if (l.indexOf('.vtt') !== -1 || l.indexOf('.srt') !== -1) {
                  VortexSnifferBridge.onSubtitleUrl(u);
                }
              }

              function inspectBodyText(u, ct, text) {
                if (!u || typeof u !== 'string') return;
                var lowerCt = (ct || '').toLowerCase();
                var trimmed = (text || '').trimStart();
                if (lowerCt.indexOf('mpegurl') !== -1 || lowerCt.indexOf('m3u8') !== -1 || trimmed.indexOf('#EXTM3U') === 0) {
                  VortexSnifferBridge.onMediaUrlWithType(u, 'hls', trimmed.indexOf('#EXT-X-STREAM-INF') !== -1);
                } else if (lowerCt.indexOf('dash+xml') !== -1 || trimmed.indexOf('<MPD') !== -1) {
                  VortexSnifferBridge.onMediaUrlWithType(u, 'dash', true);
                } else if (lowerCt.indexOf('text/vtt') !== -1 || trimmed.indexOf('WEBVTT') === 0) {
                  VortexSnifferBridge.onSubtitleUrl(u);
                }
              }

              var origFetch = win.fetch;
              if (origFetch) {
                win.fetch = function(input) {
                  var u = '';
                  try {
                    u = typeof input === 'string' ? input : (input && input.url) || '';
                    inspectUrl(u);
                  } catch(e) {}
                  var p = origFetch.apply(this, arguments);
                  try {
                    p.then(function(resp) {
                      try {
                        var respUrl = (resp && resp.url) || u;
                        var ct = (resp && resp.headers && resp.headers.get && resp.headers.get('content-type')) || '';
                        var lct = ct.toLowerCase();
                        if (lct.indexOf('mpegurl') !== -1 || lct.indexOf('m3u8') !== -1 || lct.indexOf('dash+xml') !== -1 || lct.indexOf('text/vtt') !== -1) {
                          if (resp && resp.clone) {
                            resp.clone().text().then(function(txt) {
                              inspectBodyText(respUrl, ct, txt);
                            }).catch(function(){ inspectBodyText(respUrl, ct, ''); });
                          } else {
                            inspectBodyText(respUrl, ct, '');
                          }
                        } else if (resp && resp.clone && (lct.indexOf('text/plain') !== -1 || lct === '' || lct.indexOf('octet-stream') !== -1)) {
                          resp.clone().text().then(function(txt) {
                            if (txt && (txt.trimStart().indexOf('#EXTM3U') === 0 || txt.trimStart().indexOf('WEBVTT') === 0 || txt.trimStart().indexOf('<MPD') !== -1)) {
                              inspectBodyText(respUrl, ct, txt);
                            }
                          }).catch(function(){});
                        }
                      } catch(e) {}
                    }).catch(function(){});
                  } catch(e) {}
                  return p;
                };
              }

              var origOpen = win.XMLHttpRequest && win.XMLHttpRequest.prototype && win.XMLHttpRequest.prototype.open;
              if (origOpen) {
                win.XMLHttpRequest.prototype.open = function(method, url) {
                  try {
                    this.__vortexUrl = url;
                    inspectUrl(url);
                    this.addEventListener('load', function() {
                      try {
                        var ct = this.getResponseHeader && this.getResponseHeader('content-type');
                        var u = this.responseURL || this.__vortexUrl || '';
                        var txt = (typeof this.responseText === 'string' && this.responseText.length < 131072) ? this.responseText : '';
                        inspectBodyText(u, ct || '', txt);
                      } catch(e) {}
                    });
                  } catch(e) {}
                  return origOpen.apply(this, arguments);
                };
              }

              muteExisting();
              win.setInterval(function() {
                try {
                  muteExisting();
                  win.document.querySelectorAll('video, source').forEach(function(el) {
                    var src = el.currentSrc || el.src;
                    if (src && src.indexOf('blob:') !== 0) inspectUrl(src);
                  });
                  win.document.querySelectorAll('track').forEach(function(tr) {
                    if (tr.src) inspectUrl(tr.src);
                  });
                  win.document.querySelectorAll('iframe').forEach(function(fr) {
                    try { if (fr.contentWindow) installInWindow(fr.contentWindow); } catch(e) {}
                  });
                } catch(e) {}
              }, 500);
            } catch(e) {}
          }
          installInWindow(window);
        })();
    """

    private const val AUTO_TRIGGER_PLAY_JS = """
        (function() {
          try {
            document.querySelectorAll('video, audio').forEach(function(e) { e.muted = true; e.volume = 0; });
            var selectors = [
              '.plyr__control--overlaid',
              '.vjs-big-play-button',
              '.jw-icon-display',
              '#bigPlay',
              '[aria-label*="play" i]',
              '[class*="play-btn" i]',
              '[class*="playButton" i]',
              'button[class*="play" i]',
              'video'
            ];
            for (var i = 0; i < selectors.length; i++) {
              var el = document.querySelector(selectors[i]);
              if (el) { el.click(); break; }
            }
          } catch(e) {}
        })();
    """
}
