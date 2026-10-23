package dev.musicforge.recommendation

import dev.musicforge.data.db.ArtistStat
import dev.musicforge.data.db.LibraryCounts
import dev.musicforge.data.db.MusicDao
import dev.musicforge.data.db.PlaylistSummary
import dev.musicforge.data.db.RecommendationFeedback
import dev.musicforge.data.db.Song
import dev.musicforge.data.db.SongStat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Reads the listener's signals from Room; Room runs these queries off the main thread. */
class HomeRepository(private val dao: MusicDao, private val now: () -> Long = System::currentTimeMillis) {

    /**
     * The signals, rebuilt at every hour boundary: "around this hour", the day's forgotten-likes
     * pick and the expiry of corrections all depend on the time, and Home's ViewModel lives for
     * the whole process.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun signals(): Flow<LocalSignals> = flow {
        while (true) {
            val t = now()
            emit(t)
            delay(HOUR_MS - t % HOUR_MS + 1_000)
        }
    }.flatMapLatest(::signalsAt)

    @Suppress("UNCHECKED_CAST")
    private fun signalsAt(start: Long): Flow<LocalSignals> {
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
            dao.forgottenLikes(likedBefore = start - 45 * DAY_MS, notPlayedSince = start - 30 * DAY_MS, seed = daySeed),
            dao.activeFeedback(start),
        ) { values ->
            val feedback = values[9] as List<RecommendationFeedback>
            val counts = values[3] as LibraryCounts
            LocalSignals(
                recent = values[0] as List<Song>,
                rotation = values[1] as List<Pair<Song, Int>>,
                downloads = values[2] as List<Song>,
                downloadCount = counts.downloaded,
                likedCount = counts.liked,
                isLibraryEmpty = counts.songs + counts.liked + counts.playlists + counts.events + counts.downloaded == 0,
                playlists = values[4] as List<PlaylistSummary>,
                weekMs = values[5] as Long,
                topArtistThisWeek = (values[6] as List<ArtistStat>).firstOrNull()?.artist?.name,
                aroundNow = values[7] as List<Pair<Song, Int>>,
                hour = hour,
                forgotten = values[8] as List<Song>,
                daySeed = daySeed,
                hidden = feedback.mapNotNullTo(HashSet()) { f -> f.mediaId.takeIf { f.action == RecommendationFeedback.Action.HIDE.name } },
                lessOf = feedback.mapNotNullTo(HashSet()) { f -> f.artistId.takeIf { f.action == RecommendationFeedback.Action.LESS_OF_ARTIST.name } },
            )
        }
    }

    /** "Not for me": the song leaves Home until the listener resets it. */
    suspend fun hide(songId: String) =
        dao.insert(RecommendationFeedback(mediaId = songId, action = RecommendationFeedback.Action.HIDE.name, createdAt = now()))

    /** "Less of this artist": off the suggestion shelves for [LESS_OF_DAYS] days. */
    suspend fun lessOf(artistId: String) = now().let { t ->
        dao.insert(
            RecommendationFeedback(
                artistId = artistId, action = RecommendationFeedback.Action.LESS_OF_ARTIST.name,
                createdAt = t, expiresAt = t + LESS_OF_DAYS * DAY_MS,
            ),
        )
    }

    suspend fun reset() = dao.clearFeedback()

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
        const val HOUR_MS = 60 * 60 * 1000L
        const val DAY_MS = 24 * HOUR_MS
        const val LESS_OF_DAYS = 30L
    }
}
