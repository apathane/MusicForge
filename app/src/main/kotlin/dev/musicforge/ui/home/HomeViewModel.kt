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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

@Stable
data class HomeState(
    /** Null until Room's first answer, so Home never flashes the welcome on a full library. */
    val sections: List<HomeSection>? = null,
    val remote: RemoteFeed = RemoteFeed(),
    val isWelcomeDismissed: Boolean = false,
) {
    val isColdStart get() = sections?.isEmpty() == true
}

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
}

sealed interface HomeEvent {
    data class Navigate(val route: String) : HomeEvent
}

class HomeViewModel(
    repository: HomeRepository,
    private val player: PlayerConnection,
    welcomeDismissed: Boolean,
    private val onWelcomeDismissed: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState(isWelcomeDismissed = welcomeDismissed))
    val state = _state.asStateFlow()

    private val _events = Channel<HomeEvent>()
    val events = _events.receiveAsFlow()

    private var continuation: String? = null
    private var isPaging = false

    init {
        viewModelScope.launch {
            repository.signals().collect { signals -> _state.update { it.copy(sections = HomeComposer.compose(signals)) } }
        }
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
        }
    }

    private fun play(action: HomeAction.PlayFrom) {
        val section = _state.value.sections?.firstOrNull { it.id == action.sectionId } ?: return
        val songs = section.items.filterIsInstance<HomeItem.Track>().map { it.song.toMediaItem() }
        if (songs.isNotEmpty()) player.play(songs, action.index.coerceIn(0, songs.lastIndex))
    }

    private fun navigate(route: String) {
        viewModelScope.launch { _events.send(HomeEvent.Navigate(route)) }
    }

    /** [chip] null = the unfiltered feed. Chips from the first load are kept while filtering. */
    private fun loadRemote(chip: HomePage.Chip?) {
        _state.update { it.copy(remote = it.remote.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
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
        if (isPaging) return
        isPaging = true
        viewModelScope.launch {
            YouTube.home(continuation = token).onSuccess { page ->
                continuation = page.continuation
                _state.update { s ->
                    val known = s.remote.sections.mapTo(HashSet()) { it.title }
                    s.copy(
                        remote = s.remote.copy(
                            sections = s.remote.sections + page.sections.filter { it.items.isNotEmpty() && it.title !in known },
                            hasMore = continuation != null,
                        ),
                    )
                }
            }.onFailure {
                continuation = null
                _state.update { it.copy(remote = it.remote.copy(hasMore = false)) }
            }
            isPaging = false
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            "You're offline. Everything you've downloaded still plays."
        else -> e.message ?: "Something went wrong"
    }
}
