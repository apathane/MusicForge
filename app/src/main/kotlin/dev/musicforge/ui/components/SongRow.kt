package dev.musicforge.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem

/** A playable song: tap plays, long-press or ⋮ opens actions, the badge shows download state. */
@Composable
fun SongRow(
    item: MediaItem,
    subtitle: String,
    number: Int? = null,
    extraActions: List<Pair<String, () -> Unit>> = emptyList(),
    dragHandle: (@Composable () -> Unit)? = null,
    onPlay: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val meta = item.mediaMetadata
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    TrackRow(
        title = meta.title?.toString().orEmpty(),
        subtitle = subtitle,
        artworkUrl = meta.artworkUri?.toString(),
        onClick = onPlay,
        onLongClick = { menu = true },
        number = number,
        interactionSource = press,
        trailing = {
            DownloadBadge(item.mediaId)
            IconAction(ForgeIcons.More, "More options") { menu = true }
            dragHandle?.invoke()
        },
    )
    if (menu) TrackActionsSheet(item, extraActions) { menu = false }
}
