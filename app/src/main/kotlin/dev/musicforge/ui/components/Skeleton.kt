package dev.musicforge.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import dev.musicforge.ui.designsystem.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholder rows shaped like [TrackRow], so the page keeps its layout while it loads instead of
 * a spinner jumping to content (Metrolist, ConvX and BitChord all do this). A slow opacity pulse
 * rather than a moving shimmer: one layer alpha, read in the draw phase.
 */
@Composable
fun SkeletonRows(count: Int = 8, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha = pulse.animateFloat(1.0f, 0.45f, infiniteRepeatable(tween(Motion.Ambient, easing = Motion.EaseInOutWave), RepeatMode.Restart), label = "pulse")
    // Ink at low alpha, not a surface colour: on pure black the surfaces are nearly black too.
    val tone = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)
    Column(modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha.value }.semantics { contentDescription = "Loading" }) {
        repeat(count) { i ->
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Bar(48.dp, 48.dp, tone, 6.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    // Varied widths read as text; equal bars read as a grid.
                    Bar(listOf(180, 140, 210, 120)[i % 4].dp, 12.dp, tone)
                    Spacer(Modifier.height(8.dp))
                    Bar(listOf(110, 90, 130, 70)[i % 4].dp, 10.dp, tone)
                }
            }
        }
    }
}

@Composable
private fun Bar(width: Dp, height: Dp, tone: androidx.compose.ui.graphics.Color, corner: Dp = 4.dp) {
    Spacer(Modifier.size(width, height).clip(RoundedCornerShape(corner)).background(tone))
}
