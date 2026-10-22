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
    val nav = LocalNavController.current

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
                Shelf(section.title, section.items) { item -> openItem(item, section.items, nav, player) }
            }
        }
    }
}

