package dev.musicforge.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import dev.musicforge.playback.NowPlaying
import dev.musicforge.playback.PlayerConnection
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.glass
import dev.musicforge.ui.designsystem.pressScale

/**
 * Mini player: a 56 dp frosted pill docked in the floating nav bar.
 * Pull up to morph into the full player (shared artwork morph, Convx morph system).
 * Swipe sideways to skip (velocity or quarter-width drag).
 *
 * Combined from BitChord's MiniPlayerPull and Convx's AppleMiniPlayer / FloatingMiniPlayer.
 */
@Composable
fun MiniPlayer(
    now: NowPlaying,
    connection: PlayerConnection,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val prefs by context.graph.settings.data.collectAsStateWithLifecycle(initialValue = null)
    val hapticsEnabled = prefs?.get(Keys.HapticsEnabled) ?: true
    val swipeEnabled = prefs?.get(Keys.SwipeMiniToSkip) ?: true
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier
            .fillMaxWidth()
            .glass(shape)
            .padding(horizontal = 8.dp)
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp)
                .then(if (swipeEnabled) rememberSkipSwipe(now, connection, haptics) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Artwork: 40 dp, 8 dp corner, content description.
            Artwork(
                now.metadata.artworkUri?.toString(),
                40.dp,
                corner = 8.dp,
                contentDescription = now.metadata.title?.toString().orEmpty(),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    now.metadata.title?.toString().orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    now.metadata.artist?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            // Play/pause: 48 dp, press feedback, content description.
            val isPlaying = now.isPlaying
            ControlButton(
                if (isPlaying) ForgeIcons.Pause else ForgeIcons.Play,
                if (isPlaying) "Pause" else "Play",
                enabled = true,
                onClick = { connection.togglePlay() },
            )
            Spacer(Modifier.width(8.dp))
            ControlButton(
                ForgeIcons.Next,
                "Next",
                enabled = now.hasNext,
                onClick = { connection.next() },
            )
        }
    }
}

/** Swipe the mini player sideways to skip: velocity or quarter-width drag. */
@Composable
private fun rememberSkipSwipe(
    now: NowPlaying,
    connection: PlayerConnection,
    hapticsEnabled: Boolean,
): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main) }
    val latest by rememberUpdatedState(now)
    val haptics = LocalHapticFeedback.current

    return Modifier.pointerInput(connection) {
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragCancel = { scope.launch { offset.snapTo(0f) } },
            onHorizontalDrag = { change, dx ->
                tracker.addPosition(change.uptimeMillis, change.position)
                val blocked = (dx < 0 && !latest.hasNext) || (dx > 0 && !latest.hasPrevious)
                scope.launch {
                    offset.snapTo(offset.value + if (blocked) dx * 0.25f else dx)
                }
            },
            onDragEnd = {
                val v = tracker.calculateVelocity().x
                val width = size.width.toFloat()
                val goNext = latest.hasNext && (offset.value < -width / 4f || v < -FLING_VELOCITY)
                val goPrev = latest.hasPrevious && (offset.value > width / 4f || v > FLING_VELOCITY)
                scope.launch {
                    if (goNext || goPrev) {
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (goNext) connection.next() else connection.previous()
                        scope.launch { offset.animateTo(if (goNext) -width else width, tween(140)) }
                    }
                    scope.launch { offset.snapTo(0f) }
                }
            },
        )
    }
}

/** Play / next control: 48 dp, press feedback, content description. */
@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun pressInteraction() = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

/** Pixels per second that count as a flick. */
private const val FLING_VELOCITY = 1200f

/** For the press spring (defined in Motion.kt as Motion.Press). */
private fun tween(durationMs: Long) = androidx.compose.animation.core.tween(durationMs)
