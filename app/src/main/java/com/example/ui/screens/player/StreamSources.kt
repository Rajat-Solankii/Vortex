package com.example.ui.screens.player

import com.example.data.model.MediaType

enum class StreamServer(val displayName: String, val description: String) {
    SERVER1("Server 1", "vaplayer.ru/embed/..."),
    SERVER2("Server 2", "player.videasy.net/..."),
    SERVER3("Server 3", "nhdapi.com/embed/...")
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
        return when (server) {
            StreamServer.SERVER1 -> {
                if (isTv) {
                    "https://vaplayer.ru/embed/tv/$mediaId/$season/$episode"
                } else {
                    "https://vaplayer.ru/embed/movie/$mediaId"
                }
            }
            StreamServer.SERVER2 -> {
                if (isTv) {
                    "https://player.videasy.net/tv/$mediaId/$season/$episode"
                } else {
                    "https://player.videasy.net/movie/$mediaId"
                }
            }
            StreamServer.SERVER3 -> {
                if (isTv) {
                    "https://nhdapi.com/embed/tv/$mediaId/$season/$episode"
                } else {
                    "https://nhdapi.com/embed/movie/$mediaId"
                }
            }
        }
    }
}
