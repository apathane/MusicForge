package dev.musicforge.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChecksumsTest {
    private val digest = "0123456789abcdef".repeat(4)

    @Test fun parsesMatchingFilename() =
        assertThat(parseSha256Sums("$digest  MusicForge.apk", "MusicForge.apk")).isEqualTo(digest)

    @Test fun supportsBinaryChecksumFormat() =
        assertThat(parseSha256Sums("$digest *MusicForge.apk", "MusicForge.apk")).isEqualTo(digest)

    @Test fun ignoresOtherFiles() =
        assertThat(parseSha256Sums("$digest  other.apk", "MusicForge.apk")).isNull()

    @Test fun rejectsMalformedDigest() =
        assertThat(parseSha256Sums("not-a-hash  MusicForge.apk", "MusicForge.apk")).isNull()

    @Test fun rejectsMissingFilename() =
        assertThat(parseSha256Sums(digest, "MusicForge.apk")).isNull()
}
