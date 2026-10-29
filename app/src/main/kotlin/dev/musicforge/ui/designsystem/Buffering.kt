package dev.musicforge.ui.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The buffering indicator is a breathing dot, not a spinner — the skeleton breathe pattern
 * (§2.6/§2.8: 1.0↔.45 over Motion.Ambient on EaseInOutWave) so the player and the lists share one
 * idle language. One value read in the draw phase via Canvas: no recomposition per frame.
 */
@Composable
fun BufferingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
) {
    val breathe = rememberInfiniteTransition(label = "buffering")
    val alpha = breathe.animateFloat(
        1.0f, 0.45f,
        infiniteRepeatable(tween(Motion.Ambient, easing = Motion.EaseInOutWave), RepeatMode.Restart),
        label = "alpha",
    )
    Canvas(modifier.size(size)) {
        drawCircle(color.copy(alpha = color.alpha * alpha.value), style = Fill)
    }
}
