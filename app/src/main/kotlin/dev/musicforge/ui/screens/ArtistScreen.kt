package dev.musicforge.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.ArtistPage
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.IconAction
import dev.musicforge.ui.components.SectionTitle
import dev.musicforge.ui.components.Shelf
import dev.musicforge.ui.components.SongRow
import dev.musicforge.ui.openItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArtistViewModel(private val id: String) : ViewModel() {
    sealed interface State {
        data object Loading : State
        data class Ready(val page: ArtistPage) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = YouTube.artist(id).fold({ State.Ready(it) }, { State.Failed(it.message ?: "Can't load this artist") })
        }
    }
}

@Composable
fun ArtistScreen(id: String, onBack: () -> Unit) {
    val vm = viewModel(key = "artist:$id") { ArtistViewModel(id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val player = LocalContext.current.graph.player

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp)) { IconAction(ForgeIcons.Back, "Back", onBack) } }
        when (val s = state) {
            ArtistViewModel.State.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground, strokeWidth = 2.dp)
                }
            }
            is ArtistViewModel.State.Failed -> item { EmptyState("Couldn't load", s.message) }
            is ArtistViewModel.State.Ready -> {
                val page = s.page
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Artwork(page.artist.thumbnail, 180.dp, corner = 90.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(page.artist.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                        val sub = page.monthlyListenerCount ?: page.subscriberCountText
                        if (sub != null) {
                            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                for (section in page.sections) {
                    val songs = section.items.filterIsInstance<SongItem>()
                    if (songs.size == section.items.size && songs.isNotEmpty()) {
                        // "Songs" sections read best as a list you can play straight away.
                        item(key = "t:${section.title}") { SectionTitle(section.title) }
                        items(songs, key = { "s:${section.title}:${it.id}" }) { song ->
                            val mediaItem = remember(song.id) { song.toMediaItem() }
                            SongRow(mediaItem, song.album?.name ?: song.artists.joinToString { it.name }) {
                                player.play(songs.map { it.toMediaItem() }, songs.indexOf(song))
                            }
                        }
                    } else {
                        item(key = "h:${section.title}") {
                            Shelf(section.title, section.items) { openItem(it, section.items, nav, player) }
                        }
                    }
                }
            }
        }
    }
}
