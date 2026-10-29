package dev.musicforge.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/** Attached to a resolved [DataSpec] as `customData` by the resolving layer. */
data class ChunkPolicy(val videoId: String, val totalLength: Long, val chunkBytes: Long)

/**
 * Reads a stream as a run of bounded HTTP ranges instead of one open-ended request.
 *
 * googlevideo paces an unbounded response to roughly playback speed; bounded ranges are
 * served at line rate (BitChord measured ~15 kB/s vs ~5.7 MB/s on the same track). Callers
 * see one continuous stream of the length they asked for, starting at any position, so
 * Media3's cache and DownloadManager resume work unchanged: a resumed download simply
 * opens at the first uncached byte and the ranges are cut from there.
 *
 * Without a [ChunkPolicy] the request is forwarded untouched.
 */
@UnstableApi
class ChunkedDataSource(
    private val upstream: DataSource,
    private val onRefused: (videoId: String, httpCode: Int) -> Unit,
) : DataSource {
    private var base: DataSpec? = null
    private var policy: ChunkPolicy? = null
    private var position = 0L
    private var remaining = 0L
    private var chunkRemaining = 0L
    private var chunkOpen = false
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val p = dataSpec.customData as? ChunkPolicy
        if (p == null || p.chunkBytes <= 0 || p.totalLength <= 0) {
            passthrough = true
            chunkOpen = true
            return upstream.open(dataSpec)
        }
        passthrough = false
        base = dataSpec
        policy = p
        position = dataSpec.position
        val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) p.totalLength else minOf(p.totalLength, position + dataSpec.length)
        remaining = (end - position).coerceAtLeast(0)
        if (remaining > 0) openChunk()
        return remaining
    }

    private fun openChunk() {
        val p = requireNotNull(policy)
        val length = minOf(p.chunkBytes, remaining)
        val spec = requireNotNull(base).buildUpon().setPosition(position).setLength(length).build()
        try {
            upstream.open(spec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            onRefused(p.videoId, e.responseCode)
            throw e
        }
        chunkRemaining = length
        chunkOpen = true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        // A range that ends early is re-opened for the missing part; that's also how we
        // step to the next range. The attempt cap stops a server that sends nothing.
        // A short backoff between retries gives a stalling server time to recover
        // without blocking the whole pipeline (the Adreno 610's load thread is shared).
        repeat(MAX_EMPTY_RANGES) {
            if (chunkRemaining == 0L) {
                closeChunk()
                openChunk()
            }
            val n = upstream.read(buffer, offset, minOf(length.toLong(), chunkRemaining).toInt())
            if (n != C.RESULT_END_OF_INPUT) {
                position += n
                chunkRemaining -= n
                remaining -= n
                return n
            }
            chunkRemaining = 0
            if (it < MAX_EMPTY_RANGES - 1) Thread.sleep(EMPTY_RANGE_BACKOFF_MS)
        }
        // Never report a short stream as complete: a truncated file in the cache is worse
        // than an error, which Media3 retries from the current position.
        throw java.io.IOException("stream ended $remaining bytes early at $position")
    }

    private fun closeChunk() {
        if (chunkOpen) {
            try {
                upstream.close()
            } finally {
                chunkOpen = false
            }
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: base?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        closeChunk()
        base = null
        policy = null
        remaining = 0
        chunkRemaining = 0
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val onRefused: (String, Int) -> Unit,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource(), onRefused)
    }

    private companion object {
        const val MAX_EMPTY_RANGES = 3
        /** Brief backoff between empty-range retries: gives a stalling server time to recover. */
        const val EMPTY_RANGE_BACKOFF_MS = 150L
    }
}
