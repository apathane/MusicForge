package dev.musicforge.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.musicforge.data.db.ArtistEntity
import dev.musicforge.data.db.PlaylistEntity
import dev.musicforge.data.db.PlaylistSummary
import dev.musicforge.data.db.Song
import dev.musicforge.data.db.SongEntity
import dev.musicforge.graph
import dev.musicforge.playback.toMediaItem
import dev.musicforge.recommendation.HomeComposer
import dev.musicforge.recommendation.HomeItem
import dev.musicforge.recommendation.HomeLayout
import dev.musicforge.recommendation.HomeRepository
import dev.musicforge.recommendation.HomeSection
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.PillButton
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.Shelf
import dev.musicforge.ui.components.SongRow
import dev.musicforge.ui.components.TrackActionsSheet
import dev.musicforge.ui.designsystem.SectionHeader
import dev.musicforge.ui.designsystem.Signal
import dev.musicforge.ui.designsystem.SignalChip
import dev.musicforge.ui.designsystem.signalSurface
import dev.musicforge.ui.openItem
import dev.musicforge.ui.theme.ForgeTheme
import java.util.Calendar
import kotlinx.coroutines.flow.map

@Composable
fun HomeRoot() {
    val context = LocalContext.current
    val graph = context.graph
    val once = remember { context.getSharedPreferences("forge.once", android.content.Context.MODE_PRIVATE) }
    val vm = viewModel {
        HomeViewModel(
            HomeRepository(graph.database.dao(), graph.settings.data.map { it[dev.musicforge.data.prefs.Keys.SuggestionsPaused] == true }),
            graph.player,
            welcomeDismissed = once.getBoolean("welcomeDismissed", false),
            onWelcomeDismissed = { once.edit().putBoolean("welcomeDismissed", true).apply() },
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is HomeEvent.Navigate -> nav.navigate(event.route)
            }
        }
    }
    // Remote items open through the shared router, the same as Search and artist pages.
    HomeScreen(state, vm::onAction, onRemoteItem = { item, siblings -> openItem(item, siblings, nav, graph.player) })
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Late night"
}

@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
    onRemoteItem: (com.metrolist.innertube.models.YTItem, List<com.metrolist.innertube.models.YTItem>) -> Unit = { _, _ -> },
) {
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 3 }
    }
    // Keyed on the page count too: a page of repeats changes nothing else, and paging must go on.
    LaunchedEffect(nearEnd, state.remote.hasMore, state.remote.pages) { if (nearEnd && state.remote.hasMore) onAction(HomeAction.LoadMore) }

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = LocalBottomInset.current) {
        item(key = "header", contentType = "header") { ScreenHeader(greeting()) }
        if (state.isColdStart && !state.isWelcomeDismissed) {
            item(key = "welcome", contentType = "welcome") { WelcomeCard(onAction) }
        }
        state.sections.orEmpty().forEach { section ->
            item(key = section.id, contentType = section.layout) { Section(section, onAction) }
        }
        remoteFeed(state, onAction, onRemoteItem)
    }
}

