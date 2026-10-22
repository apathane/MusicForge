package dev.musicforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import dev.musicforge.playback.EqPreset
import dev.musicforge.playback.EqSettings
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.IconAction
import dev.musicforge.ui.components.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    val bands = EqSettings.bands
    val prefs by graph.settings.data.collectAsStateWithLifecycle(initialValue = null)
    val enabled = prefs?.get(Keys.EqEnabled) ?: false
    val levels = EqSettings.parse(prefs?.get(Keys.EqLevels)).let { l -> List(bands.size) { l.getOrElse(it) { 0 } } }
    val preset = prefs?.get(Keys.EqPreset)

    fun save(on: Boolean = enabled, newLevels: List<Int> = levels, presetName: String? = null) = scope.launch {
        graph.settings.edit {
            it[Keys.EqEnabled] = on
            it[Keys.EqLevels] = EqSettings.format(newLevels)
            if (presetName != null) it[Keys.EqPreset] = presetName else it.remove(Keys.EqPreset)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp)) { IconAction(ForgeIcons.Back, "Back", onBack) }
        ScreenHeader("Equalizer", belowBackRow = true) {
            Switch(
                enabled,
                { save(on = it, presetName = preset) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
            )
        }
        run {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EqPreset.entries) { p ->
                    val on = preset == p.name
                    Text(
                        p.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { save(on = true, newLevels = bands.map { b -> p.gainMbAt(b.centerHz) }, presetName = p.name) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            bands.forEachIndexed { i, band ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(hz(band.centerHz), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(72.dp))
                        Slider(
                            value = levels[i].toFloat(),
                            onValueChange = { v -> save(on = true, newLevels = levels.toMutableList().also { it[i] = v.toInt() }) },
                            valueRange = band.minMb.toFloat()..band.maxMb.toFloat(),
                            enabled = enabled,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%+.1f dB".format(levels[i] / 100f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp).padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Phone's own sound settings",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(50))
                .clickable {
                    val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                        .putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        .putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, graph.audioSessionId)
                        .putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                    runCatching { context.startActivity(intent) }.onFailure {
                        android.widget.Toast.makeText(context, "This phone has no system equalizer; use the one above.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun hz(v: Int) = if (v >= 1000) "%.1f kHz".format(v / 1000f).replace(".0 ", " ") else "$v Hz"
