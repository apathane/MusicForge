package dev.musicforge.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import dev.musicforge.ui.theme.ArtworkAmbience
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StackBlurTest {
    @Test fun uniformImageStaysUniformIncludingEdges() {
        // A review claimed the blur darkens edges; with edge-clamped sampling it must not.
        val b = Bitmap.createBitmap(54, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 40, 90)) }
        ArtworkAmbience.stackBlur(b, 6)
        for ((x, y) in listOf(0 to 0, 53 to 0, 0 to 119, 53 to 119, 27 to 60)) {
            assertThat(b.getPixel(x, y)).isEqualTo(Color.rgb(200, 40, 90))
        }
    }

    @Test fun hardEdgeIsSoftened() {
        val b = Bitmap.createBitmap(54, 120, Bitmap.Config.ARGB_8888)
        for (x in 0 until 54) for (y in 0 until 120) b.setPixel(x, y, if (x < 27) Color.BLACK else Color.WHITE)
        ArtworkAmbience.stackBlur(b, 6)
        val mid = Color.red(b.getPixel(27, 60))
        assertThat(mid).isIn(com.google.common.collect.Range.open(60, 200))
        assertThat(Color.red(b.getPixel(0, 60))).isEqualTo(0)
        assertThat(Color.red(b.getPixel(53, 60))).isEqualTo(255)
    }
}
