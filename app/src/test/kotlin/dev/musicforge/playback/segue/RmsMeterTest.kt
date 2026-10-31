package dev.musicforge.playback.segue

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class RmsMeterTest {

    private fun sine(frames: Int, channels: Int, amplitude: Double, rate: Int = 48_000): ShortArray =
        ShortArray(frames * channels) { i ->
            val frame = i / channels
            (amplitude * 32767 * sin(2 * PI * 440 * frame / rate)).toInt().toShort()
        }

    @Test fun silenceIsZero() {
        val m = RmsMeter(48_000, 2, 500)
        m.feed(ShortArray(48_000 * 2))
        assertThat(m.result().toList()).containsExactly(0f, 0f)
    }

    @Test fun fullScaleSineIsAboutOneOverRootTwo() {
        val m = RmsMeter(48_000, 1, 500)
        m.feed(sine(24_000, 1, 1.0))
        assertThat(m.result().single()).isWithin(0.01f).of((1 / sqrt(2.0)).toFloat())
    }

    @Test fun chunkSizeDoesNotChangeTheResult() {
        val pcm = sine(48_000, 2, 0.5)
        val whole = RmsMeter(48_000, 2, 250).also { it.feed(pcm) }.result()
        val chunked = RmsMeter(48_000, 2, 250).also { m ->
            var i = 0
            while (i < pcm.size) {
                val n = minOf(777, pcm.size - i)
                m.feed(pcm, i, n)
                i += n
            }
        }.result()
        assertThat(chunked.toList()).isEqualTo(whole.toList())
        assertThat(whole.size).isEqualTo(4)
    }

    @Test fun partialTrailingStepIsDropped() {
        val m = RmsMeter(48_000, 1, 500)
        m.feed(sine(30_000, 1, 0.5)) // 625 ms: one full step and a partial one
        assertThat(m.result().size).isEqualTo(1)
    }

    @Test fun louderHalfShowsUpInItsStep() {
        val m = RmsMeter(48_000, 1, 500)
        m.feed(sine(24_000, 1, 0.1))
        m.feed(sine(24_000, 1, 0.8))
        val r = m.result()
        assertThat(r[1]).isGreaterThan(r[0] * 5)
    }
}
