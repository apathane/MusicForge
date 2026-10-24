package dev.musicforge.ui.screens

import com.google.common.truth.Truth.assertThat
import dev.musicforge.data.db.ArtistEntity
import dev.musicforge.data.db.PlaylistPlays
import dev.musicforge.data.db.Song
import dev.musicforge.data.db.SongEntity
import org.junit.Test

class PlaylistInsightTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun song(id: String, artist: String, downloaded: Boolean = false) =
        Song(SongEntity(id = id, title = id, isDownloaded = downloaded), listOf(ArtistEntity(id = artist, name = artist)))

    @Test
    fun saysHowThePlaylistIsUsed() {
        val songs = listOf(song("a", "Portishead", true), song("b", "Portishead"), song("c", "Portishead", true), song("d", "Tricky"))
        assertThat(playlistInsight(songs, PlaylistPlays(34, now - 3 * day), now))
            .isEqualTo("Played 34 times · last 3 days ago · mostly Portishead · 2 downloaded")
    }

    @Test
    fun leavesOutWhatHasNothingToSay() {
        val mixed = (1..6).map { song("s$it", "artist$it") }
        assertThat(playlistInsight(mixed, PlaylistPlays(0, null), now)).isNull()
        assertThat(playlistInsight(mixed.map { it.copy(song = it.song.copy(isDownloaded = true)) }, PlaylistPlays(1, now - 10_000), now))
            .isEqualTo("Played once · last just now · all downloaded")
    }
}
