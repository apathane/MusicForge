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

/** A song with how often and how long it was played in a period. */
data class SongStat(
    @Embedded val song: SongEntity,
    val plays: Int,
    val timeMs: Long,
)

data class ArtistStat(
    @Embedded val artist: ArtistEntity,
    val plays: Int,
    val timeMs: Long,
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

    /** Home shows a few; the full list can be thousands of rows. */
    @Transaction
    @Query("SELECT * FROM song WHERE isDownloaded ORDER BY dateDownload DESC LIMIT :limit")
    fun recentDownloads(limit: Int): Flow<List<Song>>

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
        WHERE playlistId = :playlistId ORDER BY position, playlist_song_map.id
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

    /** Everything a user would be upset to lose, as one number. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM song WHERE inLibrary IS NOT NULL OR liked OR isDownloaded)
             + (SELECT COUNT(*) FROM playlist) + (SELECT COUNT(*) FROM event)
             + (SELECT COUNT(*) FROM album WHERE bookmarkedAt IS NOT NULL)
             + (SELECT COUNT(*) FROM artist WHERE bookmarkedAt IS NOT NULL)
        """,
    )
    suspend fun librarySize(): Int

    @Query(
        """
        SELECT song.*, COUNT(event.id) AS plays, SUM(event.playTime) AS timeMs
        FROM event JOIN song ON song.id = event.songId
        WHERE event.timestamp >= :since
        GROUP BY event.songId ORDER BY plays DESC, timeMs DESC LIMIT :limit
        """,
    )
    fun topSongs(since: Long, limit: Int = 25): Flow<List<SongStat>>

    @Query(
        """
        SELECT artist.*, COUNT(event.id) AS plays, SUM(event.playTime) AS timeMs
        FROM event JOIN song_artist_map m ON m.songId = event.songId AND m.position = 0
        JOIN artist ON artist.id = m.artistId
        WHERE event.timestamp >= :since
        GROUP BY artist.id ORDER BY timeMs DESC LIMIT :limit
        """,
    )
    fun topArtists(since: Long, limit: Int = 12): Flow<List<ArtistStat>>

    /** Songs played at least twice at these local hours of the day, across all history. */
    @Query(
        """
        SELECT song.*, COUNT(event.id) AS plays, SUM(event.playTime) AS timeMs
        FROM event JOIN song ON song.id = event.songId
        WHERE CAST(strftime('%H', event.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) IN (:hours)
        GROUP BY event.songId HAVING plays >= 2 ORDER BY plays DESC, timeMs DESC LIMIT :limit
        """,
    )
    fun songsAtHours(hours: List<Int>, limit: Int = 40): Flow<List<SongStat>>

    /**
     * Liked before [likedBefore] (or at an unknown time) and not played since [notPlayedSince]:
     * the ones that slipped away. [seed] picks a different sample of the whole set each day.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM song WHERE liked AND (likedDate IS NULL OR likedDate < :likedBefore)
          AND id NOT IN (SELECT songId FROM event WHERE timestamp >= :notPlayedSince)
        ORDER BY (rowid * 2654435761 + :seed * 40503) % 1000003 LIMIT :limit
        """,
    )
    fun forgottenLikes(likedBefore: Long, notPlayedSince: Long, seed: Int, limit: Int = 60): Flow<List<Song>>

    @Query("SELECT COALESCE(SUM(playTime), 0) FROM event WHERE timestamp >= :since")
    fun listenedMs(since: Long): Flow<Long>

    @Query("SELECT COUNT(DISTINCT songId) FROM event WHERE timestamp >= :since")
    fun distinctSongs(since: Long): Flow<Int>

    /** Songs you have (in library, liked, downloaded or played) matching title or artist. */
    @Transaction
    @Query(
        """
        SELECT DISTINCT song.* FROM song
        LEFT JOIN song_artist_map m ON m.songId = song.id
        LEFT JOIN artist ON artist.id = m.artistId
        WHERE (song.title LIKE '%' || :q || '%' OR artist.name LIKE '%' || :q || '%')
          AND (song.inLibrary IS NOT NULL OR song.liked OR song.isDownloaded OR song.totalPlayTime > 0)
        ORDER BY song.isDownloaded DESC, song.totalPlayTime DESC LIMIT :limit
        """,
    )
    suspend fun searchLibrary(q: String, limit: Int = 12): List<Song>

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

    @Query("SELECT * FROM lyrics WHERE id = :id")
    suspend fun lyrics(id: String): LyricsEntity?

    @Query("SELECT * FROM format WHERE id = :id")
    suspend fun format(id: String): FormatEntity?

    @Query("SELECT liked FROM song WHERE id = :id")
    fun isLiked(id: String): Flow<Boolean?>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_song_map WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: String): Int

    @Insert suspend fun insert(map: PlaylistSongMap)

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(playlist: PlaylistEntity)

    /** Removes one occurrence (the earliest) of a song from a playlist. */
    @Query(
        """
        DELETE FROM playlist_song_map WHERE id =
          (SELECT id FROM playlist_song_map WHERE playlistId = :playlistId AND songId = :songId ORDER BY position LIMIT 1)
        """,
    )
    suspend fun removeFromPlaylist(playlistId: String, songId: String)

    @Query("SELECT id FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position, id")
    suspend fun playlistMapIds(playlistId: String): List<Int>

    @Query("UPDATE playlist_song_map SET position = :position WHERE id = :mapId")
    suspend fun setMapPosition(mapId: Int, position: Int)

    @Query("UPDATE playlist SET name = :name, lastUpdateTime = :at WHERE id = :id")
    suspend fun renamePlaylist(id: String, name: String, at: Long)

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("UPDATE playlist SET lastUpdateTime = :at WHERE id = :id")
    suspend fun touchPlaylist(id: String, at: Long)

    @Query("UPDATE song SET liked = :liked, likedDate = :at WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean, at: Long?)

    @Query("SELECT id FROM song WHERE isDownloaded")
    suspend fun downloadedIds(): List<String>

    @Query("UPDATE song SET isDownloaded = :downloaded, dateDownload = :at WHERE id = :id")
    suspend fun setDownloaded(id: String, downloaded: Boolean, at: Long?)

    /** Corrections still in force at [now]. */
    @Query("SELECT * FROM recommendation_feedback WHERE expiresAt IS NULL OR expiresAt > :now")
    fun activeFeedback(now: Long): Flow<List<RecommendationFeedback>>

    @Insert suspend fun insert(feedback: RecommendationFeedback)

    @Query("DELETE FROM recommendation_feedback")
    suspend fun clearFeedback()

    @Query("UPDATE song SET totalPlayTime = totalPlayTime + :ms WHERE id = :id")
    suspend fun addPlayTime(id: String, ms: Long)
}
