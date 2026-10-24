package dev.musicforge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.completed
import dev.musicforge.data.db.AlbumEntity
import dev.musicforge.data.db.ArtistEntity
import dev.musicforge.data.db.MusicDatabase
import dev.musicforge.data.db.PlaylistEntity
import dev.musicforge.data.db.PlaylistSongMap
import dev.musicforge.data.prefs.Keys
import dev.musicforge.playback.PlayRecorder
import dev.musicforge.playback.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * Mirrors the signed-in YouTube Music library into Room: liked songs, library songs, saved albums,
 * followed artists and the account's playlists with their songs. Ported from Metrolist's
 * SyncUtils, keeping its safety rule: something is removed locally only if the previous complete
 * remote snapshot had it and the current one doesn't, so a failed or partial fetch never wipes
 * the library, and things only added on this phone are kept.
 */
class AccountSync(
    context: Context,
    private val db: MusicDatabase,
    private val recorder: PlayRecorder,
    private val settings: DataStore<Preferences>,
) {
    sealed interface Status {
        data object Idle : Status
        data class Running(val step: String) : Status
        data class Done(val at: Long, val summary: String) : Status
        data class Failed(val message: String) : Status
    }

    private val dir = File(context.filesDir, "account-sync").apply { mkdirs() }
    private val lock = Mutex()
    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val dao get() = db.dao()

    /** Syncs when signed in and the last sync is older than [maxAgeMs]; app start uses this. */
    suspend fun syncIfStale(maxAgeMs: Long = 60 * 60 * 1000L) {
        val last = settings.data.first()[Keys.LastAccountSync] ?: 0L
        if (System.currentTimeMillis() - last >= maxAgeMs) sync()
    }

    /** Mirrors the account's YouTube Music history fresh enough to be useful on the Home shelf. */
    suspend fun syncHistoryIfStale(maxAgeMs: Long = 6 * 60 * 60 * 1000L) {
        if (YouTube.cookie == null) return
        val last = settings.data.first()[Keys.LastHistorySync] ?: 0L
        if (System.currentTimeMillis() - last < maxAgeMs) return
        syncHistory()
    }

    private suspend fun syncHistory() = withContext(Dispatchers.IO) {
        if (YouTube.cookie == null) return@withContext
        if (!lock.tryLock()) return@withContext
        try {
            step("History") { history() }
            settings.edit { it[Keys.LastHistorySync] = System.currentTimeMillis() }
        } catch (e: Exception) {
            Timber.w(e, "history sync failed")
        } finally {
            lock.unlock()
        }
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        if (YouTube.cookie == null) return@withContext
        if (!lock.tryLock()) return@withContext // one at a time; a second request rides on the first
        try {
            val counts = mutableListOf<String>()
            step("Liked songs") { likedSongs()?.let { counts += "$it liked" } }
            step("Library songs") { librarySongs()?.let { counts += "$it saved" } }
            step("Albums") { albums()?.let { counts += "$it albums" } }
            step("Artists") { artists()?.let { counts += "$it artists" } }
            step("Playlists") { playlists()?.let { counts += "$it playlists" } }
            val now = System.currentTimeMillis()
            settings.edit { it[Keys.LastAccountSync] = now }
            _status.value = Status.Done(now, counts.joinToString(", ").ifEmpty { "nothing to bring over" })
        } catch (e: Exception) {
            Timber.w(e, "account sync failed")
            _status.value = Status.Failed(e.message ?: "Sync failed")
        } finally {
            lock.unlock()
        }
    }

    private suspend fun step(name: String, block: suspend () -> Unit) {
        _status.value = Status.Running(name)
        // One section failing (a timeout, a changed response) doesn't stop the others.
        runCatching { block() }.onFailure { Timber.w(it, "account sync: %s failed", name) }
    }

    /** Liked songs, from the "LM" playlist. Returns how many, or null when the fetch failed. */
    private suspend fun likedSongs(): Int? {
        val page = YouTube.playlist("LM").completed().getOrNull() ?: return null
        val songs = page.songs
        val remote = songs.mapTo(LinkedHashSet()) { it.id }
        val advertised = page.playlist.songCountText?.filter(Char::isDigit)?.toIntOrNull()
        // Only a complete list may unlike anything.
        val complete = advertised == null || songs.size >= advertised
        val removed = if (complete) removedRemotely(dao.likedIds(), readSnapshot(LIKED), remote) else emptySet()
        val now = System.currentTimeMillis()
        db.withTransaction {
            removed.forEach { dao.setLiked(it, false, null) }
            songs.forEachIndexed { i, song ->
                recorder.ensureSong(song.toMediaItem())
                val row = dao.songEntity(song.id)
                if (row != null && !row.liked) dao.setLiked(song.id, true, row.likedDate ?: now - i * 1000L)
            }
        }
        if (complete) writeSnapshot(LIKED, remote)
        return songs.size
    }

    /** Songs saved to the library (not only liked). */
    private suspend fun librarySongs(): Int? {
        val page = YouTube.library("FEmusic_liked_videos").completed().getOrNull() ?: return null
        val songs = page.items.filterIsInstance<SongItem>()
        val remote = songs.mapTo(LinkedHashSet()) { it.id }
        // An empty page is more likely a failed fetch than an emptied library.
        val removed = if (remote.isEmpty()) emptySet() else removedRemotely(dao.libraryIds(), readSnapshot(LIBRARY), remote)
        val now = System.currentTimeMillis()
        db.withTransaction {
            removed.forEach { dao.setInLibrary(it, null) }
            songs.forEachIndexed { i, song ->
                recorder.ensureSong(song.toMediaItem())
                if (dao.songEntity(song.id)?.inLibrary == null) dao.setInLibrary(song.id, now - i * 1000L)
            }
        }
        if (remote.isNotEmpty()) writeSnapshot(LIBRARY, remote)
        return songs.size
    }

    private suspend fun albums(): Int? {
        val page = YouTube.library("FEmusic_liked_albums").completed().getOrNull() ?: return null
        val albums = page.items.filterIsInstance<AlbumItem>()
        val remote = albums.mapTo(LinkedHashSet()) { it.id }
        val removed = if (remote.isEmpty()) emptySet() else removedRemotely(dao.bookmarkedAlbumIds(), readSnapshot(ALBUMS), remote)
        val now = System.currentTimeMillis()
        db.withTransaction {
            removed.forEach { dao.setAlbumBookmark(it, null) }
            albums.forEachIndexed { i, album ->
                val row = dao.albumEntity(album.id)
                if (row == null) {
                    dao.upsert(
                        AlbumEntity(
                            id = album.id, playlistId = album.playlistId, title = album.title, year = album.year,
                            thumbnailUrl = album.thumbnail, explicit = album.explicit, bookmarkedAt = now - i * 1000L,
                        ),
                    )
                } else if (row.bookmarkedAt == null) {
                    dao.setAlbumBookmark(album.id, now - i * 1000L)
                }
            }
        }
        if (remote.isNotEmpty()) writeSnapshot(ALBUMS, remote)
        return albums.size
    }

    private suspend fun artists(): Int? {
        val page = YouTube.library("FEmusic_library_corpus_artists").completed().getOrNull() ?: return null
        val artists = page.items.filterIsInstance<ArtistItem>()
        val remote = artists.mapTo(LinkedHashSet()) { it.id }
        val removed = if (remote.isEmpty()) emptySet() else removedRemotely(dao.bookmarkedArtistIds(), readSnapshot(ARTISTS), remote)
        val now = System.currentTimeMillis()
        db.withTransaction {
            removed.forEach { dao.setArtistBookmark(it, null) }
            artists.forEachIndexed { i, artist ->
                val row = dao.artist(artist.id)
                val channelId = artist.channelId ?: artist.id.takeIf { it.startsWith("UC") }
                dao.upsert(
                    (row ?: ArtistEntity(id = artist.id, name = artist.title)).copy(
                        name = artist.title,
                        thumbnailUrl = artist.thumbnail ?: row?.thumbnailUrl,
                        channelId = channelId ?: row?.channelId,
                        bookmarkedAt = row?.bookmarkedAt ?: (now - i * 1000L),
                        lastUpdateTime = now,
                    ),
                )
            }
        }
        if (remote.isNotEmpty()) writeSnapshot(ARTISTS, remote)
        return artists.size
    }

    /** The account's saved and own playlists, each with its songs in the remote order. */
    private suspend fun playlists(): Int? {
        val page = YouTube.library("FEmusic_liked_playlists").completed().getOrNull() ?: return null
        val remote = page.items.filterIsInstance<PlaylistItem>()
            .filterNot { it.id == "LM" || it.id == "SE" } // Liked music is synced as likes; SE is "episodes for later"
            .distinctBy { it.id }
        val remoteIds = remote.mapTo(LinkedHashSet()) { it.id }
        // A playlist removed on YouTube goes here too (its songs stay in the library).
        if (remoteIds.isNotEmpty()) {
            removedRemotely(dao.syncedPlaylistBrowseIds(), readSnapshot(PLAYLISTS), remoteIds).forEach { browseId ->
                dao.playlistByBrowseId(browseId)?.let { dao.deletePlaylist(it.id) }
            }
        }
        for (item in remote) {
            val existing = dao.playlistByBrowseId(item.id)
            val entity = (existing ?: PlaylistEntity(id = UUID.randomUUID().toString(), name = item.title, browseId = item.id)).copy(
                name = item.title,
                thumbnailUrl = item.thumbnail ?: existing?.thumbnailUrl,
                isEditable = item.isEditable,
                remoteSongCount = item.songCountText?.let { Regex("""\d+""").find(it.replace(",", ""))?.value?.toIntOrNull() },
                playEndpointParams = item.playEndpoint?.params,
                shuffleEndpointParams = item.shuffleEndpoint?.params,
                radioEndpointParams = item.radioEndpoint?.params,
                bookmarkedAt = existing?.bookmarkedAt ?: System.currentTimeMillis(),
            )
            dao.upsert(entity)
            runCatching { playlistSongs(item.id, entity.id) }.onFailure { Timber.w(it, "playlist %s failed", item.title) }
            delay(150) // gentle on YouTube for accounts with many playlists
        }
        if (remoteIds.isNotEmpty()) writeSnapshot(PLAYLISTS, remoteIds)
        return remote.size
    }

    private suspend fun history(): Int? {
        val page = YouTube.musicHistory().getOrNull() ?: return null
        val songs = page.sections?.flatMap { it.songs } ?: emptyList()
        if (songs.isEmpty()) return 0
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.clearRemoteHistory()
            songs.forEachIndexed { i, song ->
                recorder.ensureSong(song.toMediaItem())
                dao.insert(dev.musicforge.data.db.RemoteHistoryEntity(songId = song.id, position = i, syncedAt = now))
            }
        }
        return songs.size
    }

    private suspend fun playlistSongs(browseId: String, playlistId: String) {
        val page = YouTube.playlist(browseId.removePrefix("VL")).completed().getOrNull() ?: return
        val songs = page.songs
        // An empty fetch for a playlist that says it has songs is a failed fetch, not an emptied playlist.
        val advertised = page.playlist.songCountText?.filter(Char::isDigit)?.toIntOrNull()
        if (songs.isEmpty() && (advertised ?: 0) > 0) return
        val remote = songs.map { it.id }
        if (remote == dao.playlistSongIds(playlistId)) return
        db.withTransaction {
            dao.clearPlaylistSongs(playlistId)
            songs.forEachIndexed { i, song ->
                recorder.ensureSong(song.toMediaItem())
                dao.insert(PlaylistSongMap(playlistId = playlistId, songId = song.id, position = i, setVideoId = song.setVideoId))
            }
        }
    }

    private fun readSnapshot(name: String): Set<String>? =
        File(dir, name).takeIf { it.exists() }?.readLines()?.filterTo(HashSet()) { it.isNotBlank() }

    private fun writeSnapshot(name: String, ids: Set<String>) {
        runCatching { File(dir, name).writeText(ids.joinToString("\n")) }
    }

    /** Signing out or switching account: the next account's first sync must not remove anything. */
    fun forgetSnapshots() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val LIKED = "liked"
        private const val LIBRARY = "library"
        private const val ALBUMS = "albums"
        private const val ARTISTS = "artists"
        private const val PLAYLISTS = "playlists"

        /**
         * Local items to drop because they were removed on YouTube: in the previous complete remote
         * snapshot but not in this one. Items never seen remotely (added on this phone) are kept,
         * and with no previous snapshot nothing is removed.
         */
        fun removedRemotely(local: Collection<String>, previous: Set<String>?, current: Set<String>): Set<String> =
            if (previous == null) emptySet() else local.filterTo(HashSet()) { it in previous && it !in current }
    }
}
