package dev.musicforge.recommendation

import dev.musicforge.data.db.ArtistStat
import dev.musicforge.data.db.LibraryCounts
import dev.musicforge.data.db.MusicDao
import dev.musicforge.data.db.PlaylistSummary
import dev.musicforge.data.db.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Reads the listener's signals from Room; Room runs these queries off the main thread. */
class HomeRepository(private val dao: MusicDao, private val now: () -> Long = System::currentTimeMillis) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun signals(): Flow<LocalSignals> {
        val start = now()
        val month = start - 30 * DAY_MS
        val week = start - 7 * DAY_MS
        // topSongs carries no artist credits; rotation needs them for its per-artist cap.
        val rotation = dao.topSongs(month, 40).flatMapLatest { stats ->
            if (stats.isEmpty()) return@flatMapLatest flowOf(emptyList())
            dao.songs(stats.map { it.song.id }).map { songs ->
                val byId = songs.associateBy { it.id }
                stats.mapNotNull { stat -> byId[stat.song.id]?.let { it to stat.plays } }
            }
        }
        return combine(
            dao.historySongs(16),
            rotation,
            dao.recentDownloads(5),
            dao.counts(),
            dao.playlists(),
            dao.listenedMs(week),
            dao.topArtists(week, 1),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            LocalSignals(
                recent = values[0] as List<Song>,
                rotation = values[1] as List<Pair<Song, Int>>,
                downloads = values[2] as List<Song>,
                downloadCount = (values[3] as LibraryCounts).downloaded,
                likedCount = (values[3] as LibraryCounts).liked,
                playlists = values[4] as List<PlaylistSummary>,
                weekMs = values[5] as Long,
                topArtistThisWeek = (values[6] as List<ArtistStat>).firstOrNull()?.artist?.name,
            )
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
