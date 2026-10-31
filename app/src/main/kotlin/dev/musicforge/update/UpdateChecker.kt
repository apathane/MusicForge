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
import androidx.datastore.preferences.core.stringPreferencesKey
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
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val DAY_MS = 24 * 3600 * 1000L
private const val GITHUB_RELEASES = "https://api.github.com/repos/apathane/MusicForge/releases"

/** DataStore keys this feature owns; kept here so Prefs.kt stays importable-only. */
object UpdateKeys {
    /** Whether prerelease/preview versions are offered. Default off; users opt in. */
    val includePreviews = booleanPreferencesKey("forge.includePreviews")
    /** Epoch ms of the last automatic check, to cap frequency at once per day. */
    val lastUpdateCheck = longPreferencesKey("forge.lastUpdateCheck")
    /** Tag the user chose to skip. That release is never offered again; a newer one is. */
    val skippedRelease = stringPreferencesKey("forge.skippedRelease")
}

/** What the Settings row shows. One state at a time, always on the IO scope. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val versionName: String) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    /** The user skipped this release. Shown so the choice is visible and reversible. */
    data class Skipped(val release: ReleaseInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** GitHub's unauthenticated quota is spent. Not an error in the app: just wait it out. */
internal class RateLimited : IOException("GitHub is limiting update checks")

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

    /** Skip a release: it stops being offered, and a newer one still is. */
    fun skip(release: ReleaseInfo) = scope.launch {
        settings.edit { it[UpdateKeys.skippedRelease] = release.tagName }
        _state.value = UpdateState.Skipped(release)
    }

    /** Undo a skip and look again, so the skipped release comes back as Available. */
    fun unskip() = scope.launch {
        settings.edit { it.remove(UpdateKeys.skippedRelease) }
        checkNow()
    }

    private suspend fun publishFromFetch() {
        _state.value = UpdateState.Checking
        try {
            val prefs = settings.data.first()
            val includePreviews = prefs[UpdateKeys.includePreviews] ?: includePreviewsDefault()
            val skipped = prefs[UpdateKeys.skippedRelease]
            val release = withContext(Dispatchers.IO) { fetchLatest(includePreviews) }
            _state.value = when {
                release == null -> UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                release.tagName == skipped -> UpdateState.Skipped(release)
                else -> UpdateState.Available(release)
            }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(friendlyMessage(e))
            Timber.w(e, "Update check failed")
        }
    }

    /** Says what went wrong in words a user can act on: no network, or GitHub asking us to wait. */
    private fun friendlyMessage(e: Exception): String = when (e) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "No connection. Couldn't check for updates."
        is RateLimited -> "GitHub is limiting update checks for now. Try again later."
        else -> e.message ?: "Couldn't check for updates"
    }

    private suspend fun fetchLatest(includePreviews: Boolean): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GITHUB_RELEASES)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MusicForge/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(request).execute().use { response ->
            // GitHub answers 403 (or 429) with an empty quota when the unauthenticated limit is spent.
            if (response.code == 429 || (response.code == 403 && response.header("X-RateLimit-Remaining") == "0")) {
                throw RateLimited()
            }
            if (!response.isSuccessful) {
                throw IOException("GitHub API HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("No response body")
            pickNewerRelease(body.string(), includePreviews, BuildConfig.VERSION_CODE)
        }
    }

    private suspend fun downloadAndInstall(release: ReleaseInfo) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // The asset name comes from the network: strip any path from it.
            val apkName = File(release.apkAssetName).name
            val apkFile = File(dir, apkName)

            // Reuse a cached APK that still verifies; otherwise fall through and re-fetch.
            // A cached copy that fails verification is overwritten by downloadApk below.
            if (isCachedApkValid(apkFile, release)) {
                proceedToInstall(apkFile, release)
                return
            }

            // Delete only other APKs: keep the one we're fetching so a retry after the
            // permission screen skips a second download.
            dir.listFiles()?.filter { it.name != apkName }?.forEach { it.delete() }

            _state.value = UpdateState.Downloading(0f)
            downloadApk(release, apkFile)

            // Fail-closed: never hand an unsigned/mismatched APK to the installer.
            val expected = expectedChecksum(release, apkName) ?: run {
                apkFile.delete()
                _state.value = UpdateState.Failed("SHA-256 manifest missing or entry not found; refusing to install")
                return
            }
            if (!shouldReuseCachedApk(apkFile, expected)) {
                apkFile.delete()
                _state.value = UpdateState.Failed("SHA-256 mismatch; download is corrupt or does not match the release manifest")
                return
            }

            proceedToInstall(apkFile, release)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "Download or install failed")
            Timber.w(e, "Update install failed")
        }
    }

    /** Fetches the release manifest and resolves the expected SHA-256 for [apkName], or null if absent. */
    private suspend fun expectedChecksum(release: ReleaseInfo, apkName: String): String? = withContext(Dispatchers.IO) {
        val sumsUrl = release.sha256SumsUrl ?: return@withContext null
        parseSha256Sums(fetchText(sumsUrl), apkName)
    }

    /**
     * Reuse decision over cached bytes: true when the APK exists on disk and its SHA-256
     * matches the release manifest, so the download can be skipped.
     */
    private suspend fun isCachedApkValid(apkFile: File, release: ReleaseInfo): Boolean = withContext(Dispatchers.IO) {
        val expected = expectedChecksum(release, apkFile.name) ?: return@withContext false
        shouldReuseCachedApk(apkFile, expected)
    }

    /**
     * Shared verification and install handoff logic.
     * Why separate: keeps the retry path (cached APK) and the fresh-download path identical.
     */
    private fun proceedToInstall(apkFile: File, release: ReleaseInfo) {
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

}
