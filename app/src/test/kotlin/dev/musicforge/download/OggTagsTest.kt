package dev.musicforge.download

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OggTagsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun crcMatchesKnownOggValue() {
        // Ogg's CRC: poly 0x04C11DB7, init 0, no reflection, no final xor. Its check value for
        // "123456789" is 0x89A1897F (the complement of CRC-32/POSIX's 0x765E7680). Also verified
        // against five pages written by Android's own Ogg muxer.
        assertThat(OggTags.crc("123456789".toByteArray())).isEqualTo(0x89A1897F.toInt())
    }

    @Test fun rewritesTagPageAndKeepsAudioPages() {
        val file = tmp.newFile("t.ogg")
        val head = page(seq = 0, payload = "OpusHead".toByteArray() + ByteArray(11))
        val tags = page(seq = 1, payload = "OpusTags".toByteArray() + le32(4) + "test".toByteArray() + le32(0))
        val audio = page(seq = 2, payload = ByteArray(300) { it.toByte() })
        file.writeBytes(head + tags + audio)

        assertThat(OggTags.write(file, mapOf("TITLE" to "Roads", "ARTIST" to "Portishead"))).isTrue()
        val out = file.readBytes()
        val text = String(out, Charsets.ISO_8859_1)
        assertThat(text).contains("TITLE=Roads")
        assertThat(text).contains("ARTIST=Portishead")
        assertThat(text).contains("test") // vendor kept
        // The audio page is byte-for-byte unchanged at the end of the file.
        assertThat(out.copyOfRange(out.size - audio.size, out.size)).isEqualTo(audio)
        // The rebuilt tag page carries a valid CRC.
        val tagStart = head.size
        val n = out[tagStart + 26].toInt() and 0xff
        val len = (0 until n).sumOf { out[tagStart + 27 + it].toInt() and 0xff }
        val page = out.copyOfRange(tagStart, tagStart + 27 + n + len)
        val stored = (page[22].toInt() and 0xff) or ((page[23].toInt() and 0xff) shl 8) or ((page[24].toInt() and 0xff) shl 16) or ((page[25].toInt() and 0xff) shl 24)
        page[22] = 0; page[23] = 0; page[24] = 0; page[25] = 0
        assertThat(OggTags.crc(page)).isEqualTo(stored)
    }

    @Test fun leavesUnexpectedFilesAlone() {
        val file = tmp.newFile("x.ogg")
        file.writeBytes("not an ogg file".toByteArray())
        assertThat(OggTags.write(file, mapOf("TITLE" to "x"))).isFalse()
        assertThat(String(file.readBytes())).isEqualTo("not an ogg file")
    }

    @Test fun coverSizedTagStillFitsOnePage() {
        val file = tmp.newFile("c.ogg")
        val head = page(seq = 0, payload = "OpusHead".toByteArray() + ByteArray(11))
        val tags = page(seq = 1, payload = "OpusTags".toByteArray() + le32(4) + "test".toByteArray() + le32(0))
        val audio = page(seq = 2, payload = ByteArray(300) { it.toByte() })
        file.writeBytes(head + tags + audio)
        val picture = "A".repeat(55_000) // what a base64 cover inside the export budget looks like

        assertThat(OggTags.write(file, mapOf("TITLE" to "Roads", "METADATA_BLOCK_PICTURE" to picture))).isTrue()
        val out = file.readBytes()
        assertThat(out.copyOfRange(out.size - audio.size, out.size)).isEqualTo(audio)
        val n = out[head.size + 26].toInt() and 0xff
        assertThat(n).isAtMost(255)
    }

    @Test fun refusesTagsTooBigForOnePage() {
        val file = tmp.newFile("big.ogg")
        val head = page(seq = 0, payload = "OpusHead".toByteArray() + ByteArray(11))
        val tags = page(seq = 1, payload = "OpusTags".toByteArray() + le32(4) + "test".toByteArray() + le32(0))
        val original = head + tags
        file.writeBytes(original)
        assertThat(OggTags.write(file, mapOf("METADATA_BLOCK_PICTURE" to "A".repeat(70_000)))).isFalse()
        assertThat(file.readBytes()).isEqualTo(original)
    }

    private fun le32(v: Int) = byteArrayOf((v and 0xff).toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())

    private fun page(seq: Int, payload: ByteArray): ByteArray {
        val segs = mutableListOf<Int>()
        var left = payload.size
        while (left >= 255) { segs += 255; left -= 255 }
        segs += left
        val h = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(), 0, if (seq == 0) 2 else 0) +
            ByteArray(8) + le32(0x1234) + le32(seq) + ByteArray(4) + byteArrayOf(segs.size.toByte()) + segs.map { it.toByte() }.toByteArray()
        return h + payload
    }
}
