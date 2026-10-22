package dev.musicforge.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SectionTitle
import dev.musicforge.ui.components.SongRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { ScreenHeader("Downloads") }
        if (active.isEmpty() && done.isEmpty()) {
            item { EmptyState("Nothing downloaded yet", "Long-press any song and choose Download. Downloads play offline and pick up where they left off if the phone kills the app.") }
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
