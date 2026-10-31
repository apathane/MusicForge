/*
 * Ported from BitChord (https://github.com/kushagrasinghx/BitChord),
 * Copyright (C) 2026 Kushagra Singh, licensed GPL-3.0.
 * Copyright (C) 2026 apathane (MusicForge adaptation).
 * Distributed in MusicForge under the AGPL-3.0 (GPLv3/AGPLv3 section 13). See NOTICE.
 */
package dev.musicforge.automix

/**
 * Common contract for high-precision, in-place Float32 audio DSP processors.
 *
 * Processors operate directly on canonical [AudioBlock] instances holding normalized
 * IEEE 754 32-bit floats, avoiding intermediate fixed-point quantization and preserving
 * dynamic headroom throughout the DSP pipeline.
 */
interface FloatAudioProcessor {
    /**
     * Configures the processor for the specified [sampleRate] and [channelCount].
     */
    fun configure(sampleRate: Int, channelCount: Int = AudioBlock.DEFAULT_CHANNELS)

    /**
     * Processes audio frames in [block] in-place.
     *
     * @param block Reusable AudioBlock containing interleaved normalized Float32 samples.
     */
    fun process(block: AudioBlock)

    /**
     * Flushes internal filter states and histories (e.g. on seek or stream boundary).
     */
    fun flush()

    /**
     * Resets internal states and releases sample buffers.
     */
    fun reset()
}

// BitChord's spatial-audio and equaliser adapters are not ported: MusicForge has no spatial
// processor, and its EqualizerProcessor is a Media3 AudioProcessor in the sink, not a float stage.

/** Adapts [TransitionFilterProcessor] to [FloatAudioProcessor]. */
@OptIn(androidx.media3.common.util.UnstableApi::class)
fun TransitionFilterProcessor.asFloatAudioProcessor(): FloatAudioProcessor =
    object : FloatAudioProcessor {
        override fun configure(sampleRate: Int, channelCount: Int) =
            this@asFloatAudioProcessor.configure(sampleRate, channelCount)
        override fun process(block: AudioBlock) =
            this@asFloatAudioProcessor.process(block)
        @Suppress("DEPRECATION")
        override fun flush() = this@asFloatAudioProcessor.flush()
        override fun reset() = this@asFloatAudioProcessor.reset()
    }

