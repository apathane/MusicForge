package dev.musicforge.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import dev.musicforge.ui.designsystem.pressScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import dev.musicforge.graph

/** Download state of one song, small enough to sit at the end of a row. */
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
    val download by remember(item.mediaId) { graph.downloads.state(item.mediaId) }.collectAsStateWithLifecycle(null)
    val liked by remember(item.mediaId) { graph.database.dao().isLiked(item.mediaId) }.collectAsStateWithLifecycle(null)
    var picking by remember { mutableStateOf(false) }
    val bitrate by androidx.compose.runtime.produceState<Int?>(null, item.mediaId) { value = graph.database.dao().format(item.mediaId)?.bitrate }
    val meta = item.mediaMetadata
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
            if (picking) {
                PlaylistPicker(item, onDone = onDismiss)
                return@Column
            }
            Action(ForgeIcons.Next, "Play next") { graph.player.playNext(item); onDismiss() }
            // Radio: a station seeded from this song (endless play takes it from there).
            Action(ForgeIcons.Radio, "Start radio") { graph.player.startRadio(item); onDismiss() }
            // A heart that's full needs a moment to decide it's hollow — 140 in, 120 out,
            // because breaking up is harder than making up (and the colour fades faster than the shape).
            AnimatedContent(
                targetState = liked == true,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(120)) },
                label = "like",
            ) { l ->
                if (l) {
                    Action(ForgeIcons.Heart, "Unlike") { graph.library.setLiked(item, false); onDismiss() }
                } else {
                    Action(ForgeIcons.HeartOutline, "Like") { graph.library.setLiked(item, true); onDismiss() }
                }
            }
            Action(ForgeIcons.PlaylistAdd, "Add to playlist") { picking = true }
            extra.forEach { (label, run) -> Action(ForgeIcons.More, label) { run(); onDismiss() } }
            when (download?.state) {
                Download.STATE_COMPLETED -> {
                    // Downloaded at one of YouTube's low-quality formats (on mobile data, say).
                    if (dev.musicforge.download.Upgrades.isLow(bitrate) && download?.request?.customCacheKey.let { it == null || it == item.mediaId }) {
                        Action(ForgeIcons.Download, "Re-download in high quality") { graph.downloads.upgrade(item.mediaId); onDismiss() }
                    }
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
        }
    }
}

@Composable
private fun PlaylistPicker(item: MediaItem, onDone: () -> Unit) {
    val graph = LocalContext.current.graph
    val playlists by remember { graph.database.dao().playlists() }.collectAsStateWithLifecycle(emptyList())
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    if (naming) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Playlist name") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            PillButton("Create", onClick = { graph.library.createPlaylist(name, item); onDone() })
        }
        return
    }
    Action(ForgeIcons.PlaylistAdd, "New playlist") { naming = true }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
        items(playlists.filter { it.playlist.isEditable }, key = { it.playlist.id }) { p ->
            Row(
                Modifier.fillMaxWidth().animateItem(tween(220)).clickable { graph.library.addToPlaylist(item, p.playlist.id); onDone() }
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
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().pressScale(press).clickable(interactionSource = press, indication = null, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
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
