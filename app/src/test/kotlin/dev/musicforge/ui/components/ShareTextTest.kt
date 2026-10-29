package dev.musicforge.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareTextTest {
    @Test fun formatsTitleArtistAndShortLink() {
        val text = shareText("Roads", "Portishead", "v1abcXYZ")
        assertThat(text).isEqualTo("Roads – Portishead https://music.youtube.com/watch?v=v1abcXYZ")
    }

    @Test fun blankTitleOrArtistStillSharesALink() {
        val t = shareText("", "Portishead", "v1abcXYZ")
        assertThat(t).contains("Portishead https://music.youtube.com/watch?v=v1abcXYZ")
        assertThat(t).startsWith(" – ")
    }

    @Test fun nullTitleOrArtistFallsBackGracefully() {
        val t = shareText(null, null, "v1abcXYZ")
        assertThat(t).isEqualTo(" –  https://music.youtube.com/watch?v=v1abcXYZ")
    }

    // Why a dash? "Title – Artist url" parses as a title, a name, then a link when pasted into
    // a chat or tweet — Metrolist uses the same layout so the two apps don't fight the parser.
    @Test fun usesEnDashNotHyphen() {
        val t = shareText("Roads", "Portishead", "id")
        assertThat(t).contains("–")
    }
}
