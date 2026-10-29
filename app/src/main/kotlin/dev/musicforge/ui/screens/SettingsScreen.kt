package dev.musicforge.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import dev.musicforge.ui.designsystem.LoadingBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.musicforge.AppGraph
import dev.musicforge.BuildConfig
import dev.musicforge.data.backup.BackupImporter
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import dev.musicforge.ui.components.GlassTier
import dev.musicforge.ui.designsystem.pressScale
import dev.musicforge.update.ReleaseInfo
import dev.musicforge.update.UpdateKeys
import dev.musicforge.update.UpdateState
import dev.musicforge.ui.components.BackRow
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SectionTitle
import dev.musicforge.ui.theme.DarkMode
import dev.musicforge.ui.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Human-readable byte count, as Metrolist and ConvX do. */
private fun formatBytes(bytes: Long?): String = when {
    bytes == null -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}

class SettingsViewModel(private val graph: AppGraph) : ViewModel() {
    sealed interface RestoreState {
        data object Idle : RestoreState
        data object Working : RestoreState
        data class Confirm(val uri: Uri, val preview: BackupImporter.Preview) : RestoreState
        data class Done(val result: BackupImporter.Result) : RestoreState
        data class Failed(val message: String) : RestoreState
    }

    private val _restore = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restore = _restore.asStateFlow()
    val prefs = graph.settings.data

