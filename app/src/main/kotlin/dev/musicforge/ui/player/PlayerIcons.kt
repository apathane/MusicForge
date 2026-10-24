package dev.musicforge.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** A few icons the player needs that the rest of the app doesn't, so they live here
 *  (in the player package) instead of in the shared icon set. Built from moveTo/lineTo/
 *  cubicTo only, so the path data is always valid. */
object PlayerIcons {
    val VolumeUp: ImageVector = imageVector(
        "VolumeUp",
        "M2,8L2,16L12,16L12,8ZM12,4.5L20,12L12,19.5ZM16,3.5C16.5,3.5 17.3,3.5 18,2.8C18.7,2.1 18.7,1 18,0.3"
    )

    val VolumeOff: ImageVector = imageVector(
        "VolumeOff",
        "M2,8L2,16L12,16L12,8ZM12,4.5L20,12L12,19.5ZM3,21L5,19L21,3L19,5Z"
    )

    val Headphones: ImageVector = imageVector(
        "Headphones",
        "M6,14C6,8 18,8 18,14ZM5,14L5,19L8,19L8,14ZM16,14L16,19L19,19L19,14Z"
    )

    val LockClosed: ImageVector = imageVector(
        "LockClosed",
        "M8,11C8,7.5 16,7.5 16,11ZM7,11L7,21L17,21L17,11Z"
    )

    val LockOpen: ImageVector = imageVector(
        "LockOpen",
        "M8,13C8,9.5 16,9.5 16,13ZM16,13L16,9ZM7,13L7,21L17,21L17,13Z"
    )

    val Lyrics: ImageVector = imageVector(
        "Lyrics",
        "M5,3h14v2H5ZM5,7h14v2H5zM5,11h10v2H5zM5,15h14v2H5zM5,19h10v2H5z"
    )

    val Queue: ImageVector = imageVector(
        "Queue",
        "M3,5h18v2H3zM3,11h12v2H3zM3,17h18v2H3z"
    )

    val Play: ImageVector = imageVector(
        "Play",
        "M5,4v16l13-8z"
    )

    val Download: ImageVector = imageVector(
        "Download",
        "M5,20h14v-2H5zM12,2l-4,4h3v6H9l4,4 4-4h-3V6h3z"
    )

    val Sleep: ImageVector = imageVector(
        "Sleep",
        "M12.34,2.02C6.59,1.82 2,6.42 2,12c0,5.52 4.48,10 10,10 3.71,0 6.93,-2.02 8.66,-5.02 -7.51,-0.25 -12.09,-8.43 -8.32,-14.96z"
    )

    val Heart: ImageVector = imageVector(
        "Heart",
        "M12,21.35l-1.16-1.03C5.84,16.26 2,12.16 2,7.4v-.2 0a5,5 0 0,1,5-5c1.95,0 3.68,1.03 4.58,2.53C12.32,3.23 14.05,2.2 16,2.2c1.95,0 3.68,1.03 4.58,2.53C22,6.91 23,8.69 23,10.4v.2 0a5,5 0 0,1-5,5c-.2,0-.4-.01-.58-.04L12,21.35z"
    )

    val HeartOutline: ImageVector = imageVector(
        "HeartOutline",
        "M12,21.35l-1.16-1.03C5.84,16.26 2,12.16 2,7.4v-.2a5,5 0 0,1,5-5c1.95,0 3.68,1.03 4.58,2.53C12.32,3.23 14.05,2.2 16,2.2c1.95,0 3.68,1.03 4.58,2.53C22,6.91 23,8.69 23,10.4v.2a5,5 0 0,1-5,5c-.2,0-.4-.01-.58-.04L12,21.35z"
    )

    val Shuffle: ImageVector = imageVector(
        "Shuffle",
        "M4,6h2.24l4.91 4.91 4.65-4.65C15.64,6.1 15.35,6 15,6h-1V4h1c2.21,0 3.97,1.75 4.5-4 .53,2.53-.16,5.03-2.31,6.71L14.23,9.35 9.24,14.34 4.59,9.68 4,10.27V6zM4,14v4.24l4.91-4.91 4.65,4.65C14.64,13.9 15.33,14 16,14h1v2h-1c-2.21,0-3.97-1.75-4.5-4.5-.53-2.53.16-5.03,2.31-6.71L18.77,9.35l2.66,2.66a5.42,5.42 0 0,1,1.17-1.32L23,11v8H4z"
    )

    val RepeatOne: ImageVector = imageVector(
        "RepeatOne",
        "M7,7h2v6.5L11,15l1-1.5L11.5,13 11.5,11 13,11 13,7h2v6.5L11,15l-1-1.5L10.5,13 10.5,11 9,11V7zM6,4v3H3l7,7V4h2v6L4,10V7V4zM18,20v-3h3l-7-7V20h-2v-6l6,6L18,20z"
    )

    val Repeat: ImageVector = imageVector(
        "Repeat",
        "M6,4v3H3l7,7V4h4v7L18,3l3,3h-3v7l-7,7L4,7V4z"
    )

    val More: ImageVector = imageVector(
        "More",
        "M12,8a4,4 0 0,0-4,4 4,4 0 0,0,4,4 4,4 0 0,0,4-4 4,4 0 0,0-4-4m0-2a6,6 0 0,1,6,6 6,6 0 0,1-6,6 6,6 0 0,1-6-6 6,6 0 0,1,6-6m0,14a8,8 0 0,1,8-8 8,8 0 0,1-8,8 8,8 0 0,1-8-8 8,8 0 0,1,8-8"
    )
}

private fun imageVector(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.White)).build()
