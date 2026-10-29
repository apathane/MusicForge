package dev.musicforge.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import dev.musicforge.playback.NowPlaying
import dev.musicforge.playback.PlayerConnection
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.glass
import dev.musicforge.ui.designsystem.BufferingIndicator

@Composable
fun MiniPlayer(
    now: NowPlaying,
    connection: PlayerConnection,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val position: State<Long> = remember(connection) { connection.position() }.collectAsStateWithLifecycle(0L)
    val track = MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(16.dp)
    val prefs by context.graph.settings.data.collectAsStateWithLifecycle(initialValue = null)
    val hapticsEnabled = prefs?.get(Keys.HapticsEnabled) ?: true
    val swipeEnabled = prefs?.get(Keys.SwipeMiniToSkip) ?: true
    val swipe = rememberSkipSwipe(now, connection, hapticsEnabled)
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .glass(shape)
            .clickable(onClick = onOpen)
            .then(if (swipeEnabled) swipe.gesture else Modifier),
    ) {
        // The glass stays put; only its contents follow the finger, as on ConvX and Metrolist.
        Row(
            Modifier
                .graphicsLayer {
                    translationX = swipe.offset()
                    alpha = 1f - (kotlin.math.abs(swipe.offset()) / size.width).coerceIn(0f, 0.6f)
                }
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(now.metadata.artworkUri?.toString(), 44.dp, corner = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(now.metadata.title?.toString().orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    now.error ?: now.metadata.artist?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (now.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Play, pause, buffer — three states, one button, 140 ms to decide which it is today.
            // The spinner only shows when the music's pretending to be ready.
            AnimatedContent(
                targetState = now.isPlaying to now.isBuffering,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(120)) },
                label = "playPause",
            ) { (playing, buffering) ->
                if (buffering && !playing) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        BufferingIndicator(Modifier.size(20.dp), color = track)
                    }
                } else {
                    ControlButton(if (playing) ForgeIcons.Pause else ForgeIcons.Play, if (playing) "Pause" else "Play") { connection.togglePlay() }
                }
            }
            ControlButton(ForgeIcons.Next, "Next", enabled = now.hasNext) { connection.next() }
        }
        // Progress hairline, inset from the rounded corners. Position is read only while
        // drawing, so it never recomposes.
        Spacer(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 5.dp)
                .height(2.dp)
                .drawBehind {
                    val fraction = if (now.durationMs > 0) (position.value.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f
                    val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    drawRoundRect(track.copy(alpha = 0.14f), cornerRadius = r)
                    drawRoundRect(track, topLeft = Offset.Zero, size = Size(size.width * fraction, size.height), cornerRadius = r)
                },
        )
    }
}

private class SkipSwipe(val gesture: Modifier, val offset: () -> Float)

/**
 * Swipe the mini player sideways to skip: left for next, right for previous. A flick is enough
 * (velocity), or a drag past a quarter of the width. Released short of that, it springs back.
 */
@Composable
private fun rememberSkipSwipe(now: NowPlaying, connection: PlayerConnection, hapticsEnabled: Boolean): SkipSwipe {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(now)
    val gesture = Modifier.pointerInput(connection) {
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragCancel = { scope.launch { offset.animateTo(0f, SettleSpring) } },
            onHorizontalDrag = { change, dx ->
                tracker.addPosition(change.uptimeMillis, change.position)
                // Resist toward a side there is nothing to skip to.
                val blocked = (dx < 0 && !latest.hasNext) || (dx > 0 && !latest.hasPrevious)
                scope.launch { offset.snapTo(offset.value + if (blocked) dx * 0.25f else dx) }
            },
            onDragEnd = {
                val v = tracker.calculateVelocity().x
                val width = size.width.toFloat()
                val goNext = latest.hasNext && (offset.value < -width / 4 || v < -FlingVelocity)
                val goPrev = latest.hasPrevious && (offset.value > width / 4 || v > FlingVelocity)
                scope.launch {
                    if (goNext || goPrev) {
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (goNext) connection.next() else connection.previous()
                        // Leave off the side it was thrown to and come back in from the other.
                        offset.animateTo(if (goNext) -width else width, tween(140))
                        offset.snapTo(if (goNext) width * 0.4f else -width * 0.4f)
                    }
                    offset.animateTo(0f, SettleSpring)
                }
            },
        )
    }
    return remember(gesture) { SkipSwipe(gesture) { offset.value } }
}

private val SettleSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)

/** Pixels per second that count as a flick. */
private const val FlingVelocity = 1200f

@Composable
private fun ControlButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(28.dp),
        )
    }
}
