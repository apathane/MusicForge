package dev.musicforge.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The Signal design tokens (docs/PERSONALIZED-UI-SPEC.md §3). Colour lives in the theme's scheme;
 * these are the shapes and spacing every screen shares, so screens stop picking their own.
 */
object Signal {
    val radiusSmall = 10.dp
    val radiusRow = 14.dp
    val radiusSurface = 22.dp
    val radiusHero = 30.dp
    val gutter = 20.dp
}

/** A section's title and the short reason it is on screen. */
@Composable
fun SectionHeader(title: String, reason: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = Signal.gutter, end = Signal.gutter, top = 22.dp, bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!reason.isNullOrEmpty()) {
            Text(
                reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Filter, mood and period chips: one selected look, ink on canvas inverted. */
@Composable
fun SignalChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .semantics { role = Role.Tab; this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A flat grouping surface (cards, welcome and restore prompts): no blur, no shadow. */
fun Modifier.signalSurface(color: androidx.compose.ui.graphics.Color, radius: androidx.compose.ui.unit.Dp = Signal.radiusSurface) =
    clip(RoundedCornerShape(radius)).background(color)

/**
 * Pressed things give way a little (0.96), so a tap is felt before anything else happens — 2 px down
 * and a tenth of the glyph's opacity, like the ghost's icon buttons. Springs follow the duration
 * scale, so reduced motion still snaps to target. Read in the draw phase on one layer: no
 * recomposition per frame.
 */
fun Modifier.pressScale(interaction: androidx.compose.foundation.interaction.InteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.96f else 1f,
        Motion.Press,
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
