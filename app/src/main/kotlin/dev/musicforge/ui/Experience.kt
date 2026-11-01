package dev.musicforge.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which interface MusicForge draws. Both share everything below the screen: the player, the queue,
 * the database, the ViewModels and the routes, so switching keeps playback and the current page.
 * Classic is the interface every release up to P8 shipped; Forge is the rebuilt one
 * (docs/ui-rebuild/PLAN.md). Two coats of paint on one engine, except the second coat moves.
 */
enum class Experience(val label: String) {
    CLASSIC("Classic"),
    FORGE("Forge");

    companion object {
        fun of(stored: String?): Experience = entries.firstOrNull { it.name == stored } ?: CLASSIC
    }
}

val LocalExperience = staticCompositionLocalOf { Experience.CLASSIC }
