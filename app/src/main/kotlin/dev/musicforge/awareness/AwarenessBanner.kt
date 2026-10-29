package dev.musicforge.awareness

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.musicforge.ui.designsystem.Signal
import dev.musicforge.ui.theme.ForgeTheme

/**
 * A small dismissible banner at the top of Home showing the awareness message.
 * Animates in/out with graphicsLayer (no recomposition per frame).
 */
@Composable
fun AwarenessBanner(
    message: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val visible by remember { mutableStateOf(message != null) }
    val messageText = message ?: ""

    // Deferred read of message for the Text composable
    var currentMessage by remember { mutableStateOf(messageText) }
    if (message != currentMessage) {
        currentMessage = messageText
    }

    val alpha by animateFloatAsState(
        if (visible) 1f else 0f,
        spring(dampingRatio = 0.9f, stiffness = 400f),
        label = "banner alpha"
    )
    val height by animateDpAsState(
        if (visible) 48.dp else 0.dp,
        spring(dampingRatio = 0.9f, stiffness = 400f),
        label = "banner height"
    )

    // Auto-hide after 8 seconds
    androidx.compose.runtime.LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(8000)
            if (visible) {
                onDismiss()
            }
        }
    }

    if (height.value > 0.dp || alpha > 0f) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    this.alpha = alpha
                    translationY = (1f - alpha) * -20f
                }
                .clip(RoundedCornerShape(0.dp, 0.dp, Signal.radiusSurface, Signal.radiusSurface))
                .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.95f))
                .padding(horizontal = Signal.gutter)
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = currentMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onInverseSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = context.getString(android.R.string.cancel),
                        tint = MaterialTheme.colorScheme.onInverseSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
@Preview(widthDp = 360, heightDp = 100)
@OptIn(androidx.compose.ui.tooling.preview.Preview::class)
private fun AwarenessBannerPreview() {
    ForgeTheme {
        AwarenessBanner("You're offline. Showing what's downloaded.") {}
    }
}

@Composable
@Preview(widthDp = 360, heightDp = 100, name = "AwarenessBannerPreview_Long")
@OptIn(androidx.compose.ui.tooling.preview.Preview::class)
private fun AwarenessBannerPreviewLong() {
    ForgeTheme {
        AwarenessBanner("Battery saver is on, so artwork is lighter. Downloads may pause on metered networks.") {}
    }
}

@Composable
@Preview(widthDp = 360, heightDp = 100, name = "AwarenessBannerPreview_None")
@OptIn(androidx.compose.ui.tooling.preview.Preview::class)
private fun AwarenessBannerPreviewNone() {
    ForgeTheme {
        AwarenessBanner(null) {}
    }
}