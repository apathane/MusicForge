package dev.musicforge.ui.home

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.HomePage
import dev.musicforge.playback.PlayerConnection
import dev.musicforge.playback.toMediaItem
import dev.musicforge.recommendation.HomeComposer
import dev.musicforge.recommendation.HomeItem
import dev.musicforge.recommendation.HomeRepository
import dev.musicforge.recommendation.HomeSection
import dev.musicforge.ui.CollectionKind
import dev.musicforge.ui.Routes
import dev.musicforge.ui.SETTINGS_ROUTE
import dev.musicforge.ui.Tab
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** YouTube Music's own feed, shown under its own label after the personal sections. */
@Stable
data class RemoteFeed(
    val chips: List<HomePage.Chip> = emptyList(),
    val selected: String? = null,
    val sections: List<HomePage.Section> = emptyList(),
    val isLoading: Boolean = true,
    val hasMore: Boolean = false,
    val error: String? = null,
    /** Continuation pages loaded so far. */
    val pages: Int = 0,
)

@Stable
data class HomeState(
    /** Null until Room's first answer, so Home never flashes the welcome on a full library. */
    val sections: List<HomeSection>? = null,
    val remote: RemoteFeed = RemoteFeed(),
    val isWelcomeDismissed: Boolean = false,
    /** Nothing in the library at all: Home asks for intent instead of guessing. */
    val isColdStart: Boolean = false,
)

sealed interface HomeAction {
    data class PlayFrom(val sectionId: String, val index: Int) : HomeAction
    data class OpenPlaylist(val id: String) : HomeAction
    data object OpenStats : HomeAction
    data object OpenDownloads : HomeAction
    data object OpenRestore : HomeAction
    data object DismissWelcome : HomeAction
    data class SelectChip(val chip: HomePage.Chip?) : HomeAction
    data object LoadMore : HomeAction
    data object Retry : HomeAction
    /** "Not for me" on a suggestion. */
    data class Hide(val songId: String) : HomeAction
    /** "Less of this artist" for a while. */
    data class LessOf(val artistId: String) : HomeAction
}

sealed interface HomeEvent {
    data class Navigate(val route: String) : HomeEvent
}

class HomeViewModel(
    private val repository: HomeRepository,
    private val player: PlayerConnection,
    welcomeDismissed: Boolean,
    private val onWelcomeDismissed: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState(isWelcomeDismissed = welcomeDismissed))

    /**
     * Room is read only while Home is on screen: play-time checkpoints rewrite the song table every
     * 30 s, and recomposing Home behind another tab or a locked screen would be wasted work.
     */
    val state: StateFlow<HomeState> = combine(_state, repository.signals()) { s, signals ->
        s.copy(sections = HomeComposer.compose(signals), isColdStart = signals.isLibraryEmpty)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _state.value)

    private val _events = Channel<HomeEvent>()
    val events = _events.receiveAsFlow()

    private var continuation: String? = null
    private var feedJob: Job? = null
    private var pageJob: Job? = null

    init {
        loadRemote(null)
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.PlayFrom -> play(action)
            is HomeAction.OpenPlaylist -> navigate(Routes.collection(CollectionKind.LOCAL_PLAYLIST, action.id))
            HomeAction.OpenStats -> navigate(Routes.STATS)
            HomeAction.OpenDownloads -> navigate(Tab.Downloads.route)
            HomeAction.OpenRestore -> navigate(SETTINGS_ROUTE)
            HomeAction.DismissWelcome -> {
                onWelcomeDismissed()
                _state.update { it.copy(isWelcomeDismissed = true) }
            }
            is HomeAction.SelectChip -> loadRemote(action.chip)
            HomeAction.LoadMore -> loadMore()
            HomeAction.Retry -> loadRemote(null)
            is HomeAction.Hide -> viewModelScope.launch { repository.hide(action.songId) }
            is HomeAction.LessOf -> viewModelScope.launch { repository.lessOf(action.artistId) }
        }
    }

    private fun play(action: HomeAction.PlayFrom) {
        val section = state.value.sections?.firstOrNull { it.id == action.sectionId } ?: return
        val songs = section.items.filterIsInstance<HomeItem.Track>().map { it.song.toMediaItem() }
        if (songs.isNotEmpty()) player.play(songs, action.index.coerceIn(0, songs.lastIndex))
    }

    private fun navigate(route: String) {
        viewModelScope.launch { _events.send(HomeEvent.Navigate(route)) }
    }

    /** [chip] null = the unfiltered feed. Chips from the first load are kept while filtering. */
    private fun loadRemote(chip: HomePage.Chip?) {
        // A new feed replaces the old one: its page in flight and its next-page token go with it.
        feedJob?.cancel()
        pageJob?.cancel()
        continuation = null
        _state.update { it.copy(remote = it.remote.copy(isLoading = true, hasMore = false, error = null)) }
        feedJob = viewModelScope.launch {
            YouTube.home(params = chip?.endpoint?.params).onSuccess { page ->
                continuation = page.continuation
                _state.update { s ->
                    s.copy(
                        remote = RemoteFeed(
                            chips = if (chip == null || s.remote.chips.isEmpty()) page.chips.orEmpty() else s.remote.chips,
                            selected = chip?.title,
                            sections = page.sections.filter { it.items.isNotEmpty() },
                            isLoading = false,
                            hasMore = continuation != null,
                        ),
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(remote = it.remote.copy(isLoading = false, error = friendly(e))) }
            }
        }
    }

    /** The feed goes on as you scroll, like the official app's. */
    private fun loadMore() {
        val token = continuation ?: return
        if (pageJob?.isActive == true || _state.value.remote.isLoading) return
        pageJob = viewModelScope.launch {
            YouTube.home(continuation = token).onSuccess { page ->
                continuation = page.continuation
                _state.update { s ->
                    val known = s.remote.sections.mapTo(HashSet()) { it.title }
                    s.copy(
                        remote = s.remote.copy(
                            sections = s.remote.sections + page.sections.filter { it.items.isNotEmpty() && it.title !in known },
                            hasMore = continuation != null,
                            // A page of repeats adds nothing visible; the counter lets the screen ask again.
                            pages = s.remote.pages + 1,
                        ),
                    )
                }
            }.onFailure {
                continuation = null
                _state.update { it.copy(remote = it.remote.copy(hasMore = false)) }
            }
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            "You're offline. Everything you've downloaded still plays."
        else -> e.message ?: "Something went wrong"
    }
}
