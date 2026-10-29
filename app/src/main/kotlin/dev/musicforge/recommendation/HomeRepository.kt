package dev.musicforge.recommendation

import dev.musicforge.data.db.ArtistStat
import dev.musicforge.data.db.LibraryCounts
import dev.musicforge.data.db.MusicDao
import dev.musicforge.data.db.PlaylistSummary
import dev.musicforge.data.db.RecommendationFeedback
import dev.musicforge.data.db.RemoteHistoryEntity
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

/** One play, local or remote: the song id and when it went down. */
data class RecentPlay(
    val songId: String,
    /** Epoch ms of the play: the event timestamp for local, the sync time for remote. */
    val playedAt: Long,
    /** True when this came from the YouTube Music account, not a play here. */
    val fromRemote: Boolean,
)

/**
 * Merge local plays and remote YouTube Music plays into one newest-first list. A remote play,
 * dated at sync time, is overridden by a newer local play of the same song. Dedupes by song id
 * and keeps [limit] entries. Local wins ties: a play here at the same instant a remote sync
 * landed is the one we show.
 */
fun mergeRecent(local: List<RecentPlay>, remote: List<RecentPlay>, limit: Int = 16): List<RecentPlay> {
    val bySong = LinkedHashMap<String, RecentPlay>()
    for (entry in local + remote) {
        val existing = bySong[entry.songId]
        if (existing == null || entry.playedAt > existing.playedAt) bySong[entry.songId] = entry
    }
    return bySong.values.sortedByDescending { it.playedAt }.take(limit)
}

/** Reads the listener's signals from Room; Room runs these queries off the main thread. */
class HomeRepository(
    private val dao: MusicDao,
    private val paused: Flow<Boolean> = flowOf(false),
    private val now: () -> Long = System::currentTimeMillis,
) {

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
    }.flatMapLatest(::signalsAt).combine(paused) { signals, isPaused -> signals.copy(isPaused = isPaused) }

    /** Latest 16 plays, merging local events with remote (another device) history. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun recentFlow(): Flow<Pair<List<Song>, Boolean>> =
        combine(dao.playedSongs(16), dao.remoteHistory()) { local, remote ->
            val merged = mergeRecent(
                local.map { RecentPlay(it.song.id, it.lastPlayed, fromRemote = false) },
                remote.map { RecentPlay(it.songId, it.syncedAt, fromRemote = true) },
            )
            merged.map { it.songId } to (merged.firstOrNull()?.fromRemote == true)
        }.flatMapLatest { (ids, remoteOnly) ->
            if (ids.isEmpty()) {
                flowOf(emptyList<Song>() to remoteOnly)
            } else {
                dao.songs(ids).map { songs ->
                    val order = ids.withIndex().associate { it.value to it.index }
                    songs.sortedBy { order[it.id] ?: Int.MAX_VALUE } to remoteOnly
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun signalsAt(start: Long): Flow<LocalSignals> {
        val month = start - 30 * DAY_MS
        val week = start - 7 * DAY_MS
        val hour = java.util.Calendar.getInstance().apply { timeInMillis = start }.get(java.util.Calendar.HOUR_OF_DAY)
        val hours = (hour - 1..hour + 1).map { it.mod(24) }
        val daySeed = ((start + java.util.TimeZone.getDefault().getOffset(start)) / DAY_MS).toInt()
        return combine(
            recentFlow(),
            withCredits(dao.topSongs(month, 40)),
            dao.recentDownloads(5),
            dao.counts(),
            dao.playlists(),
            dao.listenedMs(week),
            dao.topArtists(week, 1),
            dao.topArtists(week, 8),
            kotlinx.coroutines.flow.flow { emit(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dao.playDays(14) }) },
            withCredits(dao.songsAtHours(hours)),
            dao.forgottenLikes(likedBefore = start - 45 * DAY_MS, notPlayedSince = start - 30 * DAY_MS, seed = daySeed),
            dao.activeFeedback(start),
        ) { values ->
            val (recent, remoteOnly) = values[0] as Pair<List<Song>, Boolean>
            val feedback = values[11] as List<RecommendationFeedback>
            val counts = values[3] as LibraryCounts
            val playDays = values[8] as List<String>
            val streak = computeStreak(playDays, start)
            LocalSignals(
                recent = recent,
                recentReason = if (remoteOnly) "Played on another device" else "Recently played",
                rotation = values[1] as List<Pair<Song, Int>>,
                downloads = values[2] as List<Song>,
                downloadCount = counts.downloaded,
                likedCount = counts.liked,
                isLibraryEmpty = counts.songs + counts.liked + counts.playlists + counts.events + counts.downloaded == 0,
                playlists = values[4] as List<PlaylistSummary>,
                weekMs = values[5] as Long,
                topArtistThisWeek = (values[6] as List<ArtistStat>).firstOrNull()?.artist?.name,
                topArtists = values[7] as List<ArtistStat>,
                streakDays = streak,
                aroundNow = values[9] as List<Pair<Song, Int>>,
                hour = hour,
                forgotten = values[10] as List<Song>,
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

        /** Consecutive days with at least one play, counting back from today. */
        fun computeStreak(playDays: List<String>, now: Long): Int {
            if (playDays.isEmpty()) return 0
            val set = playDays.toSet()
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            var streak = 0
            while (streak < playDays.size) {
                val day = fmt.format(cal.time)
                if (day in set) { streak++; cal.add(java.util.Calendar.DAY_OF_YEAR, -1) }
                else break
            }
            return streak
        }
    }
}
