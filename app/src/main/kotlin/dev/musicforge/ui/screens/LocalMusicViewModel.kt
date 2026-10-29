package dev.musicforge.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.musicforge.AppGraph
import dev.musicforge.data.db.MusicDao
import dev.musicforge.data.db.Song
import dev.musicforge.ui.LocalSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** State for the Local music screen: the song list, live query, and current sort. */
data class LocalMusicState(
    val songs: List<Song> = emptyList(),
    val query: String = "",
    val sort: LocalSort = LocalSort.TITLE,
    val count: Int = 0,
)

class LocalMusicViewModel(
    graph: AppGraph,
    savedSort: LocalSort? = null,
) : ViewModel() {
    private val dao: MusicDao = graph.database.dao()
    private val scope = viewModelScope

    /** Sort order picked in the menu, persisted across opens via the route. */
    private val sort = MutableStateFlow(savedSort ?: LocalSort.TITLE)
    private val query = MutableStateFlow("")

    /** The song list, re-queried from the DB whenever sort or query changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val songs: Flow<List<Song>> = combine(sort, query) { s, q -> s to q }
        .distinctUntilChanged()
        .flatMapLatest { (s, q) ->
            val base = when (s) {
                LocalSort.TITLE -> dao.localSongsByNameAsc()
                LocalSort.DATE_ADDED -> dao.localSongsByDateAddedDesc()
                LocalSort.ALBUM -> dao.localSongsByAlbumSort()
                LocalSort.ARTIST -> dao.localSongsByArtistSort()
            }
            if (q.isBlank()) base else base.map { list -> list.filter { matches(it, q) } }
        }

    val ui: StateFlow<LocalMusicState> = combine(songs, sort, query) { list, s, q ->
        LocalMusicState(songs = list, query = q, sort = s, count = list.size)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), LocalMusicState())

    /** Re-apply the remembered sort when the screen is recreated with a new route arg. */
    fun onRestoreSort(newSort: LocalSort) {
        sort.value = newSort
    }

    fun onSortChanged(newSort: LocalSort) {
        sort.value = newSort
    }

    fun onQueryChanged(newQuery: String) {
        query.value = newQuery
    }

    private fun matches(song: Song, query: String): Boolean {
        val q = query
        return song.song.title.contains(q, ignoreCase = true) ||
            song.artistLine.contains(q, ignoreCase = true) ||
            (song.song.albumName?.contains(q, ignoreCase = true) ?: false)
    }
}
