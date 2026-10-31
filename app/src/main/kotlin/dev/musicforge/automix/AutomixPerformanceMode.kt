/*
 * Ported from BitChord (https://github.com/kushagrasinghx/BitChord),
 * Copyright (C) 2026 Kushagra Singh, licensed GPL-3.0.
 * Copyright (C) 2026 apathane (MusicForge adaptation).
 * Distributed in MusicForge under the AGPL-3.0 (GPLv3/AGPLv3 section 13). See NOTICE.
 */
package dev.musicforge.automix

/** CPU budget for Automix's background analysis, not its audible mix algorithm. */
enum class AutomixPerformanceMode(val inferenceThreads: Int) {
    EFFICIENT(1),
    BALANCED(2),
    PERFORMANCE(4),
    ;

    /** Whether analysis should yield to playback rather than compete with it. */
    val yieldsToPlayback: Boolean get() = this == EFFICIENT
}
