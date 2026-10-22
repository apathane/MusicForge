package dev.musicforge.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.graph
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.TrackRow

@Composable
fun DownloadsScreen() {
    val dao = LocalContext.current.graph.database.dao()
    val songs by remember { dao.downloadedSongs() }.collectAsStateWithLifecycle(emptyList())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { ScreenHeader("Downloads") }
        if (songs.isEmpty()) {
            item { EmptyState("Nothing downloaded yet", "Downloaded songs play offline and survive restarts.") }
        }
        items(songs, key = { it.id }) { s ->
            TrackRow(s.song.title, s.artistLine, s.song.thumbnailUrl, onClick = {})
        }
    }
}
