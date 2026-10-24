package dev.musicforge.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The schema is a superset of Metrolist (Room v38) and ConvX (Room v40). Table and
 * column names match theirs exactly, so a backup from either app restores with a plain
 * column-intersection copy (see BackupImporter). Dates are UTC epoch millis, which is
 * what both upstream TypeConverters write.
 */

@Entity(tableName = "song", indices = [Index("albumId")])
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    @ColumnInfo(defaultValue = "0") val explicit: Boolean = false,
    val year: Int? = null,
    val date: Long? = null,
    val dateModified: Long? = null,
    val liked: Boolean = false,
    val likedDate: Long? = null,
    val totalPlayTime: Long = 0,
    val inLibrary: Long? = null,
    val dateDownload: Long? = null,
    @ColumnInfo(defaultValue = "false") val isLocal: Boolean = false,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    @ColumnInfo(defaultValue = "0") val lyricsOffset: Int = 0,
    @ColumnInfo(defaultValue = "true") val romanizeLyrics: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isDownloaded: Boolean = false,
    @ColumnInfo(defaultValue = "false") val isUploaded: Boolean = false,
    @ColumnInfo(defaultValue = "false") val isVideo: Boolean = false,
    @ColumnInfo(defaultValue = "false") val isEpisode: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val playbackPosition: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val uploadEntityId: String? = null,
    @ColumnInfo(defaultValue = "0") val isCached: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isPodcastEpisode: Boolean = false,
)

@Entity(tableName = "artist")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val bookmarkedAt: Long? = null,
    @ColumnInfo(defaultValue = "false") val isLocal: Boolean = false,
    @ColumnInfo(defaultValue = "false") val isPodcastChannel: Boolean = false,
    val cachedPageJson: String? = null,
)

@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    @ColumnInfo(defaultValue = "0") val explicit: Boolean = false,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val bookmarkedAt: Long? = null,
    val likedDate: Long? = null,
    val inLibrary: Long? = null,
    @ColumnInfo(defaultValue = "false") val isLocal: Boolean = false,
    @ColumnInfo(defaultValue = "false") val isUploaded: Boolean = false,
    val description: String? = null,
    val pinnedAt: Long? = null,
)

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val browseId: String? = null,
    val createdAt: Long? = System.currentTimeMillis(),
    val lastUpdateTime: Long? = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "true") val isEditable: Boolean = true,
    val bookmarkedAt: Long? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val thumbnailUrl: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    @ColumnInfo(defaultValue = "false") val isLocal: Boolean = false,
    @ColumnInfo(defaultValue = "false") val isAutoSync: Boolean = false,
    val description: String? = null,
    val pinnedAt: Long? = null,
)

