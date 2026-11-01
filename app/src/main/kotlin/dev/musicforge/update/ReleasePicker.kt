package dev.musicforge.update

import org.json.JSONArray

/** The release list from GitHub, reduced to what the updater needs. */
internal data class ParsedRelease(
    val tagName: String,
    val versionCode: Int,
    val isPrerelease: Boolean,
    val name: String,
    val body: String,
    val apkUrl: String,
    val apkName: String,
    val sha256SumsUrl: String?,
)

/**
 * The one place that decides what the updater may offer: newer than the running build, with an
 * installable APK, not drafted, not skipped by the user, and not a preview unless previews are on.
 * Returns null when nothing qualifies. Pure, so the rules have tests.
 */
internal fun pickNewerRelease(
    json: String,
    includePreviews: Boolean,
    currentVersionCode: Int,
): ReleaseInfo? =
    parseReleases(json)
        .filter { includePreviews || !it.isPrerelease }
        // Stable beats preview at the same phase, which is the only fair fight in this codebase.
        .maxByOrNull { it.versionCode }
        ?.takeIf { it.versionCode > currentVersionCode }
        ?.toReleaseInfo()

internal fun parseReleases(json: String): List<ParsedRelease> {
    val arr = JSONArray(json)
    val result = mutableListOf<ParsedRelease>()
    for (i in 0 until arr.length()) {
        val r = arr.getJSONObject(i)
        if (r.optBoolean("draft", false)) continue
        val tag = r.getString("tag_name")
        val releaseBody = r.optString("body", "")
        val vc = versionCodeOf(tag, releaseBody) ?: continue
        val assets = r.optJSONArray("assets") ?: JSONArray()
        var apkUrl: String? = null
        var apkName: String? = null
        var shaUrl: String? = null
        for (j in 0 until assets.length()) {
            val a = assets.getJSONObject(j)
            val name = a.getString("name")
            val url = a.getString("browser_download_url")
            when {
                name.endsWith(".apk") -> { apkUrl = url; apkName = name }
                name == "SHA256SUMS.txt" || name == "SHA256SUMS" -> shaUrl = url
            }
        }
        if (apkUrl != null && apkName != null) {
            result.add(
                ParsedRelease(
                    tagName = tag,
                    versionCode = vc,
                    isPrerelease = r.optBoolean("prerelease", false),
                    name = r.optString("name", tag),
                    body = releaseBody,
                    apkUrl = apkUrl,
                    apkName = apkName,
                    sha256SumsUrl = shaUrl,
                ),
            )
        }
    }
    return result
}

private fun ParsedRelease.toReleaseInfo() = ReleaseInfo(
    tagName = tagName,
    versionName = name,
    versionCode = versionCode,
    apkAssetName = apkName,
    apkUrl = apkUrl,
    sha256SumsUrl = sha256SumsUrl,
    notes = body,
)
