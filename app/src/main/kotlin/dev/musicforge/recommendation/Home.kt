package dev.musicforge.recommendation

import dev.musicforge.data.db.PlaylistSummary
import dev.musicforge.data.db.Song

/** Where a Home item came from. Every item carries one; nothing is shown without a real source. */
enum class Provenance { LOCAL_LIBRARY, YOUTUBE_ACCOUNT, YOUTUBE_CATALOG, MOOD_CURATED, EXTERNAL_SIGNAL }

enum class HomeLayout { SHELF, ROWS, PLAYLISTS, STATS }

/** One thing Home can show: a song from the library, or a playlist to open. */
sealed interface HomeItem {
    val key: String
    val provenance: Provenance

    data class Track(val song: Song, override val provenance: Provenance = Provenance.LOCAL_LIBRARY) : HomeItem {
        override val key get() = "s:" + song.id
    }

    data class Playlist(val playlist: PlaylistSummary, override val provenance: Provenance = Provenance.LOCAL_LIBRARY) : HomeItem {
        override val key get() = "p:" + playlist.playlist.id
    }
}

/** [reason] is the short "why this is here" line every personal section shows. */
data class HomeSection(
    val id: String,
    val title: String,
    val reason: String,
    val layout: HomeLayout,
    val items: List<HomeItem> = emptyList(),
    /** Only for [HomeLayout.STATS]: the one personal number it shows. */
    val stat: String? = null,
)

/** What the library knows about the listener, read from Room. */
data class LocalSignals(
    val recent: List<Song> = emptyList(),
    /** Most played in the last 30 days, with play counts, most first. */
    val rotation: List<Pair<Song, Int>> = emptyList(),
    val downloads: List<Song> = emptyList(),
    val downloadCount: Int = 0,
    val playlists: List<PlaylistSummary> = emptyList(),
    val likedCount: Int = 0,
    val weekMs: Long = 0,
    val topArtistThisWeek: String? = null,
    /** Songs played at this hour of the day (±1 h) across history, with play counts. */
    val aroundNow: List<Pair<Song, Int>> = emptyList(),
    /** The hour [aroundNow] is about, 0..23. */
    val hour: Int = 12,
    /** Liked 45+ days ago, not played in the last 30, oldest like first. */
    val forgotten: List<Song> = emptyList(),
    /** Changes once a day, so the forgotten-likes shelf turns over daily but holds still within a day. */
    val daySeed: Int = 0,
)

/**
 * Builds Home from the listener's own library, deterministically: the same signals always give
 * the same sections. A section appears only when it has real items, so a clean install shows
 * the welcome state and nothing invented.
 */
object HomeComposer {
    const val MAX_PER_ARTIST = 2
    private const val SHELF = 12

    fun compose(s: LocalSignals): List<HomeSection> {
        // Each shelf skips what a shelf above already shows.
        val shown = HashSet<String>()
        return listOfNotNull(
            jumpBackIn(s)?.also { shown += it.ids() },
            aroundThisHour(s, shown)?.also { shown += it.ids() },
            rotation(s, shown)?.also { shown += it.ids() },
            forgotten(s, shown),
            downloads(s),
            library(s),
            stats(s),
        )
    }

    /** Nothing personal to show yet: Home asks for intent instead of guessing. */
    fun isColdStart(s: LocalSignals) = compose(s).isEmpty()

    private fun HomeSection.ids() = items.filterIsInstance<HomeItem.Track>().map { it.song.id }

    private fun jumpBackIn(s: LocalSignals) = s.recent.take(SHELF).takeIf { it.isNotEmpty() }?.let { songs ->
        HomeSection("jump-back-in", "Jump back in", "Recently played", HomeLayout.SHELF, songs.map { HomeItem.Track(it) })
    }

    /**
     * Up to [SHELF] songs, at most [MAX_PER_ARTIST] per artist so one album binge doesn't fill
     * the shelf, none already [shown]. Fewer than [min] means there's no real pattern yet.
     */
    private fun pick(songs: Sequence<Song>, shown: Set<String>, min: Int = 3): List<HomeItem>? {
        val perArtist = HashMap<String, Int>()
        val picked = songs
            .filter { it.id !in shown }
            .filter { song ->
                val artist = song.artists.firstOrNull()?.id ?: song.artistLine
                val n = perArtist.getOrDefault(artist, 0)
                (n < MAX_PER_ARTIST).also { if (it) perArtist[artist] = n + 1 }
            }
            .take(SHELF)
            .map { HomeItem.Track(it) }
            .toList()
        return picked.takeIf { it.size >= min }
    }

    /** What this listener reaches for at this time of day: a habit, not a trend. */
    private fun aroundThisHour(s: LocalSignals, shown: Set<String>) =
        pick(s.aroundNow.asSequence().map { it.first }, shown, min = 4)?.let {
            HomeSection("around-this-hour", "Around this hour", "What you play around ${hourLabel(s.hour)}", HomeLayout.SHELF, it)
        }

    /** Songs on repeat this month, at least two plays each. */
    private fun rotation(s: LocalSignals, shown: Set<String>) =
        pick(s.rotation.asSequence().filter { it.second >= 2 }.map { it.first }, shown)?.let {
            HomeSection("your-rotation", "Your rotation", "On repeat this month", HomeLayout.SHELF, it)
        }

    /** Likes that slipped away, a different handful each day. */
    private fun forgotten(s: LocalSignals, shown: Set<String>) =
        pick(s.forgotten.shuffled(kotlin.random.Random(s.daySeed)).asSequence(), shown)?.let {
            HomeSection("liked-then-forgotten", "Liked, then forgotten", "You liked these, then stopped playing them", HomeLayout.SHELF, it)
        }

    fun hourLabel(hour: Int): String = when (val h = hour.mod(24)) {
        0 -> "midnight"
        12 -> "noon"
        in 1..11 -> "$h am"
        else -> "${h - 12} pm"
    }

    private fun downloads(s: LocalSignals) = s.downloads.take(5).takeIf { it.isNotEmpty() }?.let { songs ->
        val n = s.downloadCount
        HomeSection(
            "your-downloads", "Your downloads",
            if (n == 1) "1 song plays offline" else "$n songs play offline",
            HomeLayout.ROWS, songs.map { HomeItem.Track(it) },
        )
    }

    private fun library(s: LocalSignals) = s.playlists.take(SHELF).takeIf { it.isNotEmpty() }?.let { lists ->
        HomeSection("from-your-library", "From your library", "Your playlists", HomeLayout.PLAYLISTS, lists.map { HomeItem.Playlist(it) })
    }

    private fun stats(s: LocalSignals): HomeSection? {
        val minutes = s.weekMs / 60_000
        if (minutes < 1) return null
        val time = if (minutes >= 120) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
        return HomeSection(
            "listening", "Last 7 days", s.topArtistThisWeek?.let { "Mostly $it" } ?: "Your listening",
            HomeLayout.STATS, stat = time,
        )
    }
}
