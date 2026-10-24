package dev.musicforge.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SniffAudioMimeTest {
    @Test fun webmByEbmlMagic() {
        assertThat(sniffAudioMime(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0, 0, 0, 0))).isEqualTo("audio/webm")
    }

    @Test fun mp4ByFtypBox() {
        assertThat(sniffAudioMime(byteArrayOf(0, 0, 0, 0x20) + "ftypM4A ".toByteArray())).isEqualTo("audio/mp4")
    }

    @Test fun unknownOrShortIsNull() {
        assertThat(sniffAudioMime("ID3\u0003....".toByteArray())).isNull()
        assertThat(sniffAudioMime(byteArrayOf(0x1A))).isNull()
    }
}
