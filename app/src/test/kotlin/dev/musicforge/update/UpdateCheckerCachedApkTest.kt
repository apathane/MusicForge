package dev.musicforge.update

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Unit tests for the pure reuse-or-redownload decision ([shouldReuseCachedApk]). */
class UpdateCheckerCachedApkTest {

    @get:Rule val temp = TemporaryFolder()

    private fun writeApk(name: String, bytes: ByteArray): File =
        temp.newFile(name).apply { FileOutputStream(this).use { it.write(bytes) } }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun reusesApkWhenChecksumMatches() {
        val bytes = "apk-payload".toByteArray()
        val apk = writeApk("MusicForge-0.15.0.apk", bytes)

        assertThat(shouldReuseCachedApk(apk, sha256(bytes))).isTrue()
    }

    @Test fun redownloadsWhenChecksumDiffers() {
        val apk = writeApk("MusicForge-0.15.0.apk", "apk-payload".toByteArray())

        assertThat(shouldReuseCachedApk(apk, "f".repeat(64))).isFalse()
    }

    @Test fun redownloadsWhenApkMissing() {
        // A file that was never written — the stale/missing case the caller must re-fetch.
        val missing = File(temp.root, "MusicForge-0.15.0.apk")

        assertThat(shouldReuseCachedApk(missing, sha256("apk-payload".toByteArray()))).isFalse()
    }
}
