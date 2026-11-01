/*
 * MusicForge's stand-in for BitChord's AudioCache, with the API TrackAnalyzer calls
 * (BitChord: https://github.com/kushagrasinghx/BitChord). Written for MusicForge.
 * Copyright (C) 2026 apathane. AGPL-3.0, see LICENSE and NOTICE.
 */
package dev.musicforge.automix

import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata

/**
 * What Automix's analyzer needs from the cache, answered from MusicForge's own two caches.
 *
 * BitChord keeps up to three copies of a recording (Opus, a substituted source, a quality
 * upgrade). MusicForge keeps at most two: the permanent download (keyed by its storage key) and
 * the player's LRU copy (keyed by the video id). Either is the same music, so either can be
 * analysed (two copies, one tune: the cache's idea of a duet). Reads go through the player's own data source, so a part not cached yet is fetched
 * once and lands in the player cache for when it plays.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioCache(
    private val downloadCache: Cache,
    private val playerCache: Cache,
    private val storageKey: (String) -> String,
    private val playbackSource: DataSource.Factory,
) {
    /** One copy of a recording on disk. Same shape as BitChord's, so TrackAnalyzer is unchanged. */
    data class Rendition(val key: String, val contentLength: Long, val cachedPrefix: Long) {
        val isComplete: Boolean get() = contentLength > 0 && cachedPrefix >= contentLength
    }

    /** The video id behind a MusicForge item URI (`…/watch?v=<id>`), or the URI itself for local files. */
    private fun idOf(uri: Uri): String = uri.getQueryParameter("v") ?: uri.toString()

    /** The key the download cache files this recording under (shared copies use the owner's key). */
    fun cacheKeyOf(uri: Uri): String = storageKey(idOf(uri))

    /** Copies of [uri]'s recording that are on disk, the longest cached prefix first. */
    fun renditionsOf(uri: Uri): List<Rendition> {
        val id = idOf(uri)
        return listOfNotNull(rendition(downloadCache, storageKey(id)), rendition(playerCache, id))
            .sortedByDescending { it.cachedPrefix }
    }

    private fun rendition(cache: Cache, key: String): Rendition? {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        val prefix = cache.getCachedLength(key, 0, if (length > 0) length else Long.MAX_VALUE)
        return if (prefix > 0) Rendition(key, length.coerceAtLeast(0), prefix) else null
    }

    /** Random reads for MediaExtractor, through the player's data source (cache first, then network). */
    fun renditionDataSource(uri: Uri, rendition: Rendition): MediaDataSource =
        PlaybackMediaDataSource(playbackSource, uri, idOf(uri), rendition.contentLength)

    /**
     * BitChord fetches the first seconds of a track for analysis ahead of playback. MusicForge's
     * ReadAhead already caches the start of upcoming items, and reads here fetch what is missing.
     */
    fun requestAnalysisHead(@Suppress("UNUSED_PARAMETER") uri: Uri) = Unit

    /**
     * not ported: BitChord deletes a cached copy that fails to decode. MusicForge never deletes
     * cached or downloaded bytes on an analysis failure: playback and downloads own those files.
     */
    fun discardBadRendition(@Suppress("UNUSED_PARAMETER") uri: Uri, @Suppress("UNUSED_PARAMETER") key: String): Boolean = false
}

/** MediaExtractor's random reads served by a Media3 DataSource; one open per read. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class PlaybackMediaDataSource(
    private val factory: DataSource.Factory,
    private val uri: Uri,
    private val key: String,
    knownLength: Long,
) : MediaDataSource() {
    private val length: Long by lazy {
        if (knownLength > 0) return@lazy knownLength
        val ds = factory.createDataSource()
        try {
            ds.open(DataSpec.Builder().setUri(uri).setKey(key).build()).takeIf { it != C.LENGTH_UNSET.toLong() } ?: -1L
        } finally {
            ds.close()
        }
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size == 0) return 0
        if (length >= 0 && position >= length) return -1
        val ds = factory.createDataSource()
        try {
            ds.open(DataSpec.Builder().setUri(uri).setKey(key).setPosition(position).setLength(size.toLong()).build())
            var read = 0
            while (read < size) {
                val n = ds.read(buffer, offset + read, size - read)
                if (n == C.RESULT_END_OF_INPUT) break
                read += n
            }
            return if (read == 0) -1 else read
        } finally {
            ds.close()
        }
    }

    override fun getSize(): Long = length

    override fun close() = Unit
}
