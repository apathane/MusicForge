package dev.musicforge.playback

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import dev.musicforge.AppGraph

/** Turns a spoken or typed request into something to play: your library first, then YouTube Music. */
object SearchPlay {
    suspend fun resolve(graph: AppGraph, query: String): List<MediaItem> {
        val q = query.trim()
        if (q.isEmpty()) {
            // "Play music": pick up where you left off.
            return graph.database.dao().searchLibrary("", 25).map { it.toMediaItem() }
        }
        val local = graph.database.dao().searchLibrary(q, 25)
        if (local.isNotEmpty()) return local.map { it.toMediaItem() }
        return YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
            ?.filterIsInstance<SongItem>()?.take(1)?.map { it.toMediaItem() }.orEmpty()
    }
}
