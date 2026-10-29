package dev.musicforge.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SongStatsDaoTest {
    private lateinit var db: MusicDatabase

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, MusicDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    @Test fun statsAggregatePlayCountAndTime() = runBlocking {
        val dao = db.dao()
        dao.upsert(SongEntity(id = "v1", title = "Roads"))
        dao.insert(Event(songId = "v1", timestamp = 1, playTime = 30_000L))
        dao.insert(Event(songId = "v1", timestamp = 2, playTime = 40_000L))

        val stats = dao.statsFor("v1")
        assertThat(stats.plays).isEqualTo(2)
        assertThat(stats.timeMs).isEqualTo(70_000L)
    }

    @Test fun statsAreZeroForUntouchedSong() = runBlocking {
        val dao = db.dao()
        dao.upsert(SongEntity(id = "v1", title = "Roads"))

        val stats = dao.statsFor("v1")
        assertThat(stats.plays).isEqualTo(0)
        assertThat(stats.timeMs).isEqualTo(0L)
    }

    @Test fun deleteEventsRemovesAllCheckpoints() {
        val dao = db.dao()
        runBlocking {
            dao.upsert(SongEntity(id = "v1", title = "Roads"))
            dao.insert(Event(songId = "v1", timestamp = 1, playTime = 30_000L))
            dao.insert(Event(songId = "v1", timestamp = 2, playTime = 40_000L))
        }

        runBlocking { dao.deleteEvents("v1") }
        val after = runBlocking { dao.statsFor("v1") }
        assertThat(after.plays).isEqualTo(0)
        assertThat(after.timeMs).isEqualTo(0L)
    }

    @Test fun deleteEventsOnlyTouchesTheNamedSong() {
        val dao = db.dao()
        runBlocking {
            dao.upsert(SongEntity(id = "v1", title = "Roads"))
            dao.upsert(SongEntity(id = "v2", title = "Teardrop"))
            dao.insert(Event(songId = "v1", timestamp = 1, playTime = 30_000L))
            dao.insert(Event(songId = "v2", timestamp = 2, playTime = 40_000L))
        }

        runBlocking { dao.deleteEvents("v1") }
        assertThat(runBlocking { dao.statsFor("v1") }.plays).isEqualTo(0)
        assertThat(runBlocking { dao.statsFor("v2") }.plays).isEqualTo(1)
    }
}
