package dev.musicforge.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

@UnstableApi
class EqualizerProcessorTest {
    private val fs = 48_000

    /** RMS level (dB) of a sine after processing, skipping the filter's settling time. */
    private fun responseDb(eq: EqualizerProcessor, hz: Double): Double {
        eq.configure(AudioProcessor.AudioFormat(fs, 2, C.ENCODING_PCM_16BIT))
        eq.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val frames = fs // one second
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val v = (sin(2 * PI * hz * i / fs) * 0.25 * 32767).toInt().toShort()
            input.putShort(v); input.putShort(v)
        }
        input.flip()
        eq.queueInput(input)
        val out = eq.output.order(ByteOrder.nativeOrder())
        var sum = 0.0
        var n = 0
        var i = 0
        while (out.remaining() >= 4) {
            val l = out.getShort() / 32768.0
            out.getShort()
            if (i++ > fs / 4) { sum += l * l; n++ }
        }
        return 20 * log10(sqrt(sum / n) / (0.25 / sqrt(2.0)))
    }

    @Test fun disabledIsTransparent() {
        val eq = EqualizerProcessor()
        eq.set(false, listOf(6f, 6f, 6f, 6f, 6f))
        assertThat(responseDb(eq, 1000.0)).isWithin(0.1).of(0.0)
    }

    @Test fun bassBoostRaisesLowsRelativeToMids() {
        val eq = EqualizerProcessor()
        eq.set(true, listOf(6f, 0f, 0f, 0f, 0f))
        val deep = responseDb(eq, 25.0)
        val corner = responseDb(eq, 60.0)
        val mid = responseDb(eq, 2000.0)
        // A shelf: about half the gain at its 60 Hz corner, approaching the full 6 dB below it.
        assertThat(corner - mid).isWithin(1.0).of(3.0)
        assertThat(deep - mid).isIn(com.google.common.collect.Range.closed(5.0, 6.3))
        // Pre-gain takes the boost back off, so the output is never louder than the input.
        assertThat(deep).isAtMost(0.3)
    }

    @Test fun peakBandCutsAtItsCentre() {
        val eq = EqualizerProcessor()
        eq.set(true, listOf(0f, 0f, -6f, 0f, 0f))
        assertThat(responseDb(eq, 910.0)).isWithin(0.6).of(-6.0)
        assertThat(responseDb(eq, 14_000.0)).isWithin(0.6).of(0.0)
    }
}
