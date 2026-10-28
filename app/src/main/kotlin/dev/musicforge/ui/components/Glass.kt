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
import dev.musicforge.ui.backdrop.backdrops.LayerBackdrop
import dev.musicforge.ui.backdrop.drawBackdrop
import dev.musicforge.ui.backdrop.effects.blur
import dev.musicforge.ui.backdrop.effects.colorControls
import dev.musicforge.ui.backdrop.effects.lens
import dev.musicforge.ui.backdrop.highlight.Highlight
import dev.musicforge.ui.backdrop.highlight.HighlightStyle
import dev.musicforge.ui.backdrop.shadow.Shadow
import kotlin.math.roundToInt

/**
 * How glass surfaces are drawn. Chosen for the device, not for taste: the Honor X7b's
 * Adreno 610 can't afford a live backdrop capture per frame.
 */
enum class GlassTier {
    /** Tinted translucent fill. Free. */
    SOLID,

    /** Frosted Crayon: a slice of one pre-blurred artwork image, aligned to the window. One bitmap draw. */
    ARTWORK,

    /**
     * Liquid Glass (API 31+): the screen behind is blurred, gently saturated and bent at the
     * edges by a lens shader, with layered specular highlights. The content is recorded once
     * into a layer the glass samples. Restrict this to the nav pill and mini player: two small
     * surfaces, never a scrolling area, because live blur is expensive on mid-range GPUs.
     */
    LIQUID;

    companion object {
        /** Liquid where the platform can draw it, the artwork slice elsewhere. */
        val Default get() = if (dev.musicforge.ui.backdrop.isRenderEffectSupported()) LIQUID else ARTWORK
    }
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
    /** The recorded screen content, set only for [GlassTier.LIQUID]. */
    val live: LayerBackdrop? = null,
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
    val fill = if (tint != Color.Unspecified) tint else if (dark) scheme.surfaceContainerHigh else scheme.surface
    val rim = if (dark) Color.White else Color.Black
    val backdrop = style.backdrop.takeIf { style.tier != GlassTier.SOLID }
    style.live?.let { return liquid(it, shape, fill, rim, dark) }
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

/**
 * Lens from ConvX's nav-bar spec (0.60/0.69 of 48 dp). Its 3 dp blur left text behind the pill
 * sharp enough to fight the tab labels, so this blurs at 7 dp. Sampled at half resolution: the
 * blur hides the lost detail and the Adreno 610 does a quarter of the work.
 */
@Composable
private fun Modifier.liquid(backdrop: LayerBackdrop, shape: Shape, fill: Color, rim: Color, dark: Boolean): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scale = LiquidScale
    val blurPx = with(density) { 11.dp.toPx() } * scale
    val lensHeight = with(density) { (0.76f * 48).dp.toPx() } * scale
    val lensAmount = with(density) { (0.92f * 48).dp.toPx() } * scale
    // Keep the material transparent enough to reveal the live backdrop, while preserving label contrast.
    val tint = fill.copy(alpha = if (dark) 0.31f else 0.40f)
    val rimColor = Color.White.copy(alpha = if (dark) 0.72f else 0.82f)
    val shapeBlock = remember(shape) { { shape } }
    val effects: dev.musicforge.ui.backdrop.BackdropEffectScope.() -> Unit = remember(blurPx, lensHeight, lensAmount) {
        {
            colorControls(saturation = 1.6f)
            blur(blurPx)
            lens(lensHeight, lensAmount)
        }
    }
    val highlight = remember(rimColor) { { Highlight(width = 1.15.dp, style = HighlightStyle.Default(color = rimColor, angle = 38f)) } }
    val shadow = remember(dark) { { Shadow(radius = 22.dp, color = Color.Black.copy(alpha = if (dark) 0.38f else 0.16f)) } }
    val surface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = remember(tint) { { drawRect(tint) } }
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = shapeBlock,
            effects = effects,
            highlight = highlight,
            shadow = shadow,
            onDrawSurface = surface,
            backdropScale = scale,
        )
        .border(
            width = 0.8.dp,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) 0.48f else 0.62f),
                    rim.copy(alpha = 0.16f),
                    rim.copy(alpha = if (dark) 0.07f else 0.10f),
                ),
            ),
            shape = shape,
        )
}

private const val LiquidScale = 0.65f
