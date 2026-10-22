package dev.musicforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import dev.musicforge.AppGraph
import dev.musicforge.data.db.SearchHistory
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.TrackRow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchKind(val label: String, val filter: YouTube.SearchFilter) {
    Songs("Songs", YouTube.SearchFilter.FILTER_SONG),
    Videos("Videos", YouTube.SearchFilter.FILTER_VIDEO),
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(private val graph: AppGraph) : ViewModel() {
    sealed interface Results {
        data object Idle : Results
        data object Loading : Results
        data class Songs(val items: List<SongItem>) : Results
        data class Failed(val message: String) : Results
    }

    val query = MutableStateFlow("")
    val kind = MutableStateFlow(SearchKind.Songs)
    private val _results = MutableStateFlow<Results>(Results.Idle)
    val results = _results.asStateFlow()

    val history = graph.database.dao().searchHistory(12)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Suggestions follow typing, 250 ms behind, and stop once a search is submitted. */
    val suggestions = query.debounce(250).distinctUntilChanged().mapLatest { q ->
        if (q.isBlank() || _results.value !is Results.Idle) emptyList()
        else YouTube.searchSuggestions(q).getOrNull()?.queries.orEmpty().take(6)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun edit(text: String) {
        query.value = text
        _results.value = Results.Idle
    }

    fun submit(text: String = query.value) {
        val q = text.trim()
        if (q.isEmpty()) return
        query.value = q
        viewModelScope.launch {
            _results.value = Results.Loading
            graph.database.dao().insertSearch(SearchHistory(query = q))
            _results.value = YouTube.search(q, kind.value.filter).fold(
                onSuccess = { r -> Results.Songs(r.items.filterIsInstance<SongItem>()) },
                onFailure = { Results.Failed(it.message ?: "Search failed") },
            )
        }
    }

    fun select(k: SearchKind) {
        kind.value = k
        if (_results.value !is Results.Idle) submit()
    }
}

@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val vm = viewModel { SearchViewModel(context.graph) }
    val query by vm.query.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val player = context.graph.player

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { ScreenHeader("Search") }
        item {
            SearchField(
                value = query,
                onChange = vm::edit,
                onSubmit = { vm.submit(); focus.clearFocus() },
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchKind.entries.forEach { k -> Chip(k.label, k == kind) { vm.select(k) } }
            }
        }
        when (val r = results) {
            SearchViewModel.Results.Idle -> {
                val lines = if (query.isBlank()) history else suggestions
                items(lines, key = { "s:$it" }) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.submit(line); focus.clearFocus() }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            SearchViewModel.Results.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground, strokeWidth = 2.dp)
                }
            }
            is SearchViewModel.Results.Failed -> item { EmptyState("Couldn't search", r.message) }
            is SearchViewModel.Results.Songs -> {
                if (r.items.isEmpty()) item { EmptyState("No results", "Try a different spelling or another filter.") }
                items(r.items, key = { it.id }) { song ->
                    TrackRow(
                        title = song.title,
                        subtitle = listOfNotNull(song.artists.joinToString { it.name }, song.album?.name).joinToString(" · "),
                        artworkUrl = song.thumbnail,
                        onClick = { player.play(listOf(song.toMediaItem())) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ForgeIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Artists, songs, lyrics", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
