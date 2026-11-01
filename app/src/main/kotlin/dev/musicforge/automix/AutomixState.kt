/*
 * MusicForge's stand-in for the parts of BitChord's AppSettings that CrossfadeController reads
 * and writes (BitChord: https://github.com/kushagrasinghx/BitChord). Same names, types and
 * defaults, so the ported controller keeps its code. Written for MusicForge.
 * Copyright (C) 2026 apathane. AGPL-3.0, see LICENSE and NOTICE.
 */
package dev.musicforge.automix

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Automix's live state. Inputs are set by MusicForge's PlaybackService from its own settings
 * (Keys.AutomixEnabled, Keys.CrossfadeEnabled/CrossfadeSeconds, the player's speed); outputs are
 * written by [CrossfadeController] and can be shown by the UI (seek-bar window, mix view).
 */
object AutomixState {
    // Inputs (BitChord's defaults).
    /** Automix on: plans blends from analysis. BitChord: AppSettings.smartFadeEnabled. */
    val smartFadeEnabled = MutableStateFlow(false)
    /** Plain crossfade length in whole seconds, 0 = off. BitChord: AppSettings.crossfadeSeconds (Int). */
    val crossfadeSeconds = MutableStateFlow(0)
    val playbackSpeed = MutableStateFlow(1.0f)

    // Outputs.
    val smartMixInProgress = MutableStateFlow(false)
    val smartMixBlend = MutableStateFlow<MixBlend?>(null)
    val smartTransitionWindow = MutableStateFlow<TransitionWindow?>(null)
    val smartAnalysis = MutableStateFlow(SmartAnalysis())
    val smartMixView = MutableStateFlow<MixTransitionView?>(null)
    val smartMixProgress = MutableStateFlow(0f)
    val automixDetail = MutableStateFlow<String?>(null)

    /** not ported: BitChord stops Automix during Listen Together. MusicForge has no party mode. */
    const val inParty: Boolean = false

    /** not ported: BitChord skips blends while its bit-perfect USB output is active. MusicForge has none. */
    const val bitPerfectActive: Boolean = false
}

/**
 * The lyric fields Automix reads to keep a loop out of sung lines. BitChord's LyricLine carries
 * more (alignment, background vocals); MusicForge maps its synced lyrics onto this.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val sungUntilMs: Long? = null,
    val isGap: Boolean = false,
)

/** One timed word; Automix only needs where it ends. */
data class LyricWord(val endMs: Long)

/**
 * The part of BitChord's SleepTimer the controller reads: "stop after this track", which ends the
 * queue instead of blending into the next song. MusicForge's sleep timer counts minutes only, so
 * this stays off until it grows that mode.
 */
object SleepTimer {
    val afterTrack = MutableStateFlow(false)

    fun cancel() {
        afterTrack.value = false
    }
}

/**
 * BitChord skips Automix for items picked from a music-video row. not ported: MusicForge does not
 * mark music-video items yet, so every item counts as a song here.
 */
val androidx.media3.common.MediaItem.isVideoOrigin: Boolean get() = false
