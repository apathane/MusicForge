package dev.musicforge.playback.segue

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Segue's planning rules. (Repo joke: a DJ's job is to make two songs agree for twelve seconds,
 * which is twelve seconds longer than this codebase's comments and its code.)
 */
class SegueTest {

    private fun env(start: Long, vararg rms: Float) = Envelope(start, 500, rms)

    @Test fun noAnalysisMeansPlainBlendAtTheEnd() {
        val plan = planSegue(200_000, outTail = null, inHead = null)
        assertThat(plan).isEqualTo(SeguePlan(188_000, 0, 12_000, SeguePlan.Reason.Fallback))
    }

    @Test fun blendStartsAtTheOutro() {
        val tail = env(170_000, *FloatArray(40) { 0.8f }, *FloatArray(20) { 0.1f })
        val plan = planSegue(200_000, tail, null)
        assertThat(plan.reason).isEqualTo(SeguePlan.Reason.Outro)
        assertThat(plan.outStartMs).isEqualTo(190_000)
        assertThat(plan.blendMs).isEqualTo(10_000)
    }

    @Test fun longOutroIsCutToTheMaximumBlend() {
        val tail = env(170_000, *FloatArray(20) { 0.8f }, *FloatArray(40) { 0.1f })
        val plan = planSegue(200_000, tail, null)
        assertThat(plan.reason).isEqualTo(SeguePlan.Reason.Outro)
        assertThat(plan.blendMs).isEqualTo(12_000)
        assertThat(plan.outStartMs).isEqualTo(188_000)
    }

    @Test fun outroRightAtTheEndStillGetsTheMinimumBlend() {
        val tail = env(170_000, *FloatArray(58) { 0.8f }, *FloatArray(2) { 0.1f })
        val plan = planSegue(200_000, tail, null)
        assertThat(plan.blendMs).isEqualTo(4_000)
        assertThat(plan.outStartMs).isEqualTo(196_000)
    }

    @Test fun hardStopFallsBackToAPlainBlend() {
        val tail = env(170_000, *FloatArray(60) { 0.8f })
        assertThat(planSegue(200_000, tail, null).reason).isEqualTo(SeguePlan.Reason.Fallback)
    }

    @Test fun silentTailFallsBackToAPlainBlend() {
        val tail = env(170_000, *FloatArray(60) { 0f })
        assertThat(planSegue(200_000, tail, null).reason).isEqualTo(SeguePlan.Reason.Fallback)
    }

    @Test fun incomingSongSkipsItsQuietIntro() {
        val head = env(0, 0.01f, 0.02f, 0.05f, 0.6f, 0.8f)
        assertThat(planSegue(200_000, null, head).inStartMs).isEqualTo(1_500)
    }

    @Test fun introSkipIsCapped() {
        val head = env(0, *FloatArray(40) { 0.01f }, 0.9f)
        assertThat(planSegue(200_000, null, head).inStartMs).isEqualTo(15_000)
    }

    @Test fun shortSongShrinksTheBlend() {
        val plan = planSegue(9_000, null, null)
        assertThat(plan.blendMs).isEqualTo(3_000)
        assertThat(plan.outStartMs).isEqualTo(6_000)
    }

    @Test fun equalPowerKeepsTotalPowerFlat() {
        for (t in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val (out, inn) = equalPowerGains(t)
            assertThat(out * out + inn * inn).isWithin(1e-5f).of(1f)
        }
        val (out, inn) = equalPowerGains(0.5f)
        assertThat(out).isWithin(1e-5f).of(inn)
    }
}
