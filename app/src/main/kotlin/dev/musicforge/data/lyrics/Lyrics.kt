package dev.musicforge.data.lyrics

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import dev.musicforge.data.db.LyricsEntity
import dev.musicforge.data.db.MusicDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.abs

/** Lyrics as shown: timed lines when we have them, otherwise plain text. */
sealed interface Lyrics {
    data class Synced(val lines: List<Line>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data object None : Lyrics

    data class Line(val timeMs: Long, val text: String)
}

object Lrc {
    private val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** Parses LRC, including lines with several timestamps. Returns null if nothing is timed. */
    fun parse(text: String): List<Lyrics.Line>? {
        val lines = mutableListOf<Lyrics.Line>()
        for (raw in text.lineSequence()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val words = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val (min, sec, frac) = m.destructured
                // LRC seconds are always 00–59. Some providers return malformed timestamps.
                if (sec.toInt() !in 0..59) continue
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                lines += Lyrics.Line(min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs, words)
            }
        }
        return lines.sortedBy { it.timeMs }.takeIf { it.isNotEmpty() }
    }

    /** `[mm:ss.xx]text` per line, the form every LRC reader understands. */
    fun format(lines: List<Lyrics.Line>): String = lines.joinToString("\n") { line ->
        val cs = line.timeMs / 10
        "[%02d:%02d.%02d]%s".format(java.util.Locale.ROOT, cs / 6000, (cs / 100) % 60, cs % 100, line.text)
    }
}

/**
 * Chooses the best lyric-bearing LRCLIB search result without treating a missing duration as zero.
 * When the track duration is unknown, preserve the provider's relevance order.
 */
internal fun chooseLyricsSearchResult(results: List<JSONObject>, durationSec: Int?): String? {
    fun JSONObject.lyricsText(): String? =
        optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            ?: optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }

    val candidates = results.mapNotNull { json -> json.lyricsText()?.let { json to it } }
    val targetDuration = durationSec?.takeIf { it > 0 }?.toDouble()
    val selected = if (targetDuration == null) {
        candidates.firstOrNull()
    } else {
        candidates
            .filter { (json, _) ->
                json.optDouble("duration", Double.NaN).let { it.isFinite() && it > 0.0 }
            }
            .minByOrNull { (json, _) -> abs(json.optDouble("duration") - targetDuration) }
            ?: candidates.firstOrNull()
    }
    return selected?.second
}

/**
 * Lyrics lookup: our DB first (restored backups bring their lyrics along), then LRCLIB
 * (synced, open database), then YouTube Music's own plain lyrics. Results are cached in the
 * `lyrics` table under the same columns Metrolist and ConvX use.
 */
class LyricsRepository(private val dao: MusicDao, private val http: OkHttpClient) {
    suspend fun lyrics(videoId: String, title: String, artist: String, album: String?, durationSec: Int?): Lyrics =
        withContext(Dispatchers.IO) {
            dao.lyrics(videoId)?.let { return@withContext toLyrics(it.lyrics) }
            val fetched = lrclib(title, artist, album, durationSec)?.let { it to "LRCLIB" }
                ?: youtube(videoId)?.let { it to "YouTube Music" }
                ?: return@withContext Lyrics.None
            dao.upsert(LyricsEntity(id = videoId, lyrics = fetched.first, provider = fetched.second))
            toLyrics(fetched.first)
        }

    private fun toLyrics(text: String): Lyrics =
        Lrc.parse(text)?.let { Lyrics.Synced(it) } ?: text.takeIf { it.isNotBlank() }?.let { Lyrics.Plain(it) } ?: Lyrics.None

    private fun lrclib(title: String, artist: String, album: String?, durationSec: Int?): String? = runCatching {
        val get = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply { album?.let { addQueryParameter("album_name", it) } }
            .apply { durationSec?.takeIf { it > 0 }?.let { addQueryParameter("duration", it.toString()) } }
            .build()
        call(get.toString())?.let { JSONObject(it).best() }?.let { return@runCatching it }
        // No exact match: search and take the closest duration.
        val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val results = call(search.toString())?.let(::JSONArray) ?: return@runCatching null
        chooseLyricsSearchResult(
            (0 until results.length()).map { results.getJSONObject(it) },
            durationSec,
        )
    }.onFailure { Timber.w(it, "LRCLIB lookup failed") }.getOrNull()

    private fun JSONObject.best(): String? =
        optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            ?: optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }

    private fun call(url: String): String? =
        http.newCall(Request.Builder().url(url).header("User-Agent", "MusicForge (personal player)").build()).execute().use {
            if (it.isSuccessful) it.body.string() else null
        }

    private suspend fun youtube(videoId: String): String? {
        val endpoint = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()?.lyricsEndpoint ?: return null
        return YouTube.lyrics(endpoint).getOrNull()?.takeIf { it.isNotBlank() }
    }
}
