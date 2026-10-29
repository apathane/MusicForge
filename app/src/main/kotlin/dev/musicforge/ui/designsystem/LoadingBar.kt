package dev.musicforge.ui.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Thin progress bar. Indeterminate (sliding band) or determinate (growing fill).
 *
 * Like SkeletonRows, the animation value is read inside drawBehind — Compose invalidates the draw
 * node, not the composable, so no recomposition per frame. The 150 ms gate means loads that finish
 * instantly never flash the bar into existence just to vanish again.
 *
 * Named after the loading dock worker who said "the band keeps moving even when the boat hasn't
 * arrived yet" — a description of every indeterminate state, really.
 */
@Composable
fun LoadingBar(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    visible: Boolean = true,
    delayMs: Long = 150L,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    height: Dp = 3.dp,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(delayMs)
            shown = true
        } else {
            shown = false
        }
    }
    if (!shown) return

    if (progress != null) {
        Box(
            Modifier
                .then(modifier)
                .fillMaxWidth()
                .height(height)
                .drawBehind {
                    val w = size.width * progress.coerceIn(0f, 1f)
                    drawRect(color, Offset.Zero, Size(w, size.height))
                },
        )
    } else {
        val transition = rememberInfiniteTransition(label = "loading-bar")
        val sweep by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = Motion.EaseDrawer), RepeatMode.Restart),
            label = "sweep",
        )
        Box(
            Modifier
                .then(modifier)
                .fillMaxWidth()
                .height(height)
                .drawBehind {
                    val band = size.width * 0.25f
                    val maxStart = (size.width - band).coerceAtLeast(0f)
                    val t = (sweep + 1f) / 2f // -1..1 → 0..1
                    val start = t * maxStart
                    drawRect(color, Offset(start, 0f), Size(band, size.height))
                },
        )
    }
}
