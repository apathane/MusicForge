package dev.musicforge.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/** Same file name as Metrolist/ConvX ("settings" → settings.preferences_pb). */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Key names deliberately match Metrolist and ConvX, so imported settings need no
 * renaming. Only keys this app actually uses are listed.
 */
object Keys {
    enum class Kind { BOOL, INT, FLOAT, STRING }

    private val kinds = HashMap<String, Kind>()
    private fun str(name: String) = stringPreferencesKey(name).also { kinds[name] = Kind.STRING }
    private fun bool(name: String) = booleanPreferencesKey(name).also { kinds[name] = Kind.BOOL }
    private fun int(name: String) = intPreferencesKey(name).also { kinds[name] = Kind.INT }
    private fun float(name: String) = floatPreferencesKey(name).also { kinds[name] = Kind.FLOAT }

    fun kindOf(key: Preferences.Key<*>): Kind = kinds.getValue(key.name)

    // Account (YouTube Music login)
    val InnerTubeCookie = str("innerTubeCookie")
    val VisitorData = str("visitorData")
    val DataSyncId = str("dataSyncId")
    val InnerTubeAuthUser = str("innerTubeAuthUser")
    val AccountName = str("accountName")
    val AccountEmail = str("accountEmail")
    val AccountChannelHandle = str("accountChannelHandle")
    /** When the YouTube Music library was last mirrored into this phone (epoch ms). Not imported. */
    val LastAccountSync = longPreferencesKey("forge.lastAccountSync")
    /** When the YouTube Music history was last mirrored into this phone (epoch ms). Not imported. */
    val LastHistorySync = longPreferencesKey("forge.lastHistorySync")
    /** Metrolist's key: browse requests (Home, artists, charts) carry the account. Default on. */
    val UseLoginForBrowse = bool("useLoginForBrowse")
    val PauseSearchHistory = bool("pauseSearchHistory")
    /** Metrolist's key: plays here stay out of your YouTube Music history. */
    val PauseListenHistory = bool("pauseListenHistory")

    // Content
    val ContentLanguage = str("contentLanguage")
    val ContentCountry = str("contentCountry")

    // Look
    val DarkMode = str("darkMode") // ON / OFF / AUTO / TIME
    val PureBlack = bool("pureBlack")
    val GlassTier = str("forge.glassTier") // SOLID / ARTWORK / LIVE
    val ExportToMusic = bool("forge.exportToMusic")
    /** Home shows only what you did, not what it guesses from it. */
    val SuggestionsPaused = bool("forge.suggestionsPaused")
    /** Same name as Metrolist and ConvX, so a restore carries it across. */
    val AutoDownloadOnLike = bool("autoDownloadOnLike")
    val EqEnabled = bool("forge.eqEnabled")
    val EqLevels = str("forge.eqLevelsMb")
    val EqPreset = str("forge.eqPreset")
    /** Hide the top bar (status bar scrim and screen headers) for an edge-to-edge canvas. */
    val HideTopBar = bool("forge.hideTopBar")
    // Same names as the upstream app that has ListenBrainz, so a restore brings it along.
    val ListenBrainzToken = str("listenbrainz_token")
    val ListenBrainzEnabled = bool("listenbrainz_enabled")
    val ListenBrainzUser = str("forge.listenBrainzUser")

    // General
    /** Metrolist's key: which tab opens first. "defaultOpenTab" → Tab.route. */
    val StartScreen = str("defaultOpenTab")
    /** Metrolist's key: keep the screen on while the player is open. */
    val KeepScreenOn = bool("keepScreenOn")
    /** Local: haptics on toggle, long-press, swipe-to-skip. */
    val HapticsEnabled = bool("forge.hapticsEnabled")

    // Playback
    val AudioQuality = str("audioQuality") // AUTO / HIGH / LOW / ...
    val SkipSilence = bool("skipSilence")
    val AudioNormalization = bool("audioNormalization")
    val PersistentQueue = bool("persistentQueue")
    /** Metrolist's name: carry on with radio when the queue runs out. */
    val Autoplay = bool("autoplay")
    /** Swipe and drag are disabled in the queue while this is on. */
    val QueueLocked = bool("forge.queueLocked")
    val CrossfadeEnabled = bool("crossfadeEnabled")
    val CrossfadeSeconds = float("crossfadeDuration")
    val CrossfadeGapless = bool("crossfadeGapless")
    val MaxSongCacheSizeMb = int("maxSongCacheSize")
    val MaxImageCacheSizeMb = int("maxImageCacheSize")
    /** Local: double-tap the cover to seek ±10 s. */
    val DoubleTapSeek = bool("forge.doubleTapSeek")
    /** Local: swipe the mini player to skip. */
    val SwipeMiniToSkip = bool("forge.swipeMiniToSkip")

    // Network
    val ProxyEnabled = bool("proxyEnabled")
    val ProxyType = str("proxyType")
    val ProxyUrl = str("proxyUrl")
    val ProxyUsername = str("proxyUsername")
    val ProxyPassword = str("proxyPassword")

    // Lyrics
    /** Metrolist's key: show lyrics in the player. */
    val ShowLyrics = bool("showLyrics")
    /** Metrolist's key: lyrics text size in sp. Default 24f. */
    val LyricsTextSize = float("lyricsTextSize")

    /** Upstream key name → our key, for settings that import across. */
    val importable: Map<String, Preferences.Key<*>> = listOf(
        InnerTubeCookie, VisitorData, DataSyncId, InnerTubeAuthUser, AccountName, AccountEmail,
        AccountChannelHandle, ContentLanguage, ContentCountry, DarkMode, PureBlack, AudioQuality,
        SkipSilence, AudioNormalization, PersistentQueue, CrossfadeEnabled, CrossfadeSeconds,
        CrossfadeGapless, MaxSongCacheSizeMb, MaxImageCacheSizeMb, ProxyEnabled, ProxyType, ProxyUrl,
        ProxyUsername, ProxyPassword, ListenBrainzToken, ListenBrainzEnabled, AutoDownloadOnLike, Autoplay, UseLoginForBrowse,
        PauseSearchHistory, PauseListenHistory, StartScreen, KeepScreenOn, ShowLyrics, LyricsTextSize,
    ).associateBy { it.name } + mapOf(
        // Metrolist renamed its crossfade duration key when it moved to a float.
        "crossfadeDurationFloat" to CrossfadeSeconds,
    )
}
