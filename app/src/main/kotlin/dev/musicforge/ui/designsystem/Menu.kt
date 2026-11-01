package dev.musicforge.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * A menu that grows out of the thing that opened it and shrinks back into it (OpenGhost's row
 * menu, row-menu.js): 133 ms from scale(1.09, 0.96) and transparent, pinned at the trigger's corner,
 * and the close is the same motion reversed (the owner's rule). Place it inside the trigger's Box,
 * as with Material's DropdownMenu; keep it composed and drive [expanded], or the close can't play.
 */
@Composable
fun AnchoredMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val v = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded) {
        if (expanded) {
            shown = true
            v.animateTo(1f, Mirror.Menu.enter())
        } else {
            v.animateTo(0f, Mirror.Menu.exit())
            shown = false
        }
    }
    if (!shown) return
    val place = remember { MenuPlacement() }
    Popup(
        popupPositionProvider = place,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    val p = v.value
                    transformOrigin = place.origin
                    scaleX = 1.09f - 0.09f * p
                    scaleY = 0.96f + 0.04f * p
                    alpha = p
                }
                .widthIn(min = 180.dp, max = 280.dp)
                .clip(RoundedCornerShape(Signal.radiusRow))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 6.dp),
            content = content,
        )
    }
}

/** One menu line: 48 dp tall, a little give when pressed, optional icon in front. */
@Composable
fun AnchoredMenuItem(text: String, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Box(Modifier.padding(end = 14.dp)) { leading() }
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Below the trigger and lined up with its end edge; flipped above when there is no room, nudged
 * inside the window. Remembers which corner it grew from, so the scale is pinned there.
 * (A menu that knows where it came from: more than most of us can say on a Monday.)
 */
private class MenuPlacement : PopupPositionProvider {
    var origin = TransformOrigin(1f, 0f)

    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val endAligned = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val x = endAligned.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom
        val above = anchorBounds.top - popupContentSize.height
        val flip = below + popupContentSize.height > windowSize.height && above >= 0
        val y = if (flip) above else below.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        origin = TransformOrigin(if (layoutDirection == LayoutDirection.Ltr) 1f else 0f, if (flip) 1f else 0f)
        return IntOffset(x, y)
    }
}
