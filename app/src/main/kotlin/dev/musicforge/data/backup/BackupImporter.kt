package dev.musicforge.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.musicforge.data.db.MusicDatabase
import dev.musicforge.data.prefs.Keys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Restores a Metrolist or ConvX backup (or one of ours) into this app.
 *
 * Both upstreams write a zip of `song.db` (+ `-wal`/`-shm`) and `settings.preferences_pb`,
 * and share a Room schema lineage. Our schema is a superset of both, so instead of
 * per-version migrations every table is copied by the *intersection* of its columns.
 * A backup from any historical version of either app restores the same way.
 *
 * Restore replaces the library, as both upstreams do. Downloads are not part of a backup,
 * so download flags are cleared except for songs whose bytes we actually have.
 */
class BackupImporter(
    private val context: Context,
    private val database: MusicDatabase,
    private val settings: DataStore<Preferences>,
) {
    enum class Origin { METROLIST, CONVX, MUSICFORGE, UNKNOWN }

    data class Preview(
        val origin: Origin,
        val dbVersion: Int,
        val rowCounts: Map<String, Int>,
        val settingsCount: Int,
        val accountName: String?,
    )

    data class Result(
        val origin: Origin,
        val copied: Map<String, Int>,
        val settingsImported: Int,
        val warnings: List<String>,
        /** Songs the backup marked as downloaded that we don't hold: offered for re-download. */
        val wereDownloaded: List<String> = emptyList(),
    )

    class InvalidBackupException(message: String) : Exception(message)

    suspend fun preview(uri: Uri): Preview = withStaged(uri) { preview(it) }

    suspend fun import(
        uri: Uri,
        includeAccount: Boolean = true,
        downloadedIds: Set<String> = emptySet(),
    ): Result = withStaged(uri) { import(it, includeAccount, downloadedIds) }

    // ---- staged (file-based) entry points; also what the tests drive ----

    internal suspend fun preview(staged: Staged): Preview = withContext(Dispatchers.IO) {
        val info = inspect(staged.db)
        val prefs = staged.settings?.let { PreferencesProto.parse(it.readBytes()) }.orEmpty()
        Preview(
            origin = if (staged.isOurs) Origin.MUSICFORGE else info.origin,
            dbVersion = info.version,
            rowCounts = info.rowCounts,
            settingsCount = prefs.size,
            accountName = (prefs["accountName"] as? PreferencesProto.Value.Str)?.v?.takeIf { it.isNotBlank() },
        )
    }

    internal suspend fun import(staged: Staged, includeAccount: Boolean, downloadedIds: Set<String>): Result =
        withContext(Dispatchers.IO) {
            val info = inspect(staged.db)
            // Restore replaces the library; an empty backup would only erase it.
            val worth = listOf("song", "playlist", "event").sumOf { info.rowCounts[it] ?: 0 }
            if (worth == 0) throw InvalidBackupException("This backup is empty: no songs, playlists or history to restore")
            val warnings = mutableListOf<String>()
            val wereDownloaded = mutableListOf<String>()
            val copied = copyTables(staged.db, info, downloadedIds, warnings, wereDownloaded, isOurs = staged.isOurs)
            val imported = staged.settings?.let { importSettings(it, includeAccount, warnings) } ?: 0
            Result(if (staged.isOurs) Origin.MUSICFORGE else info.origin, copied, imported, warnings, wereDownloaded)
        }

    // ---- staging ----

    internal class Staged(val dir: File) {
        val db get() = File(dir, MusicDatabase.DB_NAME)
        val settings get() = File(dir, SETTINGS_FILE).takeIf { it.exists() }
        val isOurs get() = File(dir, MANIFEST_FILE).exists()
    }

    private suspend fun <T> withStaged(uri: Uri, block: suspend (Staged) -> T): T {
        val dir = withContext(Dispatchers.IO) {
            val d = File(context.cacheDir, "restore-${System.nanoTime()}").apply { mkdirs() }
            val input = context.contentResolver.openInputStream(uri)
                ?: throw InvalidBackupException("Can't open the file")
            input.use { unzip(it, d) }
            d
        }
        return try {
            block(Staged(dir))
        } finally {
            withContext(Dispatchers.IO) { dir.deleteRecursively() }
        }
    }

    internal companion object {
        const val SETTINGS_FILE = "settings.preferences_pb"
        const val MANIFEST_FILE = "musicforge.json"

        private val ACCEPTED = setOf(
            MusicDatabase.DB_NAME, "${MusicDatabase.DB_NAME}-wal", "${MusicDatabase.DB_NAME}-shm",
            SETTINGS_FILE, MANIFEST_FILE,
        )

        /**
         * Parents before children, so foreign keys hold while copying. Tables not listed
         * here are not restored; `room_master_table` and `android_metadata` are Room's own.
         */
        val TABLE_ORDER = listOf(
            "song", "artist", "album", "playlist", "podcast",
            "song_artist_map", "song_album_map", "album_artist_map", "playlist_song_map",
            "related_song_map", "event", "format", "lyrics", "playCount", "search_history",
            "set_video_id", "speed_dial_item", "recognition_history", "song_analysis",
        )

        /**
         * MusicForge's own tables (the listener's Home corrections). Only a MusicForge backup carries
         * them; restoring Metrolist or ConvX leaves them as they are.
         */
        val OWN_TABLES = listOf("recommendation_feedback")

        /**
         * Extracts only the flat entries we know. Anything else, including paths with
         * directories (zip-slip), is ignored.
         */
        fun unzip(input: InputStream, dir: File) {
            var found = false
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name in ACCEPTED) {
                        File(dir, entry.name).outputStream().use { zip.copyTo(it) }
                        if (entry.name == MusicDatabase.DB_NAME) found = true
                    }
                    zip.closeEntry()
                }
            }
            if (!found) throw InvalidBackupException("This zip has no song.db: not a Metrolist or ConvX backup")
        }
    }

    // ---- inspection ----

    internal data class DbInfo(
        val version: Int,
        val columns: Map<String, List<String>>,
        val rowCounts: Map<String, Int>,
        val origin: Origin,
    )

    private fun inspect(dbFile: File): DbInfo {
        // Opening read-write on the staged copy lets SQLite replay a bundled -wal file.
        val db = try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: Exception) {
            throw InvalidBackupException("song.db is not a readable database (${e.message})")
        }
        return db.use {
            if (File("${dbFile.path}-wal").exists()) {
                it.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c -> c.moveToFirst() }
            }
            val version = it.rawQuery("PRAGMA user_version", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            val tables = it.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            if ("song" !in tables) throw InvalidBackupException("song.db has no song table")
            val check = it.rawQuery("PRAGMA quick_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else "?" }
            if (check != "ok") throw InvalidBackupException("song.db is damaged ($check)")
            val columns = tables.filter { t -> t in TABLE_ORDER || t in OWN_TABLES }.associateWith { t -> columnsOf(it, t) }
            val counts = columns.keys.associateWith { t ->
                it.rawQuery("SELECT COUNT(*) FROM ${q(t)}", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            }
            DbInfo(version, columns, counts, fingerprint(tables, columns))
        }
    }

    private fun columnsOf(db: SQLiteDatabase, table: String): List<String> =
        db.rawQuery("PRAGMA table_info(${q(table)})", null).use { c ->
            val name = c.getColumnIndexOrThrow("name")
            buildList { while (c.moveToNext()) add(c.getString(name)) }
        }

    private fun fingerprint(tables: List<String>, columns: Map<String, List<String>>): Origin {
        val convx = "song_analysis" in tables || "pinnedAt" in columns["album"].orEmpty() ||
            "isPodcastEpisode" in columns["song"].orEmpty()
        val metrolist = "podcast" in tables || "isCached" in columns["song"].orEmpty() ||
            "cachedPageJson" in columns["artist"].orEmpty()
        return when {
            convx && !metrolist -> Origin.CONVX
            metrolist && !convx -> Origin.METROLIST
            else -> Origin.UNKNOWN
        }
    }

    // ---- copy ----

    private data class Column(val name: String, val type: String, val notNull: Boolean, val hasDefault: Boolean)

    private fun mainColumns(db: SupportSQLiteDatabase, table: String): List<Column> =
        db.query("PRAGMA main.table_info(${q(table)})").use { c ->
            val name = c.getColumnIndexOrThrow("name")
            val type = c.getColumnIndexOrThrow("type")
            val notNull = c.getColumnIndexOrThrow("notnull")
            val dflt = c.getColumnIndexOrThrow("dflt_value")
            buildList {
                while (c.moveToNext()) {
                    add(Column(c.getString(name), c.getString(type).uppercase(), c.getInt(notNull) == 1, !c.isNull(dflt)))
                }
            }
        }

    private data class ForeignKey(val from: String, val table: String, val to: String)

    private fun foreignKeys(db: SupportSQLiteDatabase, table: String): List<ForeignKey> =
        db.query("PRAGMA main.foreign_key_list(${q(table)})").use { c ->
            val table0 = c.getColumnIndexOrThrow("table")
            val from = c.getColumnIndexOrThrow("from")
            val to = c.getColumnIndexOrThrow("to")
            buildList { while (c.moveToNext()) add(ForeignKey(c.getString(from), c.getString(table0), c.getString(to))) }
        }

    private fun copyTables(
        backupFile: File,
        info: DbInfo,
        downloadedIds: Set<String>,
        warnings: MutableList<String>,
        wereDownloaded: MutableList<String>,
        isOurs: Boolean,
    ): Map<String, Int> {
        // Our own tables are replaced only from our own backup, and only when it has them.
        val tables = TABLE_ORDER + OWN_TABLES.filter { isOurs && it in info.columns }
        // No ATTACH: on Android it permanently turns WAL off for Room's connection pool and
        // throws inside a transaction. Rows are streamed from a separate connection instead.
        val backup = SQLiteDatabase.openDatabase(backupFile.path, null, SQLiteDatabase.OPEN_READONLY)
        val db = database.openHelper.writableDatabase
        val copied = LinkedHashMap<String, Int>()
        backup.use {
            database.runInTransaction {
                stashDownloaded(db, downloadedIds)
                for (table in tables.asReversed()) db.execSQL("DELETE FROM main.${q(table)}")
                for (table in tables) {
                    val backupCols = info.columns[table] ?: continue
                    val ours = mainColumns(db, table)
                    val common = ours.filter { it.name in backupCols }
                    if (common.isEmpty()) {
                        warnings += "$table: no columns in common, skipped"
                        continue
                    }
                    // Columns the backup lacks but we require: fill with a typed zero.
                    val filled = ours.filter { it.name !in backupCols && it.notNull && !it.hasDefault }
                    val fks = foreignKeys(db, table).filter { it.table in TABLE_ORDER }
                    val target = (common + filled).joinToString { q(it.name) }
                    val values = (common.map { "?" } + filled.map { zeroOf(it.type) }).joinToString()
                    val exists = fks.joinToString(" AND ") {
                        "EXISTS (SELECT 1 FROM main.${q(it.table)} WHERE ${q(it.to)} = ?)"
                    }.ifEmpty { "1" }
                    val insert = db.compileStatement(
                        "INSERT OR IGNORE INTO main.${q(table)} ($target) SELECT $values WHERE $exists",
                    )
                    val fkIndex = fks.map { fk -> common.indexOfFirst { it.name == fk.from } }
                    var n = 0
                    var seen = 0
                    insert.use { stmt ->
                        backup.rawQuery("SELECT ${common.joinToString { q(it.name) }} FROM ${q(table)}", null).use { c ->
                            while (c.moveToNext()) {
                                seen++
                                stmt.clearBindings()
                                common.forEachIndexed { i, col -> bindFrom(stmt, i + 1, c, i, col) }
                                fkIndex.forEachIndexed { j, ci ->
                                    val arg = common.size + j + 1
                                    if (ci < 0 || c.isNull(ci)) stmt.bindNull(arg) else stmt.bindString(arg, c.getString(ci))
                                }
                                if (stmt.executeInsert() != -1L) n++
                            }
                        }
                    }
                    copied[table] = n
                    if (n < seen) warnings += "$table: ${seen - n} of $seen rows skipped (orphaned or duplicate)"
                }
                restoreStash(db)
                syncEpisodeFlags(db)
                // Downloads don't travel in backups; remember which ones the old app had.
                db.query("SELECT id FROM song WHERE isDownloaded = 1 ORDER BY dateDownload DESC").use { c ->
                    while (c.moveToNext()) c.getString(0).takeIf { it !in downloadedIds }?.let(wereDownloaded::add)
                }
                resetDownloads(db, downloadedIds)
            }
        }
        return copied
    }

    private fun bindFrom(
        stmt: androidx.sqlite.db.SupportSQLiteStatement,
        arg: Int,
        c: android.database.Cursor,
        index: Int,
        col: Column,
    ) {
        when (c.getType(index)) {
            android.database.Cursor.FIELD_TYPE_NULL ->
                if (col.notNull) bindZero(stmt, arg, col.type) else stmt.bindNull(arg)
            android.database.Cursor.FIELD_TYPE_INTEGER -> stmt.bindLong(arg, c.getLong(index))
            android.database.Cursor.FIELD_TYPE_FLOAT -> stmt.bindDouble(arg, c.getDouble(index))
            android.database.Cursor.FIELD_TYPE_BLOB -> stmt.bindBlob(arg, c.getBlob(index))
            else -> stmt.bindString(arg, c.getString(index))
        }
    }

    private fun bindZero(stmt: androidx.sqlite.db.SupportSQLiteStatement, arg: Int, type: String) =
        when (zeroOf(type)) {
            "0" -> stmt.bindLong(arg, 0)
            "X''" -> stmt.bindBlob(arg, ByteArray(0))
            else -> stmt.bindString(arg, "")
        }

    /**
     * Songs we hold the bytes for must survive a restore even when the backup doesn't know
     * them, or they'd become invisible files in the download cache. Their rows (artist credits
     * and format) are parked in temp tables while the library is replaced.
     */
    private fun stashDownloaded(db: SupportSQLiteDatabase, ids: Set<String>) {
        listOf("keep_ids", "keep_song", "keep_map", "keep_artist", "keep_format").forEach { db.execSQL("DROP TABLE IF EXISTS temp.$it") }
        db.execSQL("CREATE TEMP TABLE keep_ids (id TEXT PRIMARY KEY)")
        ids.forEach { db.execSQL("INSERT OR IGNORE INTO temp.keep_ids VALUES (?)", arrayOf(it)) }
        db.execSQL("CREATE TEMP TABLE keep_song AS SELECT * FROM main.song WHERE id IN (SELECT id FROM temp.keep_ids)")
        db.execSQL("CREATE TEMP TABLE keep_map AS SELECT * FROM main.song_artist_map WHERE songId IN (SELECT id FROM temp.keep_ids)")
        db.execSQL("CREATE TEMP TABLE keep_artist AS SELECT * FROM main.artist WHERE id IN (SELECT artistId FROM temp.keep_map)")
        // The format row describes the bytes on disk (mime, length); export and mid-file reopen need it.
        db.execSQL("CREATE TEMP TABLE keep_format AS SELECT * FROM main.format WHERE id IN (SELECT id FROM temp.keep_ids)")
    }

    private fun restoreStash(db: SupportSQLiteDatabase) {
        // CREATE TABLE AS SELECT * keeps the column order, so SELECT * lines up again.
        db.execSQL("INSERT OR IGNORE INTO main.song SELECT * FROM temp.keep_song")
        db.execSQL("INSERT OR IGNORE INTO main.artist SELECT * FROM temp.keep_artist")
        db.execSQL("INSERT OR IGNORE INTO main.song_artist_map SELECT * FROM temp.keep_map")
        // Ours wins: the backup's row may describe another rendition than the one we downloaded.
        db.execSQL("INSERT OR REPLACE INTO main.format SELECT * FROM temp.keep_format")
        listOf("keep_ids", "keep_song", "keep_map", "keep_artist", "keep_format").forEach { db.execSQL("DROP TABLE IF EXISTS temp.$it") }
    }

    /** Metrolist calls it `isEpisode`, ConvX `isPodcastEpisode`; keep both truthful. */
    private fun syncEpisodeFlags(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE song SET isEpisode = 1, isPodcastEpisode = 1 WHERE isEpisode = 1 OR isPodcastEpisode = 1")
    }

    private fun resetDownloads(db: SupportSQLiteDatabase, keep: Set<String>) {
        db.execSQL("UPDATE song SET isDownloaded = 0, dateDownload = NULL, isCached = 0")
        // Restore the flag only for songs whose bytes we actually hold.
        keep.chunked(500).forEach { ids ->
            val marks = ids.joinToString { "?" }
            db.execSQL(
                "UPDATE song SET isDownloaded = 1, dateDownload = COALESCE(dateDownload, ?) WHERE id IN ($marks)",
                listOf<Any>(System.currentTimeMillis()).plus(ids).toTypedArray(),
            )
        }
    }

    private fun zeroOf(type: String) = when {
        "INT" in type || "REAL" in type || "FLOA" in type || "DOUB" in type -> "0"
        "BLOB" in type -> "X''"
        else -> "''"
    }

    private fun q(identifier: String) = "`" + identifier.replace("`", "``") + "`"

    // ---- settings ----

    private suspend fun importSettings(file: File, includeAccount: Boolean, warnings: MutableList<String>): Int {
        val values = try {
            PreferencesProto.parse(file.readBytes())
        } catch (e: Exception) {
            warnings += "settings: unreadable (${e.message})"
            return 0
        }
        var imported = 0
        settings.edit { prefs ->
            for ((name, value) in values) {
                val key = Keys.importable[name] ?: continue
                if (!includeAccount && key in ACCOUNT_KEYS) continue
                if (prefs.putCoerced(key, value)) imported++ else warnings += "settings: $name has an unexpected type"
            }
        }
        return imported
    }

    private val ACCOUNT_KEYS = setOf(
        Keys.InnerTubeCookie, Keys.VisitorData, Keys.DataSyncId, Keys.InnerTubeAuthUser,
        Keys.AccountName, Keys.AccountEmail, Keys.AccountChannelHandle,
    )

    @Suppress("UNCHECKED_CAST")
    private fun MutablePreferences.putCoerced(key: Preferences.Key<*>, value: PreferencesProto.Value): Boolean {
        val number: Double? = when (value) {
            is PreferencesProto.Value.I32 -> value.v.toDouble()
            is PreferencesProto.Value.I64 -> value.v.toDouble()
            is PreferencesProto.Value.F32 -> value.v.toDouble()
            is PreferencesProto.Value.F64 -> value.v
            else -> null
        }
        when (Keys.kindOf(key)) {
            Keys.Kind.STRING -> this[key as Preferences.Key<String>] =
                (value as? PreferencesProto.Value.Str)?.v ?: return false
            Keys.Kind.BOOL -> this[key as Preferences.Key<Boolean>] =
                (value as? PreferencesProto.Value.Bool)?.v ?: return false
            Keys.Kind.INT -> this[key as Preferences.Key<Int>] = number?.toInt() ?: return false
            Keys.Kind.FLOAT -> this[key as Preferences.Key<Float>] = number?.toFloat() ?: return false
        }
        return true
    }
}
