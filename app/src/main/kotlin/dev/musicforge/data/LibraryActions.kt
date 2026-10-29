package dev.musicforge.data

import androidx.media3.common.MediaItem
import androidx.room.withTransaction
import com.metrolist.innertube.YouTube
import dev.musicforge.data.db.MusicDatabase
import dev.musicforge.data.db.PlaylistEntity
import dev.musicforge.data.db.PlaylistSongMap
import dev.musicforge.playback.PlayRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/** Library edits from the UI. Local first; mirrored to YouTube Music when signed in. */
class LibraryActions(
    private val db: MusicDatabase,
    private val recorder: PlayRecorder,
    private val appScope: CoroutineScope,
    /** Called after a song is liked (auto-download, if the user wants it). */
    private val onLiked: suspend (MediaItem) -> Unit = {},
) {
    /** One writer at a time, in the order edits were made: two quick drags can't land swapped. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(appScope.coroutineContext + kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1))

    fun setLiked(item: MediaItem, liked: Boolean) = scope.launch {
        recorder.ensureSong(item)
        db.dao().setLiked(item.mediaId, liked, if (liked) System.currentTimeMillis() else null)
        if (liked) onLiked(item)
        if (YouTube.cookie != null) {
            YouTube.likeVideo(item.mediaId, liked).onFailure { Timber.w(it, "YouTube like sync failed") }
        }
    }

    fun addToPlaylist(item: MediaItem, playlistId: String) = scope.launch {
        recorder.ensureSong(item)
        db.withTransaction {
            val dao = db.dao()
            dao.insert(PlaylistSongMap(playlistId = playlistId, songId = item.mediaId, position = dao.lastPosition(playlistId) + 1))
            dao.touchPlaylist(playlistId, System.currentTimeMillis())
        }
        // A playlist that lives in the YouTube Music account gets the change there too.
        remoteId(playlistId)?.let { browseId ->
            YouTube.addToPlaylist(browseId, item.mediaId).onFailure { Timber.w(it, "YouTube playlist add failed") }
        }
    }

    fun removeFromPlaylist(playlistId: String, songId: String) = scope.launch {
        // YouTube removes by the entry's setVideoId, known once the playlist has been synced.
        val setVideoId = db.dao().setVideoId(playlistId, songId)
        db.dao().removeFromPlaylist(playlistId, songId)
        db.dao().touchPlaylist(playlistId, System.currentTimeMillis())
        val browseId = remoteId(playlistId)
        if (browseId != null && setVideoId != null) {
            YouTube.removeFromPlaylist(browseId, songId, setVideoId).onFailure { Timber.w(it, "YouTube playlist remove failed") }
        }
    }

    /** The account playlist behind a local one, when signed in and it's editable there. */
    private suspend fun remoteId(playlistId: String): String? {
        if (YouTube.cookie == null) return null
        val p = db.dao().playlistEntity(playlistId) ?: return null
        return p.browseId?.takeIf { p.isEditable }?.removePrefix("VL")
    }

    /** Moves one song within a playlist and renumbers positions 0..n-1 in one transaction. */
    fun movePlaylistSong(playlistId: String, from: Int, to: Int) = scope.launch {
        db.withTransaction {
            val dao = db.dao()
            val ids = dao.playlistMapIds(playlistId).toMutableList()
            if (from !in ids.indices || to !in ids.indices) return@withTransaction
            ids.add(to, ids.removeAt(from))
            ids.forEachIndexed { i, mapId -> dao.setMapPosition(mapId, i) }
            dao.touchPlaylist(playlistId, System.currentTimeMillis())
        }
    }

    fun renamePlaylist(id: String, name: String) = scope.launch {
        name.trim().takeIf { it.isNotEmpty() }?.let { db.dao().renamePlaylist(id, it, System.currentTimeMillis()) }
    }

    /** The playlist goes; the songs stay in the library (their map rows cascade away). */
    fun deletePlaylist(id: String) = scope.launch { db.dao().deletePlaylist(id) }

    /** Drops every play checkpoint for this track — the "Remove from history" row. */
    fun removeFromHistory(item: MediaItem) = scope.launch { db.dao().deleteEvents(item.mediaId) }

    /** Same id style as Metrolist's local playlists (a UUID), so our backups read back there too. */
    fun createPlaylist(name: String, firstSong: MediaItem? = null) = scope.launch {
        val id = UUID.randomUUID().toString()
        db.dao().insert(PlaylistEntity(id = id, name = name.trim().ifEmpty { "New playlist" }, isLocal = true))
        firstSong?.let { addToPlaylist(it, id).join() }
    }
}
