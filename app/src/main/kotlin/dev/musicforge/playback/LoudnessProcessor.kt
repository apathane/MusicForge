package dev.musicforge.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loudness normalisation inside each player's audio sink, last in the chain (after the EQ and
 * Automix's transition filter). It used to be `player.volume`, but Automix's crossfade owns the
 * two players' volumes now, so the per-track gain lives here where nothing else writes it.
 *
 * [blendTrim] is Automix's headroom while two songs overlap (BitChord's LoudnessProcessor has the
 * same knob): both players drop a little so the sum doesn't clip.
 */
@UnstableApi
class LoudnessProcessor : BaseAudioProcessor() {
    @Volatile var trackGain: Float = 1f
    @Volatile var blendTrim: Float = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size).order(ByteOrder.nativeOrder())
        val gain = (trackGain * blendTrim).coerceIn(0f, 1f) // attenuation only: nothing can clip
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            while (input.remaining() >= 4) out.putFloat(input.float * gain)
        } else if (gain == 1f) {
            out.put(input)
        } else {
            while (input.remaining() >= 2) out.putShort((input.short * gain).toInt().toShort())
        }
        out.flip()
    }
}
