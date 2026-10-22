package dev.musicforge.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.musicforge.data.db.MusicDao
import kotlinx.coroutines.flow.first

/**
 * What Android Auto, car head units and watches can browse: a short, glanceable tree.
 * ```
 * root ─ Liked songs
 *      ─ Downloads          (plays with no signal)
 *      ─ Recently played
 *      ─ Playlists ─ <playlist> ─ songs
 * ```
 */
class BrowseTree(private val dao: MusicDao) {
    fun root(): MediaItem = folder(ROOT, "MusicForge", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    suspend fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> listOf(
            folder(LIKED, "Liked songs", MediaMetadata.MEDIA_TYPE_PLAYLIST),
            folder(DOWNLOADS, "Downloads", MediaMetadata.MEDIA_TYPE_PLAYLIST),
            folder(RECENT, "Recently played", MediaMetadata.MEDIA_TYPE_PLAYLIST),
            folder(PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        )
        parentId == LIKED -> dao.likedSongs().first().take(LIMIT).map { it.toMediaItem() }
        parentId == DOWNLOADS -> dao.downloadedSongs().first().take(LIMIT).map { it.toMediaItem() }
        parentId == RECENT -> dao.historySongs(LIMIT).first().map { it.toMediaItem() }
        parentId == PLAYLISTS -> dao.playlists().first().map {
            folder(PLAYLIST_PREFIX + it.playlist.id, it.playlist.name, MediaMetadata.MEDIA_TYPE_PLAYLIST, it.playlist.thumbnailUrl)
        }
        parentId.startsWith(PLAYLIST_PREFIX) -> dao.playlistSongs(parentId.removePrefix(PLAYLIST_PREFIX)).first().take(LIMIT).map { it.toMediaItem() }
        else -> emptyList()
    }

    /** A playable item for an id a controller sent back, with its metadata restored. */
    suspend fun song(id: String): MediaItem? = dao.song(id).first()?.toMediaItem()

    suspend fun search(query: String): List<MediaItem> = dao.searchLibrary(query, LIMIT).map { it.toMediaItem() }

    private fun folder(id: String, title: String, type: Int, art: String? = null) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(type)
                .apply { art?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                .build(),
        )
        .build()

    companion object {
        const val ROOT = "root"
        const val LIKED = "liked"
        const val DOWNLOADS = "downloads"
        const val RECENT = "recent"
        const val PLAYLISTS = "playlists"
        const val PLAYLIST_PREFIX = "playlist:"
        private const val LIMIT = 200
    }
}
