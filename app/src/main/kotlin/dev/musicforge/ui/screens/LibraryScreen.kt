package dev.musicforge.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.musicforge.AppGraph
import dev.musicforge.data.db.LibraryCounts
import dev.musicforge.data.db.PlaylistSummary
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.CollectionKind
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.Routes
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.IconAction
import dev.musicforge.ui.components.PillButton
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SectionTitle
import dev.musicforge.ui.designsystem.Motion
import dev.musicforge.ui.designsystem.Signal
import dev.musicforge.ui.designsystem.SignalChip
import dev.musicforge.ui.designsystem.pressScale
import dev.musicforge.ui.designsystem.reducedMotion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.metrolist.innertube.utils.completed

class LibraryViewModel(graph: AppGraph) : ViewModel() {
    private val dao = graph.database.dao()
    val counts: StateFlow<LibraryCounts?> =
        dao.counts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val playlists: StateFlow<List<PlaylistSummary>> =
        dao.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Filter as you type, local only: songs you have, and playlists by name. */
    val query = kotlinx.coroutines.flow.MutableStateFlow("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val found: StateFlow<Pair<List<dev.musicforge.data.db.Song>, List<PlaylistSummary>>?> = query
        .debounce(150)
        .mapLatest { q -> q.trim().takeIf { it.isNotEmpty() }?.let { t -> t to dao.searchLibrary(t, 50) } }
        // Playlist matches follow the live list, so a rename or a new playlist shows without retyping.
        .combine(dao.playlists()) { hit, lists ->
            hit?.let { (t, songs) -> songs to lists.filter { it.playlist.name.contains(t, ignoreCase = true) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Account playlists from YouTube Music; empty when signed out or offline. */
    val accountPlaylists = kotlinx.coroutines.flow.MutableStateFlow<List<com.metrolist.innertube.models.PlaylistItem>>(emptyList())
    val signedIn = graph.settings.data.map { it[dev.musicforge.data.prefs.Keys.InnerTubeCookie] != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Songs scanned from local files, for the "On this phone" shelf count. */
    val localCount = dao.localSongsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            signedIn.collect { yes ->
                accountPlaylists.value = if (!yes) emptyList() else
                    com.metrolist.innertube.YouTube.library("FEmusic_liked_playlists").completed().getOrNull()?.items
                        ?.filterIsInstance<com.metrolist.innertube.models.PlaylistItem>()
                        ?.filterNot { it.id == "LM" || it.id == "SE" }
                        // YouTube repeats a playlist now and then; one id is one row, always.
                        ?.distinctBy { it.id }
                        .orEmpty()
            }
        }
    }
}

/** Small rise+fade on arrival, draw-phase only; snaps under reduced motion. */
fun Modifier.riseFade(delayMs: Int = 0): Modifier = composed {
    val reduced = reducedMotion()
    val t = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        t.animateTo(1f, tween(if (reduced) 0 else Motion.Row, easing = Motion.EaseDrawer))
    }
    graphicsLayer { alpha = t.value; translationY = (1f - t.value) * 8.dp.toPx() }
}

@Composable
fun LibraryScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel { LibraryViewModel(context.graph) }
    val counts by vm.counts.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val account by vm.accountPlaylists.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    val localCount by vm.localCount.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val empty = counts?.let { it.songs + it.liked + it.playlists + it.albums + it.artists + it.downloaded + it.events == 0 } ?: false

    // Signed in, pulling the library down syncs it with your account now.
    val sync by context.graph.accountSync.status.collectAsStateWithLifecycle()
    val refreshing = sync is dev.musicforge.data.AccountSync.Status.Running
    val refresh = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val pulling by remember { androidx.compose.runtime.derivedStateOf { refresh.distanceFraction > 0f } }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { if (signedIn) context.graph.scope.launch { context.graph.accountSync.sync() } },
        state = refresh,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            // At rest the stock indicator left a grey circle peeking over "Library". It earns its
            // place only while you pull or while the sync runs.
            if (refreshing || pulling) {
                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                    state = refresh,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
    ) {
        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        dev.musicforge.ui.ScrollToTopOnReselect(list)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            LazyColumn(
                Modifier.fillMaxSize().then(if (wide) Modifier.padding(horizontal = (maxWidth - 720.dp).coerceAtLeast(0.dp) / 2) else Modifier),
                state = list,
                contentPadding = LocalBottomInset.current,
            ) {
                item(key = "header") {
                    ScreenHeader("Library") { IconAction(ForgeIcons.Settings, "Settings", onOpenSettings) }
                }
                if (!empty) {
                    item(key = "find") {
                        GlassSearchField(query, { vm.query.value = it }, onSubmit = {}, placeholder = "Find in your library")
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (!empty) {
                    item(key = "filters") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = Signal.pageGutter, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SmartFilter("Recently added", ForgeIcons.History) { nav.navigate(Routes.collection(CollectionKind.HISTORY)) }
                            SmartFilter("Most played", ForgeIcons.BarChart) { nav.navigate(Routes.STATS) }
                            SmartFilter("Downloaded", ForgeIcons.Download) { nav.navigate(Routes.collection(CollectionKind.DOWNLOADED)) }
                            SmartFilter("Liked", ForgeIcons.Heart) { nav.navigate(Routes.collection(CollectionKind.LIKED)) }
                            SmartFilter("Albums", ForgeIcons.Album) { nav.navigate(Routes.ALBUMS) }
                            SmartFilter("Artists", ForgeIcons.Person) { nav.navigate(Routes.ARTISTS) }
                            SmartFilter("Playlists", ForgeIcons.Library) { /* inline below */ }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                found?.let { (songs, lists) ->
                    if (songs.isEmpty() && lists.isEmpty()) {
                        item(key = "find-none") { EmptyState("Nothing in your library matches", "Search looks at titles and artists of songs you have, and playlist names.") }
                    }
                    items(lists, key = { "f:p:" + it.playlist.id }) { p ->
                        ShelfRow(ForgeIcons.Library, p.playlist.name, p.songCount) { nav.navigate(Routes.collection(CollectionKind.LOCAL_PLAYLIST, p.playlist.id)) }
                    }
                    items(songs, key = { "f:s:" + it.id }) { s ->
                        val item = remember(s.id) { s.toMediaItem() }
                        dev.musicforge.ui.components.SongRow(item, s.artistLine) { context.graph.player.play(songs.map { it.toMediaItem() }, songs.indexOf(s)) }
                    }
                    return@LazyColumn
                }
                if (empty) {
                    item(key = "empty") {
                        EmptyState(
                            title = "Your library is empty",
                            body = "Sign in with Google to bring your YouTube Music likes, playlists, albums and artists, kept in sync. Or restore a backup from Settings.",
                        ) { PillButton("Sign in with Google", { nav.navigate(Routes.LOGIN) }) }
                    }
                    return@LazyColumn
                }
                counts?.let { c ->
                    item(key = "shelf-liked") { ShelfRow(ForgeIcons.Heart, "Liked songs", c.liked) { nav.navigate(Routes.collection(CollectionKind.LIKED)) } }
                    item(key = "shelf-songs") { ShelfRow(ForgeIcons.MusicNote, "Songs", c.songs) { nav.navigate(Routes.collection(CollectionKind.SONGS)) } }
                    item(key = "shelf-downloaded") { ShelfRow(ForgeIcons.Download, "Downloaded", c.downloaded) { nav.navigate(Routes.collection(CollectionKind.DOWNLOADED)) } }
                    item(key = "shelf-albums") { ShelfRow(ForgeIcons.Album, "Albums", c.albums) { nav.navigate(Routes.ALBUMS) } }
                    item(key = "shelf-artists") { ShelfRow(ForgeIcons.Person, "Artists", c.artists) { nav.navigate(Routes.ARTISTS) } }
                    item(key = "shelf-history") { ShelfRow(ForgeIcons.History, "Recently played", -1) { nav.navigate(Routes.collection(CollectionKind.HISTORY)) } }
                    item(key = "shelf-listening") { ShelfRow(ForgeIcons.BarChart, "Your listening", -1) { nav.navigate(Routes.STATS) } }
                }
                if (signedIn) {
                    item(key = "yt-header") { SectionTitle("From YouTube Music") }
                    item(key = "yt-liked") { ShelfRow(ForgeIcons.Heart, "Liked music", -1) { nav.navigate(Routes.collection(CollectionKind.REMOTE_PLAYLIST, "LM")) } }
                    items(account, key = { "yt:" + it.id }, contentType = { "playlist" }) { p ->
                        PlaylistRow(p.title, p.thumbnail, p.songCountText) {
                            nav.navigate(Routes.collection(CollectionKind.REMOTE_PLAYLIST, p.id))
                        }
                    }
                }
                item(key = "device-header") { SectionTitle("On this phone") }
                item(key = "device-local") {
                    ShelfRow(ForgeIcons.MusicNote, "Local music", localCount) {
                        nav.navigate(Routes.localMusic())
                    }
                }
                if (playlists.isNotEmpty()) {
                    item(key = "local-header") { SectionTitle("Playlists") }
                    items(playlists, key = { "local:" + it.playlist.id }, contentType = { "playlist" }) { p ->
                        PlaylistRow(p.playlist.name, p.playlist.thumbnailUrl, "${p.songCount} songs") {
                            nav.navigate(Routes.collection(CollectionKind.LOCAL_PLAYLIST, p.playlist.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartFilter(label: String, icon: ImageVector, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun GlassSearchField(query: String, onChange: (String) -> Unit, onSubmit: () -> Unit, placeholder: String) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ForgeIcons.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
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

/**
 * One playlist row, shared by the account and local lists. No `animateItem()` here: a replaced
 * account list faded old rows out over new ones and the list haunted itself, Victorian style.
 * Rows are keyed by list + id and carry one content type, so two rows cannot share a slot.
 */
@Composable
private fun PlaylistRow(title: String, artwork: String?, subtitle: String?, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth()
            .riseFade()
            .pressScale(press)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artwork, 56.dp, corner = 8.dp, fallbackLetter = title)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ShelfRow(icon: ImageVector, title: String, count: Int, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Column(Modifier.riseFade()) {
        Row(
            Modifier.fillMaxWidth().pressScale(press).clickable(interactionSource = press, indication = null, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (count >= 0) Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(ForgeIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
    }
}
