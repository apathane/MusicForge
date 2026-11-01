package dev.musicforge.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The pure pulse maths — beat shape and fill alpha — covered without Robolectric: no Android classes involved. */
class MixPulseTest {

    @Test fun beatShapePeaksOnTheBeatAndDiesAtTheHalf() {
        assertThat(beatShape(0f)).isEqualTo(1f)
        assertThat(beatShape(0.5f)).isWithin(1e-6f).of(0f)
        assertThat(beatShape(1f)).isWithin(1e-6f).of(1f)
    }

    @Test fun beatShapeIsSymmetricalAroundTheBeat() {
        assertThat(beatShape(0.25f)).isWithin(0.001f).of(0.25f)
        assertThat(beatShape(0.75f)).isWithin(0.001f).of(0.25f)
        assertThat(beatShape(0.1f)).isWithin(1e-5f).of(beatShape(0.9f))
        assertThat(beatShape(0.3f)).isWithin(1e-5f).of(beatShape(0.7f))
    }

    @Test fun beatShapeNeverLeavesZeroToOne() {
        // `until` doesn't exist on Float, so walk 0..1 in integers and scale.
        // beatShape is a raised cosine squared, so it can't escape 0..1 even
        // if floating-point drift ever pushed a phase just past 1f.
        for (i in 0..100) {
            assertThat(beatShape(i / 100f)).isIn(0f..1f)
        }
    }

    @Test fun pulseAlphaRestsBelowFullAndReachesItOnTheBeat() {
        assertThat(pulseAlpha(1f, 0f, 1f, 0.1f)).isWithin(1e-6f).of(0.1f)
        assertThat(pulseAlpha(1f, 1f, 1f, 0.1f)).isWithin(1e-6f).of(1f)
        assertThat(pulseAlpha(0.5f, 0f, 1f, 0.1f)).isWithin(1e-6f).of(0.05f)
    }

    @Test fun pulseAlphaWithNoDepthIsFlat() {
        assertThat(pulseAlpha(1f, 0f, 0f, 0.1f)).isEqualTo(1f)
        assertThat(pulseAlpha(1f, 0.7f, 0f, 0.1f)).isEqualTo(1f)
        assertThat(pulseAlpha(0.4f, 0.9f, 0f, 0.1f)).isEqualTo(0.4f)
    }

    @Test fun pulseAlphaGrowsWithTheLevelAndDepth() {
        assertThat(pulseAlpha(1f, 0f, 1f, 0.1f)).isLessThan(pulseAlpha(1f, 0.5f, 1f, 0.1f))
        assertThat(pulseAlpha(1f, 0.5f, 1f, 0.1f)).isLessThan(pulseAlpha(1f, 1f, 1f, 0.1f))
        assertThat(pulseAlpha(1f, 0f, 0f, 0.1f)).isGreaterThan(pulseAlpha(1f, 0f, 1f, 0.1f))
    }
}
