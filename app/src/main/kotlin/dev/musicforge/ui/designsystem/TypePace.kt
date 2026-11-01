package dev.musicforge.ui.designsystem

import java.text.BreakIterator
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * OpenGhost's typing numbers as plain Kotlin, so the composables that draw text stay thin and the
 * maths can be unit-tested (TypePaceTest).
 */
object TypePace {
    /** stream-view.js:4 PACE: chars/s follow backlog / lag while text streams, / drain once done. */
    const val LAG = 0.28f
    const val DRAIN = 0.14f
    const val MIN_RATE = 40f
    const val MAX_RATE = 2400f
    const val SMOOTH = 0.1f

    /** stream-view.js:5 WAVE: each grapheme rises over 220 ms; only the newest 90; stagger ≤ 50 ms. */
    const val WAVE_MS = 220
    const val WAVE_MAX = 90
    const val WAVE_SPREAD_MS = 50

    /** chat-list.js:32 WRITE: titles write a letter every 18 ms, all of it inside 420 ms. */
    const val WRITE_MS = 360
    const val WRITE_STEP_MS = 18
    const val WRITE_SPREAD_MS = 420

    /** Graphemes, not chars: an emoji or a Zulu click letter is one strike (Intl.Segmenter → BreakIterator). */
    fun graphemes(text: String): List<String> {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val out = ArrayList<String>(text.length)
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            if (end > start) out.add(text.substring(start, end))
            start = end
            end = it.next()
        }
        return out
    }

    /** Delay before the [index]th letter of an [count]-letter title starts writing (chat-list.js:37). */
    fun writeDelayMs(index: Int, count: Int): Float = index * min(WRITE_STEP_MS.toFloat(), WRITE_SPREAD_MS.toFloat() / max(1, count))

    /**
     * Paces text that arrives over time (stream-view.js:129). Feed it the full text so far with
     * [push]; call [advance] once a frame with the seconds since the last one; draw [shown] graphemes.
     */
    class Stream {
        var total = 0
            private set
        var shown = 0f
            private set
        var done = false
            private set
        private var rate = 0f

        fun push(total: Int, done: Boolean = false) {
            this.total = total
            this.done = done
        }

        fun advance(dt: Float, reduced: Boolean = false) {
            if (reduced) { shown = total.toFloat(); return }
            val backlog = total - shown
            val want = (backlog / if (done) DRAIN else LAG).coerceIn(MIN_RATE, MAX_RATE)
            rate += (want - rate) * (1f - exp(-dt / SMOOTH))
            if (backlog > 0f) shown = min(total.toFloat(), shown + max(MIN_RATE, rate) * dt)
        }

        val caughtUp: Boolean get() = shown >= total
    }
}
