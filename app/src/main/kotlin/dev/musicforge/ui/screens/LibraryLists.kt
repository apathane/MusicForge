package dev.musicforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp)) { IconAction(ForgeIcons.Back, "Back", onBack) } }
        item { ScreenHeader("Albums", belowBackRow = true) }
        if (albums.isEmpty()) item { EmptyState("No saved albums", "Albums you save, or restore from a backup, appear here.") }
        items(albums, key = { it.id }) { a ->
            ListRow(a.thumbnailUrl, a.title, a.year?.toString().orEmpty(), round = false) {
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { Row(Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp)) { IconAction(ForgeIcons.Back, "Back", onBack) } }
        item { ScreenHeader("Artists", belowBackRow = true) }
        if (artists.isEmpty()) item { EmptyState("No followed artists", "Artists you follow, or restore from a backup, appear here.") }
        items(artists, key = { it.id }) { a ->
            // Artists we only know by name (from local files) have no page to open.
            val openable = !a.id.startsWith("name:") && !a.isLocal
            ListRow(a.thumbnailUrl, a.name, "", round = true) { if (openable) nav.navigate(Routes.artist(a.channelId ?: a.id)) }
        }
    }
}

@Composable
private fun ListRow(art: String?, title: String, subtitle: String, round: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(art, 56.dp, corner = if (round) 28.dp else 8.dp, fallbackLetter = title)
        Spacer(Modifier.width(14.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
