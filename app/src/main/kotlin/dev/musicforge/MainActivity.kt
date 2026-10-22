package dev.musicforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.data.prefs.Keys
import dev.musicforge.ui.ForgeRoot
import dev.musicforge.ui.components.GlassStyle
import dev.musicforge.ui.components.GlassTier
import dev.musicforge.ui.theme.DarkMode
import dev.musicforge.ui.theme.ForgeTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val look = graph.settings.data.map { prefs ->
            Look(
                darkMode = prefs[Keys.DarkMode]?.let { runCatching { DarkMode.valueOf(it) }.getOrNull() } ?: DarkMode.AUTO,
                pureBlack = prefs[Keys.PureBlack] ?: false,
                glass = prefs[Keys.GlassTier]?.let { runCatching { GlassTier.valueOf(it) }.getOrNull() } ?: GlassTier.ARTWORK,
            )
        }
        setContent {
            val current by look.collectAsStateWithLifecycle(initialValue = Look())
            ForgeTheme(darkMode = current.darkMode, pureBlack = current.pureBlack) {
                ForgeRoot(glass = GlassStyle(tier = current.glass))
            }
        }
    }

    private data class Look(
        val darkMode: DarkMode = DarkMode.AUTO,
        val pureBlack: Boolean = false,
        val glass: GlassTier = GlassTier.ARTWORK,
    )
}