@Composable
private fun Section(section: HomeSection, onAction: (HomeAction) -> Unit) {
    when (section.layout) {
        HomeLayout.SHELF -> Column {
            SectionHeader(section.title, section.reason)
            LazyRow(contentPadding = PaddingValues(horizontal = Signal.gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(section.items, key = { _, it -> it.key }) { i, item ->
                    (item as? HomeItem.Track)?.let {
                        SongCard(it.song, section, onAction) { onAction(HomeAction.PlayFrom(section.id, i)) }
                    }
                }
            }
        }
        HomeLayout.ROWS -> Column {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionHeader(section.title, section.reason, Modifier.weight(1f))
                Text(
                    "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onAction(HomeAction.OpenDownloads) }.padding(horizontal = Signal.gutter, vertical = 12.dp),
                )
            }
            section.items.forEachIndexed { i, item ->
                val track = item as? HomeItem.Track ?: return@forEachIndexed
                val media = remember(track.song.id) { track.song.toMediaItem() }
                SongRow(media, track.song.artistLine) { onAction(HomeAction.PlayFrom(section.id, i)) }
            }
        }
        HomeLayout.PLAYLISTS -> Column {
            SectionHeader(section.title, section.reason)
            LazyRow(contentPadding = PaddingValues(horizontal = Signal.gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(section.items, key = { it.key }) { item ->
                    (item as? HomeItem.Playlist)?.let { PlaylistCard(it.playlist) { onAction(HomeAction.OpenPlaylist(it.playlist.playlist.id)) } }
                }
            }
        }
        HomeLayout.STATS -> Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth()
                .signalSurface(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { onAction(HomeAction.OpenStats) }
                .padding(18.dp),
        ) {
            Text(section.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(section.stat.orEmpty(), style = MaterialTheme.typography.headlineLarge)
            Text(section.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val CardSize = 128.dp

/**
 * A song on a shelf: tap plays, long-press opens its actions with why it is here and the two
 * corrections, "Not for me" and "Less of <artist>".
 */
@Composable
private fun SongCard(song: Song, section: HomeSection, onAction: (HomeAction) -> Unit, onClick: () -> Unit) {
    var menu by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        Modifier.width(CardSize).combinedClickable(onClick = onClick, onLongClick = { menu = true }, onLongClickLabel = "More options"),
    ) {
        Artwork(song.song.thumbnailUrl, CardSize, corner = Signal.radiusSmall)
        Spacer(Modifier.height(6.dp))
        Text(song.song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artistLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (menu) {
        val media = remember(song.id) { song.toMediaItem() }
        val artist = song.artists.firstOrNull()
        TrackActionsSheet(
            media,
            extra = listOfNotNull(
                "Not for me" to { onAction(HomeAction.Hide(song.id)) },
                // Jump back in is history, not a suggestion: "less of" would change nothing there.
                artist?.takeIf { section.id in HomeComposer.SUGGESTIONS }?.let { "Less of ${it.name}" to { onAction(HomeAction.LessOf(it.id)) } },
            ),
            why = "On Home because: ${section.reason.replaceFirstChar { it.lowercase() }}. From your own library.",
        ) { menu = false }
    }
}

@Composable
private fun PlaylistCard(p: PlaylistSummary, onClick: () -> Unit) {
    Column(Modifier.width(CardSize).clickable(onClick = onClick)) {
        Artwork(p.playlist.thumbnailUrl, CardSize, corner = Signal.radiusSmall, fallbackLetter = p.playlist.name)
        Spacer(Modifier.height(6.dp))
        Text(p.playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (p.songCount == 1) "1 song" else "${p.songCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** First run: say how Home fills up instead of pretending to know the listener. */
@Composable
private fun WelcomeCard(onAction: (HomeAction) -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .signalSurface(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        Text("Make Home yours", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Home fills with what you actually play. Coming from Metrolist or ConvX? Bring your likes, playlists, " +
                "history and sign-in over in a few seconds, or start with a mood below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Restore my library", { onAction(HomeAction.OpenRestore) })
            Text(
                "Not now",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onAction(HomeAction.DismissWelcome) }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** YouTube Music's feed, labelled as such and always after what's yours. */
private fun androidx.compose.foundation.lazy.LazyListScope.remoteFeed(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
    onRemoteItem: (com.metrolist.innertube.models.YTItem, List<com.metrolist.innertube.models.YTItem>) -> Unit,
) {
    val remote = state.remote
    item(key = "remote-header", contentType = "header") {
        SectionHeader("From YouTube Music", remote.selected ?: "Picked by YouTube Music, not from your listening")
    }
    if (remote.chips.isNotEmpty()) {
        item(key = "remote-chips", contentType = "chips") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(remote.chips, key = { it.title }) { chip ->
                    val on = chip.title == remote.selected
                    SignalChip(chip.title, on) { onAction(HomeAction.SelectChip(if (on) null else chip)) }
                }
            }
        }
    }
    when {
        remote.isLoading -> item(key = "remote-loading", contentType = "loading") {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground, strokeWidth = 2.dp)
            }
        }
        // With nothing of your own to fall back on, the failure gets the whole screen and a way out.
        remote.error != null && (state.isColdStart || state.sections == null) -> item(key = "remote-error", contentType = "error") {
            EmptyState("Can't reach YouTube Music", remote.error) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Open downloads", { onAction(HomeAction.OpenDownloads) })
                    PillButton("Try again", { onAction(HomeAction.Retry) })
                }
            }
        }
        remote.error != null -> item(key = "remote-error", contentType = "error") {
            Text(
                "${remote.error} Tap to try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { onAction(HomeAction.Retry) }.padding(horizontal = Signal.gutter, vertical = 8.dp),
            )
        }
        else -> remote.sections.forEachIndexed { i, section ->
            // Titles repeat across continuation pages only when YouTube repeats them; index keeps keys unique.
            item(key = "remote:$i:${section.title}", contentType = "remote-shelf") {
                Shelf(section.title, section.items) { onRemoteItem(it, section.items) }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    fun song(id: String, title: String, artist: String) =
        Song(SongEntity(id = id, title = title), listOf(ArtistEntity(id = "a$id", name = artist)))
    val recent = listOf(song("1", "Roads", "Portishead"), song("2", "Teardrop", "Massive Attack"), song("3", "Glory Box", "Portishead"))
    ForgeTheme {
        HomeScreen(
            HomeState(
                sections = listOf(
                    HomeSection("jump-back-in", "Jump back in", "Recently played", HomeLayout.SHELF, recent.map { HomeItem.Track(it) }),
                    HomeSection(
                        "from-your-library", "From your library", "Your playlists", HomeLayout.PLAYLISTS,
                        listOf(HomeItem.Playlist(PlaylistSummary(PlaylistEntity(id = "p", name = "Late nights"), 24))),
                    ),
                    HomeSection("listening", "Last 7 days", "Mostly Portishead", HomeLayout.STATS, stat = "4 h 12 min"),
                ),
                remote = RemoteFeed(isLoading = false),
            ),
            onAction = {},
        )
    }
}
