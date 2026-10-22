package dev.musicforge.ui.player

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
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
import dev.musicforge.playback.NowPlaying
import dev.musicforge.playback.PlayerConnection
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.glass

@Composable
fun MiniPlayer(
    now: NowPlaying,
    connection: PlayerConnection,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position: State<Long> = remember(connection) { connection.position() }.collectAsStateWithLifecycle(0L)
    val track = MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .glass(shape)
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
            if (now.isBuffering && !now.isPlaying) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = track, strokeWidth = 2.dp)
                }
            } else {
                ControlButton(if (now.isPlaying) ForgeIcons.Pause else ForgeIcons.Play, if (now.isPlaying) "Pause" else "Play") { connection.togglePlay() }
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

@Composable
private fun ControlButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
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
