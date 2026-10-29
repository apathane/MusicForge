package dev.musicforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.graph
import dev.musicforge.ui.CollectionKind
import dev.musicforge.ui.components.CollectionTarget
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.Routes
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.CollectionActionsSheet
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.BackRow
import dev.musicforge.ui.components.ScreenHeader

@Composable
fun LibraryAlbumsScreen(onBack: () -> Unit) {
    val dao = LocalContext.current.graph.database.dao()
    val albums by remember { dao.libraryAlbums() }.collectAsStateWithLifecycle(emptyList())
    val nav = LocalNavController.current
    var sheetTarget by remember { mutableStateOf<CollectionTarget?>(null) }
    CoverGrid("Albums", onBack, empty = albums.isEmpty(), emptyTitle = "No saved albums", emptyBody = "Albums you save, or restore from a backup, appear here.") { side ->
        items(albums, key = { it.id }, contentType = { "album" }) { a ->
            GridCell(
                art = a.thumbnailUrl,
                title = a.title,
                subtitle = a.year?.toString().orEmpty(),
                round = false,
                side = side,
                onClick = { nav.navigate(Routes.collection(CollectionKind.ALBUM, a.id)) },
                onLongPress = {
                    sheetTarget = CollectionTarget.Album(
                        id = a.id,
                        title = a.title,
                        thumbnail = a.thumbnailUrl,
                        artists = emptyList(),
                        year = a.year,
                    )
                },
            )
        }
    }
    sheetTarget?.let { target ->
        CollectionActionsSheet(target) { sheetTarget = null }
    }
}

@Composable
fun LibraryArtistsScreen(onBack: () -> Unit) {
    val dao = LocalContext.current.graph.database.dao()
    val artists by remember { dao.libraryArtists() }.collectAsStateWithLifecycle(emptyList())
    val nav = LocalNavController.current
    var sheetTarget by remember { mutableStateOf<CollectionTarget?>(null) }
    CoverGrid("Artists", onBack, empty = artists.isEmpty(), emptyTitle = "No followed artists", emptyBody = "Artists you follow, or restore from a backup, appear here.") { side ->
        items(artists, key = { it.id }, contentType = { "artist" }) { a ->
            // Artists we only know by name (from local files) have no page to open.
            val openable = !a.id.startsWith("name:") && !a.isLocal
            GridCell(
                art = a.thumbnailUrl,
                title = a.name,
                subtitle = "",
                round = true,
                side = side,
                onClick = { if (openable) nav.navigate(Routes.artist(a.channelId ?: a.id)) },
                onLongPress = {
                    if (openable) {
                        sheetTarget = CollectionTarget.Artist(
                            id = a.id,
                            title = a.name,
                            thumbnail = a.thumbnailUrl,
                            channelId = a.channelId,
                        )
                    }
                },
            )
        }
    }
    sheetTarget?.let { target ->
        CollectionActionsSheet(target) { sheetTarget = null }
    }
}

private val Edge = 16.dp
private val Gap = 14.dp
private val MinCell = 148.dp

/**
 * Two columns on a phone, more on wider windows (cells at least 148 dp, spec F). The cell side is
 * worked out once for the grid, so cells don't each measure themselves while scrolling.
 */
@Composable
private fun CoverGrid(
    title: String,
    onBack: () -> Unit,
    empty: Boolean,
    emptyTitle: String,
    emptyBody: String,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.(side: androidx.compose.ui.unit.Dp) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val inner = maxWidth - Edge * 2
        val columns = ((inner + Gap) / (MinCell + Gap)).toInt().coerceAtLeast(1)
        val side = (inner - Gap * (columns - 1)) / columns
        val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> androidx.compose.foundation.lazy.grid.GridItemSpan =
            { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Edge, end = Edge, bottom = LocalBottomInset.current.calculateBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The side padding is for the cells; full-width rows bleed past it to line up with other screens.
            item(span = full) { BackRow(onBack, Modifier.bleed(Edge)) }
            item(span = full) { Box(Modifier.bleed(Edge)) { ScreenHeader(title, belowBackRow = true) } }
            if (empty) item(span = full) { Box(Modifier.bleed(Edge)) { EmptyState(emptyTitle, emptyBody) } }
            content(side)
        }
    }
}

/** Widens a full-span row by [x] on both sides, measured, so its own padding and alignment hold. */
private fun Modifier.bleed(x: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val px = x.roundToPx()
    val wide = constraints.copy(minWidth = constraints.minWidth + 2 * px, maxWidth = constraints.maxWidth + 2 * px)
    val placeable = measurable.measure(wide)
    layout(constraints.maxWidth, placeable.height) { placeable.place(-px, 0) }
}

@Composable
private fun GridCell(
    art: String?,
    title: String,
    subtitle: String,
    round: Boolean,
    side: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    val press = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxWidth().pressScale(press).combinedClickable(
            interactionSource = press,
            indication = null,
            onClick = onClick,
            onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress() },
            onLongClickLabel = "More options",
        ),
    ) {
        Artwork(art, side, corner = if (round) side / 2 else 10.dp, fallbackLetter = title)
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
