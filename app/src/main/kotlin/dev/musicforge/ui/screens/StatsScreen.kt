package dev.musicforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.musicforge.graph
import dev.musicforge.ui.designsystem.SignalChip
import dev.musicforge.playback.toMediaItem
import dev.musicforge.ui.LocalBottomInset
import dev.musicforge.ui.LocalNavController
import dev.musicforge.ui.Routes
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.EmptyState
import dev.musicforge.ui.components.BackRow
import dev.musicforge.ui.components.ScreenHeader
import dev.musicforge.ui.components.SectionTitle
import dev.musicforge.ui.components.SongRow
import java.util.Calendar

private enum class Period(val label: String) { WEEK("This week"), MONTH("This month"), YEAR("This year"), ALL("All time") }

private fun Period.since(): Long {
    if (this == Period.ALL) return 0L
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    when (this) {
        Period.WEEK -> c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
        Period.MONTH -> c.set(Calendar.DAY_OF_MONTH, 1)
        Period.YEAR -> c.set(Calendar.DAY_OF_YEAR, 1)
        Period.ALL -> Unit
    }
    return c.timeInMillis
}

/** What you've actually been listening to, from the play history the app records. */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val graph = LocalContext.current.graph
    val dao = graph.database.dao()
    val nav = LocalNavController.current
    var period by remember { mutableStateOf(Period.MONTH) }
    val since = remember(period) { period.since() }
    val listened by remember(since) { dao.listenedMs(since) }.collectAsStateWithLifecycle(0L)
    val distinct by remember(since) { dao.distinctSongs(since) }.collectAsStateWithLifecycle(0)
    val songs by remember(since) { dao.topSongs(since) }.collectAsStateWithLifecycle(emptyList())
    val artists by remember(since) { dao.topArtists(since) }.collectAsStateWithLifecycle(emptyList())

    LazyColumn(Modifier.fillMaxSize(), contentPadding = LocalBottomInset.current) {
        item { BackRow(onBack) }
        item { ScreenHeader("Your listening", belowBackRow = true) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Period.entries) { p ->
                    SignalChip(p.label, p == period) { period = p }
                }
            }
        }
        item {
            val minutes = listened / 60_000
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text(
                    if (minutes >= 120) "${minutes / 60} hours" else "$minutes minutes",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    "of music, across $distinct ${if (distinct == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (songs.isEmpty()) {
            item { EmptyState("Nothing yet", "Plays count after 30 seconds (or half a short song). Your stats grow as you listen.") }
            return@LazyColumn
        }
        if (artists.isNotEmpty()) {
            item { SectionTitle("Top artists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(artists, key = { _, a -> a.artist.id }) { i, a ->
                        Column(
                            Modifier.width(104.dp).clickable(enabled = !a.artist.id.startsWith("name:")) { nav.navigate(Routes.artist(a.artist.channelId ?: a.artist.id)) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (a.artist.thumbnailUrl == null) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                androidx.compose.runtime.LaunchedEffect(a.artist.id) { context.graph.artRepair.missingArtist(a.artist.id) }
                            }
                            Artwork(a.artist.thumbnailUrl, 96.dp, corner = 48.dp, fallbackLetter = a.artist.name)
                            Spacer(Modifier.height(8.dp))
                            Text("${i + 1}. ${a.artist.name}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${a.timeMs / 60_000} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { SectionTitle("Top songs") }
        itemsIndexed(songs, key = { _, s -> s.song.id }) { i, s ->
            val item = remember(s.song.id) { dev.musicforge.data.db.Song(s.song, emptyList()).toMediaItem() }
            SongRow(item, "${s.plays} ${if (s.plays == 1) "play" else "plays"} · ${s.timeMs / 60_000} min", number = i + 1) {
                graph.player.play(songs.map { dev.musicforge.data.db.Song(it.song, emptyList()).toMediaItem() }, i)
            }
        }
    }
}
