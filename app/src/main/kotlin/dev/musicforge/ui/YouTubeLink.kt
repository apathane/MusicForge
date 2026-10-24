package dev.musicforge.ui

import java.net.URI

/** A YouTube or YouTube Music link pasted into search, reduced to what it points at. */
sealed interface YouTubeLink {
    data class Song(val videoId: String) : YouTubeLink
    data class Playlist(val playlistId: String) : YouTubeLink
    data class Album(val browseId: String) : YouTubeLink
    data class Artist(val channelId: String) : YouTubeLink

    companion object {
        private val hosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

        fun parse(text: String): YouTubeLink? {
            val raw = text.trim().let { if ("://" in it) it else "https://$it" }
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (host !in hosts) return null
            val path = uri.path.orEmpty().trimEnd('/')
            val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
                val (k, v) = it.split('=', limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
                k to v
            }.toMap()
            val segments = path.split('/').filter { it.isNotEmpty() }
            return when {
                host == "youtu.be" -> segments.firstOrNull()?.let(::Song)
                // A song in a playlist link plays the song: that is what was being shared.
                query["v"] != null -> Song(query.getValue("v"))
                segments.firstOrNull() == "shorts" -> segments.getOrNull(1)?.let(::Song)
                query["list"] != null -> Playlist(query.getValue("list"))
                segments.firstOrNull() == "browse" -> segments.getOrNull(1)?.let { id ->
                    when {
                        id.startsWith("MPRE") -> Album(id)
                        id.startsWith("UC") -> Artist(id)
                        id.startsWith("VL") -> Playlist(id.removePrefix("VL"))
                        else -> null
                    }
                }
                segments.firstOrNull() == "channel" -> segments.getOrNull(1)?.let(::Artist)
                else -> null
            }
        }
    }
}
