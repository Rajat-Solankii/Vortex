package com.rajatsolankii.vortex.features.vortex

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The Vortex embed servers. Each one renders a full web player for a TMDB id, so
 * Vortex streams are played in [VortexEmbedPlayer] rather than the native video player.
 */
enum class VortexServer(
    val displayName: String,
    val recommendedForAnime: Boolean = false,
) {
    SERVER1("Vortex Server 1"),
    SERVER2("Vortex Server 2", recommendedForAnime = true),
    SERVER3("Vortex Server 3"),
    SERVER4("Vortex Server 4"),
    SERVER5("Vortex Server 5"),
    SERVER6("Vortex Server 6"),
    SERVER7("Vortex Server 7"),
    ;

    fun embedUrl(target: VortexEmbedTarget): String {
        val id = target.tmdbId
        val s = target.season
        val e = target.episode
        val tv = target.isSeries
        return when (this) {
            SERVER1 -> if (tv) "https://nxsha.space/embed/tv/$id/$s/$e" else "https://nxsha.space/embed/movie/$id"
            SERVER2 -> if (tv) "https://rozgarlelo.modiplay.xyz/embed/tmdb/tv?id=$id&s=$s&e=$e" else "https://rozgarlelo.modiplay.xyz/embed/tmdb/movie?id=$id"
            SERVER3 -> if (tv) "https://primesrc.me/embed/tv?tmdb=$id&season=$s&episode=$e" else "https://primesrc.me/embed/movie?tmdb=$id"
            SERVER4 -> if (tv) "https://ythd.org/embed/$id/$s-$e" else "https://ythd.org/embed/$id"
            SERVER5 -> if (tv) "https://vixsrc.to/embed/tv/$id/$s/$e" else "https://vixsrc.to/embed/movie/$id"
            SERVER6 -> if (tv) "https://vidcore.io/embed/tv/$id/$s/$e" else "https://vidcore.io/embed/movie/$id"
            SERVER7 -> if (tv) "https://vidrock.net/embed/tv/$id/$s/$e" else "https://vidrock.net/embed/movie/$id"
        }
    }

    val host: String
        get() = VortexAdShield.hostOf(embedUrl(VortexEmbedTarget(tmdbId = 1, isSeries = false))).orEmpty()
}

/** What an embed server should play: a movie, or one episode of a series. */
data class VortexEmbedTarget(
    val tmdbId: Int,
    val isSeries: Boolean,
    val season: Int = 1,
    val episode: Int = 1,
)

/** One server choice inside the embed player. */
data class VortexEmbedSource(
    val server: VortexServer,
    val url: String,
)

object VortexServers {
    private val serverHosts: Set<String> by lazy { VortexServer.entries.map { it.host }.toSet() }

    // Embed URL -> what it plays, so the player can offer every server for the same title.
    private val targetsByUrl = MutableStateFlow<Map<String, VortexEmbedTarget>>(emptyMap())

    /** True when [url] points at one of the Vortex embed servers (not a direct media file). */
    fun isEmbedUrl(url: String?): Boolean {
        val host = url?.let(VortexAdShield::hostOf) ?: return false
        return serverHosts.any { serverHost -> host == serverHost || host.endsWith(".$serverHost") }
    }

    fun serverFor(url: String): VortexServer? {
        val host = VortexAdShield.hostOf(url) ?: return null
        return VortexServer.entries.firstOrNull { host == it.host || host.endsWith(".${it.host}") }
    }

    /** Server the player opens first: the one the viewer picked last in this session. */
    var preferredServer: VortexServer = VortexServer.SERVER1

    fun preferredSource(target: VortexEmbedTarget): VortexEmbedSource =
        sourcesFor(target).first { it.server == preferredServer }

    fun sourcesFor(target: VortexEmbedTarget): List<VortexEmbedSource> =
        VortexServer.entries.map { server ->
            val url = server.embedUrl(target)
            remember(url, target)
            VortexEmbedSource(server = server, url = url)
        }

    /**
     * Every server for whatever [url] plays. Falls back to just [url] when it was not
     * produced in this session (for example a link restored from the reuse cache).
     */
    fun sourcesForUrl(url: String): List<VortexEmbedSource> {
        val target = targetsByUrl.value[url] ?: parseTarget(url)
        if (target != null) return sourcesFor(target)
        val server = serverFor(url) ?: VortexServer.SERVER1
        return listOf(VortexEmbedSource(server = server, url = url))
    }

