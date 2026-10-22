package dev.musicforge.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.data.lyrics.Lyrics
import dev.musicforge.graph
import dev.musicforge.playback.NowPlaying
import dev.musicforge.playback.durationSec
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.ForgeIcons

/**
 * Synced lyrics: the current line is bright and large, the rest recede; the list follows
 * the music unless you're reading ahead, and a tap on any line jumps there.
 */
@Composable
fun LyricsPane(now: NowPlaying, position: State<Long>, accent: Color, onSeek: (Long) -> Unit) {
    val graph = LocalContext.current.graph
    val meta = now.metadata
    val lyrics by produceState<Lyrics?>(null, now.mediaId) {
        value = graph.lyrics.lyrics(
            now.mediaId,
            meta.title?.toString().orEmpty(),
            meta.artist?.toString().orEmpty().substringBefore(","),
            meta.albumTitle?.toString(),
            meta.durationSec ?: (now.durationMs / 1000).toInt().takeIf { it > 0 },
        )
    }
    val white = Color.White
    when (val l = lyrics) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), color = white, strokeWidth = 2.dp)
        }
        Lyrics.None -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No lyrics for this one.\nJust listen.", color = white.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        is Lyrics.Plain -> Text(
            l.text,
            color = white.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 30.sp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        )
        is Lyrics.Synced -> SyncedLyrics(l.lines, position, onSeek)
    }
}

@Composable
private fun SyncedLyrics(lines: List<Lyrics.Line>, position: State<Long>, onSeek: (Long) -> Unit) {
    val list = rememberLazyListState()
    // Recomposes only when the line changes, not on every position tick.
    val current by remember(lines) {
        derivedStateOf {
            val p = position.value + 250 // lead slightly: a line should light up as it's sung
            lines.indexOfLast { it.timeMs <= p }
        }
    }
    val touchedAt = remember { mutableLongStateOf(0L) }
    LaunchedEffect(current) {
        // Don't yank the list away while someone is reading ahead.
        if (current >= 0 && android.os.SystemClock.uptimeMillis() - touchedAt.longValue > 3_000) {
            list.animateScrollToItem(current, scrollOffset = -list.layoutInfo.viewportSize.height / 3)
        }
    }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(vertical = 120.dp),
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                touchedAt.longValue = android.os.SystemClock.uptimeMillis()
            }
        },
    ) {
        itemsIndexed(lines, key = { i, l -> "$i:${l.timeMs}" }) { i, line ->
            val active = i == current
            val color by animateColorAsState(
                if (active) Color.White else Color.White.copy(alpha = if (i < current) 0.32f else 0.42f),
                tween(220),
                label = "lyricColor",
            )
            Text(
                line.text.ifEmpty { "♪" },
                color = color,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 25.sp,
                    lineHeight = 32.sp,
                    fontWeight = if (active) FontWeight.W800 else FontWeight.W700,
                ),
                modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }.padding(vertical = 9.dp),
            )
        }
    }
}

/** The queue, current song highlighted; tap to jump, ✕ to remove. */
@Composable
fun QueuePane() {
    val connection = LocalContext.current.graph.player
    val queue by connection.queue.collectAsStateWithLifecycle()
    val current by connection.currentIndex.collectAsStateWithLifecycle()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    val white = Color.White
    Column(Modifier.fillMaxSize()) {
        Text("Up next", style = MaterialTheme.typography.titleMedium, color = white, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue, key = { i, item -> "$i:${item.mediaId}" }) { i, item ->
                val playing = i == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (playing) white.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { connection.skipTo(i) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(item.mediaMetadata.artworkUri?.toString(), 42.dp, corner = 6.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.mediaMetadata.title?.toString().orEmpty(), color = white, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.mediaMetadata.artist?.toString().orEmpty(), color = white.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!playing) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable { connection.removeAt(i) }, contentAlignment = Alignment.Center) {
                            Text("✕", color = white.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(sleepAt: Long?, onPick: (minutes: Int?) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onPick(-1) }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (sleepAt != null) {
                val left = ((sleepAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                Text("Stopping in about $left min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            }
            val options = listOf(0 to "End of this song", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour", 90 to "1½ hours")
            options.forEach { (m, label) -> SheetRow(label) { onPick(m) } }
            if (sleepAt != null) SheetRow("Turn off") { onPick(null) }
        }
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(ForgeIcons.Sleep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}
