package dev.musicforge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class, ArtistEntity::class, AlbumEntity::class, PlaylistEntity::class,
        SongArtistMap::class, SongAlbumMap::class, AlbumArtistMap::class, PlaylistSongMap::class,
        SearchHistory::class, FormatEntity::class, LyricsEntity::class, Event::class,
        RelatedSongMap::class, SetVideoIdEntity::class, PlayCountEntity::class,
        RecognitionHistory::class, SpeedDialItem::class, PodcastEntity::class, SongAnalysis::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun dao(): MusicDao

    companion object {
        /** Same file name as Metrolist and ConvX, so our backups share their layout. */
        const val DB_NAME = "song.db"

        fun open(context: Context): MusicDatabase =
            Room.databaseBuilder(context, MusicDatabase::class.java, DB_NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
