package dev.musicforge.recommendation

import com.google.common.truth.Truth.assertThat
import dev.musicforge.data.db.ArtistEntity
import dev.musicforge.data.db.ArtistStat
import dev.musicforge.data.db.PlaylistEntity
import dev.musicforge.data.db.PlaylistSummary
import dev.musicforge.data.db.Song
import dev.musicforge.data.db.SongEntity
import org.junit.Test

class HomeComposerTest {
    private fun song(id: String, artist: String = "a-$id") =
        Song(SongEntity(id = id, title = "Song $id"), listOf(ArtistEntity(id = artist, name = "Artist $artist")))

    private fun artistStat(id: String, name: String, plays: Int = 5, timeMs: Long = 60_000L) =
        ArtistStat(ArtistEntity(id = id, name = name), plays = plays, timeMs = timeMs)

    @Test
    fun cleanInstallShowsNoSectionsAtAll() {
        val empty = LocalSignals()
        assertThat(HomeComposer.compose(empty)).isEmpty()
    }

    @Test
    fun restoredLibraryGivesUsefulHomeWithoutNetwork() {
        val signals = LocalSignals(
            recent = listOf(song("r1"), song("r2")),
            rotation = (1..5).map { song("t$it") to 4 },
            downloads = listOf(song("d1")),
            downloadCount = 2113,
            playlists = listOf(PlaylistSummary(PlaylistEntity(id = "p", name = "Late"), songCount = 12)),
            weekMs = 3 * 60 * 60_000L + 5 * 60_000L,
            topArtistThisWeek = "Portishead",
        )
        val sections = HomeComposer.compose(signals)
        assertThat(sections.map { it.id })
            .containsExactly("jump-back-in", "your-rotation", "your-downloads", "from-your-library", "listening").inOrder()
        assertThat(sections.first { it.id == "your-downloads" }.reason).isEqualTo("2113 songs play offline")
        assertThat(sections.last().stat).isEqualTo("3 h 5 min")
        assertThat(sections.last().reason).isEqualTo("Mostly Portishead")
        assertThat(sections.last().title).isEqualTo("Your week")
    }

    @Test
    fun everySectionHasAReasonAndEveryItemAProvenance() {
        val sections = HomeComposer.compose(
            LocalSignals(recent = listOf(song("r")), playlists = listOf(PlaylistSummary(PlaylistEntity(id = "p", name = "P"), 1))),
        )
        sections.forEach { section ->
            assertThat(section.reason).isNotEmpty()
            section.items.forEach { assertThat(it.provenance).isNotNull() }
        }
    }

    @Test
    fun rotationCapsEachArtistAndSkipsWhatJumpBackInShows() {
        val heavy = (1..6).map { song("h$it", artist = "same") to 9 }
        val others = (1..3).map { song("o$it") to 3 }
        val recent = listOf(song("o1"))
        val rotation = HomeComposer.compose(LocalSignals(recent = recent, rotation = heavy + others))
            .first { it.id == "your-rotation" }.items.map { (it as HomeItem.Track).song }
        assertThat(rotation.count { it.artists.first().id == "same" }).isEqualTo(HomeComposer.MAX_PER_ARTIST)
        assertThat(rotation.map { it.id }).doesNotContain("o1")
    }

    @Test
    fun rotationNeedsRepeatsNotSinglePlays() {
        val once = (1..10).map { song("x$it") to 1 }
        assertThat(HomeComposer.compose(LocalSignals(rotation = once)).map { it.id }).doesNotContain("your-rotation")
    }

    @Test
    fun aroundThisHourNamesTheHourAndNeedsARealHabit() {
        val habit = (1..5).map { song("n$it") to 3 }
        val section = HomeComposer.compose(LocalSignals(aroundNow = habit, hour = 22)).first { it.id == "around-this-hour" }
        assertThat(section.reason).isEqualTo("What you play around 10 pm")
        assertThat(HomeComposer.compose(LocalSignals(aroundNow = habit.take(3), hour = 22)).map { it.id })
            .doesNotContain("around-this-hour")
    }

