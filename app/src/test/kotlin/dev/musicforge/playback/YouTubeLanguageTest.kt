package dev.musicforge.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class YouTubeLanguageTest {
    private fun hl(tag: String, chosen: String? = null) = YouTubeSession.youTubeLanguage(chosen, Locale.forLanguageTag(tag))

    @Test fun unknownRegionFallsBackToLanguage() {
        // The X7b case: YouTube rejects hl=en-ZA with HTTP 400 on every request.
        assertThat(hl("en-ZA")).isEqualTo("en")
        assertThat(hl("af-ZA")).isEqualTo("af")
    }

    @Test fun supportedTagsPassThrough() {
        assertThat(hl("en-GB")).isEqualTo("en-GB")
        assertThat(hl("pt-PT")).isEqualTo("pt-PT")
    }

    @Test fun choiceWinsOnlyWhenSupported() {
        assertThat(hl("en-ZA", chosen = "de")).isEqualTo("de")
        assertThat(hl("en-ZA", chosen = "xx-YY")).isEqualTo("en")
    }

    @Test fun unknownLanguageFallsBackToEnglish() {
        assertThat(hl("tlh")).isEqualTo("en")
    }
}
