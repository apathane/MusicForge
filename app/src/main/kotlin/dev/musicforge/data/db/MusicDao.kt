package dev.musicforge.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A song with its artists, in credited order. */
data class Song(
    @Embedded val song: SongEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(SongArtistMap::class, parentColumn = "songId", entityColumn = "artistId"),
    )
    val artists: List<ArtistEntity>,
) {
    val id get() = song.id
    val artistLine get() = artists.joinToString { it.name }
}

data class PlaylistSummary(
    @Embedded val playlist: PlaylistEntity,
    val songCount: Int,
)

data class LibraryCounts(
    val songs: Int,
    val liked: Int,
    val downloaded: Int,
    val playlists: Int,
    val albums: Int,
    val artists: Int,
    val events: Int,
)

@Dao
interface MusicDao {
    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY inLibrary DESC")
    fun librarySongs(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY likedDate DESC")
    fun likedSongs(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isDownloaded ORDER BY dateDownload DESC")
    fun downloadedSongs(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :id")
    fun song(id: String): Flow<Song?>

    @Transaction
    @Query("SELECT * FROM song WHERE id IN (:ids)")
    fun songs(ids: List<String>): Flow<List<Song>>

    @Query("SELECT * FROM song WHERE id = :id")
    suspend fun songEntity(id: String): SongEntity?

    @Query(
        """
        SELECT playlist.*, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount
        FROM playlist ORDER BY COALESCE(pinnedAt, 0) DESC, COALESCE(lastUpdateTime, createdAt, 0) DESC
        """,
    )
    fun playlists(): Flow<List<PlaylistSummary>>

    @Transaction
    @Query(
        """
        SELECT song.* FROM playlist_song_map JOIN song ON song.id = playlist_song_map.songId
        WHERE playlistId = :playlistId ORDER BY position
        """,
    )
    fun playlistSongs(playlistId: String): Flow<List<Song>>

    @Query("SELECT * FROM playlist WHERE id = :id")
    fun playlist(id: String): Flow<PlaylistEntity?>

    /** Most recently played first, one row per song. */
    @Transaction
    @Query(
        """
        SELECT song.* FROM song JOIN (SELECT songId, MAX(timestamp) AS lastPlayed FROM event GROUP BY songId) e
        ON e.songId = song.id ORDER BY e.lastPlayed DESC LIMIT :limit
        """,
    )
    fun historySongs(limit: Int = 300): Flow<List<Song>>

    /** Songs we know belong to an album, for offline album pages. */
    @Transaction
    @Query("SELECT * FROM song WHERE albumId = :albumId ORDER BY title")
    fun albumSongsLocal(albumId: String): Flow<List<Song>>

    @Query("SELECT * FROM album WHERE id = :id")
    suspend fun albumEntity(id: String): AlbumEntity?

    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL OR inLibrary IS NOT NULL ORDER BY COALESCE(pinnedAt, 0) DESC, COALESCE(bookmarkedAt, inLibrary) DESC")
    fun libraryAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt DESC")
    fun libraryArtists(): Flow<List<ArtistEntity>>

    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM song WHERE inLibrary IS NOT NULL) AS songs,
          (SELECT COUNT(*) FROM song WHERE liked) AS liked,
          (SELECT COUNT(*) FROM song WHERE isDownloaded) AS downloaded,
          (SELECT COUNT(*) FROM playlist) AS playlists,
          (SELECT COUNT(*) FROM album WHERE bookmarkedAt IS NOT NULL OR inLibrary IS NOT NULL) AS albums,
          (SELECT COUNT(*) FROM artist WHERE bookmarkedAt IS NOT NULL) AS artists,
          (SELECT COUNT(*) FROM event) AS events
        """,
    )
    fun counts(): Flow<LibraryCounts>

    @Query("SELECT query FROM search_history ORDER BY id DESC LIMIT :limit")
    fun searchHistory(limit: Int = 20): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(entry: SearchHistory)

    @Upsert suspend fun upsert(song: SongEntity)
    @Upsert suspend fun upsert(artist: ArtistEntity)
    @Upsert suspend fun upsert(album: AlbumEntity)
    @Upsert suspend fun upsert(map: SongArtistMap)
    @Upsert suspend fun upsert(format: FormatEntity)
    @Upsert suspend fun upsert(lyrics: LyricsEntity)

    @Insert suspend fun insert(event: Event)

    @Query("SELECT * FROM artist WHERE id = :id")
    suspend fun artist(id: String): ArtistEntity?

    @Query("SELECT * FROM format WHERE id = :id")
    suspend fun format(id: String): FormatEntity?

    @Query("SELECT liked FROM song WHERE id = :id")
    fun isLiked(id: String): Flow<Boolean?>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_song_map WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: String): Int

    @Insert suspend fun insert(map: PlaylistSongMap)

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(playlist: PlaylistEntity)

    @Query("UPDATE playlist SET lastUpdateTime = :at WHERE id = :id")
    suspend fun touchPlaylist(id: String, at: Long)

    @Query("UPDATE song SET liked = :liked, likedDate = :at WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean, at: Long?)

    @Query("UPDATE song SET isDownloaded = :downloaded, dateDownload = :at WHERE id = :id")
    suspend fun setDownloaded(id: String, downloaded: Boolean, at: Long?)

    @Query("UPDATE song SET totalPlayTime = totalPlayTime + :ms WHERE id = :id")
    suspend fun addPlayTime(id: String, ms: Long)
}
