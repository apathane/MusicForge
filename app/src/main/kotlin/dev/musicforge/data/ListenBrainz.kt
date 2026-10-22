package dev.musicforge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import dev.musicforge.BuildConfig
import dev.musicforge.data.prefs.Keys
import dev.musicforge.playback.durationSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Scrobbling to ListenBrainz (open, non-profit; a personal token is all it needs).
 * Listens that can't be sent (offline) wait in a small file queue and go out as one
 * "import" batch with the next successful submission.
 */
class ListenBrainz(
    context: Context,
    private val settings: DataStore<Preferences>,
    private val http: OkHttpClient,
) {
    private val queue = File(context.filesDir, "listenbrainz-queue.jsonl")
    private val lock = Mutex()
    private val json = "application/json".toMediaType()

    /** Returns the user name for a valid token, or null. */
    suspend fun validate(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$API/validate-token").header("Authorization", "Token ${token.trim()}").build()
            http.newCall(req).execute().use { r ->
                val body = JSONObject(r.body.string())
                if (body.optBoolean("valid")) body.optString("user_name").ifBlank { null } else null
            }
        }.getOrNull()
    }

    suspend fun playingNow(item: MediaItem) {
        val token = token() ?: return
        submit(token, "playing_now", JSONArray().put(JSONObject().put("track_metadata", metadata(item))))
    }

    /** [startedAtSec]: Unix time playback of this song began. */
    suspend fun scrobble(item: MediaItem, startedAtSec: Long) = lock.withLock {
        val token = token() ?: return@withLock
        val listen = JSONObject().put("listened_at", startedAtSec).put("track_metadata", metadata(item))
        if (submit(token, "single", JSONArray().put(listen))) flushQueue(token) else enqueue(listen)
    }

    private suspend fun token(): String? {
        val prefs = settings.data.first()
        if (prefs[Keys.ListenBrainzEnabled] == false) return null
        return prefs[Keys.ListenBrainzToken]?.takeIf { it.isNotBlank() }
    }

    private fun metadata(item: MediaItem): JSONObject {
        val m = item.mediaMetadata
        return JSONObject()
            .put("artist_name", m.artist?.toString().orEmpty().ifBlank { "Unknown artist" })
            .put("track_name", m.title?.toString().orEmpty())
            .apply { m.albumTitle?.toString()?.takeIf { it.isNotBlank() }?.let { put("release_name", it) } }
            .put(
                "additional_info",
                JSONObject()
                    .put("music_service", "music.youtube.com")
                    .put("origin_url", "https://music.youtube.com/watch?v=${item.mediaId}")
                    .put("submission_client", "MusicForge")
                    .put("submission_client_version", BuildConfig.VERSION_NAME)
                    .apply { m.durationSec?.let { put("duration_ms", it * 1000L) } },
            )
    }

    private suspend fun submit(token: String, type: String, payload: JSONArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("listen_type", type).put("payload", payload).toString().toRequestBody(json)
            val req = Request.Builder().url("$API/submit-listens").header("Authorization", "Token $token").post(body).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) Timber.w("ListenBrainz %s: HTTP %d", type, r.code)
                r.isSuccessful
            }
        }.getOrElse { Timber.w(it, "ListenBrainz %s failed", type); false }
    }

    private suspend fun enqueue(listen: JSONObject) = withContext(Dispatchers.IO) {
        queue.appendText(listen.toString() + "\n")
    }

    private suspend fun flushQueue(token: String) = withContext(Dispatchers.IO) {
        if (!queue.exists()) return@withContext
        val pending = queue.readLines().filter { it.isNotBlank() }.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        if (pending.isEmpty()) { queue.delete(); return@withContext }
        // "import" takes many listens per request; stay well under the payload limits.
        val failed = mutableListOf<JSONObject>()
        pending.chunked(100).forEach { batch ->
            if (!submit(token, "import", JSONArray(batch))) failed += batch
        }
        if (failed.isEmpty()) queue.delete() else queue.writeText(failed.joinToString("\n", postfix = "\n"))
    }

    private companion object {
        const val API = "https://api.listenbrainz.org/1"
    }
}
