package dev.musicforge.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.musicforge.ui.designsystem.LoadingBar
import dev.musicforge.ui.designsystem.pressScale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
@Composable
fun DownloadsScreen() {
    val graph = LocalContext.current.graph
    val dao = graph.database.dao()
    val done by remember { dao.downloadedSongs() }.collectAsStateWithLifecycle(emptyList())
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

    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    dev.musicforge.ui.ScrollToTopOnReselect(list)
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = LocalBottomInset.current) {
        item { DownloadHeader() }
        if (active.isNotEmpty()) item { LoadingBar(visible = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) }
        pending?.let { p -> item(key = "redownload") { BringBackCard(p) } }
        if (active.isEmpty() && done.isEmpty()) {
            item { EmptyState("Nothing downloaded yet", "Long-press any song and choose Download. Downloads play offline and pick up where they left off if the phone kills the app.") }
        }
        if (failed > 0) {
            item(key = "failed") {
                DownloadFailedRow(failed, onRetry = { graph.downloads.retryFailed() })
            }
        }
        if (active.isNotEmpty()) {
            item { SectionTitle("In progress") }
            items(active, key = { "a:" + it.id }) { s ->
                val item = remember(s.id) { s.toMediaItem() }
                DownloadSongRow(item, s.artistLine, onPlay = { player.play(listOf(item)) })
            }
            if (done.isNotEmpty()) item { SectionTitle("Downloaded") }
        }
        items(done, key = { "d:" + it.id }) { s ->
            val item = remember(s.id) { s.toMediaItem() }
            DownloadSongRow(item, s.artistLine, onPlay = { player.play(done.map { it.toMediaItem() }, done.indexOf(s)) })
        }
    }
}

@Composable
private fun DownloadHeader() {
    dev.musicforge.ui.components.ScreenHeader("Downloads") { dev.musicforge.ui.SettingsAction() }
}

/** Failed downloads: pill retry, 48 dp. */
@Composable
private fun DownloadFailedRow(failed: Int, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (failed == 1) "1 download failed" else "$failed downloads failed",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        PillButton("Retry", onClick = onRetry)
    }
}

/** Song row with a determinate progress bar when active. */
@Composable
private fun DownloadSongRow(item: androidx.media3.common.MediaItem, subtitle: String, onPlay: () -> Unit) {
    dev.musicforge.ui.components.SongRow(item, subtitle, onPlay = onPlay)
}

/** Bring-back card: glass surface, pill buttons, 48 dp. */
@OptIn(androidx.annotation.OptIn::class)
@Composable
private fun BringBackCard(pending: dev.musicforge.download.Redownloads.Pending) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val songs by androidx.compose.runtime.produceState(emptyList<dev.musicforge.data.db.Song>(), pending) {
        value = pending.ids.chunked(500).flatMap { graph.database.dao().songs(it).first() }
    }
    val bytes = songs.sumOf { it.song.duration.coerceAtLeast(0).toLong() } * 16_000L
    val metered = remember {
        context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
    }
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            DownloadPill(if (metered) "Download anyway" else "Download all", onClick = {
                scope.launch {
                    graph.downloads.download(songs.map { it.toMediaItem() })
                    graph.redownloads.clear()
                }
            }, enabled = songs.isNotEmpty())
            NotNowButton { scope.launch { graph.redownloads.clear() } }
        }
    }
}

/** Download pill: accent colour, 48 dp min height. */
@Composable
private fun DownloadPill(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .pressScale(press)
            .background(if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.2f))
            .clickable(interactionSource = press, indication = null, enabled = enabled, onClickLabel = label, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
    }
}

/** Not now button: 48 dp min height. */
@Composable
private fun NotNowButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Not now", style = MaterialTheme.typography.labelLarge, color = scheme.onBackground)
    }
}
