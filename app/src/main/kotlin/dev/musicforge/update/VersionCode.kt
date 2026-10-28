package dev.musicforge.update

/**
 * Parses a MusicForge release tag into the comparable versionCode (phase * 100 + build),
 * mirroring the scheme in app/build.gradle.kts.
 *
 *   v0.14.0-preview.4        -> 1404  (preview build overrides the release build number)
 *   v0.13.1-little-bug-boy   -> 1301  (non-preview suffix keeps the tag's build)
 *   v0.14.0                  -> 1400 when no explicit build code is published
 *
 * Only 0.x tags are recognised; a future 1.0 release uses a different scheme (versionCode)
 * and null is returned for anything that doesn't fit, so it's simply ignored.
 */
fun versionCodeOf(tag: String, releaseBody: String? = null): Int? {
    // Published release notes include the exact Android versionCode. This is required for a
    // final 0.14.0 release to supersede previews (which already use 1401+), while preserving
    // tag-only compatibility for older releases.
    val declaredCode = releaseBody
        ?.let { Regex("""\bcode\s+(\d+)\b""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        ?.toIntOrNull()
    if (declaredCode != null && declaredCode > 0) return declaredCode
    val clean = tag.removePrefix("v")
    val firstDot = clean.indexOf('.')
    val secondDot = clean.indexOf('.', firstDot + 1)
    if (firstDot < 0 || secondDot < 0) return null
    // Major must be 0 for phase*100+build to apply.
    if (clean.substring(0, firstDot) != "0") return null
    val phase = clean.substring(firstDot + 1, secondDot).toIntOrNull() ?: return null
    val rest = clean.substring(secondDot + 1) // "0-preview.3" or "1-little-bug-boy" or "0"
    val dash = rest.indexOf('-')
    val buildPart = if (dash < 0) rest else rest.substring(0, dash)
    val baseBuild = buildPart.toIntOrNull() ?: return null
    val suffix = if (dash < 0) "" else rest.substring(dash + 1)
    if (suffix.startsWith("preview.")) {
        // A preview suffix takes precedence over the release build number, so
        // v0.14.0-preview.3 -> 1403, not 1400.
        val previewBuild = suffix.removePrefix("preview.").takeIf { it.all(Char::isDigit) }?.toIntOrNull()
        if (previewBuild != null) return phase * 100 + previewBuild
    }
    return phase * 100 + baseBuild
}
