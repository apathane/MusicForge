package dev.musicforge.data

import com.metrolist.innertube.YouTube
import dev.musicforge.data.db.MusicDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections

/**
 * Picture links saved in the library go stale: restored backups carry artist avatars that now
 * answer 403 and generated playlist covers that answer 404 (about 3% of each on the X7b's
 * library), and artists that came in as song credits have no picture at all. When a picture
 * fails or is missing, look the item up again and save the link YouTube gives now; Room's flows
 * then redraw it. Each link or id is tried once per process, a few at a time.
 */
class ArtRepair(private val dao: MusicDao, private val scope: CoroutineScope) {
    private val tried = Collections.synchronizedSet(HashSet<String>())
    private val gate = Semaphore(3)

    /** [url] failed to load: refresh every row that shows it. */
    fun failed(url: String) {
        if (!tried.add(url)) return
        scope.launch {
            gate.withPermit {
                dao.songIdsWithThumb(url).forEach { id -> song(id)?.let { dao.setSongThumb(id, it) } }
                var missed = false
                dao.albumIdsWithThumb(url).forEach { id ->
                    YouTube.album(id, withSongs = false).getOrNull()?.album?.thumbnail?.let { dao.setAlbumThumb(id, it) } ?: run { missed = true }
                }
                dao.artistIdsWithThumb(url).forEach { id -> artist(id)?.let { dao.setArtistThumb(id, it) } ?: run { missed = true } }
                dao.playlistBrowseIdsWithThumb(url).forEach { browseId ->
                    YouTube.playlist(browseId.removePrefix("VL")).getOrNull()?.playlist?.thumbnail?.let { dao.setPlaylistThumb(browseId, it) } ?: run { missed = true }
                }
                // A lookup that failed (flaky network, a 5xx) may be tried again the next time it shows.
                if (missed) tried.remove(url)
            }
        }
    }

    /** An artist row has no picture yet. */
    fun missingArtist(id: String) {
        if (!tried.add(id)) return
        scope.launch { gate.withPermit { artist(id)?.let { dao.setArtistThumb(id, it) } ?: tried.remove(id) } }
    }

    private suspend fun artist(id: String) = YouTube.artist(id).getOrNull()?.artist?.thumbnail

    // A song's own video thumbnail always exists, so it is the fallback when the lookup fails.
    private suspend fun song(id: String) =
        YouTube.queue(listOf(id)).getOrNull()?.firstOrNull()?.thumbnail ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
}
