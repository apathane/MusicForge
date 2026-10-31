/*
 * Ported from BitChord (https://github.com/kushagrasinghx/BitChord),
 * Copyright (C) 2026 Kushagra Singh, licensed GPL-3.0.
 * Copyright (C) 2026 apathane (MusicForge adaptation).
 * Distributed in MusicForge under the AGPL-3.0 (GPLv3/AGPLv3 section 13). See NOTICE.
 */
package dev.musicforge.automix

/** Where the analyser's warnings go, without it having to know the platform. */
object AnalysisLog {

    private var sink: ((String, Throwable?) -> Unit)? = null

    fun install(sink: (String, Throwable?) -> Unit) {
        this.sink = sink
    }

    fun warn(message: String, error: Throwable? = null) {
        sink?.invoke(message, error)
    }
}
