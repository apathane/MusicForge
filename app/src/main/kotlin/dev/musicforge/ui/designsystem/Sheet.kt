package dev.musicforge.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch

/**
 * MusicForge's bottom sheet, replacing Material's. It rises on [Mirror.Panel] (380 ms, house curve)
 * and leaves on the same curve run backwards, with the scrim riding the same progress, so closing
 * is the opening played in reverse (the owner's rule). It follows a finger down; let go past a
 * quarter of its height or with a flick and it closes, otherwise it springs back on OpenGhost's
 * glide [520, 40] keeping the finger's speed.
 *
 * Scrim taps, back and drags animate out before [onDismissRequest] runs. An action inside that
 * calls the caller's own dismiss closes at once, which is right: the action has already happened.
 * Drawn in a dialog window so it sits above the full player; the dialog's own dim is turned off
 * because the scrim here is ours.
 */
@Composable
fun ForgeSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val open = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    var heightPx by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissRequest)

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            open.animateTo(0f, Mirror.Panel.exit())
            dismiss()
        }
    }

    LaunchedEffect(Unit) { open.animateTo(1f, Mirror.Panel.enter()) }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = open.value }
                    .background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { close() } }
                    .semantics {
                        contentDescription = "Close"
                        onClick { close(); true }
                    },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { heightPx = it.height }
                    .graphicsLayer {
                        // Unmeasured, it would flash at rest for a frame before sliding in.
                        alpha = if (heightPx == 0) 0f else 1f
                        translationY = (1f - open.value) * heightPx + drag.value
                    }
                    .clip(RoundedCornerShape(topStart = Signal.radiusSurface, topEnd = Signal.radiusSurface))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker()
                        val fling = 700.dp.toPx()
                        detectVerticalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) }
                            },
                            onDragEnd = {
                                val v = tracker.calculateVelocity().y
                                if (drag.value > heightPx * 0.25f || v > fling) close()
                                else scope.launch { drag.animateTo(0f, Physics.glide(), initialVelocity = v) }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f, Physics.glide()) } },
                        )
                    }
                    .navigationBarsPadding(),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 6.dp)
                        .size(36.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
                content()
            }
        }
    }
}
