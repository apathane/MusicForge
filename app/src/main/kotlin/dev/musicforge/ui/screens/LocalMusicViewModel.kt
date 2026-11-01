package dev.musicforge.ui.screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.musicforge.AppGraph
import dev.musicforge.data.db.MusicDao
import dev.musicforge.data.db.Song
import dev.musicforge.data.local.LocalAudioScanner
import dev.musicforge.data.prefs.Keys
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the Local music screen: the song list, live query, current sort, and permission state. */
data class LocalMusicState(
    val songs: List<Song> = emptyList(),
    val query: String = "",
    val sort: LocalSort = LocalSort.TITLE,
    val count: Int = 0,
    /** Whether the storage permission this build needs is currently granted. */
    val hasPermission: Boolean = false,
    /**
     * True when the user denied the permission and ticked "don't ask again": only the app's
     * settings page can fix it now, so the "Allow access" button opens settings instead of
     * requesting the permission again.
     */
    val permissionDeniedPermanently: Boolean = false,
)

/** Granted or not, plus whether asking again is even worth it. */
private data class PermissionState(val granted: Boolean, val deniedPermanently: Boolean)

class LocalMusicViewModel(
    private val graph: AppGraph,
    savedSort: LocalSort? = null,
) : ViewModel() {
    private val dao: MusicDao = graph.database.dao()
    private val app: Context = graph.app

    /** READ_MEDIA_AUDIO on API 33+, READ_EXTERNAL_STORAGE below: the manifest has both. */
    private val requiredPermission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) READ_MEDIA_AUDIO else READ_EXTERNAL_STORAGE

    private val sort = MutableStateFlow(savedSort ?: LocalSort.TITLE)
    private val query = MutableStateFlow("")
    private val permission = MutableStateFlow(PermissionState(granted = false, deniedPermanently = false))

    /** The song list, re-queried from the DB whenever sort or query changes (Room re-emits the
     * flow when the song table changes, so a finished scan refreshes the list by itself). */
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

    val ui: StateFlow<LocalMusicState> = combine(songs, sort, query, permission) { list, s, q, p ->
        LocalMusicState(
            songs = list,
            query = q,
            sort = s,
            count = list.size,
            hasPermission = p.granted,
            permissionDeniedPermanently = p.deniedPermanently,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalMusicState())

    /** True while a scan the user started is running. */
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Result of the most recent scan, shown until the next scan. Null before the first scan. */
    private val _lastResult = MutableStateFlow<LocalAudioScanner.ScanResult?>(null)
    val lastResult: StateFlow<LocalAudioScanner.ScanResult?> = _lastResult.asStateFlow()

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

    /**
     * Re-check permission on resume. Revoked in system settings while we were in the background
     * → back to the explain screen. We never rescan or delete on our own when permission is missing.
     * [deniedPermanently] is only set by [onPermissionResult] (we know for sure then); on resume we
     * just refresh [granted], so a first-time install still gets the request dialog.
     */
    fun onResume(granted: Boolean) {
        permission.value = permission.value.copy(granted = granted)
    }

    /** Outcome of the permission launcher: grant → proceed, permanent denial → open settings next time. */
    fun onPermissionResult(granted: Boolean, activity: Activity) {
        permission.value = PermissionState(
            granted = granted,
            deniedPermanently = !granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, requiredPermission),
        )
    }

    /**
     * Scan now: same folder logic as SettingsScreen (all music, or the picked document folder),
     * copied here so SettingsScreen stays untouched. scanAndInsert switches to Dispatchers.IO itself.
     */
    fun scan() = viewModelScope.launch {
        val folderAll = graph.settings.data.first()[Keys.LocalMusicAll] ?: true
        val folderUri = graph.settings.data.first()[Keys.LocalMusicFolderUri]
        val mode = if (folderAll || folderUri.isNullOrBlank()) {
            LocalAudioScanner.FolderMode.All
        } else {
            LocalAudioScanner.FolderMode.Folder(Uri.parse(folderUri))
        }
        // A scan that finds nothing is still a successful scan: "no music here" is information,
        // not an error, and it loads instantly.
        _scanning.value = true
        try {
            val res = LocalAudioScanner.scanAndInsert(app, graph.database, mode)
            _lastResult.value = res
        } finally {
            _scanning.value = false
        }
    }

    private fun matches(song: Song, query: String): Boolean {
        val q = query
        return song.song.title.contains(q, ignoreCase = true) ||
            song.artistLine.contains(q, ignoreCase = true) ||
            (song.song.albumName?.contains(q, ignoreCase = true) ?: false)
    }
}
