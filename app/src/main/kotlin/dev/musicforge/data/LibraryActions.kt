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
    private val scope: CoroutineScope,
) {
    fun setLiked(item: MediaItem, liked: Boolean) = scope.launch {
        recorder.ensureSong(item)
        db.dao().setLiked(item.mediaId, liked, if (liked) System.currentTimeMillis() else null)
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
    }

    fun removeFromPlaylist(playlistId: String, songId: String) = scope.launch {
        db.dao().removeFromPlaylist(playlistId, songId)
        db.dao().touchPlaylist(playlistId, System.currentTimeMillis())
    }

    fun renamePlaylist(id: String, name: String) = scope.launch {
        name.trim().takeIf { it.isNotEmpty() }?.let { db.dao().renamePlaylist(id, it, System.currentTimeMillis()) }
    }

    /** The playlist goes; the songs stay in the library (their map rows cascade away). */
    fun deletePlaylist(id: String) = scope.launch { db.dao().deletePlaylist(id) }

    /** Same id style as Metrolist's local playlists (a UUID), so our backups read back there too. */
    fun createPlaylist(name: String, firstSong: MediaItem? = null) = scope.launch {
        val id = UUID.randomUUID().toString()
        db.dao().insert(PlaylistEntity(id = id, name = name.trim().ifEmpty { "New playlist" }, isLocal = true))
        firstSong?.let { addToPlaylist(it, id).join() }
    }
}
