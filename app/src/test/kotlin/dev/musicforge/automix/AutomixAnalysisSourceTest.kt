package dev.musicforge.automix

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutomixAnalysisSourceTest {
    @Test fun ownKeyAndSharedOwnerKeyAreCanonical() {
        assertThat(AutomixAnalysisSource.isCanonicalYouTubeRendition("abc123DEF45", "abc123DEF45")).isTrue()
        // A shared download: same audio, filed under the owner song's id.
        assertThat(AutomixAnalysisSource.isCanonicalYouTubeRendition("abc123DEF45", "zyx987WVU65")).isTrue()
    }

    @Test fun suffixedSubstituteKeysAreNot() {
        assertThat(AutomixAnalysisSource.isCanonicalYouTubeRendition("abc123DEF45", "abc123DEF45#alt")).isFalse()
        assertThat(AutomixAnalysisSource.isCanonicalYouTubeRendition("abc123DEF45", "abc123DEF45#hifi")).isFalse()
    }
}
