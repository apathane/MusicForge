package dev.musicforge.ui

import android.net.Uri
import androidx.navigation.NavController
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import dev.musicforge.playback.PlayerConnection
import dev.musicforge.playback.toMediaItem

/** What a collection page shows. Encoded into the route as `collection/{kind}/{id}`. */
enum class CollectionKind {
    /** A playlist in our DB (restored or created here). */
    LOCAL_PLAYLIST,

    /** A YouTube Music playlist, fetched live. */
    REMOTE_PLAYLIST,
    ALBUM,
    LIKED,
    SONGS,
    HISTORY,
    DOWNLOADED,
}

object Routes {
    const val COLLECTION = "collection/{kind}/{id}"
    const val ARTIST = "artist/{id}"
    const val ALBUMS = "library/albums"
    const val ARTISTS = "library/artists"
    const val LOGIN = "login"
    const val ABOUT = "about"
    const val STATS = "stats"
    const val EQUALIZER = "equalizer"

    fun collection(kind: CollectionKind, id: String = "-") = "collection/${kind.name}/${Uri.encode(id)}"
    fun artist(id: String) = "artist/${Uri.encode(id)}"
}

/**
 * One place that decides what tapping an item does, so Home, Search and artist pages agree.
 * Songs play with their neighbours as the queue; everything else opens its page.
 */
fun openItem(item: YTItem, siblings: List<YTItem>, nav: NavController, player: PlayerConnection) {
    when (item) {
        is SongItem -> {
            val songs = siblings.filterIsInstance<SongItem>().ifEmpty { listOf(item) }
            player.play(songs.map { it.toMediaItem() }, songs.indexOf(item).coerceAtLeast(0))
        }
        is AlbumItem -> nav.navigate(Routes.collection(CollectionKind.ALBUM, item.browseId))
        is PlaylistItem -> nav.navigate(Routes.collection(CollectionKind.REMOTE_PLAYLIST, item.id))
        is ArtistItem -> nav.navigate(Routes.artist(item.id))
        else -> Unit
    }
}
