package dev.musicforge.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import dev.musicforge.data.db.MusicDao
import dev.musicforge.playback.MediaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Copies finished downloads into `Music/<app>/` so every player on the phone (and a PC the
 * folder syncs to) can use them. ConvX's idea, adapted: the bytes come straight out of the
 * download cache (no second fetch), MediaStore gets title/artist/album, and IS_PENDING hides
 * the file until it's complete. The copies belong to the user: removing a download here
 * never deletes them.
 */
@OptIn(UnstableApi::class)
class MusicExport(
    private val context: Context,
    private val engine: MediaEngine,
    private val dao: MusicDao,
    private val folderName: String,
) {
    val supported get() = Build.VERSION.SDK_INT >= 29

    /** A finished download and "save all" can overlap; one export at a time avoids duplicates. */
    private val lock = kotlinx.coroutines.sync.Mutex()
    private var cleaned = false

    suspend fun export(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!supported) return@withContext false
        lock.withLock {
            if (!cleaned) {
                cleaned = true
                runCatching { removeStalePending() }
            }
            runCatching { exportQ(id) }.onFailure { Timber.w(it, "export of %s failed", id) }.getOrDefault(false)
        }
    }

    /**
     * A process killed mid-export leaves a hidden IS_PENDING row (MediaStore only expires those
     * after a week). Remove ours once per run, before writing new files.
     */
    @RequiresApi(29)
    private fun removeStalePending() {
        val resolver = context.contentResolver
        val base = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.IS_PENDING}=1"
        val n = if (Build.VERSION.SDK_INT >= 30) {
            resolver.delete(
                base,
                android.os.Bundle().apply {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(folder))
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                },
            )
        } else {
            @Suppress("DEPRECATION")
            resolver.delete(MediaStore.setIncludePending(base), selection, arrayOf(folder))
        }
        if (n > 0) Timber.i("removed %d unfinished exports", n)
    }

    @RequiresApi(29)
    private suspend fun exportQ(id: String): Boolean {
        val song = dao.song(id).firstOrNull() ?: return false
        val format = dao.format(id) ?: return false
        val artist = song.artistLine.ifBlank { "Unknown artist" }
        val ext = when {
            "webm" in format.mimeType -> "ogg"
            "mp4" in format.mimeType || "m4a" in format.mimeType -> "m4a"
            else -> return false
        }
        val name = fileName("$artist - ${song.song.title}") + ".$ext"
        if (alreadyExported(name)) return true
        val raw = java.io.File(context.cacheDir, "export-$id.raw")
        val remuxed = java.io.File(context.cacheDir, "export-$id.ogg")
        try {
            copyFromCache(id, format.contentLength, raw)
            // MediaStore won't take audio/webm in Music/ (".webm" is treated as video), so WebM/Opus
            // is remuxed into Ogg/Opus: same audio packets, no re-encode, playable everywhere.
            val (file, mime, _) = when {
                "webm" in format.mimeType -> {
                    remuxToOgg(raw, remuxed)
                    // Tags inside the file: the media scanner (and any other player) reads these.
                    OggTags.write(
                        remuxed,
                        buildMap {
                            put("TITLE", song.song.title)
                            put("ARTIST", artist)
                            song.song.albumName?.let { put("ALBUM", it) }
                            song.song.year?.let { put("DATE", it.toString()) }
                        },
                    )
                    Triple(remuxed, "audio/ogg", "ogg")
                }
                "mp4" in format.mimeType || "m4a" in format.mimeType -> Triple(raw, "audio/mp4", "m4a")
                else -> return false
            }
            return publish(file, mime, name, song.song.title, artist, song.song.albumName)
        } finally {
            raw.delete()
            remuxed.delete()
        }
    }

    private fun copyFromCache(id: String, length: Long, target: java.io.File) {
        // Cache-only source: no upstream, so a missing byte is an error, never a network fetch.
        val source = CacheDataSource.Factory().setCache(engine.downloadCache).createDataSource()
        source.open(DataSpec.Builder().setUri("cache://$id".toUri()).setKey(id).setLength(length).build())
        try {
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = source.read(buf, 0, buf.size)
                    if (n == C.RESULT_END_OF_INPUT) break
                    out.write(buf, 0, n)
                }
            }
        } finally {
            source.close()
        }
    }

    @RequiresApi(29)
    private fun remuxToOgg(input: java.io.File, output: java.io.File): java.io.File {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(input.path)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME) == android.media.MediaFormat.MIMETYPE_AUDIO_OPUS
        } ?: error("no Opus track")
        extractor.selectTrack(track)
        val muxer = android.media.MediaMuxer(output.path, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        try {
            val out = muxer.addTrack(extractor.getTrackFormat(track))
            muxer.start()
            val buffer = java.nio.ByteBuffer.allocate(256 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, 0)
                muxer.writeSampleData(out, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            muxer.release()
            extractor.release()
        }
        return output
    }

    private val folder get() = "${android.os.Environment.DIRECTORY_MUSIC}/$folderName/"

    @RequiresApi(29)
    private fun alreadyExported(name: String): Boolean =
        context.contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(folder, name),
            null,
        )?.use { it.moveToFirst() } ?: false

    @RequiresApi(29)
    private fun publish(file: java.io.File, mime: String, name: String, title: String, artist: String, album: String?): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("can't open $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            Timber.i("exported %s to %s%s", title, folder, name)
            return true
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // never leave a half-written file behind
            throw e
        }
    }

    /** Safe on every filesystem a phone or PC might sync to. */
    private fun fileName(s: String) = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().take(150)
}
