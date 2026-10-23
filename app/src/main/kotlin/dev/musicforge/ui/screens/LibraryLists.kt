package dev.musicforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.graph
import dev.musicforge.ui.CollectionKind
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.Routes
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.IconAction
import dev.musicforge.ui.components.ScreenHeader

@Composable
fun LibraryAlbumsScreen(onBack: () -> Unit) {
    val dao = LocalContext.current.graph.database.dao()
    val albums by remember { dao.libraryAlbums() }.collectAsStateWithLifecycle(emptyList())
    val nav = LocalNavController.current
    CoverGrid("Albums", onBack, empty = albums.isEmpty(), emptyTitle = "No saved albums", emptyBody = "Albums you save, or restore from a backup, appear here.") {
        items(albums, key = { it.id }, contentType = { "album" }) { a ->
            GridCell(a.thumbnailUrl, a.title, a.year?.toString().orEmpty(), round = false) {
                nav.navigate(Routes.collection(CollectionKind.ALBUM, a.id))
            }
        }
    }
}

@Composable
fun LibraryArtistsScreen(onBack: () -> Unit) {
    val dao = LocalContext.current.graph.database.dao()
    val artists by remember { dao.libraryArtists() }.collectAsStateWithLifecycle(emptyList())
    val nav = LocalNavController.current
    CoverGrid("Artists", onBack, empty = artists.isEmpty(), emptyTitle = "No followed artists", emptyBody = "Artists you follow, or restore from a backup, appear here.") {
        items(artists, key = { it.id }, contentType = { "artist" }) { a ->
            // Artists we only know by name (from local files) have no page to open.
            val openable = !a.id.startsWith("name:") && !a.isLocal
            GridCell(a.thumbnailUrl, a.name, "", round = true) { if (openable) nav.navigate(Routes.artist(a.channelId ?: a.id)) }
        }
    }
}

/** Two columns on a phone, more on wider windows (cells at least 148 dp, spec F). */
@Composable
private fun CoverGrid(
    title: String,
    onBack: () -> Unit,
    empty: Boolean,
    emptyTitle: String,
    emptyBody: String,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit,
) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(148.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomInset.current.calculateBottomPadding()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        // The grid's side padding is for the cells; the header lines up with every other screen's.
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { Row(Modifier.offset(x = (-12).dp).statusBarsPadding().padding(top = 4.dp)) { IconAction(ForgeIcons.Back, "Back", onBack) } }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { Box(Modifier.offset(x = (-16).dp)) { ScreenHeader(title, belowBackRow = true) } }
        if (empty) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { EmptyState(emptyTitle, emptyBody) }
        content()
    }
}

@Composable
private fun GridCell(art: String?, title: String, subtitle: String, round: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = maxWidth
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Artwork(art, side, corner = if (round) side / 2 else 10.dp, fallbackLetter = title)
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (round) androidx.compose.ui.text.style.TextAlign.Center else null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
