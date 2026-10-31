package dev.musicforge.ui.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
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
 * One critically damped spring drives every direction (OpenGhost's smooth-height [260, 32]), which
 * is what makes the exit an exact mirror of the entrance; a tween pair would need two curves. The
 * tilt is read in the draw phase only, so nothing recomposes per frame on the X7b.
 */
object GhostTransition {
    private const val GROW_FROM = 0.86f   // the page starts the size of a large card
    private const val SINK_TO = 0.96f     // the page underneath steps back, not away
    const val TILT_DEG = 6f

    private fun <T> spec(): FiniteAnimationSpec<T> = spring(dampingRatio = .99f, stiffness = 260f)

    private var lastTap = Offset.Unspecified
    private var rootSize = IntSize.Zero
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
        fadeIn(spec()) + scaleIn(spec(), initialScale = GROW_FROM, transformOrigin = origin)

    /** The exact reverse of [push]: back into the card it grew from. */
    fun pop(origin: TransformOrigin): ExitTransition =
        fadeOut(spec()) + scaleOut(spec(), targetScale = GROW_FROM, transformOrigin = origin)

    fun cover(): ExitTransition = fadeOut(spec(), targetAlpha = 0.4f) + scaleOut(spec(), targetScale = SINK_TO)

    /** The exact reverse of [cover]. */
    fun uncover(): EnterTransition = fadeIn(spec(), initialAlpha = 0.4f) + scaleIn(spec(), initialScale = SINK_TO)

    /**
     * The slight 3D tilt while a page enters or leaves; flat once it has arrived. Pivot follows the
     * same origin as the scale, so the page leans away from the finger.
     */
    @Composable
    fun Modifier.tilt(scope: AnimatedVisibilityScope, origin: TransformOrigin, enabled: Boolean): Modifier {
        if (!enabled) return this
        val angle by scope.transition.animateFloat(transitionSpec = { spec() }, label = "tilt") {
            if (it == EnterExitState.Visible) 0f else TILT_DEG
        }
        return graphicsLayer {
            rotationX = angle
            transformOrigin = origin
            cameraDistance = 12f * density
        }
    }
}
