package dev.musicforge.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtworkUrlTest {
    @Test fun signedYtimgUrlsKeepTheirPath() {
        // The signature is bound to the path: rewriting mqdefault -> hqdefault made Google 403.
        val signed = "https://i.ytimg.com/vi/abc/mqdefault.jpg?sqp=-oay&rs=AOn4"
        assertThat(hiResArtwork(signed)).isEqualTo(signed)
        assertThat(sizedArtwork(signed, 128)).isEqualTo(signed)
    }

    @Test fun missingArtworkFallsBackToTheVideoThumbnail() {
        assertThat(fallbackArtwork("abc", null)).isEqualTo("https://i.ytimg.com/vi/abc/hqdefault.jpg")
        assertThat(fallbackArtwork("abc", " ")).isEqualTo("https://i.ytimg.com/vi/abc/hqdefault.jpg")
    }

    @Test fun googleCoverSizesAreRewritten() {
        assertThat(sizedArtwork("https://lh3.googleusercontent.com/x=w60-h60-l90-rj", 200)).endsWith("=w256-h256-l90-rj")
        assertThat(sizedArtwork("https://yt3.ggpht.com/x=s88", 100)).endsWith("=s128-l90-rj")
    }

    @Test fun googleCoverQueryStringSurvivesTheResize() {
        assertThat(sizedArtwork("https://lh3.googleusercontent.com/x=w60-h60-l90-rj?sqp=sig", 200))
            .isEqualTo("https://lh3.googleusercontent.com/x=w256-h256-l90-rj?sqp=sig")
    }
}
