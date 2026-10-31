package dev.musicforge.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.completed
import dev.musicforge.AppGraph
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.designsystem.ForgeSheet
import dev.musicforge.ui.designsystem.LoadingBar
import dev.musicforge.ui.designsystem.pressScale
import dev.musicforge.ui.Routes
import dev.musicforge.ui.CollectionKind
import kotlinx.coroutines.flow.first

/**
 * A collection that the long-press menu can act on: an album, an artist, or a playlist,
 * either from your library or fetched from YouTube Music.
 */
sealed interface CollectionTarget {
    val id: String
    val title: String
    val thumbnail: String?

    data class Album(
        override val id: String,
        override val title: String,
        override val thumbnail: String?,
        val artists: List<String>,
        val year: Int? = null,
        val artistId: String? = null,
    ) : CollectionTarget

    data class Artist(
        override val id: String,
        override val title: String,
        override val thumbnail: String?,
        val channelId: String? = null,
    ) : CollectionTarget

    data class Playlist(
        override val id: String,
        override val title: String,
        override val thumbnail: String?,
        val owner: String? = null,
        val songCount: Int = 0,
        val isLocal: Boolean = false,
    ) : CollectionTarget
}

/**
 * The long-press menu for an album, artist or playlist. Same visual style as [TrackActionsSheet],
 * same [ForgeSheet] primitive. Fetches the collection's tracks lazily — a [LoadingBar]
 * runs while YouTube responds, so taps don't race with navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionActionsSheet(
    target: CollectionTarget,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNavController.current
    val dao = graph.database.dao()

    // Fetch the collection's tracks. Null while loading, empty list if the fetch failed.
    val tracks by produceState<List<MediaItem>?>(null, target) {
        value = graph.fetchCollectionTracks(target)
    }
    val loading = tracks == null

    // Artist follow state — observe from the DB so it stays fresh if another surface toggles it.
    val followed by produceState(target is CollectionTarget.Artist) {
        if (target is CollectionTarget.Artist) {
            value = dao.isArtistFollowed(target.id).first() == true
        }
    }

    // Album bookmark state for library albums.
    val albumSaved by produceState(false) {
        if (target is CollectionTarget.Album) {
            value = dao.albumEntity(target.id)?.bookmarkedAt != null
        }
    }

    var pickedPlaylist by remember { mutableStateOf(false) }

    if (pickedPlaylist) {
        val all = tracks.orEmpty()
        CollectionPlaylistPicker(all) { pickedPlaylist = false }
        return
    }

    ForgeSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 12.dp)) {
            LoadingBar(visible = loading, modifier = Modifier.padding(horizontal = 20.dp))
            // Header: artwork, title, subtitle
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(target.thumbnail, 52.dp, corner = if (target is CollectionTarget.Artist) 26.dp else 8.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(target.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val subtitle = collectionSubtitle(target)
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (loading) {
                // The LoadingBar at the top carries the wait; track-dependent rows hide until ready.
                return@Column
            }

            val songs = tracks.orEmpty()
            val canAct = songs.isNotEmpty()

            // Playback actions (need tracks)
            if (canAct) {
                CollectionAction(ForgeIcons.Play, "Play") { graph.player.play(songs); onDismiss() }
                CollectionAction(ForgeIcons.Shuffle, "Shuffle") { graph.player.play(songs.shuffled()); onDismiss() }
                CollectionAction(ForgeIcons.Next, "Play next") { graph.player.playNext(songs); onDismiss() }
                CollectionAction(ForgeIcons.Queue, "Add to queue") { graph.player.addToQueue(songs); onDismiss() }
                CollectionAction(ForgeIcons.Radio, "Start radio") { songs.firstOrNull()?.let { graph.player.startRadio(it) }; onDismiss() }
            }

            // Download all (needs tracks)
            if (canAct) {
                CollectionAction(ForgeIcons.Download, "Download all") {
                    askToRunInBackground(context)
                    graph.downloads.download(songs)
                    onDismiss()
                }
            }

            // Library actions
            when (target) {
                is CollectionTarget.Album -> {
                    val icon = if (albumSaved) ForgeIcons.Heart else ForgeIcons.HeartOutline
                    val label = if (albumSaved) "Remove from library" else "Save to library"
                    CollectionAction(icon, label) {
                        graph.library.setAlbumBookmark(target.id, !albumSaved)
                    }
                }
                is CollectionTarget.Artist -> {
                    val icon = if (followed) ForgeIcons.Heart else ForgeIcons.HeartOutline
                    val label = if (followed) "Following" else "Follow"
                    CollectionAction(icon, label) {
                        graph.library.setArtistBookmark(target.id, !followed)
                    }
                }
                is CollectionTarget.Playlist -> { /* no library action for playlists in this menu */ }
            }

            // Add to playlist (needs tracks)
            if (canAct) {
                CollectionAction(ForgeIcons.PlaylistAdd, "Add to playlist") { pickedPlaylist = true }
            }

            // Share
            CollectionAction(ForgeIcons.Share, "Share") { shareCollection(context, target); onDismiss() }

            // Navigation: go to the collection's own page or related artist page
            when (target) {
                is CollectionTarget.Album -> {
                    CollectionAction(ForgeIcons.Album, "Go to album") {
                        nav.navigate(Routes.collection(CollectionKind.ALBUM, target.id))
                        onDismiss()
                    }
                    target.artistId?.let { artistId ->
                        CollectionAction(ForgeIcons.Person, "Go to artist") {
                            nav.navigate(Routes.artist(artistId))
                            onDismiss()
                        }
                    }
                }
                is CollectionTarget.Artist -> {
                    CollectionAction(ForgeIcons.Person, "Go to artist") {
                        nav.navigate(Routes.artist(target.id))
                        onDismiss()
                    }
                }
                is CollectionTarget.Playlist -> {
                    val kind = if (target.isLocal) CollectionKind.LOCAL_PLAYLIST else CollectionKind.REMOTE_PLAYLIST
                    CollectionAction(ForgeIcons.PlaylistAdd, "Go to playlist") {
                        nav.navigate(Routes.collection(kind, target.id))
                        onDismiss()
                    }
                }
            }

            // Local playlist: rename and delete
            if (target is CollectionTarget.Playlist && target.isLocal) {
                var showRename by remember { mutableStateOf(false) }
                var confirmDelete by remember { mutableStateOf(false) }
                CollectionAction(ForgeIcons.Edit, "Rename") { showRename = true }
                CollectionAction(ForgeIcons.Delete, "Delete") { confirmDelete = true }
                if (showRename) {
                    RenamePlaylistDialog(
                        name = target.title,
                        onRename = { name ->
                            graph.library.renamePlaylist(target.id, name)
                            showRename = false
                            onDismiss()
                        },
                        onDismiss = { showRename = false },
                    )
                }
                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text("Delete \"${target.title}\"?") },
                        text = { Text("The songs stay in your library.") },
                        confirmButton = {
                            TextButton({ graph.library.deletePlaylist(target.id); onDismiss() }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
                    )
                }
            }
        }
    }
}

