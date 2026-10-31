package dev.musicforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.LocalSort
import dev.musicforge.ui.components.BackRow
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SongRow
import dev.musicforge.ui.designsystem.AnchoredMenu
import dev.musicforge.ui.designsystem.AnchoredMenuItem
import dev.musicforge.ui.designsystem.pressScale

@Composable
fun LocalMusicScreen(sort: LocalSort, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val vm = viewModel<LocalMusicViewModel>(key = "local:$sort") { LocalMusicViewModel(graph, sort) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    dev.musicforge.ui.ScrollToTopOnReselect(list)

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = LocalBottomInset.current) {
        item { BackRow(onBack) }
        item {
            ScreenHeader("Local music") {
                // Sort picker: a one-shot menu, labelled with the current order. "Sort" because
                // a shelf icon would crowd the header; the gesture is rare enough to hide behind tap.
                var expanded by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .pressScale(remember { MutableInteractionSource() })
                        .clickable(onClickLabel = "Sort") { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ForgeIcons.Sort, "Sort", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                    Text(
                        sortLabel(ui.sort),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(ForgeIcons.More, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(16.dp))
                }
                AnchoredMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    LocalSort.entries.forEach { s ->
                        AnchoredMenuItem(
                            text = sortLabel(s),
                            onClick = { vm.onSortChanged(s); expanded = false },
                        )
                    }
                }
            }
        }
        item {
            dev.musicforge.ui.screens.SearchField(ui.query, { vm.onQueryChanged(it) }, onSubmit = {}, placeholder = "Find local songs")
            Spacer(Modifier.height(8.dp))
        }
        when {
            ui.songs.isEmpty() -> item {
                EmptyState(
                    title = if (ui.query.isBlank()) "Nothing on this phone yet" else "No local songs match",
                    body = if (ui.query.isBlank())
                        "Open Settings › Library › Local music folder to scan music files stored on your device."
                    else "Try a different search.",
                )
            }
            else -> itemsIndexed(ui.songs, key = { i, s -> s.id }) { index, song ->
                val item = remember(song.id) { song.toMediaItem() }
                SongRow(
                    item,
                    song.artistLine,
                    number = index + 1,
                    onPlay = { graph.player.play(ui.songs.map { it.toMediaItem() }, index) },
                )
            }
        }
    }
}

private fun sortLabel(sort: LocalSort): String = when (sort) {
    LocalSort.TITLE -> "Sort by title"
    LocalSort.DATE_ADDED -> "Sort by date added"
    LocalSort.ALBUM -> "Sort by album"
    LocalSort.ARTIST -> "Sort by artist"
}