    /**
     * Embed target for a play request. Uses a `tmdb:` id from the video or its parent title,
     * and otherwise converts an IMDb id through the Vortex API.
     */
    suspend fun resolveTarget(
        videoId: String,
        parentMetaId: String?,
        type: String,
        season: Int?,
        episode: Int?,
    ): VortexEmbedTarget? {
        val isSeries = type.equals("series", true) || type.equals("tv", true)
        targetFromVideoId(videoId, type)?.let { direct ->
            return if (isSeries && season != null && episode != null) direct.copy(season = season, episode = episode) else direct
        }
        val tmdbId = listOfNotNull(parentMetaId, videoId).firstNotNullOfOrNull { id ->
            when {
                id.startsWith("tmdb:") -> id.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
                id.startsWith("tt") -> com.rajatsolankii.vortex.features.tmdb.TmdbService
                    .ensureTmdbId(id.substringBefore(':'), type)?.toIntOrNull()
                else -> null
            }
        } ?: return null
        val parts = videoId.split(':')
        return VortexEmbedTarget(
            tmdbId = tmdbId,
            isSeries = isSeries,
            season = season ?: parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: 1,
            episode = episode ?: parts.lastOrNull()?.toIntOrNull() ?: 1,
        )
    }

    /** What [url] plays, when it is a Vortex embed URL this app built or can parse. */
    fun targetForUrl(url: String): VortexEmbedTarget? = targetsByUrl.value[url] ?: parseTarget(url)

    /** Reads `tmdb:123`, `tmdb:123:1:2`, `movie:123` or a bare id into an embed target. */
    fun targetFromVideoId(videoId: String, type: String): VortexEmbedTarget? {
        val parts = videoId.removePrefix("tmdb:").removePrefix("movie:").removePrefix("series:").split(':')
        val tmdbId = parts.firstOrNull()?.trim()?.toIntOrNull() ?: return null
        val isSeries = type.equals("series", true) || type.equals("tv", true)
        return VortexEmbedTarget(
            tmdbId = tmdbId,
            isSeries = isSeries,
            season = parts.getOrNull(1)?.toIntOrNull() ?: 1,
            episode = parts.getOrNull(2)?.toIntOrNull() ?: 1,
        )
    }

    private fun remember(url: String, target: VortexEmbedTarget) {
        targetsByUrl.update { current ->
            val base = if (current.size > 500) emptyMap() else current
            base + (url to target)
        }
    }

    /** Best-effort reverse of [VortexServer.embedUrl] across all supported server URL patterns. */
    private fun parseTarget(url: String): VortexEmbedTarget? {
        val cleanUrl = url.substringBefore('#')
        val path = cleanUrl.substringAfter("://").substringAfter('/').substringBefore('?')
        val query = cleanUrl.substringAfter('?', "")
        val segments = path.split('/').filter(String::isNotBlank)

        val tvIndex = segments.indexOf("tv")
        if (tvIndex >= 0 && segments.size >= tvIndex + 4) {
            val id = segments[tvIndex + 1].toIntOrNull() ?: return null
            val s = segments[tvIndex + 2].toIntOrNull() ?: return null
            val e = segments[tvIndex + 3].toIntOrNull() ?: return null
            return VortexEmbedTarget(id, isSeries = true, season = s, episode = e)
        }
        val movieIndex = segments.indexOf("movie")
        if (movieIndex >= 0 && segments.size >= movieIndex + 2) {
            val id = segments[movieIndex + 1].toIntOrNull() ?: return null
            return VortexEmbedTarget(id, isSeries = false)
        }

        // Query-param embeds such as SERVER2 (?id=&s=&e=) and SERVER3 (?tmdb=&season=&episode=).
        val params = query.split('&')
            .mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) null else pair.substring(0, eq).lowercase() to pair.substring(eq + 1)
            }
            .toMap()
        val queryId = (params["tmdb"] ?: params["id"])?.toIntOrNull()
        if (queryId != null) {
            val isTv = "tv" in segments || params.containsKey("s") || params.containsKey("season")
            val s = (params["s"] ?: params["season"])?.toIntOrNull() ?: 1
            val e = (params["e"] ?: params["episode"])?.toIntOrNull() ?: 1
            return VortexEmbedTarget(queryId, isSeries = isTv, season = s, episode = e)
        }

        // SERVER4: /embed/{id} or /embed/{id}/{s}-{e}
        val embedIndex = segments.indexOf("embed")
        if (embedIndex >= 0 && segments.size >= embedIndex + 2) {
            val id = segments[embedIndex + 1].toIntOrNull() ?: return null
            val epPart = segments.getOrNull(embedIndex + 2)
            if (epPart != null && '-' in epPart) {
                val s = epPart.substringBefore('-').toIntOrNull() ?: 1
                val e = epPart.substringAfter('-').toIntOrNull() ?: 1
                return VortexEmbedTarget(id, isSeries = true, season = s, episode = e)
            }
            return VortexEmbedTarget(id, isSeries = false)
        }

        return null
    }
}
