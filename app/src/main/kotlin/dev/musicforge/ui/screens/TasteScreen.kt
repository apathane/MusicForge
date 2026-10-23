package dev.musicforge.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.data.db.HourCount
import dev.musicforge.graph
import dev.musicforge.recommendation.HomeComposer
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.IconAction
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.TrackRow
import dev.musicforge.ui.designsystem.SectionHeader
import kotlinx.coroutines.launch

/**
 * What shapes Home, laid open: the hours you listen, the artists that weigh most this month, and
 * every correction you made, each undoable on its own (spec: personalisation can be inspected and
 * corrected). Read-only except for the undo taps.
 */
@Composable
fun TasteScreen(onBack: () -> Unit) {
    val graph = LocalContext.current.graph
    val dao = graph.database.dao()
    val scope = rememberCoroutineScope()
    val now = remember { System.currentTimeMillis() }
    val hours by remember { dao.playsByHour() }.collectAsStateWithLifecycle(emptyList())
    val artists by remember { dao.topArtists(now - 30 * 86_400_000L, 8) }.collectAsStateWithLifecycle(emptyList())
    val hidden by remember { dao.hiddenSongs() }.collectAsStateWithLifecycle(emptyList())
    val lessOf by remember { dao.lessOfArtists(now) }.collectAsStateWithLifecycle(emptyList())

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp)) { IconAction(ForgeIcons.Back, "Back", onBack) } }
        item { ScreenHeader("What shapes your Home", belowBackRow = true) }
        item {
            Text(
                "Home is built on this phone from what you play, like and download. Nothing here leaves it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { SectionHeader("When you listen", "Plays by hour of the day, all time. Around this hour reads this.") }
        item { HourChart(hours) }
        if (artists.isNotEmpty()) {
            item { SectionHeader("Who you play most", "Last 30 days, by listening time") }
            items(artists, key = { "a:" + it.artist.id }) { a ->
                TrackRow(a.artist.name, "${a.timeMs / 60_000} min · ${a.plays} plays", a.artist.thumbnailUrl, onClick = {})
            }
        }
        item { SectionHeader("Songs you hid", if (hidden.isEmpty()) "None. Long-press a song on Home and choose Not for me." else "Tap one to bring it back to Home") }
        items(hidden, key = { "h:" + it.id }) { s ->
            TrackRow(s.song.title, s.artistLine, s.song.thumbnailUrl, onClick = { scope.launch { dao.unhide(s.id) } })
        }
        item { SectionHeader("Artists you wanted less of", if (lessOf.isEmpty()) "None right now." else "Off the suggestion shelves until the date shown; tap to undo") }
        items(lessOf, key = { "l:" + it.artist.id }) { l ->
            val days = ((l.expiresAt - now) / 86_400_000L).coerceAtLeast(0)
            TrackRow(l.artist.name, "For $days more days", l.artist.thumbnailUrl, onClick = { scope.launch { dao.clearLessOf(l.artist.id) } })
        }
        if (hours.isEmpty() && artists.isEmpty() && hidden.isEmpty()) {
            item { EmptyState("Nothing yet", "Play some music: this fills in as you listen.") }
        }
    }
}

/**
 * Plays per hour as 24 thin bars, one ink colour: the current hour full strength and labelled
 * "now", the rest muted, so the highlight never rests on colour alone. Tap a bar to read it.
 */
@Composable
private fun HourChart(rows: List<HourCount>) {
    val byHour = remember(rows) { IntArray(24).also { a -> rows.forEach { if (it.hour in 0..23) a[it.hour] = it.plays } } }
    val max = byHour.maxOrNull()?.coerceAtLeast(1) ?: 1
    val nowHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outline
    // The text summary is the chart's table view for screen readers and anyone who'd rather read.
    val peaks = byHour.withIndex().filter { it.value > 0 }.sortedByDescending { it.value }.take(3)
    val summary = if (peaks.isEmpty()) "No plays yet" else "Most plays at " + peaks.joinToString { "${HomeComposer.hourLabel(it.index)} (${it.value})" }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            picked?.let { "${HomeComposer.hourLabel(it)} · ${byHour[it]} ${if (byHour[it] == 1) "play" else "plays"}" } ?: summary,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(top = 8.dp)
                .semantics { contentDescription = "Plays by hour of the day. $summary." }
                .pointerInput(Unit) {
                    detectTapGestures { o -> picked = (o.x / size.width * 24).toInt().coerceIn(0, 23).takeIf { it != picked } }
                },
        ) {
            val gap = 2.dp.toPx()
            val slot = size.width / 24
            val r = 4.dp.toPx()
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            for (h in 0 until 24) {
                val v = byHour[h]
                if (v == 0) continue
                val barH = (size.height - r) * v / max + r
                val left = h * slot + gap / 2
                val color = if (h == nowHour || h == picked) ink else muted
                // Rounded at the data end, square on the baseline: one shape, so the muted
                // colour isn't painted twice where a square base would overlap the round top.
                val bar = androidx.compose.ui.geometry.RoundRect(
                    androidx.compose.ui.geometry.Rect(Offset(left, size.height - barH), Size(slot - gap, barH)),
                    topLeft = CornerRadius(r, r), topRight = CornerRadius(r, r),
                )
                drawPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(bar) }, color)
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("12 am", "6 am", "noon", "6 pm").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
        }
        Text(
            "now: ${HomeComposer.hourLabel(nowHour)}",
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
