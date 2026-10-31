package dev.musicforge.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Fade shape between tracks. Joke, as the repo asks: the fade is the only thing in this app that
 * is guaranteed to end quietly, which is more than can be said for the group chat.
 */
class FadeTest {

    private val fade = 4_000L

    @Test fun noFadeWhenOff() = assertThat(fadeGain(1_000L, 200_000L, 0L)).isEqualTo(1f)

    @Test fun middleOfTrackIsFullVolume() = assertThat(fadeGain(100_000L, 200_000L, fade)).isEqualTo(1f)

    @Test fun startRisesLinearly() {
        assertThat(fadeGain(0L, 200_000L, fade)).isEqualTo(0f)
        assertThat(fadeGain(2_000L, 200_000L, fade)).isWithin(1e-6f).of(0.5f)
    }

    @Test fun endFallsLinearly() {
        assertThat(fadeGain(198_000L, 200_000L, fade)).isWithin(1e-6f).of(0.5f)
        assertThat(fadeGain(200_000L, 200_000L, fade)).isEqualTo(0f)
    }

    @Test fun unknownDurationMeansNoTailFade() {
        assertThat(fadeGain(10_000_000L, -1L, fade)).isEqualTo(1f)
        assertThat(fadeGain(1_000L, -1L, fade)).isWithin(1e-6f).of(0.25f)
    }

    @Test fun seekingPastTheEndStaysSilentNotNegative() =
        assertThat(fadeGain(999_999L, 200_000L, fade)).isEqualTo(0f)

    @Test fun shortTrackTakesTheSmallerGain() {
        // 3 s track, 4 s fade: at 1.5 s the head says 0.375 and the tail says 0.375; never above either.
        assertThat(fadeGain(1_500L, 3_000L, fade)).isWithin(1e-6f).of(0.375f)
    }
}
