/*
 * MusicForge's version of BitChord's DspChain (https://github.com/kushagrasinghx/BitChord,
 * Copyright (C) 2026 Kushagra Singh), cut down to the one stage the loop mix needs.
 * Copyright (C) 2026 apathane (MusicForge adaptation). AGPL-3.0, see LICENSE and NOTICE.
 */
package dev.musicforge.automix

import androidx.media3.common.util.UnstableApi

/**
 * The Float32 stages [PrecisionAudioSink] runs between decode and encode. BitChord chains spatial,
 * EQ, transition and loudness here; MusicForge keeps its EQ and loudness as Media3 processors in
 * the wrapped DefaultAudioSink, so only Automix's [transition] filter (with its loop-mix port)
 * runs in the float path. Same API as BitChord's, so the sink is unchanged.
 */
@UnstableApi
class DspChain(
    val transition: TransitionFilterProcessor = TransitionFilterProcessor(),
) : FloatAudioProcessor {

    override fun configure(sampleRate: Int, channelCount: Int) = transition.configure(sampleRate, channelCount)

    /** BitChord resets its loudness stage here; nothing in this chain keeps per-stream state. */
    fun onStreamBoundary() = Unit

    override fun process(block: AudioBlock) {
        if (block.frameCount == 0) return
        transition.process(block)
    }

    @Suppress("DEPRECATION")
    override fun flush() = transition.flush()

    override fun reset() = transition.reset()
}

/**
 * BitChord's process-wide output telemetry ("is this output bit-exact?"). not ported: MusicForge
 * has no output-quality screen, so the sink's reports go nowhere.
 */
object AudioOutputStatus {
    fun publishOutputExactness(@Suppress("UNUSED_PARAMETER") exact: Boolean, @Suppress("UNUSED_PARAMETER") detail: String?) = Unit

    fun publishDsp(
        @Suppress("UNUSED_PARAMETER") decoderOutputEncoding: String?,
        @Suppress("UNUSED_PARAMETER") dspFormat: String,
        @Suppress("UNUSED_PARAMETER") dspAvailable: Boolean,
    ) = Unit
}
