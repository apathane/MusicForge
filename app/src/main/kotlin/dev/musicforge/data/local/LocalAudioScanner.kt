package dev.musicforge.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.withTransaction
import dev.musicforge.data.db.AlbumEntity
import dev.musicforge.data.db.ArtistEntity
import dev.musicforge.data.db.MusicDao
import dev.musicforge.data.db.MusicDatabase
import dev.musicforge.data.db.SongAlbumMap
import dev.musicforge.data.db.SongArtistMap
import dev.musicforge.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * MediaStore scanner for on-device audio files. Mirrors ConvX's LocalAudioScanner but writes
 * through MusicForge's schema (song.id is the content URI, thumbnailUrl holds the album-art
 * content URI for Artwork to fetch). Incremental by DATE_MODIFIED: songs modified since the
 * last scan are re-read; everything else is left alone, so a full library doesn't block startup.
 *
 * Folder filter: a persisted OpenDocumentTree URI narrows the scan to that tree's path;
 * null means "all music". The caller is expected to have the READ_MEDIA_AUDIO permission
 * (and the document permission for a picked folder) before calling; this never asks for
 * permissions itself.
 */
object LocalAudioScanner {

    private const val MIN_DURATION_MS = 15_000L // skip ringtones, notifications, voice memos
    private const val DELETE_CHUNK = 500 // SQLite's bind-variable limit is 999
    private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")

    data class ScanResult(
        val totalFound: Int,
        val newSongs: Int,
        val updated: Int,
        val skippedExisting: Int,
        val removed: Int,
        /** False when a collection could not be listed, so [removed] stayed at 0 on purpose. */
        val listingComplete: Boolean,
    )

    /**
     * Scan audio files, upserting new and changed rows. The caller handles permissions and
     * (for a picked folder) document-tree read permission: this just queries MediaStore.
     */
    suspend fun scanAndInsert(
        context: Context,
        database: MusicDatabase,
        folderMode: FolderMode,
    ): ScanResult =
        withContext(Dispatchers.IO) {
            val dao = database.dao()
            var totalFound = 0
            var newSongs = 0
            var updated = 0
            var skippedExisting = 0

            val cutoffMillis = dao.maxLocalDateModified() ?: 0L
            val existingIds = dao.localSongIds().toHashSet()

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.RELATIVE_PATH,
            )

