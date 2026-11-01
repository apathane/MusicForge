package dev.musicforge.ui.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

/**
 * A cubic-bezier curve that knows its own reverse. The owner's rule (2026-10-31) is that an exit
 * is the exact reverse of its entrance; for a tween that means the same duration on the curve run
 * backwards, which for cubic-bezier(x1, y1, x2, y2) is (1 - x2, 1 - y2, 1 - x1, 1 - y1).
 * [CubicBezierEasing] hides its control points, hence this little wrapper.
 */
class Curve(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    val easing: Easing = CubicBezierEasing(x1, y1, x2, y2)
    val reversed: Curve get() = Curve(1f - x2, 1f - y2, 1f - x1, 1f - y1)

    companion object {
        val Drawer = Curve(.32f, .72f, 0f, 1f)          // the house curve (Motion.EaseDrawer)
        val OutQuint = Curve(.22f, 1f, .36f, 1f)
        val OutCubic = Curve(.33f, 1f, .68f, 1f)
        val InOutSoft = Curve(.37f, 0f, .63f, 1f)       // menus; symmetric, so its reverse is itself
    }
}

/**
 * One entrance and its mirror image. Anything that moves in on [enter] leaves on [exit], and a
 * fade that happens in the first [fadeMs] of the entrance happens in the last [fadeMs] of the exit.
 */
class Mirror(val durationMs: Int, val curve: Curve, val fadeMs: Int = durationMs) {
    fun <T> enter(): FiniteAnimationSpec<T> = tween(durationMs, easing = curve.easing)
    fun <T> exit(): FiniteAnimationSpec<T> = tween(durationMs, easing = curve.reversed.easing)
    fun <T> fadeIn(): FiniteAnimationSpec<T> = tween(fadeMs, easing = LinearEasing)
    fun <T> fadeOut(): FiniteAnimationSpec<T> = tween(fadeMs, delayMillis = durationMs - fadeMs, easing = LinearEasing)

    companion object {
        /** Pages, sheets and the player: OpenGhost's 380 ms panel on the house curve. */
        val Panel = Mirror(Motion.Panel, Curve.Drawer, fadeMs = 120)
        /** Menus from their trigger: 133 ms, OpenGhost's spotlight/row-menu open. */
        val Menu = Mirror(Motion.Menu, Curve.InOutSoft)
    }
}
