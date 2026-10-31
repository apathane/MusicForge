package dev.musicforge.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A stepped slider with the physics of OpenGhost's effort slider (effort-slider.js): the thumb
 * follows the finger on a stiff spring, gives way like a rubber band past either end, is pulled
 * toward the nearest step, gets thrown on by a flick when you let go, and squashes along the way
 * it is travelling. Positions are whole steps 0..[max]; [value] and [onValueChange] speak steps.
 *
 * Only the gesture writes state; the thumb, fill and squash are read in the Canvas draw, so a
 * drag repaints one layer and recomposes nothing. Tap jumps to the step under the finger.
 * (The rubber band is a square root and a fraction; no rubber was harmed.)
 */
@Composable
fun PhysicsSlider(
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    contentDescription: String,
    stateDescription: String,
    modifier: Modifier = Modifier,
) {
    val pos = remember { Animatable(value.toFloat()) }
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    val top = max.toFloat()

    // Settings can change underneath (restore, another screen): follow unless a finger is on it.
    LaunchedEffect(value) { if (!dragging) pos.animateTo(value.toFloat(), Physics.sliderSettle()) }

    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = MaterialTheme.colorScheme.primary
    val tickOff = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val tickOn = MaterialTheme.colorScheme.onPrimary

    fun settle(target: Float, velocity: Float) {
        val step = target.roundToInt().coerceIn(0, max)
        report(step)
        scope.launch { pos.animateTo(step.toFloat(), Physics.sliderSettle(), initialVelocity = velocity) }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..top, steps = (max - 1).coerceAtLeast(0))
                setProgress { target -> settle(target, 0f); true }
            }
            .pointerInput(max) {
                detectTapGestures { at -> settle(at.x / size.width * top, 0f) }
            }
            .pointerInput(max) {
                val tracker = VelocityTracker()
                fun stepsAt(x: Float) = x / size.width * top
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        dragging = true
                        tracker.resetTracking()
                        scope.launch { pos.animateTo(Physics.shapeSlider(stepsAt(at.x), top), Physics.sliderFollow()) }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val goal = Physics.shapeSlider(stepsAt(change.position.x), top)
                        // A new target each move: Animatable keeps the velocity it already had.
                        scope.launch { pos.animateTo(goal, Physics.sliderFollow()) }
                    },
                    onDragEnd = {
                        dragging = false
                        val v = tracker.calculateVelocity().x / size.width * top
                        settle(Physics.flingTarget(pos.value, v, top), pos.velocity)
                    },
                    onDragCancel = {
                        dragging = false
                        settle(pos.value, 0f)
                    },
                )
            },
    ) {
        val thumbR = 11.dp.toPx()
        val left = thumbR
        val width = size.width - 2 * thumbR
        val cy = size.height / 2
        val bar = 4.dp.toPx()
        val x = left + width * (pos.value / top).coerceIn(-0.05f, 1.05f)
        drawRoundRect(track, Offset(left, cy - bar / 2), Size(width, bar), CornerRadius(bar / 2))
        drawRoundRect(fill, Offset(left, cy - bar / 2), Size((x - left).coerceAtLeast(0f), bar), CornerRadius(bar / 2))
        for (i in 0..max) {
            val tx = left + width * i / top
            drawCircle(if (tx <= x) tickOn else tickOff, radius = 1.5.dp.toPx(), center = Offset(tx, cy))
        }
        // The thumb squashes along its travel (stretch along speed, dock.js / effort-slider.js).
        val (sx, sy) = Physics.stretchScale(Physics.stretchFor(pos.velocity, perSpeed = 0.035f, max = 0.3f))
        drawRoundRect(
            fill,
            topLeft = Offset(x - thumbR * sx, cy - thumbR * sy),
            size = Size(2 * thumbR * sx, 2 * thumbR * sy),
            cornerRadius = CornerRadius(thumbR * sy),
        )
    }
}
