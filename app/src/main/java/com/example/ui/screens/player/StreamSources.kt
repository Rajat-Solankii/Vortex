package com.example.ui.screens.player

import com.example.data.model.MediaType

enum class StreamServer(val displayName: String, val description: String, val isRecommendedForAnime: Boolean = false) {
    SERVER1("Server 1", "Server 1", false),
    SERVER2("Server 2", "Server 2", true),
    SERVER3("Server 3", "Server 3", false),
    SERVER4("Server 4", "Server 4", false),
    SERVER5("Server 5", "Server 5", false),
    SERVER6("Server 6", "Server 6", false),
    SERVER7("Server 7", "Server 7", false)
}

object StreamUrlBuilder {
    fun buildWebEmbedUrl(
        server: StreamServer,
        mediaId: Int,
        mediaType: MediaType,
        season: Int = 1,
        episode: Int = 1
    ): String {
        val isTv = mediaType == MediaType.TV
        
        // Use generic embed structures for the 7 servers
        return when (server) {
            StreamServer.SERVER1 -> if (isTv) "https://nxsha.space/embed/tv/$mediaId/$season/$episode" else "https://nxsha.space/embed/movie/$mediaId"
            StreamServer.SERVER2 -> if (isTv) "https://rozgarlelo.modiplay.xyz/embed/tmdb/tv?id=$mediaId&s=$season&e=$episode" else "https://rozgarlelo.modiplay.xyz/embed/tmdb/movie?id=$mediaId"
            StreamServer.SERVER3 -> if (isTv) "https://primesrc.me/embed/tv?tmdb=$mediaId&season=$season&episode=$episode" else "https://primesrc.me/embed/movie?tmdb=$mediaId"
            StreamServer.SERVER4 -> if (isTv) "https://ythd.org/embed/$mediaId/$season-$episode" else "https://ythd.org/embed/$mediaId"
            StreamServer.SERVER5 -> if (isTv) "https://vixsrc.to/embed/tv/$mediaId/$season/$episode" else "https://vixsrc.to/embed/movie/$mediaId"
            StreamServer.SERVER6 -> if (isTv) "https://vidcore.io/embed/tv/$mediaId/$season/$episode" else "https://vidcore.io/embed/movie/$mediaId"
            StreamServer.SERVER7 -> if (isTv) "https://vidrock.net/embed/tv/$mediaId/$season/$episode" else "https://vidrock.net/embed/movie/$mediaId"
        }
    }
}
