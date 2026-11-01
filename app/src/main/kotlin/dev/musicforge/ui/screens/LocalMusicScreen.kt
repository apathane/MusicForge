package dev.musicforge.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.LocalSort
import dev.musicforge.ui.components.BackRow
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.PillButton
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SongRow
import dev.musicforge.ui.designsystem.AnchoredMenu
import dev.musicforge.ui.designsystem.AnchoredMenuItem
import dev.musicforge.ui.designsystem.LoadingBar
import dev.musicforge.ui.designsystem.pressScale

@Composable
fun LocalMusicScreen(sort: LocalSort, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val graph = context.graph
    val vm = viewModel<LocalMusicViewModel>(key = "local:$sort") { LocalMusicViewModel(graph, sort) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle(false)
    val lastResult by vm.lastResult.collectAsStateWithLifecycle(null)
    val list = rememberLazyListState()
    dev.musicforge.ui.ScrollToTopOnReselect(list)

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // A permission revoked in system settings while we're in the background shows the explain
    // screen again on resume. We never rescan or delete on our own when permission is missing.
    LifecycleResumeEffect(Unit) {
        vm.onResume(granted)
        // The effect must hand back a dispose result even when there's nothing to dispose —
        // like a scanner that reports zero songs but still did its job.
        onPauseOrDispose { }
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        activity?.let { vm.onPermissionResult(granted, it) }
    }

    fun openSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

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
                if (granted) {
                    ScanButton(scanning = scanning, onScan = { vm.scan() })
                }
            }
        }
        if (scanning) {
            item {
                LoadingBar(modifier = Modifier.fillMaxWidth().height(3.dp))
            }
        }
        val result = lastResult
        if (result != null) {
            item {
                Text(
                    text = scanLine(result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, top = 4.dp, bottom = 8.dp),
                )
            }
        }
        item {
            dev.musicforge.ui.screens.SearchField(ui.query, { vm.onQueryChanged(it) }, onSubmit = {}, placeholder = "Find local songs")
            Spacer(Modifier.height(8.dp))
        }
        when {
            !granted -> item {
                // (a) No audio permission: explain and ask. Permanent denial opens app settings.
                EmptyState(
                    title = "Allow access to your music",
                    body = "MusicForge needs to read audio files on this phone to show your local library.",
                    action = {
                        PillButton(
                            text = "Allow access",
                            onClick = {
                                if (ui.permissionDeniedPermanently) openSettings()
                                else requestPermission.launch(permission)
                            },
                        )
                    },
                )
            }
            ui.songs.isEmpty() -> item {
                // (b) Permission granted, nothing indexed yet: invite a scan.
                EmptyState(
                    title = if (ui.query.isBlank()) "No music on this phone yet" else "No local songs match",
                    body = if (ui.query.isBlank())
                        "Scan to find the songs already on it."
                    else "Try a different search.",
                    action = if (ui.query.isBlank()) {
                        {
                            PillButton(text = "Scan now", onClick = { vm.scan() })
                        }
                    } else null,
                )
            }
            else -> itemsIndexed(ui.songs, key = { i, s -> s.id }) { index, song ->
                // (c) The list as today.
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

/** Small header action: "Scan" next to Sort, disabled while a scan is already running. */
@Composable
private fun ScanButton(scanning: Boolean, onScan: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(press)
            .clickable(
                interactionSource = press,
                indication = null,
                onClickLabel = "Scan",
                enabled = !scanning,
                onClick = onScan,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(percent = 50)),
    ) {
        Text(
            text = "Scan",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private fun scanLine(result: dev.musicforge.data.local.LocalAudioScanner.ScanResult): String {
    val line = "Found ${result.totalFound} songs · +${result.newSongs} new · ${result.removed} removed"
    return if (result.listingComplete) line else "$line · Couldn't check for removed files (no access)"
}

private fun sortLabel(sort: LocalSort): String = when (sort) {
    LocalSort.TITLE -> "Sort by title"
    LocalSort.DATE_ADDED -> "Sort by date added"
    LocalSort.ALBUM -> "Sort by album"
    LocalSort.ARTIST -> "Sort by artist"
}
