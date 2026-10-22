package dev.musicforge.download

import com.google.common.truth.Truth.assertThat
import dev.musicforge.playback.Credit
import dev.musicforge.playback.albumId
import dev.musicforge.playback.credits
import dev.musicforge.playback.durationSec
import dev.musicforge.playback.forgeMediaItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadMetaTest {
    @Test fun roundTripKeepsEverythingNeededToRebuildTheRow() {
        val item = forgeMediaItem(
            videoId = "abc",
            title = "Glory Box",
            credits = listOf(Credit("UC1", "Portishead"), Credit(null, "Guest")),
            artworkUrl = "https://lh3.googleusercontent.com/x=w120-h120",
            albumId = "MPREb_1",
            albumTitle = "Dummy",
            durationSec = 306,
        )
        val back = DownloadMeta.decode("abc", DownloadMeta.encode(item))
        val m = back.mediaMetadata
        assertThat(m.title.toString()).isEqualTo("Glory Box")
        assertThat(m.credits).containsExactly(Credit("UC1", "Portishead"), Credit(null, "Guest")).inOrder()
        assertThat(m.albumId).isEqualTo("MPREb_1")
        assertThat(m.albumTitle.toString()).isEqualTo("Dummy")
        assertThat(m.durationSec).isEqualTo(306)
        assertThat(m.artworkUri.toString()).contains("w544-h544")
        assertThat(DownloadMeta.title(DownloadMeta.encode(item))).isEqualTo("Glory Box")
    }

    @Test fun legacyTitleOnlyRequestsStillDecode() {
        val back = DownloadMeta.decode("xyz", "Echoes".toByteArray())
        assertThat(back.mediaId).isEqualTo("xyz")
        assertThat(back.mediaMetadata.title.toString()).isEqualTo("Echoes")
        assertThat(DownloadMeta.title("Echoes".toByteArray())).isEqualTo("Echoes")
    }
}
