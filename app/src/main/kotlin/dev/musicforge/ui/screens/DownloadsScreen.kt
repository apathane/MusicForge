package dev.musicforge.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.PillButton
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SectionTitle
import dev.musicforge.ui.components.SongRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
@Composable
fun DownloadsScreen() {
    val graph = LocalContext.current.graph
    val dao = graph.database.dao()
    val done by remember { dao.downloadedSongs() }.collectAsStateWithLifecycle(emptyList())
    // In-flight downloads, joined with their DB rows for titles and artwork.
    val active by remember {
        graph.downloads.states
            .map { states ->
                states.values.filter { it.state != Download.STATE_COMPLETED }.sortedBy { it.startTimeMs }.map { it.request.id }
            }
            .distinctUntilChanged()
            .flatMapLatest { ids -> dao.songs(ids).map { rows -> rows.sortedBy { ids.indexOf(it.id) } } }
    }.collectAsStateWithLifecycle(emptyList())
    val player = graph.player

    val pending by graph.redownloads.pending.collectAsStateWithLifecycle()
    val failed by remember { graph.downloads.states.map { s -> s.values.count { it.state == Download.STATE_FAILED } }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(0)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { ScreenHeader("Downloads") }
        pending?.let { p -> item(key = "redownload") { BringBackCard(p) } }
        if (active.isEmpty() && done.isEmpty()) {
            item { EmptyState("Nothing downloaded yet", "Long-press any song and choose Download. Downloads play offline and pick up where they left off if the phone kills the app.") }
        }
        if (failed > 0) {
            item(key = "failed") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (failed == 1) "1 download failed" else "$failed downloads failed",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton("Retry", onClick = { graph.downloads.retryFailed() })
                }
            }
        }
        if (active.isNotEmpty()) {
            item { SectionTitle("In progress") }
            items(active, key = { "a:" + it.id }) { s ->
                val item = remember(s.id) { s.toMediaItem() }
                SongRow(item, s.artistLine, onPlay = { player.play(listOf(item)) })
            }
            if (done.isNotEmpty()) item { SectionTitle("Downloaded") }
        }
        items(done, key = { "d:" + it.id }) { s ->
            val item = remember(s.id) { s.toMediaItem() }
            SongRow(item, s.artistLine, onPlay = {
                player.play(done.map { it.toMediaItem() }, done.indexOf(s))
            })
        }
    }
}

/**
 * After a restore: the songs the old app had downloaded, in one tap. Shows the rough size
 * first (Opus at ~128 kbps is ~16 KB per second) and says when the phone is on mobile data.
 */
@Composable
private fun BringBackCard(pending: dev.musicforge.download.Redownloads.Pending) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Chunked: older phones' SQLite allows 999 parameters per query, and this can be thousands.
    val songs by produceState(emptyList<dev.musicforge.data.db.Song>(), pending) {
        value = pending.ids.chunked(500).flatMap { graph.database.dao().songs(it).first() }
    }
    val bytes = songs.sumOf { it.song.duration.coerceAtLeast(0).toLong() } * 16_000L
    val metered = remember {
        context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
    }
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Bring back your downloads", style = MaterialTheme.typography.titleMedium)
        Text(
            "${pending.ids.size} songs were downloaded in ${pending.from}. Backups don't carry the files, " +
                "so they need downloading again: about ${android.text.format.Formatter.formatShortFileSize(context, bytes)}" +
                if (metered) ". You're on mobile data right now." else ".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (metered) "Download anyway" else "Download all", onClick = {
                scope.launch {
                    graph.downloads.download(songs.map { it.toMediaItem() })
                    graph.redownloads.clear()
                }
            }, enabled = songs.isNotEmpty())
            Text(
                "Not now",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).clickable { scope.launch { graph.redownloads.clear() } }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
