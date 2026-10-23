package dev.musicforge.download

import com.google.common.truth.Truth.assertThat
import dev.musicforge.data.lyrics.Lrc
import dev.musicforge.data.lyrics.Lyrics
import org.junit.Test
import java.nio.ByteBuffer

class CoversTest {
    @Test fun pictureBlockFollowsFlacLayout() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        val b = ByteBuffer.wrap(Covers.pictureBlock(jpeg, "image/jpeg", 500, 500)) // big-endian
        assertThat(b.int).isEqualTo(3) // front cover
        val mime = ByteArray(b.int).also { b.get(it) }
        assertThat(String(mime)).isEqualTo("image/jpeg")
        assertThat(b.int).isEqualTo(0) // description length
        assertThat(b.int).isEqualTo(500)
        assertThat(b.int).isEqualTo(500)
        assertThat(b.int).isEqualTo(24)
        assertThat(b.int).isEqualTo(0)
        val data = ByteArray(b.int).also { b.get(it) }
        assertThat(data).isEqualTo(jpeg)
        assertThat(b.hasRemaining()).isFalse()
    }

    @Test fun lrcRoundTrips() {
        val lines = listOf(Lyrics.Line(0, "Intro"), Lyrics.Line(12_340, "Did you know"), Lyrics.Line(61_005, "Roads"))
        val text = Lrc.format(lines)
        assertThat(text).isEqualTo("[00:00.00]Intro\n[00:12.34]Did you know\n[01:01.00]Roads")
        assertThat(Lrc.parse(text)).isEqualTo(listOf(Lyrics.Line(0, "Intro"), Lyrics.Line(12_340, "Did you know"), Lyrics.Line(61_000, "Roads")))
    }
}
