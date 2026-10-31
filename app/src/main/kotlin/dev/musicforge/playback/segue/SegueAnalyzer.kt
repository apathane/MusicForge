package dev.musicforge.playback.segue

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.nio.ByteOrder

/**
 * Measures how loud a stretch of a song is, for Segue's planner. It reads through the same data
 * source the player uses, so cached bytes are free and anything it has to fetch also lands in the
 * player cache for when that part plays. Decoding 30 s on a Snapdragon 680 is a fraction of a
 * second; [timeoutMs] keeps a slow network from holding a blend hostage.
 */
class SegueAnalyzer(private val source: DataSource.Factory, private val timeoutMs: Long = 8_000) {

    /** Envelope for [fromMs]..[toMs] of [item], or null if it couldn't be read in time. */
    suspend fun analyse(item: MediaItem, fromMs: Long, toMs: Long, stepMs: Long = 500): Envelope? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                runCatching { decode(item, fromMs, toMs, stepMs) }
                    .onFailure { Timber.w(it, "segue: analysis failed for %s", item.mediaId) }
                    .getOrNull()
            }
        }

    private fun decode(item: MediaItem, fromMs: Long, toMs: Long, stepMs: Long): Envelope? {
        val config = item.localConfiguration ?: return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(CachedMediaDataSource(source, config.uri, config.customCacheKey ?: item.mediaId))
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            extractor.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).apply {
                configure(format, null, null, 0)
                start()
            }
            return drain(extractor, codec, fromMs * 1000, toMs * 1000, stepMs)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun drain(extractor: MediaExtractor, codec: MediaCodec, fromUs: Long, toUs: Long, stepMs: Long): Envelope? {
        val info = MediaCodec.BufferInfo()
        var meter: RmsMeter? = null
        var firstUs = -1L
        var inputDone = false
        var shorts = ShortArray(0)
        while (true) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    val time = extractor.sampleTime
                    if (size < 0 || time > toUs) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, time, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex < 0) continue
            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            // Pre-roll from the sync point before fromUs is decoded but not measured.
            if (info.size > 0 && info.presentationTimeUs >= fromUs && info.presentationTimeUs <= toUs) {
                val out = codec.outputFormat
                if (meter == null) {
                    meter = RmsMeter(out.getInteger(MediaFormat.KEY_SAMPLE_RATE), out.getInteger(MediaFormat.KEY_CHANNEL_COUNT), stepMs)
                    firstUs = info.presentationTimeUs
                }
                val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                val float = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                val count = if (float) info.size / 4 else info.size / 2
                if (shorts.size < count) shorts = ShortArray(count)
                if (float) {
                    val f = buffer.asFloatBuffer()
                    for (i in 0 until count) shorts[i] = (f.get(i).coerceIn(-1f, 1f) * 32767).toInt().toShort()
                } else {
                    buffer.asShortBuffer().get(shorts, 0, count)
                }
                meter.feed(shorts, 0, count)
            }
            codec.releaseOutputBuffer(outIndex, false)
            if (eos) break
        }
        val m = meter ?: return null
        return Envelope(firstUs / 1000, stepMs, m.result())
    }
}

/** MediaExtractor's random reads, served by a Media3 DataSource (cache first, network after). */
private class CachedMediaDataSource(
    private val factory: DataSource.Factory,
    private val uri: android.net.Uri,
    private val key: String,
) : MediaDataSource() {
    private val length: Long by lazy {
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
