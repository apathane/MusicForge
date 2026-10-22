package dev.musicforge.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import dev.musicforge.AppGraph
import dev.musicforge.data.db.Song
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.CollectionKind
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.IconAction
import dev.musicforge.ui.components.SongRow
import dev.musicforge.ui.components.askToRunInBackground
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A song as a collection page shows it. */
data class Track(val item: MediaItem, val subtitle: String)

data class CollectionUi(
    val title: String,
    val subtitle: String,
    /** One cover, or up to four for a mosaic. */
    val artwork: List<String>,
    val tracks: List<Track>,
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
)

class CollectionViewModel(private val graph: AppGraph, private val kind: CollectionKind, private val id: String) : ViewModel() {
    private val _ui = MutableStateFlow(CollectionUi(title = "", subtitle = "", artwork = emptyList(), tracks = emptyList(), loading = true))
    val ui = _ui.asStateFlow()
    private val dao = graph.database.dao()
    private var continuation: String? = null

    init {
        when (kind) {
            CollectionKind.LOCAL_PLAYLIST -> local(
                combine(dao.playlist(id), dao.playlistSongs(id)) { p, songs -> (p?.name ?: "Playlist") to songs },
            )
            CollectionKind.LIKED -> local(dao.likedSongs().map { "Liked songs" to it })
            CollectionKind.SONGS -> local(dao.librarySongs().map { "Songs" to it })
            CollectionKind.DOWNLOADED -> local(dao.downloadedSongs().map { "Downloaded" to it })
            CollectionKind.HISTORY -> local(dao.historySongs().map { "Recently played" to it })
            CollectionKind.ALBUM -> album()
            CollectionKind.REMOTE_PLAYLIST -> remotePlaylist()
        }
    }

    private fun local(source: Flow<Pair<String, List<Song>>>) = viewModelScope.launch {
        source.collect { (title, songs) ->
            _ui.value = CollectionUi(
                title = title,
                subtitle = count(songs.size) + duration(songs.sumOf { it.song.duration.coerceAtLeast(0) }),
                artwork = songs.mapNotNull { it.song.thumbnailUrl }.distinct().take(4),
                tracks = songs.map { Track(it.toMediaItem(), it.artistLine) },
            )
        }
    }

    private fun album() = viewModelScope.launch {
        YouTube.album(id).onSuccess { page ->
            _ui.value = CollectionUi(
                title = page.album.title,
                subtitle = listOfNotNull(page.album.artists?.joinToString { it.name }, page.album.year?.toString(), count(page.songs.size))
                    .joinToString(" · "),
                artwork = listOf(page.album.thumbnail),
                tracks = page.songs.map { it.track() },
            )
        }.onFailure { e ->
            // Offline: show what we know locally about this album.
            val songs = dao.albumSongsLocal(id).first()
            val entity = dao.albumEntity(id)
            _ui.value = CollectionUi(
                title = entity?.title ?: "Album",
                subtitle = count(songs.size),
                artwork = listOfNotNull(entity?.thumbnailUrl),
                tracks = songs.map { Track(it.toMediaItem(), it.artistLine) },
                error = if (songs.isEmpty()) e.message ?: "Can't load this album" else null,
            )
        }
    }

    private fun remotePlaylist() = viewModelScope.launch {
        YouTube.playlist(id.removePrefix("VL")).onSuccess { page ->
            continuation = page.songsContinuation ?: page.continuation
            _ui.value = CollectionUi(
                title = page.playlist.title,
                subtitle = listOfNotNull(page.playlist.author?.name, page.playlist.songCountText).joinToString(" · "),
                artwork = listOfNotNull(page.playlist.thumbnail),
                tracks = page.songs.map { it.track() },
                hasMore = continuation != null,
            )
        }.onFailure { e ->
            _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Can't load this playlist")
        }
    }

    private var paging = false

    fun loadMore() {
        val token = continuation ?: return
        if (paging) return
        paging = true
        viewModelScope.launch {
            YouTube.playlistContinuation(token).onSuccess { page ->
                continuation = page.continuation
                val known = _ui.value.tracks.mapTo(HashSet()) { it.item.mediaId }
                _ui.value = _ui.value.copy(
                    tracks = _ui.value.tracks + page.songs.filter { it.id !in known }.map { it.track() },
                    hasMore = continuation != null,
                )
            }.onFailure { continuation = null; _ui.value = _ui.value.copy(hasMore = false) }
            paging = false
        }
    }

    private fun SongItem.track() = Track(toMediaItem(), artists.joinToString { it.name })

    private fun count(n: Int) = if (n == 1) "1 song" else "$n songs"

    private fun duration(totalSec: Int): String {
        if (totalSec <= 0) return ""
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return if (h > 0) " · $h h $m min" else " · $m min"
    }
}

@OptIn(UnstableApi::class)
@Composable
fun CollectionScreen(kind: CollectionKind, id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val vm = viewModel(key = "${kind.name}:$id") { CollectionViewModel(graph, kind, id) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 >= list.layoutInfo.totalItemsCount - 8 } }
    LaunchedEffect(nearEnd, ui.hasMore) { if (nearEnd && ui.hasMore) vm.loadMore() }

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = LocalBottomInset.current) {
        item {
            Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp)) {
                IconAction(ForgeIcons.Back, "Back", onBack)
            }
        }
        item { Header(ui, onPlay = { shuffle -> play(graph, ui, shuffle) }, onDownloadAll = {
            askToRunInBackground(context)
            graph.downloads.download(ui.tracks.map { it.item })
        }) }
        when {
            ui.loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground, strokeWidth = 2.dp)
                }
            }
            ui.error != null -> item { EmptyState("Couldn't load", ui.error!!) }
            ui.tracks.isEmpty() -> item { EmptyState("Nothing here yet", "Songs you add will show up here.") }
        }
        itemsIndexed(ui.tracks, key = { i, t -> "$i:${t.item.mediaId}" }) { index, track ->
            SongRow(
                track.item,
                track.subtitle,
                number = if (kind == CollectionKind.ALBUM) index + 1 else null,
                onPlay = { graph.player.play(ui.tracks.map { it.item }, index) },
            )
        }
    }
}

private fun play(graph: AppGraph, ui: CollectionUi, shuffle: Boolean) {
    val items = ui.tracks.map { it.item }
    if (items.isEmpty()) return
    graph.player.play(if (shuffle) items.shuffled() else items, 0)
}

@Composable
private fun Header(ui: CollectionUi, onPlay: (shuffle: Boolean) -> Unit, onDownloadAll: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(ui.artwork, 228.dp)
        Spacer(Modifier.height(18.dp))
        Text(ui.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (ui.subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(ui.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderButton("Play", ForgeIcons.Play, primary = true) { onPlay(false) }
            HeaderButton("Shuffle", ForgeIcons.Shuffle, primary = false) { onPlay(true) }
            IconAction(ForgeIcons.Download, "Download all", onDownloadAll)
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** One cover, or a 2×2 mosaic of the first four covers for playlists without their own. */
@Composable
fun Cover(urls: List<String>, size: Dp) {
    val shape = RoundedCornerShape(14.dp)
    if (urls.size < 4) {
        Artwork(urls.firstOrNull(), size, corner = 14.dp)
        return
    }
    Column(Modifier.size(size).clip(shape)) {
        for (row in 0..1) {
            Row {
                for (col in 0..1) Artwork(urls[row * 2 + col], size / 2, corner = 0.dp)
            }
        }
    }
}

@Composable
private fun HeaderButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
