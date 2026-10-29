package dev.musicforge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import dev.musicforge.ui.designsystem.pressScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem

@Composable
fun Shelf(
    title: String,
    items: List<YTItem>,
    onClick: (YTItem) -> Unit,
    onLongPress: CollectionLongPress? = null,
) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))
        // YouTube sometimes lists an item twice in one shelf; keys must be unique, so the repeat goes.
        val unique = remember(items) { items.distinctBy { it.id } }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(unique, key = { it.id }) { item -> Box(Modifier.animateItem()) { Card(item, onClick, onLongPress) } }
        }
    }
}

/** Optional callback for long-press on collection cards (album/artist/playlist). */
typealias CollectionLongPress = (YTItem) -> Unit

@Composable
fun Card(
    item: YTItem,
    onClick: (YTItem) -> Unit,
    onLongPress: CollectionLongPress? = null,
) {
    val size: Dp = 148.dp
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> listOfNotNull(item.artists?.joinToString { it.name }, item.year?.toString()).joinToString(" · ")
        is PlaylistItem -> item.author?.name.orEmpty()
        is ArtistItem -> "Artist"
        else -> ""
    }
    val press = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier.width(size).pressScale(press).combinedClickable(
            interactionSource = press,
            indication = null,
            onClick = { onClick(item) },
            onLongClick = {
                if (onLongPress != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress(item)
                }
            },
            onLongClickLabel = "More options",
        ),
    ) {
        Artwork(item.thumbnail, size, corner = if (item is ArtistItem) size / 2 else 10.dp)
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
