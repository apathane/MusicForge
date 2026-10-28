package dev.musicforge.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import dev.musicforge.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val DAY_MS = 24 * 3600 * 1000L
private const val GITHUB_RELEASES = "https://api.github.com/repos/apathane/MusicForge/releases"

/** DataStore keys this feature owns; kept here so Prefs.kt stays importable-only. */
object UpdateKeys {
    /** Whether prerelease/preview versions are offered. Default off; users opt in. */
    val includePreviews = booleanPreferencesKey("forge.includePreviews")
    /** Epoch ms of the last automatic check, to cap frequency at once per day. */
    val lastUpdateCheck = longPreferencesKey("forge.lastUpdateCheck")
}

/** What the Settings row shows. One state at a time, always on the IO scope. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val versionName: String) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** A release that is newer than the running app and has an installable APK. */
data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val versionCode: Int,
    val apkAssetName: String,
    val apkUrl: String,
    val sha256SumsUrl: String?,
    val notes: String,
)

private data class ParsedRelease(
    val tagName: String,
    val versionCode: Int,
    val isPrerelease: Boolean,
    val name: String,
    val body: String,
    val apkUrl: String,
    val apkName: String,
    val sha256SumsUrl: String?,
)

class UpdateChecker(
    private val context: Context,
    private val settings: DataStore<Preferences>,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    // Preview releases are opt-in, even while the app is in the 0.x phase.
    private fun includePreviewsDefault(): Boolean = false

    /** Called from AppGraph at app start. Checks at most once per day. */
    suspend fun checkOnStart() {
        val now = System.currentTimeMillis()
        val last = settings.data.first()[UpdateKeys.lastUpdateCheck] ?: 0L
        if (now - last < DAY_MS) return
        publishFromFetch()
        // Only successful checks consume the 24-hour window. A network failure can retry next launch.
        if (_state.value !is UpdateState.Failed) {
            settings.edit { it[UpdateKeys.lastUpdateCheck] = System.currentTimeMillis() }
        }
    }

    /** Manual check, always fetches. */
    suspend fun checkNow() = publishFromFetch()

    /** Kick off a manual check from the UI without suspending the caller. */
    fun check() = scope.launch { checkNow() }

    /** Begin downloading and installing [release]. */
    fun install(release: ReleaseInfo) = scope.launch { downloadAndInstall(release) }

    private suspend fun publishFromFetch() {
        _state.value = UpdateState.Checking
        try {
            val includePreviews = settings.data.first()[UpdateKeys.includePreviews]
                ?: includePreviewsDefault()
            val release = withContext(Dispatchers.IO) { fetchLatest(includePreviews) }
            _state.value = release?.let { UpdateState.Available(it) }
                ?: UpdateState.UpToDate(BuildConfig.VERSION_NAME)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "Couldn't check for updates")
            Timber.w(e, "Update check failed")
        }
    }

    private suspend fun fetchLatest(includePreviews: Boolean): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GITHUB_RELEASES)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MusicForge/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub API HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("No response body")
            val text = body.string()
            parseReleases(text)
                .filter { includePreviews || !it.isPrerelease }
                .maxByOrNull { it.versionCode }
                ?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
                ?.let { it.toReleaseInfo() }
        }
    }

    private suspend fun downloadAndInstall(release: ReleaseInfo) {
        try {
            // Old APKs are dead weight once installed; keep only the one being fetched.
            val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
            // The asset name comes from the network: strip any path from it.
            val apkFile = File(dir, File(release.apkAssetName).name)

            _state.value = UpdateState.Downloading(0f)
            downloadApk(release, apkFile)

            // Never install an APK without a matching checksum entry in the release manifest.
            val sumsUrl = release.sha256SumsUrl
            if (sumsUrl == null) {
                apkFile.delete()
                _state.value = UpdateState.Failed("Release has no SHA-256 manifest; refusing to install")
                return
            }
            val expected = parseSha256Sums(fetchText(sumsUrl), apkFile.name)
            if (expected == null) {
                apkFile.delete()
                _state.value = UpdateState.Failed("SHA-256 entry is missing or invalid; refusing to install")
                return
            }
            val actual = sha256Hex(apkFile)
            if (!expected.equals(actual, ignoreCase = true)) {
                apkFile.delete()
                _state.value = UpdateState.Failed("SHA-256 mismatch; download is corrupt or does not match the release manifest")
                return
            }

            // Android 8+: apps must ask the user to grant "install unknown apps" for this package.
            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                _state.value = UpdateState.Failed("Grant install permission, then check for updates and tap install again")
                return
            }

            _state.value = UpdateState.Installing
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            })
            // Leave state as Available so the user can retry if the installer rejects the APK.
            _state.value = UpdateState.Available(release)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "Download or install failed")
            Timber.w(e, "Update install failed")
        }
    }

    private suspend fun downloadApk(release: ReleaseInfo, dest: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(release.apkUrl).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("No response body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        // Whole percents only: one state per 8 KB buffer redraws Settings ~800 times.
                        if (total > 0) {
                            val p = (downloaded * 100 / total).coerceIn(0, 100) / 100f
                            if (p != (_state.value as? UpdateState.Downloading)?.progress) {
                                _state.value = UpdateState.Downloading(p)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url)
            .header("User-Agent", "MusicForge/${BuildConfig.VERSION_NAME}")
            .build()).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            it.body?.string() ?: ""
        }
    }

    private fun parseReleases(json: String): List<ParsedRelease> {
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
                result.add(ParsedRelease(
                    tagName = tag,
                    versionCode = vc,
                    isPrerelease = r.optBoolean("prerelease", false),
                    name = r.optString("name", tag),
                    body = releaseBody,
                    apkUrl = apkUrl,
                    apkName = apkName,
                    sha256SumsUrl = shaUrl,
                ))
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

    private fun sha256Hex(file: File): String = run {
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
}
