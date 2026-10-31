package dev.musicforge.ui.designsystem

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KitMathTest {
    @Test fun reversedCurveIsTheTimeMirror() {
        val c = Curve.Drawer
        for (i in 0..20) {
            val t = i / 20f
            // Playing the exit forward is the entrance played backwards.
            assertThat(c.reversed.easing.transform(t)).isWithin(2e-3f).of(1f - c.easing.transform(1f - t))
        }
    }

    @Test fun symmetricCurveIsItsOwnReverse() {
        val r = Curve.InOutSoft.reversed
        assertThat(listOf(r.x1, r.y1, r.x2, r.y2)).containsExactly(.37f, 0f, .63f, 1f).inOrder()
    }

    @Test fun mirrorFadesSitAtOppositeEnds() {
        val m = Mirror.Panel
        assertThat(m.fadeMs).isEqualTo(120)
        assertThat(m.durationMs - m.fadeMs).isEqualTo(260)
    }

    @Test fun stretchIsCappedAndSquashesAcross() {
        assertThat(Physics.stretchFor(5f)).isWithin(1e-6f).of(0.15f)
        assertThat(Physics.stretchFor(-100f)).isEqualTo(0.28f)
        val (sx, sy) = Physics.stretchScale(0.2f)
        assertThat(sx).isWithin(1e-6f).of(1.2f)
        assertThat(sy).isWithin(1e-6f).of(0.93f)
    }

    @Test fun rubberApproachesItsLimit() {
        assertThat(Physics.rubber(0f)).isEqualTo(0f)
        assertThat(Physics.rubber(0.1f)).isLessThan(0.1f)
        assertThat(Physics.rubber(1000f)).isWithin(1e-3f).of(0.16f)
        assertThat(Physics.rubberSigned(-1000f)).isWithin(1e-3f).of(-0.16f)
    }

    @Test fun sliderMagnetsToStepsAndRubbersPastTheEnds() {
        assertThat(Physics.shapeSlider(3.0f, max = 11f)).isEqualTo(3.0f)
        // 0.05 off a step is pulled closer to it, 0.4 off (outside reach) only by the pull share.
        assertThat(Physics.shapeSlider(3.05f, max = 11f)).isLessThan(3.05f)
        assertThat(Physics.shapeSlider(3.05f, max = 11f)).isGreaterThan(3.0f)
        assertThat(Physics.shapeSlider(-1f, max = 11f)).isGreaterThan(-0.16f)
        assertThat(Physics.shapeSlider(12f, max = 11f)).isLessThan(11.16f)
    }

    @Test fun flingThrowsAndRounds() {
        assertThat(Physics.flingTarget(goal = 4f, velocity = 30f, max = 11f)).isEqualTo(6f)
        assertThat(Physics.flingTarget(goal = 10f, velocity = 200f, max = 11f)).isEqualTo(11f)
    }

    @Test fun pullLiftGivesLessTheFurtherYouPull() {
        val ease = 72f
        val first = Physics.pullLift(72f, ease)
        val second = Physics.pullLift(144f, ease) - first
        assertThat(first).isLessThan(72f * 0.5f + 1f)
        assertThat(second).isLessThan(first)
        assertThat(Physics.pullLift(-5f, ease)).isEqualTo(0f)
    }

    @Test fun graphemesKeepEmojiAndClicksWhole() {
        assertThat(TypePace.graphemes("a👍🏽b")).containsExactly("a", "👍🏽", "b").inOrder()
        assertThat(TypePace.graphemes("é")).hasSize(1) // e + combining acute
    }

    @Test fun titleWritingFitsItsSpread() {
        assertThat(TypePace.writeDelayMs(1, 5)).isEqualTo(18f)
        // A 60-letter title still finishes starting inside 420 ms.
        assertThat(TypePace.writeDelayMs(59, 60)).isAtMost(420f)
    }

    @Test fun streamCatchesUpFasterOnceDone() {
        val live = TypePace.Stream().apply { push(400) }
        val done = TypePace.Stream().apply { push(400, done = true) }
        repeat(6) { live.advance(1 / 90f); done.advance(1 / 90f) }
        assertThat(done.shown).isGreaterThan(live.shown)
        val reduced = TypePace.Stream().apply { push(400) }
        reduced.advance(0.01f, reduced = true)
        assertThat(reduced.caughtUp).isTrue()
    }
}
