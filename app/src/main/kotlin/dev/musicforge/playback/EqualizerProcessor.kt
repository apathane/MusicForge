package dev.musicforge.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A five-band equaliser inside the player, so it works on every phone. The platform
 * `audiofx.Equalizer` depends on the OEM's effect engine, which some skins lock down or omit.
 *
 * Bands: low shelf 60 Hz, peaks at 230 Hz / 910 Hz / 3.6 kHz, high shelf 14 kHz (RBJ cookbook
 * biquads). Boosts are offset by an automatic pre-gain so the output never clips. 16-bit PCM,
 * mono or stereo; anything else passes through untouched.
 */
@UnstableApi
class EqualizerProcessor : BaseAudioProcessor() {
    @Volatile private var gainsDb = FloatArray(BANDS.size)
    @Volatile private var enabled = false
    @Volatile private var version = 0
    private var appliedVersion = -1

    private var sampleRate = 0
    private var channels = 0
    // Per band: b0 b1 b2 a1 a2 (normalised); per band per channel: x1 x2 y1 y2.
    private var coeffs = Array(BANDS.size) { DoubleArray(5) }
    private var state = Array(BANDS.size) { DoubleArray(8) }
    private var preGain = 1.0

    /** Called from any thread; picked up at the next audio buffer. */
    fun set(enabled: Boolean, gainsDb: List<Float>) {
        this.gainsDb = FloatArray(BANDS.size) { gainsDb.getOrElse(it) { 0f }.coerceIn(-MAX_DB, MAX_DB) }
        this.enabled = enabled
        version++
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioProcessor.AudioFormat.NOT_SET // not ours to touch
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        appliedVersion = -1
        return inputAudioFormat
    }

    private fun updateCoefficients() {
        if (appliedVersion == version) return
        appliedVersion = version
        val g = gainsDb
        BANDS.forEachIndexed { i, band -> coeffs[i] = biquad(band.type, band.hz, band.q, g[i].toDouble(), sampleRate) }
        val maxBoost = g.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        preGain = 10.0.pow(-maxBoost / 20.0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        if (!enabled) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        updateCoefficients()
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val frames = size / (2 * channels)
        for (f in 0 until frames) {
            for (ch in 0 until channels) {
                var x = input.getShort().toDouble() / 32768.0 * preGain
                for (b in coeffs.indices) {
                    val c = coeffs[b]
                    val s = state[b]
                    val o = ch * 4
                    val y = c[0] * x + c[1] * s[o] + c[2] * s[o + 1] - c[3] * s[o + 2] - c[4] * s[o + 3]
                    s[o + 1] = s[o]; s[o] = x
                    s[o + 3] = s[o + 2]; s[o + 2] = y
                    x = y
                }
                out.putShort((x * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        }
        out.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        state = Array(BANDS.size) { DoubleArray(8) }
    }

    override fun onReset() {
        state = Array(BANDS.size) { DoubleArray(8) }
        sampleRate = 0
    }

    enum class Type { LOW_SHELF, PEAK, HIGH_SHELF }

    data class Band(val hz: Double, val q: Double, val type: Type)

    companion object {
        const val MAX_DB = 12f

        val BANDS = listOf(
            Band(60.0, 0.7, Type.LOW_SHELF),
            Band(230.0, 1.0, Type.PEAK),
            Band(910.0, 1.0, Type.PEAK),
            Band(3600.0, 1.0, Type.PEAK),
            Band(14000.0, 0.7, Type.HIGH_SHELF),
        )

        /** RBJ Audio EQ Cookbook; returns normalised b0 b1 b2 a1 a2. */
        fun biquad(type: Type, hz: Double, q: Double, gainDb: Double, fs: Int): DoubleArray {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2 * PI * hz.coerceAtMost(fs * 0.45) / fs
            val cw = cos(w0)
            val alpha = sin(w0) / (2 * q)
            val (b0, b1, b2, a0, a1, a2) = when (type) {
                Type.PEAK -> Six(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
                Type.LOW_SHELF -> {
                    val sq = 2 * sqrt(a) * alpha
                    Six(
                        a * ((a + 1) - (a - 1) * cw + sq), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sq),
                        (a + 1) + (a - 1) * cw + sq, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sq,
                    )
                }
                Type.HIGH_SHELF -> {
                    val sq = 2 * sqrt(a) * alpha
                    Six(
                        a * ((a + 1) + (a - 1) * cw + sq), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sq),
                        (a + 1) - (a - 1) * cw + sq, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sq,
                    )
                }
            }
            return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        private data class Six(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double)
    }
}
