package dev.musicforge.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.musicforge.BuildConfig
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.IconAction
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SectionTitle

private val credits = listOf(
    "Metrolist" to "The download engine's design (persistent Media3 index), the InnerTube API module and the stream-extraction glue, vendored with attribution. GPL-3.0.",
    "ConvX" to "The glass-and-Cupertino interface language, lyrics/queue/sleep feature set, download export ideas and the restore format. GPL-3.0.",
    "BitChord" to "The monochrome style, bounded-range transfers that beat googlevideo throttling, read-ahead, and the same-length re-resolve rule. GPL-3.0.",
    "InnerTubeX" to "YouTube stream extraction (cipher, n-param, PoToken) by MetrolistGroup. GPL-3.0.",
    "Kyant0/backdrop" to "The liquid glass renderer (lens refraction, blur, specular rim), vendored from v2.0.0 by way of ConvX. Apache-2.0, Copyright 2025 Kyant0.",
    "LRCLIB" to "Open, community-made synced lyrics (lrclib.net).",
    "Inter" to "Typeface by Rasmus Andersson and the Inter Project Authors. SIL Open Font License 1.1.",
    "Material Icons" to "Icon path data by Google. Apache License 2.0.",
    "AndroidX Media3, Compose, Room, Coil, OkHttp, Ktor, kotlinx" to "Apache License 2.0.",
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp)) { IconAction(ForgeIcons.Back, "Back", onBack) }
        ScreenHeader(BuildConfig.CODENAME, belowBackRow = true)
        Text(
            "Version ${BuildConfig.VERSION_NAME}. A YouTube Music player built from Metrolist's durable downloads, ConvX's interface and features, " +
                "and BitChord's speed and calm. Made for people who love music.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        SectionTitle("Built on")
        credits.forEach { (name, what) ->
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(what, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionTitle("License")
        Text(
            "This app is free software under the GNU General Public License v3.0. You may share and change it under the same terms. " +
                "It is not affiliated with YouTube or Google.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}
