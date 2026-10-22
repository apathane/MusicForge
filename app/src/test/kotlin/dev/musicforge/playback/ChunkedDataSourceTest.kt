package dev.musicforge.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class ChunkedDataSourceTest {
    private val data = ByteArray(10_000) { (it * 31 % 251).toByte() }

    /** Serves [data] for bounded ranges, records every range it was asked for. */
    private inner class FakeServer(
        private val shortReadAt: Long? = null,
        private val refuseFrom: Long? = null,
    ) : DataSource {
        val ranges = mutableListOf<LongRange>()
        private var pos = 0L
        private var end = 0L
        private var truncatedOnce = false

        override fun open(dataSpec: DataSpec): Long {
            require(dataSpec.length != C.LENGTH_UNSET.toLong()) { "unbounded request reached the server" }
            if (refuseFrom != null && dataSpec.position >= refuseFrom) {
                throw HttpDataSource.InvalidResponseCodeException(403, null, null, emptyMap(), dataSpec, ByteArray(0))
            }
            pos = dataSpec.position
            end = dataSpec.position + dataSpec.length
            ranges += pos until end
            // Simulate a connection that drops early once, mid-range.
            if (shortReadAt != null && !truncatedOnce && shortReadAt in pos until end) {
                end = shortReadAt
                truncatedOnce = true
            }
            return end - pos
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (pos >= end) return C.RESULT_END_OF_INPUT
            val n = minOf(length.toLong(), end - pos).toInt()
            System.arraycopy(data, pos.toInt(), buffer, offset, n)
            pos += n
            return n
        }

        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun getUri(): Uri? = null
        override fun close() = Unit
    }

    private fun readAll(source: DataSource, spec: DataSpec): ByteArray {
        source.open(spec)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(777)
        while (true) {
            val n = source.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buf, 0, n)
        }
        source.close()
        return out.toByteArray()
    }

    private fun spec(position: Long = 0, length: Long = C.LENGTH_UNSET.toLong(), chunk: Long = 4096) =
        DataSpec.Builder()
            .setUri("https://example.invalid/videoplayback")
            .setPosition(position)
            .setLength(length)
            .setKey("vid")
            .setCustomData(ChunkPolicy("vid", data.size.toLong(), chunk))
            .build()

    @Test fun readsWholeStreamInBoundedRanges() {
        val server = FakeServer()
        val bytes = readAll(ChunkedDataSource(server) { _, _ -> }, spec())
        assertThat(bytes).isEqualTo(data)
        assertThat(server.ranges).containsExactly(0L until 4096L, 4096L until 8192L, 8192L until 10_000L).inOrder()
    }

    @Test fun resumesFromAnyOffset() {
        // What DownloadManager does after a process kill: reopen at the first uncached byte.
        val server = FakeServer()
        val bytes = readAll(ChunkedDataSource(server) { _, _ -> }, spec(position = 5_000))
        assertThat(bytes).isEqualTo(data.copyOfRange(5_000, 10_000))
        assertThat(server.ranges.first()).isEqualTo(5_000L until 9_096L)
    }

    @Test fun honoursBoundedRequests() {
        val server = FakeServer()
        val bytes = readAll(ChunkedDataSource(server) { _, _ -> }, spec(position = 100, length = 5_000))
        assertThat(bytes).isEqualTo(data.copyOfRange(100, 5_100))
        assertThat(server.ranges.last().last).isEqualTo(5_099L)
    }

    @Test fun recoversFromShortRead() {
        val server = FakeServer(shortReadAt = 3_000)
        val bytes = readAll(ChunkedDataSource(server) { _, _ -> }, spec())
        assertThat(bytes).isEqualTo(data)
        // The next range starts exactly where the truncated one stopped.
        assertThat(server.ranges.map { it.first }).contains(3_000L)
    }

    @Test fun reportsRefusalWithVideoId() {
        val refused = mutableListOf<Pair<String, Int>>()
        val source = ChunkedDataSource(FakeServer(refuseFrom = 4096)) { id, code -> refused += id to code }
        source.open(spec())
        val buf = ByteArray(8192)
        var thrown: Throwable? = null
        try {
            while (source.read(buf, 0, buf.size) != C.RESULT_END_OF_INPUT) Unit
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            thrown = e
        }
        assertThat(thrown).isNotNull()
        assertThat(refused).containsExactly("vid" to 403)
    }

    @Test fun passesThroughWithoutPolicy() {
        val passthrough = object : DataSource {
            var opened: DataSpec? = null
            override fun open(dataSpec: DataSpec): Long { opened = dataSpec; return C.LENGTH_UNSET.toLong() }
            override fun read(buffer: ByteArray, offset: Int, length: Int) = C.RESULT_END_OF_INPUT
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun getUri(): Uri? = null
            override fun close() = Unit
        }
        val source = ChunkedDataSource(passthrough) { _, _ -> }
        val plain = DataSpec.Builder().setUri("https://example.invalid/x").build()
        source.open(plain)
        assertThat(passthrough.opened).isSameInstanceAs(plain)
    }
}