            // External volume(s): API 29+ can carry a secondary (SD card) volume; the primary
            // shared volume is where Music/MusicForge lives on the X7b's single volume.
            val collections = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.getExternalVolumeNames(context).map { volume ->
                    MediaStore.Audio.Media.getContentUri(volume)
                }.ifEmpty { listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) }
            } else {
                listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            }

            // IS_MUSIC alone misses FLAC/OGG/OPUS whose scanner flag is unset; accept any
            // audio/* mime type. Exported downloads live under Music/MusicForge and are already
            // imported tracks, so skip them to avoid double-counting.
            val baseSelection =
                "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')" +
                    " AND ${MediaStore.Audio.Media.DURATION} >= ?" +
                    " AND ${MediaStore.Audio.Media.RELATIVE_PATH} NOT LIKE 'Music/MusicForge/%'"
            val baseArgs = arrayOf(MIN_DURATION_MS.toString())

            val (selection, selectionArgs) = folderMode.selection(baseSelection, baseArgs)
            val cutoffSelection = if (cutoffMillis > 0L) {
                // DATE_MODIFIED is seconds since epoch; keep rows that changed since the last scan.
                val clause = if (selection.isNotBlank()) "$selection AND " else ""
                "$clause${MediaStore.Audio.Media.DATE_MODIFIED} >= ?"
            } else selection
            val cutoffArgs = if (cutoffMillis > 0L) {
                selectionArgs + arrayOf((cutoffMillis / 1000L).toString())
            } else selectionArgs

            collections.forEach { collection ->
                context.contentResolver.query(
                    collection,
                    projection,
                    cutoffSelection,
                    cutoffArgs,
                    null,
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                    while (cursor.moveToNext()) {
                        totalFound++
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown"
                        val artistName = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val albumName = cursor.getString(albumColumn) ?: ""
                        val albumId = cursor.getLong(albumIdColumn)
                        val durationMs = cursor.getLong(durationColumn)
                        val dateAdded = cursor.getLong(dateAddedColumn)
                        val dateModified = cursor.getLong(dateModifiedColumn)
                        val year = cursor.getInt(yearColumn)

                        val contentUri = ContentUris.withAppendedId(collection, id).toString()
                        val albumArtUri = if (albumId > 0) {
                            ContentUris.withAppendedId(ALBUM_ART_URI, albumId).toString()
                        } else null

                        if (contentUri in existingIds) {
                            // Re-scan only if it actually changed; the cursor is already filtered
                            // to DATE_MODIFIED >= cutoff, so an existing row here is necessarily newer.
                            if (insertSong(database, contentUri, title, artistName, albumName, albumArtUri, durationMs, year, dateAdded, dateModified)) {
                                updated++
                            }
                        } else {
                            existingIds.add(contentUri)
                            if (insertSong(database, contentUri, title, artistName, albumName, albumArtUri, durationMs, year, dateAdded, dateModified)) {
                                newSongs++
                            }
                        }
                    }
                }
            }

            // Deletion needs its own listing: the upsert query above only sees changed rows and
            // (with a folder picked) only that folder, so it can't prove what still exists.
            val presentIds = HashSet<String>()
            var listingComplete = true
            collections.forEach { collection ->
                try {
                    val cursor = context.contentResolver.query(
                        collection,
                        arrayOf(MediaStore.Audio.Media._ID),
                        baseSelection,
                        baseArgs,
                        null,
                    )
                    if (cursor == null) {
                        listingComplete = false
                        Timber.tag("LocalAudioScanner").w("No presence cursor for $collection; keeping local rows")
                    } else {
                        cursor.use {
                            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                            while (it.moveToNext()) {
                                presentIds += ContentUris.withAppendedId(collection, it.getLong(idColumn)).toString()
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    // Permission revoked or the card is gone: we know nothing, so delete nothing.
                    listingComplete = false
                    Timber.tag("LocalAudioScanner").w(e, "No access to $collection; keeping local rows")
                } catch (e: Exception) {
                    listingComplete = false
                    Timber.tag("LocalAudioScanner").w(e, "Presence query failed for $collection; keeping local rows")
                }
            }

            val toRemove = idsToRemove(
                existing = existingIds,
                present = presentIds,
                queriedCollections = collections.map { it.toString() },
                listingComplete = listingComplete,
            )
            if (toRemove.isNotEmpty()) {
                database.withTransaction {
                    toRemove.chunked(DELETE_CHUNK).forEach { dao.deleteLocalSongs(it) }
                }
            }
            val removed = toRemove.size

            Timber.tag("LocalAudioScanner")
                .i("Scan complete: total=$totalFound new=$newSongs updated=$updated skippedExisting=$skippedExisting removed=$removed listingComplete=$listingComplete")

            ScanResult(totalFound, newSongs, updated, skippedExisting, removed, listingComplete)
        }

    private suspend fun insertSong(
        database: MusicDatabase,
        songId: String,
        title: String,
        artistName: String,
        albumName: String,
        albumArtUri: String?,
        durationMs: Long,
        year: Int,
        dateAddedSec: Long,
        dateModifiedSec: Long,
    ): Boolean {
        val dao = database.dao()
        val artistId = "local_artist_${artistName.hashCode()}"
        val albumId = albumName.ifEmpty { null }?.let { "local_album_${albumName.hashCode()}" }
        val durationSeconds = (durationMs / 1000).takeIf { durationMs > 0 }?.toInt() ?: -1
        val song = SongEntity(
            id = songId,
            title = title,
            duration = durationSeconds,
            thumbnailUrl = albumArtUri,
            albumId = albumId,
            albumName = albumName.ifEmpty { null },
            year = year.takeIf { it > 0 },
            // DATE_MODIFIED / DATE_ADDED are seconds since epoch; store millis so the cutoff
            // math (epoch seconds vs millis) stays unambiguous across a scan.
            dateModified = dateModifiedSec * 1000L,
            isLocal = true,
            inLibrary = dateAddedSec * 1000L,
        )
        return try {
            // Song + artist + album + maps reference each other, so save them atomically.
            database.withTransaction {
                dao.upsert(song)
                dao.upsert(ArtistEntity(id = artistId, name = artistName, isLocal = true, thumbnailUrl = albumArtUri))
                dao.upsert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                if (albumName.isNotBlank() && albumId != null) {
                    dao.upsert(
                        AlbumEntity(
                            id = albumId,
                            title = albumName,
                            thumbnailUrl = albumArtUri,
                            isLocal = true,
                            inLibrary = dateAddedSec * 1000L,
                        ),
                    )
                    dao.upsert(SongAlbumMap(songId = songId, albumId = albumId, index = 0))
                }
            }
            true
        } catch (e: Exception) {
            Timber.tag("LocalAudioScanner").w(e, "Failed to insert $title")
            false
        }
    }

    /** A persisted folder selection from OpenDocumentTree ("scan only this folder"), or "all". */
    sealed interface FolderMode {
        data object All : FolderMode

        /**
         * Scan only files under the tree rooted at [treeUri]. The caller has granted
         * document read permission via PersistablePermission before calling.
         */
        data class Folder(val treeUri: Uri) : FolderMode

        /** Build the MediaStore selection+args narrowing the query to this mode, applied on top of [base]. */
        fun selection(base: String, baseArgs: Array<String>): Pair<String, Array<String>> = when (this) {
            All -> base to baseArgs
            is Folder -> {
                val rel = treeRelativePath(treeUri) ?: return base to baseArgs
                // Pass the path as args, not inside the SQL: a folder called "Guns N' Roses"
                // otherwise ends the string literal early and the whole query falls over.
                val clause = "(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" +
                    " OR ${MediaStore.Audio.Media.DATA} LIKE ?)"
                val combined = if (base.isNotBlank()) "$base AND $clause" else clause
                combined to baseArgs + arrayOf("$rel%", "%/$rel/%")
            }
        }
    }

    /**
     * Which indexed local songs to delete after a scan. Deletion is destructive and the input can
     * lie (unmounted card, revoked permission), so only ids that a completed listing of their own
     * volume failed to find are removed. Songs on a volume we never queried stay put.
     */
    internal fun idsToRemove(
        existing: Set<String>,
        present: Set<String>,
        queriedCollections: List<String>,
        listingComplete: Boolean,
    ): Set<String> {
        if (!listingComplete) return emptySet()
        // An empty listing for a library that had songs is a missing card, not a tidy-up.
        if (present.isEmpty() && existing.isNotEmpty()) return emptySet()
        val prefixes = queriedCollections.map { "$it/" }
        return existing.filterTo(HashSet()) { id ->
            prefixes.any { id.startsWith(it) } && id !in present
        }
    }

    /**
     * Resolve a document-tree Uri to a path relative to the external root (e.g. "Music/MyFolder").
     * treeUri looks like content://com.android.externalstorage.documents/tree/primary%3AMusic%2FMyFolder.
     */
    private fun treeRelativePath(treeUri: Uri): String? = try {
        val path = treeUri.path ?: return null
        val afterTree = path.substringAfter("/tree/", "")
        val decoded = Uri.decode(afterTree)
        val rel = decoded.substringAfter(":", "")
        rel.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Timber.tag("LocalAudioScanner").w(e, "Could not resolve tree path for $treeUri")
        null
    }
}
