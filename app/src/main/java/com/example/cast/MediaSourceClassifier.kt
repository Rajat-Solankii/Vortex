package com.example.cast

import android.net.Uri

enum class StreamSourceType {
    DIRECT_STREAM,
    WEB_EMBED
}

data class ClassifiedSource(
    val type: StreamSourceType,
    val mimeType: String,
    val isDirectPlayable: Boolean,
    val explanation: String
)

object MediaSourceClassifier {

    private val DIRECT_EXTENSIONS = listOf(
        ".m3u8", ".mp4", ".m3u", ".webm", ".mkv", ".mpd", ".ts"
    )

    private val EMBED_DOMAINS = listOf(
        "nxsha.space",
        "modiplay.xyz",
        "primesrc.me",
        "ythd.org",
        "vixsrc.to",
        "vidcore.io",
        "vidrock.net",
        "vaplayer.ru",
        "videasy.net",
        "nhdapi.com"
    )

    /**
     * Inspect a URL and determine whether it represents a direct media stream or an HTML web embed.
     */
    fun classify(url: String): ClassifiedSource {
        val lower = url.lowercase().trim()

        // 1. Direct HLS playlist
        if (lower.contains(".m3u8") || lower.contains(".m3u")) {
            return ClassifiedSource(
                type = StreamSourceType.DIRECT_STREAM,
                mimeType = "application/x-mpegURL",
                isDirectPlayable = true,
                explanation = "Direct HLS Stream (.m3u8)"
            )
        }

        // 2. Direct MP4 container
        if (lower.contains(".mp4")) {
            return ClassifiedSource(
                type = StreamSourceType.DIRECT_STREAM,
                mimeType = "video/mp4",
                isDirectPlayable = true,
                explanation = "Direct MP4 Video Stream"
            )
        }

        // 3. Direct WebM container
        if (lower.contains(".webm")) {
            return ClassifiedSource(
                type = StreamSourceType.DIRECT_STREAM,
                mimeType = "video/webm",
                isDirectPlayable = true,
                explanation = "Direct WebM Video Stream"
            )
        }

        // 4. Direct DASH MPD stream
        if (lower.contains(".mpd")) {
            return ClassifiedSource(
                type = StreamSourceType.DIRECT_STREAM,
                mimeType = "application/dash+xml",
                isDirectPlayable = true,
                explanation = "DASH Manifest (.mpd)"
            )
        }

        // 5. Check if it's a known web embed domain or contains embed path
        var isKnownEmbed = false
        try {
            val uri = Uri.parse(lower)
            val host = uri.host ?: ""
            for (domain in EMBED_DOMAINS) {
                if (host == domain || host.endsWith(".$domain")) {
                    isKnownEmbed = true
                    break
                }
            }
        } catch (_: Exception) {}

        if (isKnownEmbed || lower.contains("/embed/") || lower.contains("/embed?") || lower.contains(".html") || lower.contains(".php")) {
            return ClassifiedSource(
                type = StreamSourceType.WEB_EMBED,
                mimeType = "text/html",
                isDirectPlayable = false,
                explanation = "Web Embed Player (HTML/Iframe)"
            )
        }

        // Check for any other direct file extension
        for (ext in DIRECT_EXTENSIONS) {
            if (lower.contains(ext)) {
                return ClassifiedSource(
                    type = StreamSourceType.DIRECT_STREAM,
                    mimeType = "video/*",
                    isDirectPlayable = true,
                    explanation = "Direct Media Stream"
                )
            }
        }

        // Default to Web Embed if it's an HTTP/HTTPS webpage URL
        return ClassifiedSource(
            type = StreamSourceType.WEB_EMBED,
            mimeType = "text/html",
            isDirectPlayable = false,
            explanation = "Web Stream URL"
        )
    }

    /**
     * Determine MIME type for media player intents.
     */
    fun resolveMimeType(url: String): String {
        return classify(url).mimeType
    }
}