@Entity(
    tableName = "song_artist_map",
    primaryKeys = ["songId", "artistId"],
    indices = [Index("songId"), Index("artistId")],
    foreignKeys = [
        ForeignKey(SongEntity::class, ["id"], ["songId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ArtistEntity::class, ["id"], ["artistId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SongArtistMap(val songId: String, val artistId: String, val position: Int)

@Entity(
    tableName = "song_album_map",
    primaryKeys = ["songId", "albumId"],
    indices = [Index("songId"), Index("albumId")],
    foreignKeys = [
        ForeignKey(SongEntity::class, ["id"], ["songId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AlbumEntity::class, ["id"], ["albumId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SongAlbumMap(val songId: String, val albumId: String, val index: Int)

@Entity(
    tableName = "album_artist_map",
    primaryKeys = ["albumId", "artistId"],
    indices = [Index("albumId"), Index("artistId")],
    foreignKeys = [
        ForeignKey(AlbumEntity::class, ["id"], ["albumId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ArtistEntity::class, ["id"], ["artistId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class AlbumArtistMap(val albumId: String, val artistId: String, val order: Int)

@Entity(
    tableName = "playlist_song_map",
    indices = [Index("playlistId"), Index("songId")],
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SongEntity::class, ["id"], ["songId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PlaylistSongMap(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: String,
    val songId: String,
    val position: Int = 0,
    val setVideoId: String? = null,
)

@Entity(tableName = "search_history", indices = [Index("query", unique = true)])
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
)

@Entity(tableName = "format")
data class FormatEntity(
    @PrimaryKey val id: String,
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double? = null,
    val playbackUrl: String?,
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val id: String,
    val lyrics: String,
    @ColumnInfo(defaultValue = "Unknown") val provider: String = "Unknown",
    @ColumnInfo(defaultValue = "") val translatedLyrics: String = "",
    @ColumnInfo(defaultValue = "") val translationLanguage: String = "",
    @ColumnInfo(defaultValue = "") val translationMode: String = "",
)

@Entity(
    tableName = "event",
    indices = [Index("songId")],
    foreignKeys = [ForeignKey(SongEntity::class, ["id"], ["songId"], onDelete = ForeignKey.CASCADE)],
)
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val timestamp: Long,
    val playTime: Long,
)

@Entity(
    tableName = "related_song_map",
    indices = [Index("songId"), Index("relatedSongId")],
    foreignKeys = [
        ForeignKey(SongEntity::class, ["id"], ["songId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SongEntity::class, ["id"], ["relatedSongId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class RelatedSongMap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val relatedSongId: String,
)

@Entity(tableName = "set_video_id")
data class SetVideoIdEntity(
    @PrimaryKey val videoId: String,
    val setVideoId: String? = null,
)

@Entity(tableName = "playCount", primaryKeys = ["song", "year", "month"])
data class PlayCountEntity(
    val song: String,
    val year: Int,
    val month: Int,
    val count: Int,
)

@Entity(tableName = "recognition_history", indices = [Index("trackId")])
data class RecognitionHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverArtUrl: String? = null,
    val coverArtHqUrl: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val label: String? = null,
    val shazamUrl: String? = null,
    val appleMusicUrl: String? = null,
    val spotifyUrl: String? = null,
    val isrc: String? = null,
    val youtubeVideoId: String? = null,
    val recognizedAt: Long,
    val liked: Boolean = false,
)

@Entity(tableName = "speed_dial_item")
data class SpeedDialItem(
    @PrimaryKey val id: String,
    val secondaryId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val subtitleIds: String? = null,
    val thumbnailUrl: String? = null,
    val type: String,
    val explicit: Boolean = false,
    val createDate: Long = System.currentTimeMillis(),
    val albumId: String? = null,
    val albumName: String? = null,
)

@Entity(tableName = "podcast")
data class PodcastEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val bookmarkedAt: Long? = null,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
)

@Entity(tableName = "song_analysis")
data class SongAnalysis(
    @PrimaryKey val id: String,
    val bpm: Double,
    val anchorBeatMs: Double,
    val bpmConfidence: Double,
    val keyIndex: Int,
    val keyIsMinor: Boolean,
    val keyConfidence: Double,
    val energyProfile: ByteArray,
    val analyzedAt: Long,
    val analysisVersion: Int,
) {
    override fun equals(other: Any?) = other is SongAnalysis && other.id == id && other.analyzedAt == analyzedAt
    override fun hashCode() = id.hashCode()
}

/*
 * MusicForge's own tables. Not part of the upstream schemas, so a Metrolist or ConvX restore
 * neither copies nor wipes them (BackupImporter.TABLE_ORDER lists only upstream tables).
 */

/** The listener's correction of a Home suggestion: hide a song, or hear less of an artist for a while. */
@Entity(tableName = "recommendation_feedback", indices = [Index("mediaId"), Index("artistId"), Index("expiresAt")])
data class RecommendationFeedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Set for [Action.HIDE]. */
    val mediaId: String? = null,
    /** Set for [Action.LESS_OF_ARTIST]. */
    val artistId: String? = null,
    val action: String,
    val createdAt: Long,
    /** Null means it lasts until the listener resets Home. */
    val expiresAt: Long? = null,
) {
    enum class Action { HIDE, LESS_OF_ARTIST }
}

/**
 * A play registered on the YouTube Music account (another device), from the last successful history
 * sync. MusicForge's own table: not in BackupImporter.TABLE_ORDER, so restores neither copy nor
 * wipe it. Replaced wholesale each successful sync.
 */
@Entity(tableName = "remote_history", primaryKeys = ["songId"])
data class RemoteHistoryEntity(
    val songId: String,
    val position: Int,
    val syncedAt: Long,
)
