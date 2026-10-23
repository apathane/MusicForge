package dev.musicforge.playback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import dev.musicforge.data.prefs.Keys
import dev.musicforge.extraction.InnerTubeXPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Credentials
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale

/**
 * Keeps the YouTube client in step with settings: locale, account cookie, visitor data,
 * proxy. Visitor data is fetched once and persisted, as YouTube expects a stable value.
 */
class YouTubeSession(
    private val context: Context,
    private val settings: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    fun start() {
        InnerTubeXPlayer.initialize(context)
        scope.launch {
            val prefs = settings.data.first()
            applyProxy(prefs)
            applyLocale(prefs)
            observe()
            // Warm the cipher and player config off the first-play path, once visitor data exists.
            launch {
                var waited = 0
                while (YouTube.visitorData == null && waited < 12_000) {
                    delay(250)
                    waited += 250
                }
                runCatching { InnerTubeXPlayer.prewarm() }
                    .onFailure { Timber.w(it, "extraction prewarm failed") }
            }
        }
    }

    private fun observe() {
        scope.launch {
            settings.data.map { it[Keys.VisitorData] }.distinctUntilChanged().collect { stored ->
                YouTube.visitorData = stored?.takeIf { it.isNotBlank() && it != "null" }
                    ?: YouTube.visitorData().getOrNull()?.also { fresh ->
                        settings.edit { it[Keys.VisitorData] = fresh }
                    }
            }
        }
        scope.launch {
            settings.data.map { it[Keys.DataSyncId] }.distinctUntilChanged().collect { id ->
                // Upstreams store "channel||account"; the API wants the account half.
                YouTube.dataSyncId = id?.let {
                    when {
                        "||" !in it -> it
                        it.endsWith("||") -> it.substringBefore("||")
                        else -> it.substringAfter("||")
                    }
                }
            }
        }
        scope.launch {
            settings.data.map { it[Keys.InnerTubeAuthUser] ?: "0" }.distinctUntilChanged().collect { YouTube.authUser = it }
        }
        scope.launch {
            settings.data.map { it[Keys.InnerTubeCookie] }.distinctUntilChanged().collect { cookie ->
                runCatching { YouTube.cookie = cookie?.takeIf { it.isNotBlank() } }
                    .onFailure {
                        Timber.e(it, "unparseable cookie, signing out")
                        settings.edit { p -> p.remove(Keys.InnerTubeCookie) }
                    }
            }
        }
        scope.launch {
            settings.data.map { it[Keys.ContentCountry] to it[Keys.ContentLanguage] }.distinctUntilChanged().collect {
                applyLocale(settings.data.first())
            }
        }
    }

    private fun applyLocale(prefs: Preferences) {
        val system = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = prefs[Keys.ContentCountry]?.takeIf { it.length == 2 } ?: system.country.takeIf { it.length == 2 } ?: "US",
            hl = youTubeLanguage(prefs[Keys.ContentLanguage]?.takeIf { it != "system" }, system),
        )
    }

    companion object {
        /**
         * YouTube answers HTTP 400 ("invalid argument") to every request whose `hl` it doesn't
         * know, and phones send tags like `en-ZA` (found on the X7b: home, search, playback all
         * failed). Only send a code from YouTube's own list, falling back to the bare language.
         */
        fun youTubeLanguage(chosen: String?, system: Locale): String =
            listOfNotNull(chosen, system.toLanguageTag(), system.language).firstOrNull { it in SUPPORTED_HL } ?: "en"

        /** YouTube's interface languages (the list Metrolist ships, checked 2026-10-23). */
        val SUPPORTED_HL = setOf(
            "af", "az", "id", "ms", "ca", "cs", "da", "de", "et", "en-GB", "en", "es", "eu", "fil", "fr", "fr-CA",
            "gl", "hr", "zu", "is", "it", "sw", "lt", "hu", "nl", "no", "or", "uz", "pl", "pt-PT", "pt", "ro", "sq",
            "sk", "sl", "fi", "sv", "bo", "vi", "tr", "bg", "ky", "kk", "mk", "mn", "ru", "sr", "uk", "el", "hy",
            "iw", "ur", "ar", "fa", "ne", "mr", "hi", "bn", "pa", "gu", "ta", "te", "kn", "ml", "si", "th", "lo",
            "my", "ka", "am", "km", "zh-CN", "zh-TW", "zh-HK", "ja", "ko",
        )
    }

    private fun applyProxy(prefs: Preferences) {
        if (prefs[Keys.ProxyEnabled] != true) return
        val url = prefs[Keys.ProxyUrl] ?: return
        runCatching {
            val type = runCatching { Proxy.Type.valueOf(prefs[Keys.ProxyType] ?: "HTTP") }.getOrDefault(Proxy.Type.HTTP)
            val host = url.substringBeforeLast(':').substringAfter("://")
            val port = url.substringAfterLast(':').toInt()
            YouTube.proxy = Proxy(type, InetSocketAddress.createUnresolved(host, port))
            val user = prefs[Keys.ProxyUsername].orEmpty()
            val pass = prefs[Keys.ProxyPassword].orEmpty()
            if (type == Proxy.Type.HTTP && (user.isNotEmpty() || pass.isNotEmpty())) {
                YouTube.proxyAuth = Credentials.basic(user, pass)
            }
        }.onFailure { Timber.w(it, "bad proxy setting ignored") }
    }
}
