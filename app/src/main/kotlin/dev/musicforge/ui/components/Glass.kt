package dev.musicforge.ui.components

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * How glass surfaces are drawn. Chosen for the device, not for taste: the Honor X7b's
 * Adreno 610 can't afford a live backdrop capture per frame.
 */
enum class GlassTier {
    /** Tinted translucent fill. Free. */
    SOLID,

    /** A slice of one pre-blurred artwork image, aligned to the window. One bitmap draw. */
    ARTWORK,

    /** Live RenderEffect blur of the content behind (API 31+). Opt-in. */
    LIVE,
}

/**
 * [backdrop] is the current artwork, blurred and downscaled by [backdropScale] relative to
 * the window. It is drawn upscaled with bilinear filtering, so the upscale is part of the blur.
 */
@Immutable
data class GlassStyle(
    val tier: GlassTier = GlassTier.SOLID,
    val backdrop: ImageBitmap? = null,
    val backdropScale: Float = 1f / 8f,
)

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }

/** Mutable holder so the window origin is read in the draw phase, never in composition. */
private class OriginHolder {
    val origin = mutableStateOf(Offset.Zero)
}

@Composable
fun Modifier.glass(shape: Shape, tint: Color = Color.Unspecified): Modifier {
    val style = LocalGlassStyle.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val fill = if (tint != Color.Unspecified) tint else if (dark) Color(0xFF1C1C1E) else Color(0xFFF7F7F9)
    val rim = if (dark) Color.White else Color.Black
    val backdrop = style.backdrop.takeIf { style.tier != GlassTier.SOLID }
    val holder = remember { OriginHolder() }

    val positioned = if (backdrop != null) {
        Modifier.onGloballyPositioned { holder.origin.value = it.positionInWindow() }
    } else {
        Modifier
    }
    return this
        .then(positioned)
        .clip(shape)
        .drawBehind {
            if (backdrop != null) {
                val o = holder.origin.value
                val s = style.backdropScale
                drawImage(
                    image = backdrop,
                    srcOffset = IntOffset((o.x * s).roundToInt().coerceAtLeast(0), (o.y * s).roundToInt().coerceAtLeast(0)),
                    srcSize = IntSize(
                        (size.width * s).roundToInt().coerceIn(1, backdrop.width),
                        (size.height * s).roundToInt().coerceIn(1, backdrop.height),
                    ),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Low,
                )
                drawRect(fill.copy(alpha = if (dark) 0.55f else 0.62f))
            } else {
                drawRect(fill.copy(alpha = if (dark) 0.92f else 0.94f))
            }
        }
        .border(
            width = 0.5.dp,
            brush = Brush.verticalGradient(listOf(rim.copy(alpha = 0.16f), rim.copy(alpha = 0.04f))),
            shape = shape,
        )
}
