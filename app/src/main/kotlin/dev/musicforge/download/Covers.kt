package dev.musicforge.download

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** Cover art for exported files. */
object Covers {
    /** YouTube video thumbnails are 4:3 with the picture letterboxed: keep the middle square. */
    fun centerSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        if (src.width == src.height) return src
        return Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    }

    /** FLAC METADATA_BLOCK_PICTURE body (front cover), as Vorbis/Opus comments carry it. */
    fun pictureBlock(image: ByteArray, mime: String, width: Int, height: Int): ByteArray {
        val o = ByteArrayOutputStream(image.size + 64)
        fun be32(v: Int) { o.write(v ushr 24 and 0xff); o.write(v ushr 16 and 0xff); o.write(v ushr 8 and 0xff); o.write(v and 0xff) }
        val m = mime.toByteArray(Charsets.US_ASCII)
        be32(3) // front cover
        be32(m.size); o.write(m)
        be32(0) // no description
        be32(width); be32(height)
        be32(24) // colour depth
        be32(0) // not indexed
        be32(image.size); o.write(image)
        return o.toByteArray()
    }
}
