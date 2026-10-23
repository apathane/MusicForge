package dev.musicforge.recommendation

import dev.musicforge.data.db.ArtistStat
import dev.musicforge.data.db.LibraryCounts
import dev.musicforge.data.db.MusicDao
import dev.musicforge.data.db.PlaylistSummary
import dev.musicforge.data.db.Song
import dev.musicforge.data.db.SongStat
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
        val hour = java.util.Calendar.getInstance().apply { timeInMillis = start }.get(java.util.Calendar.HOUR_OF_DAY)
        val hours = (hour - 1..hour + 1).map { it.mod(24) }
        val daySeed = ((start + java.util.TimeZone.getDefault().getOffset(start)) / DAY_MS).toInt()
        return combine(
            dao.historySongs(16),
            withCredits(dao.topSongs(month, 40)),
            dao.recentDownloads(5),
            dao.counts(),
            dao.playlists(),
            dao.listenedMs(week),
            dao.topArtists(week, 1),
            withCredits(dao.songsAtHours(hours)),
            dao.forgottenLikes(likedBefore = start - 45 * DAY_MS, notPlayedSince = start - 30 * DAY_MS),
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
                aroundNow = values[7] as List<Pair<Song, Int>>,
                hour = hour,
                forgotten = values[8] as List<Song>,
                daySeed = daySeed,
            )
        }
    }

    /** Play stats carry no artist credits; shelves need them for their per-artist cap. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun withCredits(stats: Flow<List<SongStat>>): Flow<List<Pair<Song, Int>>> = stats.flatMapLatest { rows ->
        if (rows.isEmpty()) return@flatMapLatest flowOf(emptyList())
        dao.songs(rows.map { it.song.id }).map { songs ->
            val byId = songs.associateBy { it.id }
            rows.mapNotNull { row -> byId[row.song.id]?.let { it to row.plays } }
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