/** Subtitle line for a collection in the menu header. */
@Composable
private fun collectionSubtitle(target: CollectionTarget): String = when (target) {
    is CollectionTarget.Album ->
        listOfNotNull(target.artists.takeIf { it.isNotEmpty() }?.joinToString(", "), target.year?.toString())
            .joinToString(" · ")
    is CollectionTarget.Artist -> ""
    is CollectionTarget.Playlist -> listOfNotNull(target.owner, target.songCount.takeIf { it > 0 }?.let { "$it songs" })
        .joinToString(" · ")
}

/** Fetch all tracks for a collection, or null on failure / while loading. */
private suspend fun AppGraph.fetchCollectionTracks(target: CollectionTarget): List<MediaItem>? {
    val dao = database.dao()
    return try {
        when (target) {
            is CollectionTarget.Album -> {
                YouTube.album(target.id).getOrThrow().songs.map { it.toMediaItem() }
            }
            is CollectionTarget.Artist -> {
                YouTube.artist(target.id).getOrThrow()
                    .sections.flatMap { s -> s.items.filterIsInstance<com.metrolist.innertube.models.SongItem>() }
                    .map { it.toMediaItem() }
                    .distinctBy { it.mediaId }
            }
            is CollectionTarget.Playlist -> {
                if (target.isLocal) {
                    dao.playlistSongs(target.id).first().map { it.toMediaItem() }
                } else {
                    val page = YouTube.playlist(target.id.removePrefix("VL")).completed().getOrThrow()
                    page.songs.map { it.toMediaItem() }
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}

private fun shareCollection(context: Context, target: CollectionTarget) {
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, target.shareText())
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun CollectionTarget.shareText(): String = when (this) {
    is CollectionTarget.Album -> "https://music.youtube.com/playlist?list=${id}"
    is CollectionTarget.Artist -> "https://music.youtube.com/channel/${channelId ?: id}"
    is CollectionTarget.Playlist -> "https://music.youtube.com/playlist?list=${id}"
}

@Composable
private fun CollectionAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun CollectionPlaylistPicker(items: List<MediaItem>, onDone: () -> Unit) {
    val graph = LocalContext.current.graph
    val playlists by remember { graph.database.dao().playlists() }.collectAsStateWithLifecycle(emptyList())
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        var name by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Playlist name") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            PillButton("Create", onClick = { graph.library.createPlaylist(name, items.firstOrNull()); onDone() })
        }
        return
    }
    TextButton({ naming = true }) { Text("+ New playlist") }
    LazyColumn(
        Modifier.fillMaxWidth().height(320.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        items(playlists.filter { it.playlist.isEditable }, key = { it.playlist.id }) { p ->
            Row(
                Modifier.fillMaxWidth().animateItem(tween(220)).clickable {
                    items.forEach { graph.library.addToPlaylist(it, p.playlist.id) }
                    onDone()
                }.padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(p.playlist.thumbnailUrl, 40.dp, corner = 6.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.songCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RenamePlaylistDialog(name: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlist") },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = { TextButton({ onRename(text.trim().ifEmpty { "New playlist" }) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
