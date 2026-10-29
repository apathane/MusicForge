package dev.musicforge.ui.screens

import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import dev.musicforge.ui.designsystem.LoadingBar
import dev.musicforge.ui.designsystem.pressScale
import dev.musicforge.ui.designsystem.Signal
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.musicforge.ui.components.BackRow
import dev.musicforge.ui.components.SectionTitle
import dev.musicforge.ui.components.Shelf
import dev.musicforge.ui.components.SongRow
import dev.musicforge.ui.openItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArtistViewModel(private val id: String, private val dao: dev.musicforge.data.db.MusicDao) : ViewModel() {
    sealed interface State {
        data object Loading : State
        data class Ready(val page: ArtistPage) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    /** Following keeps the artist in Library > Artists (a bookmark, as Metrolist and ConvX store it). */
    val following = dao.isArtistFollowed(id).map { it == true }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            _state.value = YouTube.artist(id).fold({ State.Ready(it) }, { State.Failed(it.message ?: "Can't load this artist") })
        }
    }

    fun toggleFollow(artist: com.metrolist.innertube.models.ArtistItem) = viewModelScope.launch {
        val existing = dao.artist(id)
        val now = if (following.value) null else System.currentTimeMillis()
        dao.upsert(
            (existing ?: dev.musicforge.data.db.ArtistEntity(id = id, name = artist.title, thumbnailUrl = artist.thumbnail, channelId = artist.channelId))
                .copy(bookmarkedAt = now, thumbnailUrl = existing?.thumbnailUrl ?: artist.thumbnail),
        )
    }
}

@Composable
fun ArtistScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModel(key = "artist:$id") { ArtistViewModel(id, context.graph.database.dao()) }
    val following by vm.following.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val player = LocalContext.current.graph.player

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { BackRow(onBack) }
        when (val s = state) {
            ArtistViewModel.State.Loading -> item {
                LoadingBar(visible = true)
                dev.musicforge.ui.components.SkeletonRows()
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
                        // Play and shuffle the top songs straight from the header, and follow.
                        val top = page.sections.firstOrNull { sec -> sec.items.isNotEmpty() && sec.items.all { it is SongItem } }
                            ?.items?.filterIsInstance<SongItem>().orEmpty()
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (top.isNotEmpty()) {
                                PillButton("Play", filled = true) { player.play(top.map { it.toMediaItem() }) }
                                PillButton("Shuffle") { player.play(top.shuffled().map { it.toMediaItem() }) }
                            }
                            PillButton(if (following) "Following" else "Follow", selected = following) { vm.toggleFollow(page.artist) }
                        }
                    }
                }
                for (section in page.sections) {
                    val songs = section.items.filterIsInstance<SongItem>()
                    if (songs.size == section.items.size && songs.isNotEmpty()) {
                        // "Songs" sections read best as a list you can play straight away.
                        item(key = "t:${section.title}") { SectionTitle(section.title) }
                        items(songs, key = { "s:${section.title}:${it.id}" }, contentType = { "song" }) { song ->
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
                page.description?.takeIf { it.isNotBlank() }?.let { about ->
                    item(key = "about") { About(about) }
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (filled) scheme.onPrimary else scheme.onBackground,
        modifier = Modifier
            .pressScale(press)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (filled) scheme.primary else if (selected) scheme.onBackground.copy(alpha = 0.16f) else scheme.surfaceContainerHigh)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    )
}

/** The artist's biography: four lines, then the rest on tap. */
@Composable
private fun About(text: String) {
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(Signal.radiusSurface))
            .pressScale(press)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(interactionSource = press, indication = null, onClickLabel = if (open) "Show less" else "Read more") { open = !open }
            .animateContentSize()
            .padding(18.dp),
    ) {
        Text("About", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (open) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (!open) Text("More", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    }
}
