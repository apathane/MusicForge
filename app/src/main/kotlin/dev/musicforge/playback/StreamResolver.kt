package dev.musicforge.playback

import android.net.ConnectivityManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.metrolist.innertubex.extraction.ContentHints
import dev.musicforge.data.db.FormatEntity
import dev.musicforge.data.db.MusicDao
import dev.musicforge.data.prefs.Keys
import dev.musicforge.extraction.InnerTubeXPlayer
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

/** A playable URL plus everything the transfer layer needs to fetch it fast. */
data class ResolvedStream(
    val url: String,
    val headers: Map<String, String>,
    val client: String,
    /** Total byte length, or null when it couldn't be learned (transfer passes through). */
    val contentLength: Long?,
    /** Bounded range size the transfer should use; 0 means "don't chunk". */
    val chunkBytes: Long,
)

/**
 * Resolves a videoId to a stream, with a short-lived cache.
 *
 * The cache combines Metrolist's rules (expiry, a generation counter so a late resolve
 * can't overwrite a newer one) with BitChord's: when a refused URL is re-resolved, the
 * new rendition must have the *same length* or a partially fetched file would be
 * spliced from two different encodes.
 */
class StreamResolver(
    private val dao: MusicDao,
    private val settings: DataStore<Preferences>,
    private val connectivity: ConnectivityManager,
    private val http: OkHttpClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val stream: ResolvedStream, val expiresAt: Long)

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > 300
    }
    private val generations = HashMap<String, Long>()

    fun cached(videoId: String): ResolvedStream? = synchronized(entries) {
        val e = entries[videoId] ?: return null
        if (e.expiresAt <= now()) {
            entries.remove(videoId)
            null
        } else {
            e.stream
        }
    }

    /** Drops the cached URL and bumps the generation; called on 403/410/416. */
    fun invalidate(videoId: String) = synchronized(entries) {
        entries.remove(videoId)
        generations[videoId] = (generations[videoId] ?: 0L) + 1
    }

    /**
     * @param expectLength when re-resolving mid-transfer, the length already committed to.
     *   A different length fails instead of silently switching renditions.
     */
    suspend fun resolve(videoId: String, forDownload: Boolean, expectLength: Long? = null): ResolvedStream {
        cached(videoId)?.let { if (expectLength == null || it.contentLength == expectLength) return it }
        val generation = synchronized(entries) { generations[videoId] ?: 0L }

        val quality = AudioQuality.parse(settings.data.first()[Keys.AudioQuality])
        val song = dao.songEntity(videoId)
        val data = InnerTubeXPlayer.playerResponseForPlayback(
            videoId = videoId,
            audioQuality = quality,
            connectivityManager = connectivity,
            contentHints = ContentHints(isExplicit = song?.explicit, isUploaded = song?.isUploaded),
            // Downloads chunk at our layer; let InnerTubeX pick bounded-range clients for playback.
            allowBoundedRange = !forDownload,
        ).getOrThrow()

        val format = data.format
        val length = format.contentLength?.takeIf { it > 0 }
            ?: clen(data.streamUrl)
            ?: dao.format(videoId)?.contentLength?.takeIf { it > 0 }
            ?: probeLength(data.streamUrl, data.streamHeaders)
        if (expectLength != null && length != expectLength) {
            throw IOException("rendition changed for $videoId: $expectLength → $length bytes")
        }

        dao.upsert(
            FormatEntity(
                id = videoId,
                itag = format.itag,
                mimeType = format.mimeType.substringBefore(";"),
                codecs = format.mimeType.substringAfter("codecs=", "").substringBefore(";").trim().removeSurrounding("\""),
                bitrate = format.bitrate,
                sampleRate = format.audioSampleRate,
                contentLength = length ?: 0,
                loudnessDb = data.audioConfig?.loudnessDb,
                perceptualLoudnessDb = data.audioConfig?.perceptualLoudnessDb,
                playbackUrl = data.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
            ),
        )

        val chunk = when {
            length == null -> 0L
            (data.useRangeChunks || data.requireBoundedRange) && data.rangeChunkSizeBytes > 0 -> data.rangeChunkSizeBytes
            else -> DEFAULT_CHUNK
        }
        val stream = ResolvedStream(data.streamUrl, data.streamHeaders, data.streamClient, length, chunk)
        val ttlMs = (data.streamExpiresInSeconds.coerceAtLeast(60) - 30) * 1000L
        synchronized(entries) {
            // A newer invalidation happened while we resolved: our result is already stale.
            if ((generations[videoId] ?: 0L) == generation) entries[videoId] = Entry(stream, now() + ttlMs)
        }
        Timber.d("resolved %s via %s: %s bytes, chunk %s", videoId, data.streamClient, length, chunk)
        return stream
    }

    private fun probeLength(url: String, headers: Map<String, String>): Long? {
        val request = Request.Builder().url(url).header("Range", "bytes=0-0")
            .apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        return try {
            http.newCall(request).execute().use { r ->
                when (r.code) {
                    206 -> r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    200 -> r.header("Content-Length")?.toLongOrNull()
                    else -> null
                }?.takeIf { it > 0 }
            }
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        /** BitChord measured googlevideo serving 2 MiB ranges at line rate. */
        const val DEFAULT_CHUNK = 2L * 1024 * 1024

        fun clen(url: String): Long? =
            Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
    }
}
