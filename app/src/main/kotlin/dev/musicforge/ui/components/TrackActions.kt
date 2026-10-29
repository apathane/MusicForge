package dev.musicforge.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import dev.musicforge.ui.designsystem.Motion
import dev.musicforge.ui.designsystem.pressScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import dev.musicforge.data.db.FormatEntity
import dev.musicforge.data.db.SongStats
import dev.musicforge.graph
import dev.musicforge.ui.CollectionKind
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.designsystem.pressScale
import dev.musicforge.ui.Routes
import dev.musicforge.ui.player.clock
import dev.musicforge.ui.player.codecPill
import dev.musicforge.playback.albumId
import dev.musicforge.playback.credits
import dev.musicforge.playback.durationSec

@OptIn(UnstableApi::class)
@Composable
fun DownloadBadge(id: String) {
    val downloads = LocalContext.current.graph.downloads
    val state by remember(id) { downloads.state(id) }.collectAsStateWithLifecycle(null)
    val d = state ?: return
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        when (d.state) {
            Download.STATE_COMPLETED -> Icon(ForgeIcons.Download, "Downloaded", tint = tint, modifier = Modifier.size(18.dp))
            Download.STATE_DOWNLOADING, Download.STATE_QUEUED, Download.STATE_RESTARTING -> {
                val p = d.percentDownloaded
                if (p > 0f) {
                    CircularProgressIndicator(progress = { p / 100f }, modifier = Modifier.size(16.dp), color = tint, strokeWidth = 2.dp)
                } else {
                    CircularProgressIndicator(Modifier.size(16.dp), color = tint, strokeWidth = 2.dp)
                }
            }
            Download.STATE_FAILED -> Text("!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            else -> Unit
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun TrackActionsSheet(
    item: MediaItem,
    extra: List<Pair<String, () -> Unit>> = emptyList(),
    /** Why a suggestion is on screen ("Why this?"), shown under the title. */
    why: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNavController.current
    val download by remember(item.mediaId) { graph.downloads.state(item.mediaId) }.collectAsStateWithLifecycle(null)
    val liked by remember(item.mediaId) { graph.database.dao().isLiked(item.mediaId) }.collectAsStateWithLifecycle(null)
    // "Remove from history" only appears when there's history to remove (plays > 0).
    val stats by produceState<SongStats?>(null, item.mediaId) {
        value = graph.database.dao().statsFor(item.mediaId)
    }
    val pickedPlaylist = remember { mutableStateOf(false) }
    val showingInfo = remember { mutableStateOf(false) }
    val meta = item.mediaMetadata
    val downloaded = download?.state == Download.STATE_COMPLETED

    if (showingInfo.value) {
        SongInfoDialog(item, onDismiss = { showingInfo.value = false })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(meta.artworkUri?.toString(), 52.dp, corner = 8.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(meta.title?.toString().orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(meta.artist?.toString().orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            why?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (pickedPlaylist.value) {
                PlaylistPicker(item, onDone = onDismiss)
                return@Column
            }
            // Order follows the music-app convention (Metrolist/BitChord): queue, station, save,
            // then the download lifecycle, then navigation and the song's own details.
            Action(ForgeIcons.Next, "Play next") { graph.player.playNext(item); onDismiss() }
            Action(ForgeIcons.Queue, "Add to queue") { graph.player.addToQueue(item); onDismiss() }
            Action(ForgeIcons.Radio, "Start radio") { graph.player.startRadio(item); onDismiss() }
            AnimatedContent(
                targetState = liked == true,
                transitionSpec = {
                    if (targetState) {
                        // Heart fills in with a pop (EaseBackOut). The colour still fades faster than
                        // the shape — breaking up is harder than making up.
                        (fadeIn(tween(Motion.Fade)) + androidx.compose.animation.scaleIn(
                            animationSpec = tween(Motion.Arrive, easing = Motion.EaseBackOut),
                            initialScale = 0.9f,
                        )) togetherWith fadeOut(tween(Motion.Instant))
                    } else {
                        fadeIn(tween(140)) togetherWith fadeOut(tween(120))
                    }
                },
                label = "like",
            ) { l ->
                if (l) {
                    Action(ForgeIcons.Heart, "Unlike") { graph.library.setLiked(item, false); onDismiss() }
                } else {
                    Action(ForgeIcons.HeartOutline, "Like") { graph.library.setLiked(item, true); onDismiss() }
                }
            }
            Action(ForgeIcons.PlaylistAdd, "Add to playlist") { pickedPlaylist.value = true }
            extra.forEach { (label, run) -> Action(ForgeIcons.More, label) { run(); onDismiss() } }
            when (download?.state) {
                Download.STATE_COMPLETED -> {
                    Action(ForgeIcons.Download, "Remove download") { graph.downloads.remove(item.mediaId); onDismiss() }
                }
                Download.STATE_DOWNLOADING, Download.STATE_QUEUED, Download.STATE_RESTARTING -> Action(ForgeIcons.Download, "Cancel download") {
                    graph.downloads.remove(item.mediaId); onDismiss()
                }
                else -> Action(ForgeIcons.Download, "Download") {
                    askToRunInBackground(context)
                    graph.downloads.download(listOf(item)); onDismiss()
                }
            }
            // Share is always available for a YouTube track — the id is the link.
            Action(ForgeIcons.Share, "Share") { shareTrack(context, item); onDismiss() }
            // Navigate before dismissing so the sheet doesn't swallow the pop.
            meta.albumId?.let { album ->
                Action(ForgeIcons.Album, "Go to album") { nav.navigate(Routes.collection(CollectionKind.ALBUM, album)); onDismiss() }
            }
            meta.credits.firstOrNull { it.id != null }?.let { artist ->
                Action(ForgeIcons.Person, "Go to artist") { nav.navigate(Routes.artist(artist.id!!)); onDismiss() }
            }
            Action(ForgeIcons.Info, "Song info") { showingInfo.value = true }
            if (stats?.plays ?: 0 > 0) {
                Action(ForgeIcons.History, "Remove from history") { graph.library.removeFromHistory(item); onDismiss() }
            }
            // Only offer to clear the stream cache — a finished download's bytes live elsewhere.
            if (!downloaded) {
                Action(ForgeIcons.MusicNote, "Remove from cache") { graph.mediaEngine.removeFromCache(item.mediaId); onDismiss() }
            }
        }
    }
}

/**
 * "Title – Artist https://music.youtube.com/watch?v=<id>" — kept pure so a unit test pins the
 * exact format Metrolist/ConvX/YouTube expect. Two spaces, one en dash: looks tidy pasted into
 * a tweet and parses as a title, artist, then a link.
 */
fun shareText(title: String?, artist: String?, videoId: String): String {
    val t = title?.takeIf { it.isNotBlank() }.orEmpty()
    val a = artist?.takeIf { it.isNotBlank() }.orEmpty()
    return "$t – $a https://music.youtube.com/watch?v=$videoId"
}

private fun shareTrack(context: Context, item: MediaItem) {
    val meta = item.mediaMetadata
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText(meta.title?.toString(), meta.artist?.toString(), item.mediaId))
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/**
 * A small in-sheet fact card for a track — title, artists, album, duration, format, loudness,
 * play count and total time, and the video id with a copy action. A dialog, not a bottom sheet:
 * it's the size of the details, and the menu already is the sheet.
 */
@Composable
private fun SongInfoDialog(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val meta = item.mediaMetadata
    val format by produceState<FormatEntity?>(null, item.mediaId) {
        value = graph.database.dao().format(item.mediaId)
    }
    val stats by produceState<SongStats?>(null, item.mediaId) {
        value = graph.database.dao().statsFor(item.mediaId)
    }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Song info") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                infoRow("Title", meta.title?.toString())
                infoRow("Artist", meta.credits.joinToString(separator = ", ") { it.name })
                infoRow("Album", meta.albumTitle?.toString())
                meta.durationSec?.let { sec -> infoRow("Duration", clock(sec * 1000L)) }
                infoRow("Format", format?.let { codecPill(it.mimeType, it.codecs, it.bitrate) })
                infoRow("Sample rate", format?.sampleRate?.let { "$it Hz" })
                format?.loudnessDb?.let { infoRow("Loudness", "$it LUFS") }
                infoRow("Play count", (stats?.plays ?: 0).toString())
                infoRow("Total time", clock(stats?.timeMs ?: 0L))
                // The one row that wants a button, not just text: copy the video id for a link.
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.mediaId, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(item.mediaId))
                    }) { Icon(ForgeIcons.MusicNote, contentDescription = null) }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}

@Composable
private fun infoRow(label: String, value: String?) {
    val v = value?.trim()
    if (v.isNullOrEmpty()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(2f))
    }
}

@Composable
private fun PlaylistPicker(item: MediaItem, onDone: () -> Unit) {
    val graph = LocalContext.current.graph
    val playlists by remember { graph.database.dao().playlists() }.collectAsStateWithLifecycle(emptyList())
    val naming = remember { mutableStateOf(false) }
    if (naming.value) {
        val name = remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it },
                singleLine = true,
                placeholder = { Text("Playlist name") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            PillButton("Create", onClick = { graph.library.createPlaylist(name.value, item); onDone() })
        }
        return
    }
    TextButton({ naming.value = true }) { Text("+ New playlist") }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier
            .fillMaxWidth()
            .height(320.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        items(playlists.filter { it.playlist.isEditable }, key = { it.playlist.id }) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .animateItem(tween(220))
                    .clickable { graph.library.addToPlaylist(item, p.playlist.id); onDone() }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
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
private fun PillButton(text: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = Modifier.pressScale(press),
        interactionSource = press,
    ) { Text(text) }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
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

/**
 * MagicOS (and other aggressive OEM skins) stop background work unless the app is exempt
 * from battery optimisation. Ask once, at the moment it matters: the first download.
 * Not a joke — the system really will kill the download thread otherwise.
 */
@SuppressLint("BatteryLife")
fun askToRunInBackground(context: Context) {
    val prefs = context.getSharedPreferences("forge.once", Context.MODE_PRIVATE)
    if (prefs.getBoolean("askedBattery", false)) return
    prefs.edit().putBoolean("askedBattery", true).apply()
    val pm = context.getSystemService<PowerManager>() ?: return
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
