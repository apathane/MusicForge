package dev.musicforge.ui.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * The owner's page motion (docs/motion/motion-plan-0.15.md): a pushed page grows out of the spot
 * that was tapped, tilts in slightly on X, and back plays the same thing in reverse — same spring,
 * same values, same origin, so the page folds back into the card it came from.
 *
 * Exact mirror means time-reversed: back runs the entrance's own curve backwards (the house curve
 * EaseDrawer flipped end for end) and fades in its last 120 ms where the push faded in its first.
 * A spring cannot be played backwards, so these are tweens. The incoming page turns opaque fast,
 * so the two pages never sit on top of each other half-see-through. The tilt is read in the draw
 * phase only, so nothing recomposes per frame on the X7b.
 */
object GhostTransition {
    private const val GROW_FROM = 0.86f   // the page starts the size of a large card
    private const val SINK_TO = 0.96f     // the page underneath steps back, not away
    const val TILT_DEG = 6f

    private val panel = Mirror.Panel           // 380 ms on EaseDrawer, opaque in 120 ms
    private fun <T> there(): FiniteAnimationSpec<T> = panel.enter()
    private fun <T> back(): FiniteAnimationSpec<T> = panel.exit()

    private var lastTap = Offset.Unspecified
    private var rootSize = IntSize.Zero
    // Back folds the page into the card it came from, like a letter that changed its mind.
    // shortcut: entries are never removed, a few dozen floats per session; prune if back stacks get deep.
    private val origins = HashMap<String, TransformOrigin>()

    /** Remembers where each tap lands, without consuming it, so the next push can grow from there. */
    fun Modifier.recordTaps(): Modifier = onSizeChanged { rootSize = it }.pointerInput(Unit) {
        awaitEachGesture { lastTap = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position }
    }

    /** The origin a page entry grows from: fixed the first time it is asked, so back finds it again. */
    fun originOf(entryId: String): TransformOrigin = origins.getOrPut(entryId) {
        if (lastTap == Offset.Unspecified || rootSize.width == 0 || rootSize.height == 0) TransformOrigin.Center
        else TransformOrigin(lastTap.x / rootSize.width, lastTap.y / rootSize.height)
    }

    fun push(origin: TransformOrigin): EnterTransition =
        fadeIn(panel.fadeIn()) + scaleIn(there(), initialScale = GROW_FROM, transformOrigin = origin)

    /** The exact reverse of [push]: back into the card it grew from, gone in the last 120 ms. */
    fun pop(origin: TransformOrigin): ExitTransition =
        fadeOut(panel.fadeOut()) +
            scaleOut(back(), targetScale = GROW_FROM, transformOrigin = origin)

    // The page below only steps back: fading it too showed both pages at once.
    fun cover(): ExitTransition = scaleOut(there(), targetScale = SINK_TO)

    /** The exact reverse of [cover]. */
    fun uncover(): EnterTransition = scaleIn(back(), initialScale = SINK_TO)

    /**
     * The slight 3D tilt while a page enters or leaves; flat once it has arrived. Pivot follows the
     * same origin as the scale, so the page leans away from the finger.
     */
    @Composable
    fun Modifier.tilt(scope: AnimatedVisibilityScope, origin: TransformOrigin, enabled: Boolean): Modifier {
        if (!enabled) return this
        val angle by scope.transition.animateFloat(
            transitionSpec = { if (targetState == EnterExitState.Visible) there() else back() },
            label = "tilt",
        ) {
            if (it == EnterExitState.Visible) 0f else TILT_DEG
        }
        return graphicsLayer {
            rotationX = angle
            transformOrigin = origin
            cameraDistance = 12f * density
        }
    }
}
