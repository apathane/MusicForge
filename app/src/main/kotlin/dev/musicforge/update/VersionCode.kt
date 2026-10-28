package dev.musicforge.update

/**
 * Converts MusicForge release tags to the monotonic Android versionCode scheme used by Gradle.
 *
 * New preview tags: p4-0.14.0 -> 1404
 * Stable tags:      v0.14.0  -> 1409
 * Patch releases:   v0.14.1  -> 1419
 *
 * Slots 1..8 are preview builds; slot 9 is the completed patch release. Release notes can declare
 * an explicit code and take precedence, which also keeps older release tags compatible.
 */
fun versionCodeOf(tag: String, releaseBody: String? = null): Int? {
    val declaredCode = releaseBody
        ?.let { Regex("""\bcode\s+(\d+)\b""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        ?.toIntOrNull()
    if (declaredCode != null && declaredCode > 0) return declaredCode

    val clean = tag.removePrefix("v")
    val previewTag = Regex("""p([1-8])-0\.(\d+)\.(\d+)""").matchEntire(clean)
    if (previewTag != null) {
        val preview = previewTag.groupValues[1].toInt()
        val phase = previewTag.groupValues[2].toInt()
        val patch = previewTag.groupValues[3].toInt()
        return phase * 100 + patch * 10 + preview
    }

    // Compatibility with existing v0.14.0-preview.3 tags.
    val legacyPreview = Regex("""0\.(\d+)\.(\d+)-preview\.([1-8])""").matchEntire(clean)
    if (legacyPreview != null) {
        val phase = legacyPreview.groupValues[1].toInt()
        val patch = legacyPreview.groupValues[2].toInt()
        val preview = legacyPreview.groupValues[3].toInt()
        return phase * 100 + patch * 10 + preview
    }

    // Stable and named patch releases occupy slot 9, above all previews of that patch.
    val stable = Regex("""0\.(\d+)\.(\d+)(?:-[A-Za-z0-9.-]+)?""").matchEntire(clean)
        ?: return null
    val phase = stable.groupValues[1].toIntOrNull() ?: return null
    val patch = stable.groupValues[2].toIntOrNull() ?: return null
    return phase * 100 + patch * 10 + 9
}
