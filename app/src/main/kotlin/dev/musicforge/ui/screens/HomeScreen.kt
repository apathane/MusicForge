package dev.musicforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.components.Shelf
import dev.musicforge.ui.openItem
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.PillButton
import dev.musicforge.ui.components.ScreenHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel : ViewModel() {
    sealed interface State {
        data object Loading : State
        data class Ready(
            val chips: List<HomePage.Chip>,
            val selected: String?,
            val sections: List<HomePage.Section>,
            val hasMore: Boolean,
        ) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()
    private var continuation: String? = null
    private var chips: List<HomePage.Chip> = emptyList()
    private var paging = false

    init {
        load()
    }

    /** [chip] null = the unfiltered feed. Chips from the first load are kept while filtering. */
    fun load(chip: HomePage.Chip? = null) = viewModelScope.launch {
        _state.value = State.Loading
        val params = chip?.endpoint?.params
        _state.value = YouTube.home(params = params).fold(
            onSuccess = { page ->
                continuation = page.continuation
                if (chip == null || chips.isEmpty()) chips = page.chips.orEmpty()
                State.Ready(chips, chip?.title, page.sections.filter { it.items.isNotEmpty() }, continuation != null)
            },
            onFailure = { State.Failed(friendly(it)) },
        )
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            "You're offline. Everything you've downloaded still plays."
        else -> e.message ?: "Something went wrong"
    }

    /** The feed goes on as you scroll, like the official app's. */
    fun loadMore() {
        val token = continuation ?: return
        val ready = _state.value as? State.Ready ?: return
        if (paging) return
        paging = true
        viewModelScope.launch {
            YouTube.home(continuation = token).onSuccess { page ->
                continuation = page.continuation
                val known = ready.sections.mapTo(HashSet()) { it.title }
                _state.value = ready.copy(
                    sections = ready.sections + page.sections.filter { it.items.isNotEmpty() && it.title !in known },
                    hasMore = continuation != null,
                )
            }.onFailure { continuation = null; _state.value = ready.copy(hasMore = false) }
            paging = false
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Late night"
}

@Composable
fun HomeScreen() {
    val vm = viewModel { HomeViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    val player = LocalContext.current.graph.player
    val nav = LocalNavController.current

    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val nearEnd by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 3
        }
    }
    androidx.compose.runtime.LaunchedEffect(nearEnd, state) { if (nearEnd) vm.loadMore() }

    val context = LocalContext.current
    val counts by androidx.compose.runtime.remember { context.graph.database.dao().counts() }.collectAsStateWithLifecycle(null)
    val once = androidx.compose.runtime.remember { context.getSharedPreferences("forge.once", android.content.Context.MODE_PRIVATE) }
    var welcomeDismissed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(once.getBoolean("welcomeDismissed", false)) }
    val recent by androidx.compose.runtime.remember { context.graph.database.dao().historySongs(16) }.collectAsStateWithLifecycle(emptyList())
    val libraryEmpty = counts?.let { it.songs + it.liked + it.playlists + it.events + it.downloaded == 0 } == true

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = LocalBottomInset.current) {
        item { ScreenHeader(greeting()) }
        if (libraryEmpty && !welcomeDismissed) {
            item(key = "welcome") {
                WelcomeCard(
                    onRestore = { nav.navigate(dev.musicforge.ui.SETTINGS_ROUTE) },
                    onDismiss = { welcomeDismissed = true; once.edit().putBoolean("welcomeDismissed", true).apply() },
                )
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent") { RecentShelf(recent) { i -> player.play(recent.map { it.toMediaItem() }, i) } }
        }
        (state as? HomeViewModel.State.Ready)?.takeIf { it.chips.isNotEmpty() }?.let { ready ->
            item(key = "chips") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ready.chips, key = { it.title }) { chip ->
                        val on = chip.title == ready.selected
                        Text(
                            chip.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { vm.load(if (on) null else chip) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        when (val s = state) {
            HomeViewModel.State.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground, strokeWidth = 2.dp)
                }
            }
            is HomeViewModel.State.Failed -> item {
                EmptyState("Can't reach YouTube Music", s.message) {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Open downloads", { nav.navigate(dev.musicforge.ui.Tab.Downloads.route) })
                        PillButton("Try again", { vm.load() })
                    }
                }
            }
            is HomeViewModel.State.Ready -> itemsIndexed(s.sections, key = { i, sec -> "$i:${sec.title}" }) { _, section ->
                Shelf(section.title, section.items) { item -> openItem(item, section.items, nav, player) }
            }
        }
    }
}


@Composable
private fun WelcomeCard(onRestore: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        Text("Coming from Metrolist or ConvX?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Bring your library over: likes, playlists, albums, artists, play history, lyrics and your sign-in. It takes a few seconds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PillButton("Restore my library", onRestore)
            Text(
                "Not now",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** Your own recent plays, first: instant, offline, and yours rather than the algorithm's. */
@Composable
private fun RecentShelf(songs: List<dev.musicforge.data.db.Song>, onPlay: (Int) -> Unit) {
    Column(Modifier.padding(top = 8.dp, bottom = 6.dp)) {
        Text("Jump back in", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(songs, key = { _, s -> "r:" + s.id }) { i, s ->
                Column(Modifier.width(112.dp).clickable { onPlay(i) }) {
                    Artwork(s.song.thumbnailUrl, 112.dp, corner = 10.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(s.song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artistLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
