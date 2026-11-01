package dev.musicforge.ui.forge

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.data.lyrics.Lyrics
import dev.musicforge.graph
import dev.musicforge.playback.NowPlaying
import dev.musicforge.playback.PlayerConnection
import dev.musicforge.playback.durationSec
import dev.musicforge.ui.Tab
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.glass
import dev.musicforge.ui.designsystem.Motion
import dev.musicforge.ui.designsystem.Physics
import dev.musicforge.ui.designsystem.StreamText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Forge dock: one glass capsule where Classic stacked a mini player and a nav pill. Being
 *  the only glass here, the tab lens inside is drawn rather than blured. */
@Composable
fun ForgeDock(
    now: NowPlaying?,
    connection: PlayerConnection,
    current: Tab,
    onOpenPlayer: () -> Unit,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 12.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp))) {
        if (now != null) {
            DockTop(now, connection, onOpenPlayer)
            // 0.08 is the app's hairline: visible on both themes, never a line.
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)))
        }
        DockTabs(current, onSelect, Modifier.fillMaxWidth())
    }
}

@Composable
private fun DockTop(now: NowPlaying, connection: PlayerConnection, onOpenPlayer: () -> Unit) {
    val position: State<Long> = remember(connection) { connection.position() }.collectAsStateWithLifecycle(0L)
    val line = rememberSyncedLine(now, position)
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onOpenPlayer).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(now.metadata.artworkUri?.toString(), 40.dp, corner = 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                now.metadata.title?.toString().orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (line != null) {
                // The dock sings along when it knows the words. Keyed on the line, so a new line
                // types itself in instead of the old one morphing into it letter by letter.
                key(line) {
                    Box(Modifier.fillMaxWidth().clipToBounds()) {
                        StreamText(
                            text = line.ifBlank { "♪" },
                            done = true,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }
            } else {
                // No lyrics: just who is playing, or why nothing is.
                Text(
                    now.error ?: now.metadata.artist?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (now.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DockPlayButton(now.isPlaying, connection)
    }
}

/** The current synced line, or null when the track has none. The position stream ticks four times
 *  a second; [derivedStateOf] turns that into a value that changes only when the line does, so the
 *  dock recomposes on the beat of the lyrics, not the beat of the poller. */
@Composable
private fun rememberSyncedLine(now: NowPlaying, position: State<Long>): String? {
    val graph = LocalContext.current.graph
    val meta = now.metadata
    val lyrics by produceState<Lyrics?>(null, now.mediaId) {
        // Lyrics fall back to network lookups, so keep them off the frame thread.
        value = withContext(Dispatchers.IO) {
            graph.lyrics.lyrics(
                now.mediaId,
                meta.title?.toString().orEmpty(),
                meta.artist?.toString().orEmpty().substringBefore(","),
                meta.albumTitle?.toString(),
                meta.durationSec ?: (now.durationMs / 1000).toInt().takeIf { it > 0 },
            )
        }
    }
    val lines = (lyrics as? Lyrics.Synced)?.lines ?: return null
    // 250 ms of lead, as in PlayerPanes: people read ahead of the singer.
    val index by remember(lines) { derivedStateOf { lines.indexOfLast { it.timeMs <= position.value + 250 } } }
    return lines.getOrNull(index)?.text
}

@Composable
private fun DockPlayButton(playing: Boolean, connection: PlayerConnection) {
    val label = if (playing) "Pause" else "Play"
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(percent = 50))
            .clickable(onClickLabel = label) { connection.togglePlay() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) ForgeIcons.Pause else ForgeIcons.Play,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** The same gliding lens as the classic nav pill, minus the glass: this one already sits on the
 *  dock. Equal slots, so the lens' centre is half a slot past its index — the tabs split the width
 *  like siblings splitting a pizza: evenly, and measured. */
@Composable
private fun DockTabs(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val lens = remember { Animatable(current.ordinal.toFloat()) }
    LaunchedEffect(current) { lens.animateTo(current.ordinal.toFloat(), Physics.lensGlide()) }
    val lensColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)
    Row(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 6.dp).drawBehind {
            val pitch = size.width / Tab.entries.size
            val w = pitch - 8.dp.toPx()
            val h = size.height - 8.dp.toPx()
            // Speed in px/s, the unit dock.js tuned its 0.03 against.
            val (sx, sy) = Physics.stretchScale(Physics.stretchFor(lens.velocity * pitch))
            val cx = (lens.value + 0.5f) * pitch
            drawRoundRect(
                lensColor,
                topLeft = Offset(cx - w * sx / 2, size.height / 2 - h * sy / 2),
                size = Size(w * sx, h * sy),
                cornerRadius = CornerRadius(h * sy / 2),
            )
        },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val tint by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(Motion.Fade),
                label = "dockTabTint",
            )
            Column(
                modifier = Modifier.weight(1f).height(52.dp)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(tab) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}