    fun inspect(uri: Uri) = viewModelScope.launch {
        _restore.value = RestoreState.Working
        _restore.value = runCatching { RestoreState.Confirm(uri, graph.backupImporter.preview(uri)) }
            .getOrElse { RestoreState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun confirm(uri: Uri, includeAccount: Boolean) = viewModelScope.launch {
        _restore.value = RestoreState.Working
        _restore.value = runCatching {
            graph.autoBackup.snapshotBeforeRestore()
            val result = graph.backupImporter.import(uri, includeAccount, graph.downloads.completedIds())
            val from = when (result.origin) {
                dev.musicforge.data.backup.BackupImporter.Origin.METROLIST -> "Metrolist"
                dev.musicforge.data.backup.BackupImporter.Origin.CONVX -> "ConvX"
                else -> "your backup"
            }
            graph.redownloads.offer(from, result.wereDownloaded)
            RestoreState.Done(result)
        }
            .getOrElse { RestoreState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun export(uri: Uri) = viewModelScope.launch {
        _restore.value = RestoreState.Working
        _restore.value = runCatching { graph.backupExporter.export(uri); RestoreState.Idle }
            .getOrElse { RestoreState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun syncNow() = graph.scope.launch { graph.accountSync.sync() }

    fun signOut() = viewModelScope.launch {
        graph.accountSync.forgetSnapshots()
        graph.settings.edit { p ->
            listOf(Keys.InnerTubeCookie, Keys.DataSyncId, Keys.InnerTubeAuthUser, Keys.AccountName, Keys.AccountEmail, Keys.AccountChannelHandle)
                .forEach { p.remove(it) }
        }
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
    }

    fun connectListenBrainz(token: String) = viewModelScope.launch {
        val user = graph.listenBrainz.validate(token)
        if (user == null) {
            android.widget.Toast.makeText(graph.app, "That token didn't work. Check it and try again.", android.widget.Toast.LENGTH_LONG).show()
            return@launch
        }
        graph.settings.edit {
            it[Keys.ListenBrainzToken] = token.trim()
            it[Keys.ListenBrainzUser] = user
            it[Keys.ListenBrainzEnabled] = true
        }
        android.widget.Toast.makeText(graph.app, "Scrobbling as $user", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun disconnectListenBrainz() = viewModelScope.launch {
        graph.settings.edit { it.remove(Keys.ListenBrainzToken); it.remove(Keys.ListenBrainzUser) }
    }

    fun exportAll() = viewModelScope.launch {
        val ids = graph.database.dao().downloadedIds()
        // It runs a while (lyrics and covers are fetched per song); say so instead of looking dead.
        android.widget.Toast.makeText(graph.app, "Saving ${ids.size} songs to Music/MusicForge…", android.widget.Toast.LENGTH_SHORT).show()
        var ok = 0
        ids.forEach { if (graph.musicExport.export(it)) ok++ }
        android.widget.Toast.makeText(graph.app, "Saved $ok of ${ids.size} to Music/MusicForge", android.widget.Toast.LENGTH_LONG).show()
    }

    fun upgradeLowQuality() = viewModelScope.launch {
        val ids = graph.downloads.lowQuality()
        ids.forEach(graph.downloads::upgrade)
        android.widget.Toast.makeText(graph.app, "Upgrading ${ids.size} downloads; the current copies play until each is done", android.widget.Toast.LENGTH_LONG).show()
    }

    fun resetHome() = viewModelScope.launch {
        graph.database.dao().clearFeedback()
        android.widget.Toast.makeText(graph.app, "Home reset: every suggestion is back", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun clearSearchHistory() = viewModelScope.launch {
        graph.database.dao().clearSearchHistory()
        android.widget.Toast.makeText(graph.app, "Search history cleared", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun clearImageCache() = viewModelScope.launch {
        val ctx = graph.app
        val loader = coil3.SingletonImageLoader.get(ctx)
        loader.memoryCache?.clear()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loader.diskCache?.clear() }
        android.widget.Toast.makeText(ctx, "Image cache cleared", android.widget.Toast.LENGTH_SHORT).show()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun songCacheBytes(): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { graph.mediaEngine.playerCacheSizeBytes }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun clearSongCache() = viewModelScope.launch {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { graph.mediaEngine.clearPlayerCache() }
        android.widget.Toast.makeText(graph.app, "Song cache cleared", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun dismiss() {
        _restore.value = RestoreState.Idle
    }

    fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) = viewModelScope.launch {
        graph.settings.edit { it[key] = value }
    }

    val updateState = graph.updater.state

    fun checkForUpdates() = viewModelScope.launch { graph.updater.checkNow() }

    fun installUpdate(release: ReleaseInfo) = viewModelScope.launch { graph.updater.install(release) }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onSignIn: () -> Unit, onAbout: () -> Unit, onEqualizer: () -> Unit, onTaste: () -> Unit, onCustomiseHome: () -> Unit = {}) {
    val context = LocalContext.current
    val vm = viewModel { SettingsViewModel(context.graph) }
    val restore by vm.restore.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle(initialValue = null)

    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.inspect(uri)
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.export(uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        BackRow(onBack)
        ScreenHeader("Settings", belowBackRow = true)
        if (restore is SettingsViewModel.RestoreState.Working) {
            LoadingBar(visible = true, modifier = Modifier.fillMaxWidth().height(3.dp))
        }

        SectionTitle("Account")
        Group {
            val name = prefs?.get(Keys.AccountName)?.takeIf { it.isNotBlank() && prefs?.get(Keys.InnerTubeCookie) != null }
            if (name == null) {
                ActionRow("Sign in with Google", "Your likes, playlists, albums and artists from YouTube Music, kept in sync", onSignIn)
            } else {
                val sync by context.graph.accountSync.status.collectAsStateWithLifecycle()
                val last = prefs?.get(Keys.LastAccountSync)
                ActionRow("Signed in as $name", prefs?.get(Keys.AccountEmail).orEmpty().ifEmpty { "YouTube Music" }) {}
                ActionRow(
                    "Sync now",
                    when (val s = sync) {
                        is dev.musicforge.data.AccountSync.Status.Running -> "Syncing ${s.step.lowercase()}…"
                        is dev.musicforge.data.AccountSync.Status.Done -> "Synced just now: ${s.summary}"
                        is dev.musicforge.data.AccountSync.Status.Failed -> "Last sync failed: ${s.message}"
                        else -> last?.let { "Last synced " + android.text.format.DateUtils.getRelativeTimeSpanString(it) } ?: "Likes, playlists, albums and artists"
                    },
                ) { vm.syncNow() }
                if (sync is dev.musicforge.data.AccountSync.Status.Running) {
                    LoadingBar(visible = true, modifier = Modifier.fillMaxWidth().height(3.dp))
                }
                ToggleRow(
                    "Pause search history",
                    prefs?.get(Keys.PauseSearchHistory) ?: false,
                    subtitle = "Search without your account, so searches stay out of your YouTube history",
                ) { vm.set(Keys.PauseSearchHistory, it) }
                ToggleRow(
                    "Pause listen history",
                    prefs?.get(Keys.PauseListenHistory) ?: false,
                    subtitle = "Plays here won't be added to your YouTube Music history",
                ) { vm.set(Keys.PauseListenHistory, it) }
                ActionRow("Clear search history", "Remove your recent searches from this phone") { vm.clearSearchHistory() }
                ActionRow("Switch account", "Sign in with a different Google account", onSignIn)
                ActionRow("Sign out", "Keeps your library on this phone", vm::signOut)
            }
        }

        SectionTitle("Backup & restore")
        Group {
            ActionRow(
                title = "Restore a backup",
                subtitle = "A MusicForge, Metrolist or ConvX backup file, history, lyrics and settings included. Signing in keeps the rest in sync",
                onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            )
            var autoList by androidx.compose.runtime.remember { mutableStateOf<List<java.io.File>?>(null) }
            val listScope = rememberCoroutineScope()
            ActionRow("Restore an automatic backup", "Daily copies, plus a snapshot taken before every restore") {
                listScope.launch { autoList = context.graph.autoBackup.list() }
            }
            autoList?.let { files ->
                AlertDialog(
                    onDismissRequest = { autoList = null },
                    title = { Text("Automatic backups") },
                    text = {
                        Column {
                            if (files.isEmpty()) Text("None yet. The first one is made a little after the app starts.")
                            files.forEach { f ->
                                Text(
                                    SimpleDateFormat("EEE d MMM, HH:mm", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
                                        .format(Date(f.lastModified())) + "  ·  ${f.length() / 1024} KB",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth().clickable { autoList = null; vm.inspect(android.net.Uri.fromFile(f)) }.padding(vertical = 10.dp),
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton({ autoList = null }) { Text("Close") } },
                )
            }
            ActionRow(
                title = "Create backup",
                subtitle = "Your whole library and settings in one file",
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    createBackup.launch("${BuildConfig.CODENAME}-$stamp.backup")
                },
            )
        }

        SectionTitle("General")
        Group {
            val start = prefs?.get(Keys.StartScreen)?.let { raw -> Tab.entries.firstOrNull { it.route.equals(raw, true) || it.name.equals(raw, true) }?.route } ?: Tab.Home.route
            ChoiceRow("Start screen", Tab.entries.map { it.route to it.label }, start) { vm.set(Keys.StartScreen, it) }
            ToggleRow("Keep screen on", prefs?.get(Keys.KeepScreenOn) ?: false) { vm.set(Keys.KeepScreenOn, it) }
            ToggleRow("Haptics", prefs?.get(Keys.HapticsEnabled) ?: true, subtitle = "Button presses, long-presses and swipe-to-skip") { vm.set(Keys.HapticsEnabled, it) }
        }

        SectionTitle("Playback")
        Group {
            val quality = prefs?.get(Keys.AudioQuality) ?: "AUTO"
            ChoiceRow("Audio quality", listOf("AUTO" to "Auto", "HIGH" to "High", "LOW" to "Data saver"), quality) { vm.set(Keys.AudioQuality, it) }
            ToggleRow("Even out loudness", prefs?.get(Keys.AudioNormalization) ?: true) { vm.set(Keys.AudioNormalization, it) }
            ToggleRow("Skip silence", prefs?.get(Keys.SkipSilence) ?: false) { vm.set(Keys.SkipSilence, it) }
            ToggleRow("Remember queue", prefs?.get(Keys.PersistentQueue) ?: true) { vm.set(Keys.PersistentQueue, it) }
            ToggleRow("Keep playing similar songs", prefs?.get(Keys.Autoplay) ?: true) { vm.set(Keys.Autoplay, it) }
            ToggleRow("Double-tap cover to seek", prefs?.get(Keys.DoubleTapSeek) ?: true, subtitle = "Rewind or skip 10 seconds") { vm.set(Keys.DoubleTapSeek, it) }
            ToggleRow("Swipe mini player to skip", prefs?.get(Keys.SwipeMiniToSkip) ?: true) { vm.set(Keys.SwipeMiniToSkip, it) }
            ActionRow("Equalizer", "Bands and presets, on any phone", onEqualizer)
        }

        SectionTitle("Lyrics")
        Group {
            ToggleRow("Show lyrics", prefs?.get(Keys.ShowLyrics) ?: true) { vm.set(Keys.ShowLyrics, it) }
            val size = prefs?.get(Keys.LyricsTextSize) ?: 24f
            // A slider dialog so you can nudge the size without hunting a number.
            val sizeOptions = listOf(16f to "Small", 20f to "Medium", 24f to "Default", 28f to "Large", 32f to "Extra large")
            val label = sizeOptions.find { it.first == size }?.second ?: "$size sp"
            var showSizeDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            ActionRow("Lyrics text size", label) { showSizeDialog = true }
            if (showSizeDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showSizeDialog = false },
                    title = { Text("Lyrics text size") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sizeOptions.forEach { (v, l) ->
                                Text(
                                    l,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.set(Keys.LyricsTextSize, v); showSizeDialog = false }
                                        .padding(vertical = 8.dp),
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton({ showSizeDialog = false }) { Text("Cancel") } },
                )
            }
        }

        SectionTitle("Storage")
        Group {
            // Cache so big it has its own gravitational pull? Tap to clear.
            var imgSize by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
            LaunchedEffect(Unit) {
                imgSize = coil3.SingletonImageLoader.get(context).diskCache?.size ?: 0L
            }
            ActionRow("Clear image cache", "${formatBytes(imgSize)} · tap to clear") { vm.clearImageCache() }
            var songSize by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
            LaunchedEffect(Unit) {
                songSize = vm.songCacheBytes()
            }
            ActionRow("Clear song cache", "${formatBytes(songSize)} · tap to clear") { vm.clearSongCache() }
        }

        SectionTitle("Scrobbling")
        Group {
            val user = prefs?.get(Keys.ListenBrainzUser)
            var asking by androidx.compose.runtime.remember { mutableStateOf(false) }
            if (prefs?.get(Keys.ListenBrainzToken).isNullOrBlank()) {
                ActionRow("Connect ListenBrainz", "Keep a record of everything you listen to (paste your token)") { asking = true }
            } else {
                ActionRow(if (user != null) "Scrobbling as $user" else "Scrobbling to ListenBrainz", "Tap to disconnect") { vm.disconnectListenBrainz() }
            }
            if (asking) {
                var token by androidx.compose.runtime.remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { asking = false },
                    title = { Text("ListenBrainz") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Paste the user token from listenbrainz.org/settings.", style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.material3.OutlinedTextField(token, { token = it }, singleLine = true, label = { Text("Token") })
                        }
                    },
                    confirmButton = { TextButton({ vm.connectListenBrainz(token); asking = false }) { Text("Connect") } },
                    dismissButton = { TextButton({ asking = false }) { Text("Cancel") } },
                )
            }
        }

        SectionTitle("Downloads")
        Group {
            val context = LocalContext.current
            ToggleRow("Download songs you like", prefs?.get(Keys.AutoDownloadOnLike) ?: false) { vm.set(Keys.AutoDownloadOnLike, it) }
            val low by androidx.compose.runtime.produceState(0) { value = context.graph.downloads.lowQuality().size }
            if (low > 0) {
                ActionRow("Upgrade low-quality downloads", "$low ${if (low == 1) "song was" else "songs were"} downloaded at a low bitrate; fetch them again in high quality") {
                    vm.upgradeLowQuality()
                }
            }
            val exporter = context.graph.musicExport
            if (exporter.supported) {
                ToggleRow("Also save to Music folder", prefs?.get(Keys.ExportToMusic) ?: false) { vm.set(Keys.ExportToMusic, it) }
                ActionRow("Save all downloads to Music now", "Music/MusicForge · other players and your PC can use them") {
                    vm.exportAll()
                }
            }
        }

        SectionTitle("Home")
        Group {
            ActionRow("What shapes your Home", "When you listen, who you play most, and what you hid", onTaste)
            ActionRow("Customise Home", "Hide or reorder Home shelves") { onCustomiseHome() }
            ActionRow("Reset Home", "Bring back songs you hid and artists you wanted less of") {
                vm.resetHome()
            }
        }

        SectionTitle("Appearance")
        Group {
            val dark = prefs?.get(Keys.DarkMode) ?: DarkMode.AUTO.name
            ChoiceRow("Theme", listOf("AUTO" to "Auto", "ON" to "Dark", "OFF" to "Light"), dark) { vm.set(Keys.DarkMode, it) }
            ToggleRow("Pure black", prefs?.get(Keys.PureBlack) ?: false, subtitle = "Dark theme on true black, surfaces included") { vm.set(Keys.PureBlack, it) }
            val glass = prefs?.get(Keys.GlassTier) ?: GlassTier.Default.name
            ChoiceRow(
                "Glass",
                // Live glass needs RenderEffect; on Android 11 and older it isn't offered at all.
                listOfNotNull(
                    GlassTier.SOLID.name to "Solid",
                    GlassTier.ARTWORK.name to "Frosted Crayon",
                    (GlassTier.LIQUID.name to "Liquid Glass").takeIf { dev.musicforge.ui.backdrop.isRenderEffectSupported() },
                ),
                glass,
            ) { vm.set(Keys.GlassTier, it) }
            ToggleRow(
                "Hide top bar",
                prefs?.get(Keys.HideTopBar) ?: false,
                subtitle = "Collapse the status bar scrim and screen headers for an edge-to-edge canvas",
            ) { vm.set(Keys.HideTopBar, it) }
        }

        SectionTitle("About")
        Group {
            ActionRow("About ${BuildConfig.CODENAME}", "Credits, licenses and version", onAbout)
            val updateState by vm.updateState.collectAsStateWithLifecycle()
            UpdateCheckRow(
                state = updateState,
                onCheck = { vm.checkForUpdates() },
                onInstall = { release -> vm.installUpdate(release) },
            )
            ToggleRow(
                "Include previews",
                prefs?.get(UpdateKeys.includePreviews) ?: false,
                subtitle = "Offer beta and preview releases when checking for updates",
            ) { vm.set(UpdateKeys.includePreviews, it) }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "${BuildConfig.CODENAME} ${BuildConfig.VERSION_NAME} · GPL-3.0\nOpen source; credits are under About.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

    RestoreDialogs(restore, vm)
}

@Composable
private fun RestoreDialogs(state: SettingsViewModel.RestoreState, vm: SettingsViewModel) {
    when (state) {
        SettingsViewModel.RestoreState.Idle -> Unit
        // Modal on purpose: nothing else should touch the library while a backup is restoring.
        SettingsViewModel.RestoreState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Working…") },
            text = {
                Column {
                    Text("Reading your backup. Large libraries take a few seconds.")
                    dev.musicforge.ui.designsystem.LoadingBar(modifier = Modifier.padding(top = 12.dp), delayMs = 0)
                }
            },
        )
        is SettingsViewModel.RestoreState.Confirm -> {
            var includeAccount by androidx.compose.runtime.remember { mutableStateOf(true) }
            val p = state.preview
            val from = when (p.origin) {
                BackupImporter.Origin.METROLIST -> "Metrolist"
                BackupImporter.Origin.CONVX -> "ConvX"
                BackupImporter.Origin.MUSICFORGE -> "this app"
                BackupImporter.Origin.UNKNOWN -> "Metrolist/ConvX (older version)"
            }
            AlertDialog(
                onDismissRequest = vm::dismiss,
                title = { Text("Restore from $from?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${plural(p.rowCounts["song"] ?: 0, "song")} · ${plural(p.rowCounts["playlist"] ?: 0, "playlist")} · ${plural(p.rowCounts["event"] ?: 0, "play")}")
                        Text("This replaces your current library. Downloads aren't part of backups; re-download them afterwards.")
                        if (p.accountName != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Sign in as ${p.accountName}", Modifier.weight(1f))
                                Switch(includeAccount, { includeAccount = it }, colors = monoSwitch())
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ vm.confirm(state.uri, includeAccount) }) { Text("Restore") } },
                dismissButton = { TextButton(vm::dismiss) { Text("Cancel") } },
            )
        }
        is SettingsViewModel.RestoreState.Done -> {
            val r = state.result
            AlertDialog(
                onDismissRequest = vm::dismiss,
                title = { Text("Restored") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${plural(r.copied["song"] ?: 0, "song")}, ${plural(r.copied["playlist"] ?: 0, "playlist")}, ${plural(r.copied["event"] ?: 0, "play")}, ${plural(r.settingsImported, "setting")}.")
                        if (r.wereDownloaded.isNotEmpty()) {
                            Text("${plural(r.wereDownloaded.size, "song")} were downloaded in your old app. Bring them back from the Downloads tab.")
                        }
                        r.warnings.take(4).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = { TextButton(vm::dismiss) { Text("Done") } },
            )
        }
        is SettingsViewModel.RestoreState.Failed -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Couldn't restore") },
            text = { Text(state.message) },
            confirmButton = { TextButton(vm::dismiss) { Text("OK") } },
        )
    }
}

@Composable
internal fun monoSwitch() = SwitchDefaults.colors(
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().pressScale(press).clickable(interactionSource = press, indication = null, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UpdateCheckRow(
    state: UpdateState,
    onCheck: () -> Unit,
    onInstall: (ReleaseInfo) -> Unit,
) {
    val subtitle = when (state) {
        is UpdateState.Idle -> "Version ${BuildConfig.VERSION_NAME} · tap to check"
        is UpdateState.Checking -> "Checking for updates…"
        is UpdateState.UpToDate -> "You're up to date"
        is UpdateState.Available -> "Update to ${state.release.versionName} · tap to install"
        is UpdateState.Downloading -> "Downloading… ${(state.progress * 100).roundToInt()}%"
        is UpdateState.Installing -> "Opening installer…"
        is UpdateState.Failed -> state.message
    }
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, role = androidx.compose.ui.semantics.Role.Button) {
                when (state) {
                    is UpdateState.Available -> onInstall(state.release)
                    is UpdateState.Idle, is UpdateState.UpToDate, is UpdateState.Failed -> onCheck()
                    else -> Unit
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(if (state is UpdateState.Available) "Install update" else "Check for updates", style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state is UpdateState.Downloading) {
            LoadingBar(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                progress = state.progress.coerceIn(0f, 1f),
            )
        }
    }
}

@Composable
internal fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        // One toggleable row (not a clickable row holding a second, clickable switch), so a screen
        // reader announces it as a switch with its state.
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onCheckedChange = null, colors = monoSwitch())
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp),
        ) {
            options.forEach { (value, label) ->
                val on = value == selected
                val press = remember { MutableInteractionSource() }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .pressScale(press)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(interactionSource = press, indication = null) { onSelect(value) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
