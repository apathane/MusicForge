package dev.musicforge.playback

import androidx.media3.common.MediaItem
import dev.musicforge.data.db.ArtistEntity
import dev.musicforge.data.db.Event
import dev.musicforge.data.db.MusicDatabase
import dev.musicforge.data.db.SongArtistMap
import dev.musicforge.data.db.SongEntity
import androidx.room.withTransaction

/**
 * Writes what was actually listened to: the song (if new), its artists, a play event, and
 * accumulated play time. A play counts after 30 s or half the track, whichever is first,
 * the same threshold the upstream apps use for their stats.
 */
class PlayRecorder(private val db: MusicDatabase) {
    suspend fun ensureSong(item: MediaItem) = db.withTransaction {
        val dao = db.dao()
        val meta = item.mediaMetadata
        val existing = dao.songEntity(item.mediaId)
        if (existing == null) {
            dao.upsert(
                SongEntity(
                    id = item.mediaId,
                    title = meta.title?.toString().orEmpty(),
                    duration = meta.durationSec ?: -1,
                    thumbnailUrl = meta.artworkUri?.toString(),
                    albumId = meta.albumId,
                    albumName = meta.albumTitle?.toString(),
                ),
            )
            meta.credits.forEachIndexed { i, credit ->
                val artistId = credit.id ?: "name:${credit.name}"
                if (db.dao().artist(artistId) == null) dao.upsert(ArtistEntity(id = artistId, name = credit.name))
                dao.upsert(SongArtistMap(item.mediaId, artistId, i))
            }
        }
    }

    suspend fun recordPlay(item: MediaItem, playedMs: Long, durationMs: Long) = db.withTransaction {
        if (playedMs <= 0) return@withTransaction
        ensureSong(item)
        val dao = db.dao()
        dao.addPlayTime(item.mediaId, playedMs)
        val threshold = if (durationMs > 0) minOf(30_000L, durationMs / 2) else 30_000L
        if (playedMs >= threshold) {
            dao.insert(Event(songId = item.mediaId, timestamp = System.currentTimeMillis(), playTime = playedMs))
        }
    }
}
