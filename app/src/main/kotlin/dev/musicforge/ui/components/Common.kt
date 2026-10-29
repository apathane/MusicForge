package dev.musicforge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.musicforge.graph
import dev.musicforge.ui.designsystem.pressScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/** Big, heavy page title in the BitChord style, with an optional trailing action. */
@Composable
fun ScreenHeader(title: String, belowBackRow: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            // Under a back-button row the status bar is already accounted for.
            .then(if (belowBackRow) Modifier else Modifier.statusBarsPadding())
            .padding(start = 20.dp, end = 12.dp, top = if (belowBackRow) 0.dp else 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.displayLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
    }
}

/**
 * Artwork decoded at its display size. On a 1080p phone a 48 dp thumbnail is 144 px;
 * decoding the 544 px source would waste memory and GPU upload bandwidth.
 */
@Composable
fun Artwork(url: String?, size: Dp, corner: Dp = 8.dp, modifier: Modifier = Modifier, fallbackLetter: String? = null) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val context = LocalContext.current
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    var failed by remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            // Only paint the grey tile when there is no image: under a loaded cover it was one more
            // full-card fill per frame (overdraw on the X7b's Adreno 610).
            .then(if (url == null) Modifier.background(placeholder) else Modifier),
    ) {
        if (url == null && fallbackLetter != null) {
            Text(
                fallbackLetter.take(1).uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (url != null) {
            // YouTube video thumbnails are 4:3 with the 16:9 picture letterboxed inside; zoom past
            // the baked-in black bars (360/270) so they read as square covers like album art. The
            // zoom is part of the image's scaling, not a separate layer.
            val letterboxed = dev.musicforge.playback.isLetterboxedThumbnail(url)
            val tile = remember(placeholder) { androidx.compose.ui.graphics.painter.ColorPainter(placeholder) }
            AsyncImage(
                // Right-sized file; fade only for big artwork (a fade draws both images: GPU time on the X7b).
                model = remember(url, px) {
                    ImageRequest.Builder(context).data(dev.musicforge.playback.sizedArtwork(url, px)).size(px).crossfade(px > 600).build()
                },
                contentDescription = null,
                placeholder = tile,
                error = tile,
                onError = { e ->
                    // A cover that fails to load would otherwise be a blank tile; the letter says whose it is.
                    failed = true
                    // The server refused this link (stale avatar, retired cover): fetch a fresh one.
                    // Offline failures carry no HTTP code and are left alone.
                    val code = (e.result.throwable as? coil3.network.HttpException)?.response?.code
                    if (code == 403 || code == 404 || code == 410) context.graph.artRepair.failed(url)
                },
                contentScale = if (letterboxed) CropPastLetterbox else ContentScale.Crop,
                modifier = Modifier.size(size),
            )
            if (failed && fallbackLetter != null) {
                Text(
                    fallbackLetter.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/** Crop, then zoom 4/3 so a letterboxed 16:9 picture inside a 4:3 thumbnail fills the square. */
internal object CropPastLetterbox : ContentScale {
    override fun computeScaleFactor(srcSize: androidx.compose.ui.geometry.Size, dstSize: androidx.compose.ui.geometry.Size): androidx.compose.ui.layout.ScaleFactor {
        val s = maxOf(dstSize.width / srcSize.width, dstSize.height / srcSize.height) * 4f / 3f
        return androidx.compose.ui.layout.ScaleFactor(s, s)
    }
}

@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Album track lists show the track number instead of repeating the same cover. */
    number: Int? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val press = interactionSource ?: remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(press)
            .combinedClickable(
                interactionSource = press,
                indication = null,
                onClick = onClick,
                // The tick says the menu came from the long press, not a slipped tap.
                onLongClick = onLongClick?.let { { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); it() } },
                onLongClickLabel = "More options",
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Box(Modifier.size(width = 32.dp, height = 48.dp), contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
        } else {
            Artwork(artworkUrl, 48.dp, corner = 6.dp, fallbackLetter = title)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** Pill button: black on white / white on black. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .pressScale(press)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(interactionSource = press, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
}
