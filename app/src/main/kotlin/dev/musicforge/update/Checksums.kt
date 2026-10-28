package dev.musicforge.update

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
