package dev.musicforge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.data.prefs.Keys
import dev.musicforge.ui.ForgeRoot
import dev.musicforge.ui.components.GlassTier
import dev.musicforge.ui.theme.DarkMode
import dev.musicforge.ui.theme.ForgeTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
        graph.player.connect()
        handlePlayFromSearch(intent)
        // The media notification needs this on Android 13+ (the X7b runs 14).
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            // The root composable paints the whole screen; the window's own background under it
            // was an extra full-screen fill on every frame.
            androidx.compose.runtime.SideEffect { window.setBackgroundDrawable(null) }
            val current by look.collectAsStateWithLifecycle(initialValue = Look())
            val ambience by graph.ambience.ambience.collectAsStateWithLifecycle()
            // The splash is drawn before any of our code runs, from a theme chosen now for next time.
            androidx.compose.runtime.LaunchedEffect(current.darkMode, current.pureBlack) {
                if (Build.VERSION.SDK_INT >= 33) {
                    splashScreen.setSplashScreenTheme(
                        when (if (current.pureBlack) DarkMode.ON else current.darkMode) {
                            DarkMode.ON -> R.style.Theme_Forge_Dark
                            DarkMode.OFF -> R.style.Theme_Forge_Light
                            DarkMode.AUTO -> R.style.Theme_Forge
                        },
                    )
                }
            }
            ForgeTheme(darkMode = current.darkMode, pureBlack = current.pureBlack, artwork = ambience.colors) {
                ForgeRoot(glassTier = current.glass, ambience = ambience)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handlePlayFromSearch(intent)
    }

    /** Voice: "play X on MusicForge". Your own songs first, then YouTube Music. */
    private fun handlePlayFromSearch(intent: android.content.Intent?) {
        if (intent?.action != android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty()
        graph.scope.launch {
            val items = dev.musicforge.playback.SearchPlay.resolve(graph, query)
            if (items.isNotEmpty()) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { graph.player.play(items) }
        }
    }

    private data class Look(
        val darkMode: DarkMode = DarkMode.AUTO,
        val pureBlack: Boolean = false,
        val glass: GlassTier = GlassTier.ARTWORK,
    )
}
