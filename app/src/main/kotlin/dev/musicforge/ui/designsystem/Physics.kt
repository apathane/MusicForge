package dev.musicforge.ui.designsystem

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * OpenGhost's hand-tuned springs and the little bits of physics around them, translated for
 * Compose. OpenGhost integrates `v += ((goal - x)·k - v·c)·h` in 4 ms steps with mass 1; Compose's
 * spring is the same oscillator described as stiffness = k, dampingRatio = c / (2√k), and
 * `Animatable` already keeps velocity when the target moves, which is the part OpenGhost wrote by
 * hand. The pure functions below are the shaping it does on top (unit-tested in PhysicsTest).
 */
object Physics {
    fun <T> og(k: Float, c: Float): SpringSpec<T> = spring(dampingRatio = c / (2f * sqrt(k)), stiffness = k)

    // [k, c] straight from the source files named on each line.
    fun <T> press(): SpringSpec<T> = og(230f, 27f)          // icon-button.js:23
    fun <T> glide(): SpringSpec<T> = og(520f, 40f)          // chat-list.js:4, highlight between rows
    fun <T> highlightFade(): SpringSpec<T> = og(320f, 32f)  // chat-list.js:5
    fun <T> smoothHeight(): SpringSpec<T> = og(260f, 32f)   // smooth-height.js:4
    fun <T> lensGlide(): SpringSpec<T> = og(260f, 28f)      // dock.js:50
    fun <T> stretch(): SpringSpec<T> = og(520f, 34f)        // dock.js:51
    fun <T> pressGrow(): SpringSpec<T> = og(420f, 30f)      // dock.js:52
    fun <T> sliderFollow(): SpringSpec<T> = og(1400f, 75f)  // effort-slider.js:5, while dragging
    fun <T> sliderSettle(): SpringSpec<T> = og(320f, 26f)   // effort-slider.js:6, after release

    /**
     * How much a moving lens squashes along its travel: min(max, |v|·perSpeed), dock.js:729.
     * [velocity] is in whatever unit per second the caller travels in (dock: px; slider: steps).
     */
    fun stretchFor(velocity: Float, perSpeed: Float = 0.03f, max: Float = 0.28f): Float = min(max, abs(velocity) * perSpeed)

    /** The stretch as (scaleX, scaleY): longer along the travel, a third as much thinner across. */
    fun stretchScale(stretch: Float): Pair<Float, Float> = (1f + stretch) to (1f - 0.35f * stretch)

    /**
     * Past an end, a drag gives less and less: effort-slider.js:27, `(1 - 1/(d·k/limit + 1))·limit`.
     * Approaches [limit] however far you pull; [d] is the overshoot (positive).
     */
    fun rubber(d: Float, limit: Float = 0.16f, k: Float = 0.55f): Float = (1f - 1f / (d * k / limit + 1f)) * limit

    /**
     * A slider position shaped the way OpenGhost's effort slider shapes it (effort-slider.js:271):
     * rubber-banded past either end, and pulled toward the nearest whole step when close to it.
     * [raw] and [max] are in steps.
     */
    fun shapeSlider(raw: Float, max: Float, reach: Float = 0.22f, pull: Float = 0.45f): Float = when {
        raw < 0f -> -rubber(-raw)
        raw > max -> max + rubber(raw - max)
        else -> {
            val d = raw - raw.roundToInt()
            raw - d * pull * (1f - smoothstep(0f, reach, abs(d)))
        }
    }

    /** Where a released slider lands: the goal thrown on by its speed, effort-slider.js:307. */
    fun flingTarget(goal: Float, velocity: Float, max: Float, fling: Float = 0.06f): Float =
        (goal + velocity * fling).coerceIn(0f, max).roundToInt().toFloat()

    /**
     * How far a pulled bar rises for [pulled] px of finger: less per pixel the further it goes.
     * BitChord's mini player (MiniPlayer.kt:166): rate·2·ease·(√(1 + pulled/ease) − 1).
     */
    fun pullLift(pulled: Float, ease: Float, rate: Float = 0.5f): Float =
        if (pulled <= 0f || ease <= 0f) 0f else rate * 2f * ease * (sqrt(1f + pulled / ease) - 1f)

    fun smoothstep(a: Float, b: Float, v: Float): Float {
        val t = ((v - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Signed version of [rubber] for a drag that may overshoot either way. */
    fun rubberSigned(d: Float): Float = sign(d) * rubber(abs(d))
}
