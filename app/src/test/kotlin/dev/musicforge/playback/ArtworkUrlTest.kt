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

    @Test fun onlyFourByThreeThumbnailsAreLetterboxed() {
        assertThat(isLetterboxedThumbnail("https://i.ytimg.com/vi/abc/hqdefault.jpg")).isTrue()
        assertThat(isLetterboxedThumbnail("https://i.ytimg.com/vi/abc/sddefault.jpg")).isTrue()
        assertThat(isLetterboxedThumbnail("https://i.ytimg.com/vi/abc/hq720.jpg?sqp=x")).isFalse()
        assertThat(isLetterboxedThumbnail("https://i.ytimg.com/vi/abc/maxresdefault.jpg")).isFalse()
        assertThat(isLetterboxedThumbnail("https://i.ytimg.com/vi/abc/mqdefault.jpg")).isFalse()
        assertThat(isLetterboxedThumbnail("https://lh3.googleusercontent.com/x=w544-h544")).isFalse()
    }
}
