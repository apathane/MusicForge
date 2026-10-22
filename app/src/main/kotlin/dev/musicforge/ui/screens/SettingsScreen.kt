package dev.musicforge.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.GlassTier
import dev.musicforge.ui.components.IconAction
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SectionTitle
import dev.musicforge.ui.theme.DarkMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            RestoreState.Done(graph.backupImporter.import(uri, includeAccount, graph.downloads.completedIds()))
        }
            .getOrElse { RestoreState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun export(uri: Uri) = viewModelScope.launch {
        _restore.value = RestoreState.Working
        _restore.value = runCatching { graph.backupExporter.export(uri); RestoreState.Idle }
            .getOrElse { RestoreState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun signOut() = viewModelScope.launch {
        graph.settings.edit { p ->
            listOf(Keys.InnerTubeCookie, Keys.DataSyncId, Keys.InnerTubeAuthUser, Keys.AccountName, Keys.AccountEmail, Keys.AccountChannelHandle)
                .forEach { p.remove(it) }
        }
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
    }

    fun exportAll() = viewModelScope.launch {
        val ids = graph.database.dao().downloadedIds()
        var ok = 0
        ids.forEach { if (graph.musicExport.export(it)) ok++ }
        android.widget.Toast.makeText(graph.app, "Saved $ok of ${ids.size} to Music/MusicForge", android.widget.Toast.LENGTH_LONG).show()
    }

    fun dismiss() {
        _restore.value = RestoreState.Idle
    }

    fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) = viewModelScope.launch {
        graph.settings.edit { it[key] = value }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onSignIn: () -> Unit, onAbout: () -> Unit) {
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
        ScreenHeader("Settings") { IconAction(ForgeIcons.ChevronRight, "Close", onBack) }

        SectionTitle("Account")
        Group {
            val name = prefs?.get(Keys.AccountName)?.takeIf { it.isNotBlank() && prefs?.get(Keys.InnerTubeCookie) != null }
            if (name == null) {
                ActionRow("Sign in to YouTube Music", "Your likes, playlists, history and recommendations", onSignIn)
            } else {
                ActionRow("Signed in as $name", prefs?.get(Keys.AccountEmail).orEmpty().ifEmpty { "YouTube Music" }) {}
                ActionRow("Sign out", "Keeps your library on this phone", vm::signOut)
            }
        }

        SectionTitle("Backup & restore")
        Group {
            ActionRow(
                title = "Restore from Metrolist or ConvX",
                subtitle = "Songs, likes, playlists, albums, artists, history, lyrics and settings",
                onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            )
            var autoList by androidx.compose.runtime.remember { mutableStateOf<List<java.io.File>?>(null) }
            ActionRow("Restore an automatic backup", "Daily copies, plus a snapshot taken before every restore") {
                autoList = context.graph.autoBackup.list()
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
                                    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(f.lastModified())) + "  ·  ${f.length() / 1024} KB",
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

        SectionTitle("Playback")
        Group {
            val quality = prefs?.get(Keys.AudioQuality) ?: "AUTO"
            ChoiceRow("Audio quality", listOf("AUTO" to "Auto", "HIGH" to "High", "LOW" to "Data saver"), quality) { vm.set(Keys.AudioQuality, it) }
            ToggleRow("Even out loudness", prefs?.get(Keys.AudioNormalization) ?: true) { vm.set(Keys.AudioNormalization, it) }
            ToggleRow("Skip silence", prefs?.get(Keys.SkipSilence) ?: false) { vm.set(Keys.SkipSilence, it) }
            val context = LocalContext.current
            ActionRow("Equalizer", "Opens your phone's sound settings") {
                val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    .putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    .putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                    .putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, context.graph.audioSessionId)
                runCatching { context.startActivity(intent) }.onFailure {
                    android.widget.Toast.makeText(context, "No equalizer on this phone", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        SectionTitle("Downloads")
        Group {
            val context = LocalContext.current
            val exporter = context.graph.musicExport
            if (exporter.supported) {
                ToggleRow("Also save to Music folder", prefs?.get(Keys.ExportToMusic) ?: false) { vm.set(Keys.ExportToMusic, it) }
                ActionRow("Save all downloads to Music now", "Music/MusicForge · other players and your PC can use them") {
                    vm.exportAll()
                }
            }
        }

        SectionTitle("Appearance")
        Group {
            val dark = prefs?.get(Keys.DarkMode) ?: DarkMode.AUTO.name
            ChoiceRow("Theme", listOf("AUTO" to "Auto", "ON" to "Dark", "OFF" to "Light"), dark) { vm.set(Keys.DarkMode, it) }
            ToggleRow("Pure black", prefs?.get(Keys.PureBlack) ?: false) { vm.set(Keys.PureBlack, it) }
            val glass = prefs?.get(Keys.GlassTier) ?: GlassTier.ARTWORK.name
            ChoiceRow(
                "Glass",
                listOf(GlassTier.SOLID.name to "Solid", GlassTier.ARTWORK.name to "Artwork", GlassTier.LIVE.name to "Live"),
                glass,
            ) { vm.set(Keys.GlassTier, it) }
        }

        SectionTitle("About")
        Group { ActionRow("About ${BuildConfig.CODENAME}", "Credits, licenses and version", onAbout) }

        Spacer(Modifier.height(24.dp))
        Text(
            "${BuildConfig.CODENAME} ${BuildConfig.VERSION_NAME} · GPL-3.0\nBuilt on the work of Metrolist, ConvX and BitChord.",
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
        SettingsViewModel.RestoreState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Working…") },
            text = { Text("Reading your backup. Large libraries take a few seconds.") },
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
private fun monoSwitch() = SwitchDefaults.colors(
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
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = monoSwitch())
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
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onSelect(value) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
