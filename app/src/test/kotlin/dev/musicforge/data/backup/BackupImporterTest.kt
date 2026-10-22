package dev.musicforge.data.backup

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.musicforge.data.db.MusicDatabase
import dev.musicforge.data.prefs.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Fixtures are built from the upstream apps' own Room schema exports, so these tests run
 * against the exact `CREATE TABLE` statements Metrolist v10/v38 and ConvX v40 ship.
 */
@RunWith(RobolectricTestRunner::class)
class BackupImporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: MusicDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settings: androidx.datastore.core.DataStore<Preferences>
    private lateinit var importer: BackupImporter

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") }
        importer = BackupImporter(context, db, settings)
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
    }

    // ---- fixture building ----

    private fun schema(name: String): JsonObject =
        Json.parseToJsonElement(javaClass.getResource("/fixtures/$name.json")!!.readText()).jsonObject["database"]!!.jsonObject

    /** Creates an upstream song.db from its schema export and returns the open handle. */
    private fun upstreamDb(schemaName: String, dir: File): SQLiteDatabase {
        val schema = schema(schemaName)
        val file = File(dir, "song.db")
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (entity in schema["entities"]!!.jsonArray) {
            val e = entity.jsonObject
            val table = e["tableName"]!!.jsonPrimitive.content
            sqlite.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e["indices"]?.jsonArray?.forEach { idx ->
                sqlite.execSQL(idx.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        sqlite.version = schema["version"]!!.jsonPrimitive.int
        tables[file.path] = schema["entities"]!!.jsonArray.associate { ent ->
            val o = ent.jsonObject
            o["tableName"]!!.jsonPrimitive.content to o["fields"]!!.jsonArray.map { f ->
                val fo = f.jsonObject
                Triple(fo["columnName"]!!.jsonPrimitive.content, fo["affinity"]!!.jsonPrimitive.content, fo["notNull"]?.jsonPrimitive?.boolean ?: false)
            }
        }
        return sqlite
    }

    private val tables = HashMap<String, Map<String, List<Triple<String, String, Boolean>>>>()

    /** Inserts a row, filling NOT NULL columns the caller didn't give with typed defaults. */
    private fun SQLiteDatabase.row(table: String, vararg values: Pair<String, Any?>) {
        val cols = tables[path]!![table] ?: error("no table $table in fixture")
        val given = values.toMap().filterKeys { k -> cols.any { it.first == k } }
        val cv = ContentValues()
        for ((name, affinity, notNull) in cols) {
            val v = if (name in given) given[name] else if (notNull) (if (affinity == "TEXT") "" else 0) else null
            when (v) {
                null -> if (name in given || !notNull) cv.putNull(name)
                is String -> cv.put(name, v)
                is Int -> cv.put(name, v)
                is Long -> cv.put(name, v)
                is Boolean -> cv.put(name, if (v) 1 else 0)
                is Double -> cv.put(name, v)
                is ByteArray -> cv.put(name, v)
                else -> error("unsupported $v")
            }
        }
        // Autoincrement ids the caller didn't set should be assigned by SQLite.
        if ("id" !in given && cols.firstOrNull()?.let { it.first == "id" && it.second == "INTEGER" } == true) cv.remove("id")
        insertOrThrow(table, null, cv)
    }

    private fun library(db: SQLiteDatabase) {
        db.row("song", "id" to "s1", "title" to "Glory Box", "duration" to 300, "liked" to 1, "likedDate" to 1_700_000_000_000L,
            "inLibrary" to 1_700_000_000_000L, "isDownloaded" to 1, "dateDownload" to 1_700_000_000_000L, "totalPlayTime" to 99L)
        db.row("song", "id" to "s2", "title" to "Teardrop", "duration" to 330, "liked" to 0, "inLibrary" to 1_700_000_000_001L)
        db.row("song", "id" to "s3", "title" to "Roads", "duration" to 305, "liked" to 1, "isEpisode" to 1)
        db.row("artist", "id" to "a1", "name" to "Portishead", "lastUpdateTime" to 0L)
        db.row("artist", "id" to "a2", "name" to "Massive Attack", "lastUpdateTime" to 0L)
        db.row("song_artist_map", "songId" to "s1", "artistId" to "a1", "position" to 0)
        db.row("song_artist_map", "songId" to "s2", "artistId" to "a2", "position" to 0)
        db.row("song_artist_map", "songId" to "s3", "artistId" to "a1", "position" to 0)
        db.row("playlist", "id" to "p1", "name" to "Trip hop", "isEditable" to 1)
        db.row("playlist_song_map", "playlistId" to "p1", "songId" to "s1", "position" to 0)
        db.row("playlist_song_map", "playlistId" to "p1", "songId" to "s3", "position" to 1)
        db.row("event", "songId" to "s1", "timestamp" to 1_700_000_000_000L, "playTime" to 200_000L)
        db.row("event", "songId" to "s2", "timestamp" to 1_700_000_100_000L, "playTime" to 100_000L)
        db.row("search_history", "query" to "portishead")
    }

    private fun stage(build: (File) -> Unit): BackupImporter.Staged {
        val dir = tmp.newFolder()
        build(dir)
        return BackupImporter.Staged(dir)
    }

    // ---- tests ----

    @Test fun metrolist38_restoresLibraryAndResetsDownloads() = runBlocking {
        val staged = stage { dir -> upstreamDb("metrolist-38", dir).use { library(it) } }
        val preview = importer.preview(staged)
        assertThat(preview.origin).isEqualTo(BackupImporter.Origin.METROLIST)
        assertThat(preview.dbVersion).isEqualTo(38)
        assertThat(preview.rowCounts["song"]).isEqualTo(3)

        val result = importer.import(staged, includeAccount = true, downloadedIds = emptySet())
        assertThat(result.copied["song"]).isEqualTo(3)
        assertThat(result.copied["playlist_song_map"]).isEqualTo(2)
        assertThat(result.copied["event"]).isEqualTo(2)
        assertThat(result.warnings).isEmpty()

        val dao = db.dao()
        val counts = dao.counts().first()
        assertThat(counts.liked).isEqualTo(2)
        assertThat(counts.playlists).isEqualTo(1)
        assertThat(counts.events).isEqualTo(2)
        assertThat(counts.downloaded).isEqualTo(0) // no bytes on this device
        val s1 = dao.song("s1").first()!!
        assertThat(s1.artists.map { it.name }).containsExactly("Portishead")
        assertThat(s1.song.likedDate).isEqualTo(1_700_000_000_000L)
        assertThat(s1.song.totalPlayTime).isEqualTo(99L)
        assertThat(dao.songEntity("s3")!!.isPodcastEpisode).isTrue() // isEpisode → both flags
        assertThat(dao.playlistSongs("p1").first().map { it.id }).containsExactly("s1", "s3").inOrder()
    }

    @Test fun convx40_restoresIncludingConvxOnlyTables() = runBlocking {
        val staged = stage { dir ->
            upstreamDb("convx-40", dir).use { d ->
                d.row("song", "id" to "s1", "title" to "Angel", "duration" to 380, "liked" to 1, "isPodcastEpisode" to 0)
                d.row("playlist", "id" to "p1", "name" to "Pinned", "pinnedAt" to 5L, "description" to "mine")
                d.row("song_analysis", "id" to "s1", "bpm" to 82.0, "anchorBeatMs" to 120.0, "bpmConfidence" to 0.9,
                    "keyIndex" to 9, "keyIsMinor" to 1, "keyConfidence" to 0.7, "energyProfile" to byteArrayOf(1, 2, 3),
                    "analyzedAt" to 1L, "analysisVersion" to 2)
            }
        }
        assertThat(importer.preview(staged).origin).isEqualTo(BackupImporter.Origin.CONVX)
        val result = importer.import(staged, includeAccount = true, downloadedIds = setOf("s1"))
        assertThat(result.copied["song_analysis"]).isEqualTo(1)
        val playlist = db.dao().playlists().first().single().playlist
        assertThat(playlist.pinnedAt).isEqualTo(5L)
        assertThat(playlist.description).isEqualTo("mine")
        // Bytes we hold keep their downloaded flag.
        assertThat(db.dao().songEntity("s1")!!.isDownloaded).isTrue()
    }

    @Test fun oldMetrolist10_fillsColumnsItLacks() = runBlocking {
        val staged = stage { dir ->
            upstreamDb("metrolist-10", dir).use { d ->
                d.row("song", "id" to "s1", "title" to "Unfinished Sympathy", "duration" to 310, "liked" to 1, "downloadState" to 3)
                d.row("artist", "id" to "a1", "name" to "Massive Attack")
                d.row("song_artist_map", "songId" to "s1", "artistId" to "a1", "position" to 0)
            }
        }
        assertThat(importer.preview(staged).origin).isEqualTo(BackupImporter.Origin.UNKNOWN)
        val result = importer.import(staged, includeAccount = true, downloadedIds = emptySet())
        assertThat(result.copied["song"]).isEqualTo(1)
        val song = db.dao().song("s1").first()!!
        assertThat(song.song.title).isEqualTo("Unfinished Sympathy")
        assertThat(song.song.liked).isTrue()
        assertThat(song.artists.single().name).isEqualTo("Massive Attack")
    }

    @Test fun orphanRowsAreSkippedWithWarning() = runBlocking {
        val staged = stage { dir ->
            upstreamDb("metrolist-38", dir).use { d ->
                d.execSQL("PRAGMA foreign_keys = OFF")
                library(d)
                d.row("playlist_song_map", "playlistId" to "p1", "songId" to "ghost", "position" to 2)
            }
        }
        val result = importer.import(staged, includeAccount = true, downloadedIds = emptySet())
        assertThat(result.copied["playlist_song_map"]).isEqualTo(2)
        assertThat(result.warnings.single()).contains("playlist_song_map: 1 of 3 rows skipped")
    }

    @Test fun replacesExistingLibrary() = runBlocking {
        db.dao().upsert(dev.musicforge.data.db.SongEntity(id = "local", title = "Before restore"))
        val staged = stage { dir -> upstreamDb("metrolist-38", dir).use { library(it) } }
        importer.import(staged, includeAccount = true, downloadedIds = emptySet())
        assertThat(db.dao().songEntity("local")).isNull()
    }

    @Test fun settingsAreImportedAndCoerced() = runBlocking {
        val staged = stage { dir ->
            upstreamDb("metrolist-38", dir).close()
            File(dir, BackupImporter.SETTINGS_FILE).writeBytes(
                PreferencesProto.write(
                    mapOf(
                        "innerTubeCookie" to PreferencesProto.Value.Str("SAPISID=abc"),
                        "accountName" to PreferencesProto.Value.Str("Pat"),
                        "crossfadeDurationFloat" to PreferencesProto.Value.F32(4.5f),
                        "maxSongCacheSize" to PreferencesProto.Value.I64(1024L), // long upstream, int here
                        "pureBlack" to PreferencesProto.Value.Bool(true),
                        "someConvxOnlyThing" to PreferencesProto.Value.StrSet(setOf("a", "b")),
                    ),
                ),
            )
        }
        assertThat(importer.preview(staged).accountName).isEqualTo("Pat")
        val result = importer.import(staged, includeAccount = true, downloadedIds = emptySet())
        assertThat(result.settingsImported).isEqualTo(5)
        val prefs = settings.data.first()
        assertThat(prefs[Keys.InnerTubeCookie]).isEqualTo("SAPISID=abc")
        assertThat(prefs[Keys.CrossfadeSeconds]).isEqualTo(4.5f)
        assertThat(prefs[Keys.MaxSongCacheSizeMb]).isEqualTo(1024)
        assertThat(prefs[Keys.PureBlack]).isTrue()
    }

    @Test fun settingsWithoutAccount() = runBlocking {
        val staged = stage { dir ->
            upstreamDb("convx-40", dir).close()
            File(dir, BackupImporter.SETTINGS_FILE).writeBytes(
                PreferencesProto.write(mapOf("innerTubeCookie" to PreferencesProto.Value.Str("x"), "darkMode" to PreferencesProto.Value.Str("ON"))),
            )
        }
        importer.import(staged, includeAccount = false, downloadedIds = emptySet())
        val prefs = settings.data.first()
        assertThat(prefs[Keys.InnerTubeCookie]).isNull()
        assertThat(prefs[Keys.DarkMode]).isEqualTo("ON")
    }

    @Test fun unzipIgnoresUnknownAndTraversalEntries() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("../evil.db")); z.write(1); z.closeEntry()
                z.putNextEntry(ZipEntry("home_background_files/bg.jpg")); z.write(2); z.closeEntry()
                z.putNextEntry(ZipEntry("song.db")); z.write(3); z.closeEntry()
            }
        }.toByteArray()
        val dir = tmp.newFolder()
        BackupImporter.unzip(ByteArrayInputStream(bytes), dir)
        assertThat(dir.list()!!.toList()).containsExactly("song.db")
        assertThat(File(dir.parentFile, "evil.db").exists()).isFalse()
    }

    @Test(expected = BackupImporter.InvalidBackupException::class)
    fun zipWithoutDatabaseIsRejected() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("settings.preferences_pb")); z.closeEntry() }
        }.toByteArray()
        BackupImporter.unzip(ByteArrayInputStream(bytes), tmp.newFolder())
    }

    @Test fun protoRoundTrip() {
        val values = mapOf(
            "b" to PreferencesProto.Value.Bool(true),
            "f" to PreferencesProto.Value.F32(1.25f),
            "i" to PreferencesProto.Value.I32(-7),
            "l" to PreferencesProto.Value.I64(1L shl 40),
            "s" to PreferencesProto.Value.Str("héllo"),
            "ss" to PreferencesProto.Value.StrSet(setOf("x", "y")),
            "d" to PreferencesProto.Value.F64(2.5),
        )
        assertThat(PreferencesProto.parse(PreferencesProto.write(values))).isEqualTo(values)
    }
}
