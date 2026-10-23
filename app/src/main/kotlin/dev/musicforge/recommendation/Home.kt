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
)

/**
 * Builds Home from the listener's own library, deterministically: the same signals always give
 * the same sections. A section appears only when it has real items, so a clean install shows
 * the welcome state and nothing invented.
 */
object HomeComposer {
    const val MAX_PER_ARTIST = 2
    private const val SHELF = 12

    fun compose(s: LocalSignals): List<HomeSection> = listOfNotNull(
        jumpBackIn(s),
        rotation(s),
        downloads(s),
        library(s),
        stats(s),
    )

    /** Nothing personal to show yet: Home asks for intent instead of guessing. */
    fun isColdStart(s: LocalSignals) = compose(s).isEmpty()

    private fun jumpBackIn(s: LocalSignals) = s.recent.take(SHELF).takeIf { it.isNotEmpty() }?.let { songs ->
        HomeSection("jump-back-in", "Jump back in", "Recently played", HomeLayout.SHELF, songs.map { HomeItem.Track(it) })
    }

    /**
     * Songs on repeat this month, at least two plays each, no more than [MAX_PER_ARTIST] per
     * artist so one album binge doesn't fill the shelf, and none already in Jump back in.
     */
    private fun rotation(s: LocalSignals): HomeSection? {
        val recent = s.recent.take(SHELF).mapTo(HashSet()) { it.id }
        val perArtist = HashMap<String, Int>()
        val picked = s.rotation.asSequence()
            .filter { (song, plays) -> plays >= 2 && song.id !in recent }
            .filter { (song, _) ->
                val artist = song.artists.firstOrNull()?.id ?: song.artistLine
                val n = perArtist.getOrDefault(artist, 0)
                (n < MAX_PER_ARTIST).also { if (it) perArtist[artist] = n + 1 }
            }
            .take(SHELF)
            .map { HomeItem.Track(it.first) }
            .toList()
        if (picked.size < 3) return null
        return HomeSection("your-rotation", "Your rotation", "On repeat this month", HomeLayout.SHELF, picked)
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
