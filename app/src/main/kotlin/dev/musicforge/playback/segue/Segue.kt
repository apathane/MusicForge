package dev.musicforge.playback.segue

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Segue: MusicForge's own automix planner, written clean-room. It decides where a blend between
 * two songs starts and ends, from the loudness of the outgoing song's end and the incoming
 * song's start. No beat grid yet: it listens for the outro and skips the quiet intro.
 *
 * Pure on purpose: analysis (decoding cached bytes into an [Envelope]) and playback (a second
 * player for the overlap) live elsewhere, so the decisions here have tests.
 */

/** Loudness over time: one RMS value (0..1) per [stepMs], starting at [startMs] into the song. */
class Envelope(val startMs: Long, val stepMs: Long, val rms: FloatArray) {
    init {
        require(stepMs > 0) { "stepMs must be positive" }
    }

    fun timeAt(index: Int): Long = startMs + index * stepMs
}

/**
 * The blend: the outgoing song starts fading at [outStartMs] (its own time); the incoming song
 * starts playing at [inStartMs] (its own time) at the same moment; the blend lasts [blendMs].
 * [reason] says which rule chose it, for logs and the "why" line in the UI.
 */
data class SeguePlan(val outStartMs: Long, val inStartMs: Long, val blendMs: Long, val reason: Reason) {
    enum class Reason { Outro, Fallback }
}

/** Limits for a blend. Defaults suit pop and electronic tracks; short songs shrink the blend. */
data class SegueLimits(
    val minBlendMs: Long = 4_000,
    val maxBlendMs: Long = 12_000,
    /** A step counts as quiet when it is below this share of the tail's typical loudness. */
    val outroShare: Float = 0.5f,
    /** The incoming song enters at the first step at or above this share of its head's peak. */
    val entryShare: Float = 0.25f,
    /** Never skip more than this much of the incoming song's start. */
    val maxEntrySkipMs: Long = 15_000,
)

/**
 * Plans a blend from [outDurationMs] and the two envelopes. Either envelope may be null (not
 * analysed yet); then the plan is a plain blend at the end with no intro skip.
 */
fun planSegue(
    outDurationMs: Long,
    outTail: Envelope?,
    inHead: Envelope?,
    limits: SegueLimits = SegueLimits(),
): SeguePlan {
    val maxBlend = limits.maxBlendMs.coerceAtMost(outDurationMs / 3).coerceAtLeast(0)
    val inStart = inHead?.let { entryPoint(it, limits) } ?: 0L
    val outro = outTail?.let { outroStart(it, limits) }
    if (outro == null || outro >= outDurationMs) {
        return SeguePlan((outDurationMs - maxBlend).coerceAtLeast(0), inStart, maxBlend, SeguePlan.Reason.Fallback)
    }
    // The blend runs from the outro to the end, clamped to the limits; a long outro is cut to max.
    val blend = (outDurationMs - outro).coerceIn(limits.minBlendMs.coerceAtMost(maxBlend), maxBlend)
    return SeguePlan(outDurationMs - blend, inStart, blend, SeguePlan.Reason.Outro)
}

/**
 * Where the song's ending starts: walking back from the end, the earliest step of the final run of
 * quiet steps. Null when the end is loud to the last step (a hard stop) or the tail is all quiet.
 */
internal fun outroStart(tail: Envelope, limits: SegueLimits): Long? {
    val values = tail.rms
    if (values.isEmpty()) return null
    // 75th percentile, not the median: an outro longer than half the tail would drag a median down
    // to its own level and hide itself.
    val typical = percentile(values, 0.75f)
    if (typical <= 0f) return null
    val quiet = typical * limits.outroShare
    var i = values.size
    while (i > 0 && values[i - 1] < quiet) i--
    return when (i) {
        values.size -> null // loud right to the end
        0 -> null // nothing loud: no outro to find, just silence
        else -> tail.timeAt(i)
    }
}

/** Where the incoming song gets going: the first step loud enough, capped by maxEntrySkipMs. */
internal fun entryPoint(head: Envelope, limits: SegueLimits): Long {
    val values = head.rms
    val peak = values.maxOrNull() ?: return head.startMs
    if (peak <= 0f) return head.startMs
    val idx = values.indexOfFirst { it >= peak * limits.entryShare }.coerceAtLeast(0)
    return head.timeAt(idx).coerceAtMost(head.startMs + limits.maxEntrySkipMs)
}

/** Equal-power gains at blend progress [t] in 0..1: the sum of squares stays 1, so no dip. */
fun equalPowerGains(t: Float): Pair<Float, Float> {
    val p = t.coerceIn(0f, 1f) * (PI / 2)
    return cos(p).toFloat() to sin(p).toFloat()
}

/** Nearest-rank percentile, [p] in 0..1. */
private fun percentile(values: FloatArray, p: Float): Float {
    val sorted = values.sorted()
    return sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)]
}
