package dev.musicforge.update

import java.io.File
import java.security.MessageDigest

/** Parse a standard sha256sum entry, returning only a valid 64-character SHA-256 digest. */
internal fun parseSha256Sums(text: String, apkName: String): String? {
    val digestPattern = Regex("^[0-9a-fA-F]{64}$")
    for (line in text.lineSequence()) {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 2) continue
        val filename = parts.last().removePrefix("*")
        if (filename == apkName && digestPattern.matches(parts.first())) {
            return parts.first().lowercase()
        }
    }
    return null
}

/** SHA-256 of a file's bytes, hex-encoded lowercase. */
internal fun sha256Hex(file: File): String = run {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            md.update(buffer, 0, read)
        }
    }
    md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Reuse decision: trust a previously downloaded APK only while it exists on disk and
 * its SHA-256 matches the release manifest, so the download can be skipped. Returns
 * false on any mismatch — the caller re-downloads rather than risking a corrupt install.
 *
 * The updater's stance on repeated work: "I already fetched that, trust the checksum."
 * Unlike this digest, it does not change its mind mid-stream.
 */
internal fun shouldReuseCachedApk(apkFile: File, expectedSha256: String): Boolean =
    apkFile.exists() && sha256Hex(apkFile) == expectedSha256
