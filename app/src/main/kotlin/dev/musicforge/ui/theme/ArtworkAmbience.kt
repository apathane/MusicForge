package dev.musicforge.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Everything the UI derives from the current artwork, computed once per track. */
data class Ambience(
    val colors: ArtworkColors,
    /** Window-shaped, blurred, tiny. Drawn upscaled with bilinear filtering. */
    val backdrop: ImageBitmap?,
)

/**
 * The single artwork pipeline. On an Adreno 610 a live RenderEffect blur behind scrolling
 * content costs frames, so instead each track's cover is decoded once at ~1/20 of the window
 * size, stack-blurred on the CPU (a few thousand pixels, well under a millisecond), and
 * reused for the player background, the glass surfaces and the accent colours.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkAmbience(private val context: Context, scope: CoroutineScope, artworkUrls: Flow<String?>) {
    /** Backdrop size in pixels; matches the phone's 1080×2412 aspect at 1/20 scale. */
    private val width = 54
    private val height = 120

    val ambience: StateFlow<Ambience> = artworkUrls
        .distinctUntilChanged()
        .mapLatest { url -> if (url == null) EMPTY else load(url) ?: EMPTY }
        .stateIn(scope, SharingStarted.Eagerly, EMPTY)

    private suspend fun load(url: String): Ambience? = withContext(Dispatchers.Default) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(width, height)
            .scale(Scale.FILL)
            .allowHardware(false) // we read pixels
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
        val source = result.image.toBitmap(width, height, Bitmap.Config.ARGB_8888)
        val colors = colorsOf(source)
        val blurred = source.copy(Bitmap.Config.ARGB_8888, true).also { stackBlur(it, 6) }
        Ambience(colors, blurred.asImageBitmap())
    }

    private fun colorsOf(bitmap: Bitmap): ArtworkColors {
        val palette = Palette.from(bitmap).maximumColorCount(12).generate()
        val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
            ?: return ArtworkColors.Unset
        // Accent: the most characterful colour, kept bright enough to read on near-black.
        val hsl = swatch.hsl.copyOf()
        hsl[2] = hsl[2].coerceIn(0.45f, 0.72f)
        val accent = ColorUtils.HSLToColor(hsl)
        // Ambient: the dominant colour pushed dark, for backgrounds behind white text.
        val dom = (palette.dominantSwatch ?: swatch).hsl.copyOf()
        dom[2] = dom[2].coerceAtMost(0.22f)
        dom[1] = dom[1].coerceAtMost(0.55f)
        val ambient = ColorUtils.HSLToColor(dom)
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.45) Color.Black else Color.White
        return ArtworkColors(Color(accent), onAccent, Color(ambient))
    }

    companion object {
        val EMPTY = Ambience(ArtworkColors.Unset, null)

        /** Classic box-approximated blur, in place. Radius in pixels of the small bitmap. */
        fun stackBlur(bitmap: Bitmap, radius: Int) {
            val w = bitmap.width
            val h = bitmap.height
            val px = IntArray(w * h)
            bitmap.getPixels(px, 0, w, 0, 0, w, h)
            repeat(3) {
                // Three box passes ≈ a gaussian.
                boxPass(px, w, h, radius, horizontal = true)
                boxPass(px, w, h, radius, horizontal = false)
            }
            bitmap.setPixels(px, 0, w, 0, 0, w, h)
        }

        private fun boxPass(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val lines = if (horizontal) h else w
            val len = if (horizontal) w else h
            val out = IntArray(len)
            for (line in 0 until lines) {
                var a = 0; var rr = 0; var g = 0; var b = 0
                fun at(i: Int) = px[if (horizontal) line * w + i.coerceIn(0, len - 1) else i.coerceIn(0, len - 1) * w + line]
                for (i in -r..r) {
                    val c = at(i)
                    a += c ushr 24; rr += (c shr 16) and 0xff; g += (c shr 8) and 0xff; b += c and 0xff
                }
                val n = 2 * r + 1
                for (i in 0 until len) {
                    out[i] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
                    val add = at(i + r + 1)
                    val sub = at(i - r)
                    a += (add ushr 24) - (sub ushr 24)
                    rr += ((add shr 16) and 0xff) - ((sub shr 16) and 0xff)
                    g += ((add shr 8) and 0xff) - ((sub shr 8) and 0xff)
                    b += (add and 0xff) - (sub and 0xff)
                }
                for (i in 0 until len) {
                    if (horizontal) px[line * w + i] = out[i] else px[i * w + line] = out[i]
                }
            }
        }
    }
}
