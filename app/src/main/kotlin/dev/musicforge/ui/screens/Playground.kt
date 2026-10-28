package dev.musicforge.ui.screens

import androidx.datastore.preferences.core.booleanPreferencesKey

/** Hidden behind a 7-tap easter egg on the version line in Settings. */
object PlaygroundKeys {
    val playgroundUnlocked = booleanPreferencesKey("forge.playgroundUnlocked")
}
