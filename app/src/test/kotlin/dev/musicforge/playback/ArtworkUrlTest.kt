package dev.musicforge.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtworkUrlTest {
    private val cover = "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj"

    @Test fun rowsGetSmallFiles() {
        assertThat(sizedArtwork(cover, 125)).isEqualTo("https://lh3.googleusercontent.com/abc=w128-h128-l90-rj")
        assertThat(sizedArtwork(cover, 200)).isEqualTo("https://lh3.googleusercontent.com/abc=w256-h256-l90-rj")
        assertThat(sizedArtwork(cover, 900)).isEqualTo(cover)
    }

    @Test fun videoThumbnailsUnchanged() {
        val yt = "https://i.ytimg.com/vi/abc/hqdefault.jpg"
        assertThat(sizedArtwork(yt, 125)).isEqualTo(yt)
    }
}
