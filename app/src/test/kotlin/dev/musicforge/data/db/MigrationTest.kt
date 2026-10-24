package dev.musicforge.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The user's real library lives in a version-1 database; moving to 2 must keep every row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MusicDatabase::class.java)

    @Test
    fun oneToTwoKeepsTheLibraryAndAddsFeedback() {
        helper.createDatabase("m.db", 1).use { db ->
            db.execSQL("INSERT INTO song (id, title, duration, liked, totalPlayTime) VALUES ('v1', 'Roads', 305, 1, 0)")
            db.execSQL("INSERT INTO event (songId, timestamp, playTime) VALUES ('v1', 1, 60000)")
        }
        helper.runMigrationsAndValidate("m.db", 2, true).use { db ->
            db.query("SELECT title, liked FROM song WHERE id = 'v1'").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("Roads")
                assertThat(it.getInt(1)).isEqualTo(1)
            }
            db.query("SELECT COUNT(*) FROM event").use { it.moveToFirst(); assertThat(it.getInt(0)).isEqualTo(1) }
            db.execSQL("INSERT INTO recommendation_feedback (mediaId, action, createdAt) VALUES ('v1', 'HIDE', 2)")
        }
    }

    @Test
    fun twoToThreeAddsRemoteHistory() {
        helper.createDatabase("m.db", 2).use { db ->
            db.execSQL("INSERT INTO song (id, title, duration, liked, totalPlayTime) VALUES ('v1', 'Roads', 305, 1, 0)")
            db.execSQL("INSERT INTO event (songId, timestamp, playTime) VALUES ('v1', 1, 60000)")
            db.execSQL("INSERT INTO recommendation_feedback (mediaId, action, createdAt) VALUES ('v1', 'HIDE', 2)")
        }
        helper.runMigrationsAndValidate("m.db", 3, true).use { db ->
            // Existing rows survive the migration.
            db.query("SELECT title, liked FROM song WHERE id = 'v1'").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("Roads")
            }
            db.query("SELECT COUNT(*) FROM event").use { it.moveToFirst(); assertThat(it.getInt(0)).isEqualTo(1) }
            db.query("SELECT COUNT(*) FROM recommendation_feedback").use { it.moveToFirst(); assertThat(it.getInt(0)).isEqualTo(1) }
            // The new remote_history table exists and is empty.
            db.query("SELECT COUNT(*) FROM remote_history").use { it.moveToFirst(); assertThat(it.getInt(0)).isEqualTo(0) }
            // It accepts inserts.
            db.execSQL("INSERT INTO remote_history (songId, position, syncedAt) VALUES ('v1', 0, 1000)")
            db.query("SELECT position, syncedAt FROM remote_history WHERE songId = 'v1'").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getInt(0)).isEqualTo(0)
                assertThat(it.getLong(1)).isEqualTo(1000)
            }
        }
    }
}
