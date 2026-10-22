package dev.musicforge.ui.screens

import androidx.compose.foundation.clickable
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
        data class Ready(val sections: List<HomePage.Section>) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.value = State.Loading
        _state.value = YouTube.home().fold(
            onSuccess = { page -> State.Ready(page.sections.filter { it.items.isNotEmpty() }) },
            onFailure = { State.Failed(it.message ?: "No connection") },
        )
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { ScreenHeader(greeting()) }
        when (val s = state) {
            HomeViewModel.State.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground, strokeWidth = 2.dp)
                }
            }
            is HomeViewModel.State.Failed -> item {
                EmptyState("Can't reach YouTube Music", s.message) { PillButton("Try again", { vm.load() }) }
            }
            is HomeViewModel.State.Ready -> itemsIndexed(s.sections, key = { i, sec -> "$i:${sec.title}" }) { _, section ->
                Shelf(section) { item ->
                    when (item) {
                        is SongItem -> {
                            val songs = section.items.filterIsInstance<SongItem>()
                            player.play(songs.map { it.toMediaItem() }, songs.indexOf(item).coerceAtLeast(0))
                        }
                        else -> Unit // albums, playlists and artists open in Interlude
                    }
                }
            }
        }
    }
}

@Composable
private fun Shelf(section: HomePage.Section, onClick: (YTItem) -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            section.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(section.items, key = { i, it -> "$i:${it.id}" }) { _, item -> Card(item, onClick) }
        }
    }
}

@Composable
private fun Card(item: YTItem, onClick: (YTItem) -> Unit) {
    val size: Dp = 148.dp
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> listOfNotNull(item.artists?.joinToString { it.name }, item.year?.toString()).joinToString(" · ")
        is PlaylistItem -> item.author?.name.orEmpty()
        is ArtistItem -> "Artist"
        else -> ""
    }
    Column(Modifier.width(size).clickable { onClick(item) }) {
        Artwork(item.thumbnail, size, corner = if (item is ArtistItem) size / 2 else 10.dp)
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