    @Test
    fun noSongAppearsOnTwoShelves() {
        val same = (1..6).map { song("x$it") }
        val sections = HomeComposer.compose(
            LocalSignals(recent = same.take(2), aroundNow = same.map { it to 3 }, rotation = same.map { it to 3 }, forgotten = same),
        )
        val ids = sections.flatMap { s -> s.items.filterIsInstance<HomeItem.Track>().map { it.song.id } }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun forgottenLikesTurnOverDailyButHoldStillWithinADay() {
        val liked = (1..30).map { song("l$it") }
        fun shelf(day: Int) = HomeComposer.compose(LocalSignals(forgotten = liked, daySeed = day))
            .first { it.id == "liked-then-forgotten" }.items.map { it.key }
        assertThat(shelf(100)).isEqualTo(shelf(100))
        assertThat(shelf(100)).isNotEqualTo(shelf(101))
    }

    @Test
    fun hiddenSongsLeaveEveryShelfAndLessOfLeavesOnlySuggestions() {
        val mine = song("m", artist = "loud")
        val others = (1..4).map { song("o$it") }
        val s = LocalSignals(
            recent = listOf(mine),
            downloads = listOf(mine),
            rotation = (others + song("m2", artist = "loud")).map { it to 3 },
        )
        fun ids(sig: LocalSignals, id: String) =
            HomeComposer.compose(sig).firstOrNull { it.id == id }?.items?.map { (it as HomeItem.Track).song.id }.orEmpty()

        val hidden = s.copy(hidden = setOf("m"))
        assertThat(ids(hidden, "jump-back-in")).doesNotContain("m")
        assertThat(ids(hidden, "your-downloads")).doesNotContain("m")

        val less = s.copy(lessOf = setOf("loud"))
        assertThat(ids(less, "your-rotation")).doesNotContain("m2")
        // Recently played is history, not a suggestion: it stays true.
        assertThat(ids(less, "jump-back-in")).contains("m")
    }

    @Test
    fun pausedSuggestionsLeaveOnlyWhatYouDid() {
        val s = LocalSignals(
            recent = listOf(song("r")),
            aroundNow = (1..5).map { song("n$it") to 3 },
            rotation = (1..5).map { song("t$it") to 3 },
            forgotten = (1..5).map { song("f$it") },
            isPaused = true,
        )
        assertThat(HomeComposer.compose(s).map { it.id }).containsExactly("jump-back-in")
    }

    @Test
    fun hourLabels() {
        assertThat(listOf(0, 9, 12, 13, 23).map(HomeComposer::hourLabel)).containsExactly("midnight", "9 am", "noon", "1 pm", "11 pm").inOrder()
    }

    @Test
    fun sameSignalsGiveTheSameHome() {
        val s = LocalSignals(recent = listOf(song("a"), song("b")), rotation = (1..4).map { song("t$it") to 2 })
        assertThat(HomeComposer.compose(s)).isEqualTo(HomeComposer.compose(s))
    }

    @Test
    fun customOrderReordersSections() {
        val signals = LocalSignals(recent = listOf(song("r")), rotation = (1..3).map { song("t$it") to 3 })
        val default = HomeComposer.compose(signals).map { it.id }
        val reordered = HomeComposer.compose(signals, order = listOf("your-rotation", "jump-back-in")).map { it.id }
        assertThat(reordered).isEqualTo(listOf("your-rotation", "jump-back-in"))
        assertThat(default).isNotEqualTo(reordered)
    }

    @Test
    fun hiddenShelvesAreSkipped() {
        val signals = LocalSignals(recent = listOf(song("r")))
        val all = HomeComposer.compose(signals).map { it.id }
        val hidden = HomeComposer.compose(signals, hidden = setOf("jump-back-in")).map { it.id }
        assertThat(hidden).doesNotContain("jump-back-in")
        assertThat(all.minus("jump-back-in")).isEqualTo(hidden)
    }

    @Test
    fun orderAndHiddenCombine() {
        val signals = LocalSignals(
            recent = listOf(song("r")),
            rotation = (1..3).map { song("t$it") to 3 },
        )
        val sections = HomeComposer.compose(signals, order = listOf("your-rotation"), hidden = setOf("jump-back-in"))
        assertThat(sections.map { it.id }).containsExactly("your-rotation")
    }

    @Test
    fun artistShedAppearsWithTopArtists() {
        val artist = artistStat("ar1", "Portishead", plays = 20, timeMs = 300_000L)
        val signals = LocalSignals(
            rotation = (1..3).map { song("t$it") to 3 },
            topArtists = listOf(artist),
        )
        val sections = HomeComposer.compose(signals)
        assertThat(sections.map { it.id }).contains("your-artists")
        val shelf = sections.first { it.id == "your-artists" }
        assertThat(shelf.layout).isEqualTo(HomeLayout.ARTISTS)
        assertThat(shelf.items).hasSize(1)
        assertThat((shelf.items.first() as HomeItem.Artist).artist.id).isEqualTo("ar1")
    }

    @Test
    fun artistShedNotShownWhenEmpty() {
        val signals = LocalSignals(recent = listOf(song("r")))
        assertThat(HomeComposer.compose(signals).map { it.id }).doesNotContain("your-artists")
    }

    @Test
    fun streakShownOnYourWeek() {
        val signals = LocalSignals(
            weekMs = 2 * 60 * 60_000L,
            topArtistThisWeek = "Portishead",
            streakDays = 5,
        )
        val stats = HomeComposer.compose(signals).first { it.id == "listening" }
        assertThat(stats.title).isEqualTo("Your week")
        assertThat(stats.reason).contains("5-day streak")
    }

    @Test
    fun streakNotShownWhenBelowTwoDays() {
        val signals = LocalSignals(
            weekMs = 2 * 60 * 60_000L,
            topArtistThisWeek = "Portishead",
            streakDays = 1,
        )
        val stats = HomeComposer.compose(signals).first { it.id == "listening" }
        assertThat(stats.reason).doesNotContain("streak")
    }

    @Test
    fun statsNotShownWhenNoWeekMsAndNoStreak() {
        val signals = LocalSignals(streakDays = 0)
        assertThat(HomeComposer.compose(signals).map { it.id }).doesNotContain("listening")
    }

    @Test
    fun statsShownWhenOnlyStreak() {
        val signals = LocalSignals(weekMs = 0, streakDays = 3)
        assertThat(HomeComposer.compose(signals).map { it.id }).contains("listening")
    }
}
