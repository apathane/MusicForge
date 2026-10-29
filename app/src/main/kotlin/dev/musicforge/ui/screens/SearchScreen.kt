package dev.musicforge.ui.screens

import kotlinx.coroutines.flow.first
import dev.musicforge.data.prefs.Keys
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.components.TrackRow
import dev.musicforge.ui.openItem
import dev.musicforge.AppGraph
import dev.musicforge.data.db.SearchHistory
import dev.musicforge.graph
import dev.musicforge.ui.designsystem.LoadingBar
import dev.musicforge.ui.designsystem.SignalChip
import dev.musicforge.playback.toMediaItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.musicforge.ui.CollectionKind
import dev.musicforge.ui.Routes
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SongRow
import dev.musicforge.ui.designsystem.pressScale
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
    Albums("Albums", YouTube.SearchFilter.FILTER_ALBUM),
    Artists("Artists", YouTube.SearchFilter.FILTER_ARTIST),
    Playlists("Playlists", YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST),
    Videos("Videos", YouTube.SearchFilter.FILTER_VIDEO),
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(private val graph: AppGraph) : ViewModel() {
    sealed interface Results {
        data object Idle : Results
        data object Loading : Results
        data class Items(val items: List<YTItem>, val local: List<dev.musicforge.data.db.Song> = emptyList(), val offline: String? = null) : Results
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
            val local = if (kind.value == SearchKind.Songs) graph.database.dao().searchLibrary(q) else emptyList()
            // Signed in, search goes out as you (personal ranking, and it lands in your YouTube
            // search history) unless history is paused, as Metrolist does.
            val asYou = YouTube.cookie != null && graph.settings.data.first()[Keys.PauseSearchHistory] != true
            _results.value = YouTube.search(q, kind.value.filter, recordSearchHistory = asYou).fold(
                // YouTube sometimes repeats an item; a repeated lazy-list key would crash the screen.
                onSuccess = { r -> Results.Items(r.items.distinctBy { "${it::class.simpleName}:${it.id}" }, local) },
                onFailure = {
                    // Offline: your own music still answers.
                    if (local.isNotEmpty()) Results.Items(emptyList(), local, offline = "Offline: showing songs you have")
                    else Results.Failed(it.message ?: "Search failed")
                },
            )
        }
    }

    fun forget(q: String) = viewModelScope.launch { graph.database.dao().deleteSearch(q) }
    fun forgetAll() = viewModelScope.launch { graph.database.dao().clearSearchHistory() }

    /** A pasted song link plays that song; radio carries on from it. */
    fun playVideo(id: String) = viewModelScope.launch {
        _results.value = Results.Loading
        val song = YouTube.queue(listOf(id)).getOrNull()?.firstOrNull()
        if (song == null) _results.value = Results.Failed("Couldn't open that link")
        else { _results.value = Results.Idle; graph.player.play(listOf(song.toMediaItem())) }
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
    val nav = LocalNavController.current
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    dev.musicforge.ui.ScrollToTopOnReselect(list)
    val fieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Opening Search with nothing typed means you are about to type.
    LaunchedEffect(Unit) { if (vm.query.value.isEmpty()) runCatching { fieldFocus.requestFocus() } }
    // Scrolling the results means reading them: put the keyboard away.
    LaunchedEffect(list) {
        androidx.compose.runtime.snapshotFlow { list.isScrollInProgress }.collect { if (it) focus.clearFocus() }
    }
    val submit: (String) -> Unit = { text ->
        focus.clearFocus()
        when (val link = dev.musicforge.ui.YouTubeLink.parse(text)) {
            null -> vm.submit(text)
            is dev.musicforge.ui.YouTubeLink.Song -> vm.playVideo(link.videoId)
            is dev.musicforge.ui.YouTubeLink.Playlist -> nav.navigate(Routes.collection(CollectionKind.REMOTE_PLAYLIST, link.playlistId))
            is dev.musicforge.ui.YouTubeLink.Album -> nav.navigate(Routes.collection(CollectionKind.ALBUM, link.browseId))
            is dev.musicforge.ui.YouTubeLink.Artist -> nav.navigate(Routes.artist(link.channelId))
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = LocalBottomInset.current) {
        item { ScreenHeader("Search") { dev.musicforge.ui.SettingsAction() } }
        item {
            SearchField(
                value = query,
                onChange = vm::edit,
                onSubmit = { submit(query) },
                focusRequester = fieldFocus,
                placeholder = "Songs, artists, or paste a link",
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchKind.entries.forEach { k -> SignalChip(k.label, k == kind) { vm.select(k) } }
            }
        }
        when (val r = results) {
            SearchViewModel.Results.Idle -> {
                val recent = query.isBlank()
                // Distinct: the same search made twice is two history rows, and a repeated key crashes the list.
                val lines = (if (recent) history else suggestions).distinct()
                if (recent && lines.isNotEmpty()) item(key = "recent") {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(
                            "Clear",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Clear recent searches") { vm.forgetAll() }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }
                items(lines, key = { "s:$it" }) { line ->
                    Row(
                        Modifier.fillMaxWidth().animateItem().clickable { submit(line) }.padding(start = 20.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(line, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                        if (recent) {
                            IconText("✕", "Remove $line from recent searches") { vm.forget(line) }
                        } else {
                            // Fill the field and keep typing, as Metrolist and BitChord do.
                            IconText("↖", "Edit $line") { vm.edit(line) }
                        }
                    }
                }
            }
            SearchViewModel.Results.Loading -> item {
                LoadingBar(visible = true)
                dev.musicforge.ui.components.SkeletonRows()
            }
            is SearchViewModel.Results.Failed -> item { EmptyState("Couldn't search", r.message) }
            is SearchViewModel.Results.Items -> {
                r.offline?.let { note -> item { Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) } }
                if (r.local.isNotEmpty()) {
                    item { dev.musicforge.ui.components.SectionTitle("In your library") }
                    items(r.local, key = { "l:" + it.id }) { s ->
                        val item = androidx.compose.runtime.remember(s.id) { s.toMediaItem() }
                        SongRow(item, s.artistLine, onPlay = { player.play(r.local.map { it.toMediaItem() }, r.local.indexOf(s)) })
                    }
                    if (r.items.isNotEmpty()) item { dev.musicforge.ui.components.SectionTitle("YouTube Music") }
                }
                if (r.items.isEmpty() && r.local.isEmpty()) item { EmptyState("No results", "Try a different spelling or another filter.") }
                items(r.items, key = { "${it::class.simpleName}:${it.id}" }) { result ->
                    when (result) {
                        is SongItem -> {
                            val item = androidx.compose.runtime.remember(result.id) { result.toMediaItem() }
                            SongRow(
                                item = item,
                                subtitle = listOfNotNull(result.artists.joinToString { it.name }, result.album?.name).joinToString(" · "),
                                // One tap, one song; radio carries on from it.
                                onPlay = { player.play(listOf(item)) },
                            )
                        }
                        else -> TrackRow(
                            title = result.title,
                            subtitle = when (result) {
                                is AlbumItem -> listOfNotNull("Album", result.artists?.joinToString { it.name }, result.year?.toString()).joinToString(" · ")
                                is ArtistItem -> "Artist"
                                is PlaylistItem -> listOfNotNull("Playlist", result.author?.name, result.songCountText).joinToString(" · ")
                                else -> ""
                            },
                            artworkUrl = result.thumbnail,
                            onClick = { openItem(result, r.items, nav, player) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconText(glyph: String, label: String, onClick: () -> Unit) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(50)).pressScale(press).clickable(interactionSource = press, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
internal fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String = "Artists, songs, lyrics",
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            // A fixed height: focused, BasicTextField measured a few dp taller and shoved the
            // filter chips down mid-tap. 48 dp is also the touch target it should have had.
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ForgeIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
        if (value.isNotEmpty()) {
            // A 28 dp disc inside a 44 dp target: small to look at, easy to hit.
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClickLabel = "Clear search") { onChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { contentDescription = "Clear search" })
                }
            }
        }
    }
}
