package dev.musicforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.musicforge.AppGraph
import dev.musicforge.data.db.LibraryCounts
import dev.musicforge.data.db.PlaylistSummary
import dev.musicforge.graph
import dev.musicforge.ui.CollectionKind
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.Routes
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.IconAction
import dev.musicforge.ui.components.PillButton
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import dev.musicforge.playback.toMediaItem
import kotlinx.coroutines.launch
import com.metrolist.innertube.utils.completed
import androidx.compose.foundation.lazy.items

class LibraryViewModel(graph: AppGraph) : ViewModel() {
    private val dao = graph.database.dao()
    val counts: StateFlow<LibraryCounts?> =
        dao.counts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val playlists: StateFlow<List<PlaylistSummary>> =
        dao.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Filter as you type, local only: songs you have, and playlists by name. */
    val query = kotlinx.coroutines.flow.MutableStateFlow("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val found: StateFlow<Pair<List<dev.musicforge.data.db.Song>, List<PlaylistSummary>>?> = query
        .debounce(150)
        .mapLatest { q ->
            val t = q.trim()
            if (t.isEmpty()) null
            else dao.searchLibrary(t, 50) to playlists.value.filter { it.playlist.name.contains(t, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Account playlists from YouTube Music; empty when signed out or offline. */
    val accountPlaylists = kotlinx.coroutines.flow.MutableStateFlow<List<com.metrolist.innertube.models.PlaylistItem>>(emptyList())
    val signedIn = graph.settings.data.map { it[dev.musicforge.data.prefs.Keys.InnerTubeCookie] != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            signedIn.collect { yes ->
                accountPlaylists.value = if (!yes) emptyList() else
                    com.metrolist.innertube.YouTube.library("FEmusic_liked_playlists").completed().getOrNull()?.items
                        ?.filterIsInstance<com.metrolist.innertube.models.PlaylistItem>()
                        ?.filterNot { it.id == "LM" || it.id == "SE" }
                        .orEmpty()
            }
        }
    }
}

@Composable
fun LibraryScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel { LibraryViewModel(context.graph) }
    val counts by vm.counts.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val account by vm.accountPlaylists.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val empty = counts?.let { it.songs + it.liked + it.playlists + it.albums + it.artists + it.downloaded + it.events == 0 } ?: false

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item {
            ScreenHeader("Library") { IconAction(ForgeIcons.Settings, "Settings", onOpenSettings) }
        }
        if (!empty) {
            item(key = "find") {
                SearchField(query, { vm.query.value = it }, onSubmit = {}, placeholder = "Find in your library")
                Spacer(Modifier.height(8.dp))
            }
        }
        found?.let { (songs, lists) ->
            if (songs.isEmpty() && lists.isEmpty()) {
                item(key = "find-none") { EmptyState("Nothing in your library matches", "Search looks at titles and artists of songs you have, and playlist names.") }
            }
            items(lists, key = { "f:p:" + it.playlist.id }) { p ->
                ShelfRow(ForgeIcons.Library, p.playlist.name, p.songCount) { nav.navigate(Routes.collection(CollectionKind.LOCAL_PLAYLIST, p.playlist.id)) }
            }
            items(songs, key = { "f:s:" + it.id }) { s ->
                val item = androidx.compose.runtime.remember(s.id) { s.toMediaItem() }
                dev.musicforge.ui.components.SongRow(item, s.artistLine) { context.graph.player.play(songs.map { it.toMediaItem() }, songs.indexOf(s)) }
            }
            return@LazyColumn
        }
        if (empty) {
            item {
                EmptyState(
                    title = "Your library is empty",
                    body = "Bring everything over from Metrolist or ConvX: songs, likes, playlists, history and your login.",
                ) { PillButton("Restore a backup", onOpenSettings) }
            }
            return@LazyColumn
        }
        counts?.let { c ->
            item { ShelfRow(ForgeIcons.Heart, "Liked songs", c.liked) { nav.navigate(Routes.collection(CollectionKind.LIKED)) } }
            item { ShelfRow(ForgeIcons.Library, "Songs", c.songs) { nav.navigate(Routes.collection(CollectionKind.SONGS)) } }
            item { ShelfRow(ForgeIcons.Download, "Downloaded", c.downloaded) { nav.navigate(Routes.collection(CollectionKind.DOWNLOADED)) } }
            item { ShelfRow(ForgeIcons.Library, "Albums", c.albums) { nav.navigate(Routes.ALBUMS) } }
            item { ShelfRow(ForgeIcons.Library, "Artists", c.artists) { nav.navigate(Routes.ARTISTS) } }
            item { ShelfRow(ForgeIcons.Previous, "Recently played", -1) { nav.navigate(Routes.collection(CollectionKind.HISTORY)) } }
            item { ShelfRow(ForgeIcons.Heart, "Your listening", -1) { nav.navigate(Routes.STATS) } }
        }
        if (signedIn) {
            item { SectionTitle("From YouTube Music") }
            item { ShelfRow(ForgeIcons.Heart, "Liked music", -1) { nav.navigate(Routes.collection(CollectionKind.REMOTE_PLAYLIST, "LM")) } }
            items(account, key = { "yt:" + it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { nav.navigate(Routes.collection(CollectionKind.REMOTE_PLAYLIST, p.id)) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(p.thumbnail, 56.dp, corner = 8.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        p.songCountText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            item { SectionTitle("Playlists") }
            items(playlists, key = { it.playlist.id }) { p ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { nav.navigate(Routes.collection(CollectionKind.LOCAL_PLAYLIST, p.playlist.id)) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(p.playlist.thumbnailUrl, 56.dp, corner = 8.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            "${p.songCount} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfRow(icon: ImageVector, title: String, count: Int, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (count >= 0) Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(ForgeIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
    }
}
