package dev.musicforge.playback.segue

import kotlin.math.sqrt

/**
 * Turns interleaved 16-bit PCM into one RMS value (0..1) per [stepMs], fed in any chunk size, so
 * the decoder can hand over buffers as they come. Channels are mixed by averaging their squares.
 */
class RmsMeter(private val sampleRate: Int, private val channels: Int, private val stepMs: Long) {
    init {
        require(sampleRate > 0 && channels > 0 && stepMs > 0) { "sampleRate, channels and stepMs must be positive" }
    }

    private val framesPerStep = (sampleRate * stepMs / 1000).coerceAtLeast(1)
    private val out = ArrayList<Float>()
    private var sumSquares = 0.0
    private var frames = 0L
    private var channelIndex = 0
    private var frameSquares = 0.0

    /** Feeds [length] samples from [samples] starting at [offset]; samples are interleaved by channel. */
    fun feed(samples: ShortArray, offset: Int = 0, length: Int = samples.size) {
        for (i in offset until offset + length) {
            val s = samples[i] / 32768.0
            frameSquares += s * s
            if (++channelIndex == channels) {
                sumSquares += frameSquares / channels
                frameSquares = 0.0
                channelIndex = 0
                if (++frames == framesPerStep) {
                    out.add(sqrt(sumSquares / frames).toFloat())
                    sumSquares = 0.0
                    frames = 0
                }
            }
        }
    }

    /** The steps measured so far. A trailing partial step is dropped: it would read as a fade. */
    fun result(): FloatArray = out.toFloatArray()
}
