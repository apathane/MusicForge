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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
fun TrackActionsSheet(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val download by remember(item.mediaId) { graph.downloads.state(item.mediaId) }.collectAsStateWithLifecycle(null)
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
            Spacer(Modifier.height(8.dp))
            Action(ForgeIcons.Next, "Play next") { graph.player.playNext(item); onDismiss() }
            when (download?.state) {
                Download.STATE_COMPLETED -> Action(ForgeIcons.Download, "Remove download") {
                    graph.downloads.remove(item.mediaId); onDismiss()
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
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
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
