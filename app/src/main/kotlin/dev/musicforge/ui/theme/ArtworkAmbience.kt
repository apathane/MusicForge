package dev.musicforge.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import dev.musicforge.ui.designsystem.Motion
import dev.musicforge.ui.designsystem.reducedMotion

/**
 * ArtworkAmbience: the per-track backdrop behind the player.
 *
 * - Mesh gradient sampled from the current artwork colour, so every track gets its own mood
 *   without inventing art (Convx ArtworkMeshBackground + BitChord artwork-derived tinting).
 * - Ambient canvas motion: one slow drifting particle per track, glassy, read inside Canvas draw
 *   phase (graphicsLayer) — no recomposition per frame. Dropped under reduced motion.
 */
@Composable
fun ArtworkAmbience(
    colour: Color,
    reduced: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val mesh = artworkMesh(colour)
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxSize()
            .background(mesh),
    ) {
        if (!reduced) {
            val phase = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                phase.animateTo(
                    1f,
                    animationSpec = tween(Motion.Ambient, easing = Motion.EaseInOutWave),
                )
                // Restart forever.
                while (isActive) {
                    phase.snapTo(0f)
                    phase.animateTo(1f, animationSpec = tween(Motion.Ambient, easing = Motion.EaseInOutWave))
                }
            }
            Canvas(
                modifier.fillMaxSize().graphicsLayer { alpha = 0.05f },
            ) {
                val r = size.minDimension * 0.25f
                drawCircle(
                    colour.copy(alpha = 0.06f),
                    radius = r,
                    center = Offset(phase.value * size.width, size.height * 0.4f),
                    style = Fill,
                )
            }
        }
    }
}

/** Radial mesh gradient derived from a single artwork colour: centre tint, edge shade. */
private fun artworkMesh(colour: Color): Brush {
    val light = colour.copy(alpha = 0.15f)
    val mid = colour.copy(alpha = 0.06f)
    return Brush.radialGradient(
        colors = listOf(light, mid, Color.Transparent),
        center = Offset(0.5f, 0.3f),
        radius = 0.7f,
    )
}
