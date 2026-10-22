package dev.musicforge.download

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Writes Vorbis-comment tags (TITLE, ARTIST, ALBUM...) into an Ogg/Opus file.
 *
 * MediaMuxer writes an empty OpusTags header, and Android's media scanner reads tags from the
 * file, so without this an exported song shows up as "Artist - Title" by <unknown>. Only the
 * second Ogg page (the OpusTags packet) is rebuilt, with its CRC; audio pages are untouched.
 * If the file isn't laid out as expected, it's left as is.
 */
object OggTags {
    fun write(file: File, tags: Map<String, String>): Boolean {
        val bytes = file.readBytes()
        val pages = pages(bytes) ?: return false
        if (pages.size < 2) return false
        val tagPage = pages[1]
        val payload = bytes.copyOfRange(tagPage.dataStart, tagPage.end)
        // Expect exactly one complete packet on this page: "OpusTags"...
        if (tagPage.segments.last() == 255 || !payload.startsWith("OpusTags")) return false

        val packet = opusTags(vendor(payload), tags)
        if (packet.size > 255 * 255 - 1) return false
        val rebuilt = page(bytes, tagPage, packet)
        val out = ByteArrayOutputStream(bytes.size + rebuilt.size)
        out.write(bytes, 0, tagPage.start)
        out.write(rebuilt)
        out.write(bytes, tagPage.end, bytes.size - tagPage.end)
        file.writeBytes(out.toByteArray())
        return true
    }

    private class Page(val start: Int, val dataStart: Int, val end: Int, val segments: IntArray)

    private fun pages(b: ByteArray): List<Page>? {
        val list = mutableListOf<Page>()
        var p = 0
        while (p + 27 <= b.size) {
            if (b[p] != 'O'.code.toByte() || b[p + 1] != 'g'.code.toByte() || b[p + 2] != 'g'.code.toByte() || b[p + 3] != 'S'.code.toByte()) return null
            val n = b[p + 26].toInt() and 0xff
            if (p + 27 + n > b.size) return null
            val segs = IntArray(n) { b[p + 27 + it].toInt() and 0xff }
            val dataStart = p + 27 + n
            val end = dataStart + segs.sum()
            if (end > b.size) return null
            list += Page(p, dataStart, end, segs)
            p = end
            if (list.size >= 3) break // we only need the header pages
        }
        return list
    }

    private fun ByteArray.startsWith(s: String) = size >= s.length && s.indices.all { this[it] == s[it].code.toByte() }

    private fun le32(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8) or ((b[at + 2].toInt() and 0xff) shl 16) or ((b[at + 3].toInt() and 0xff) shl 24)

    private fun vendor(payload: ByteArray): ByteArray {
        val len = le32(payload, 8)
        return payload.copyOfRange(12, 12 + len)
    }

    private fun ByteArrayOutputStream.le32(v: Int) {
        write(v and 0xff); write((v ushr 8) and 0xff); write((v ushr 16) and 0xff); write((v ushr 24) and 0xff)
    }

    private fun opusTags(vendor: ByteArray, tags: Map<String, String>): ByteArray {
        val o = ByteArrayOutputStream()
        o.write("OpusTags".toByteArray())
        o.le32(vendor.size); o.write(vendor)
        val comments = tags.filterValues { it.isNotBlank() }.map { (k, v) -> "${k.uppercase()}=$v".toByteArray(Charsets.UTF_8) }
        o.le32(comments.size)
        comments.forEach { o.le32(it.size); o.write(it) }
        return o.toByteArray()
    }

    /** Same header fields as the original page (version, flags, granule, serial, sequence). */
    private fun page(orig: ByteArray, at: Page, packet: ByteArray): ByteArray {
        val lacing = ByteArrayOutputStream()
        var left = packet.size
        while (left >= 255) { lacing.write(255); left -= 255 }
        lacing.write(left) // a packet ending on a 255 boundary gets a terminating 0
        val segs = lacing.toByteArray()
        val o = ByteArrayOutputStream()
        o.write(orig, at.start, 22) // "OggS", version, type, granule, serial, sequence
        o.write(byteArrayOf(0, 0, 0, 0)) // CRC placeholder
        o.write(segs.size)
        o.write(segs)
        o.write(packet)
        val page = o.toByteArray()
        val crc = crc(page)
        page[22] = (crc and 0xff).toByte()
        page[23] = ((crc ushr 8) and 0xff).toByte()
        page[24] = ((crc ushr 16) and 0xff).toByte()
        page[25] = ((crc ushr 24) and 0xff).toByte()
        return page
    }

    private val table = IntArray(256) { i ->
        var r = i shl 24
        repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
        r
    }

    /** Ogg's CRC-32: polynomial 0x04C11DB7, no reflection, init 0, no final xor. */
    fun crc(data: ByteArray): Int {
        var c = 0
        for (b in data) c = (c shl 8) xor table[((c ushr 24) xor (b.toInt() and 0xff)) and 0xff]
        return c
    }
}
