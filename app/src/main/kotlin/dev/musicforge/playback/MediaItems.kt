package dev.musicforge.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.metrolist.innertube.models.SongItem
import dev.musicforge.data.db.Song

private const val EXTRA_ARTIST_IDS = "forge.artistIds"
private const val EXTRA_ARTIST_NAMES = "forge.artistNames"
private const val EXTRA_ALBUM_ID = "forge.albumId"
private const val EXTRA_DURATION = "forge.durationSec"
private const val EXTRA_FROM = "forge.from"
private const val FROM_RADIO = "Radio"

/** Artist credits carried with a queue item so history can be written without a lookup. */
data class Credit(val id: String?, val name: String)

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
fun forgeMediaItem(
    videoId: String,
    title: String,
    credits: List<Credit>,
    artworkUrl: String?,
    albumId: String? = null,
    albumTitle: String? = null,
    durationSec: Int? = null,
): MediaItem {
    val extras = Bundle().apply {
        putStringArray(EXTRA_ARTIST_IDS, credits.map { it.id.orEmpty() }.toTypedArray())
        putStringArray(EXTRA_ARTIST_NAMES, credits.map { it.name }.toTypedArray())
        albumId?.let { putString(EXTRA_ALBUM_ID, it) }
        durationSec?.let { putInt(EXTRA_DURATION, it) }
    }
    return MediaItem.Builder()
        .setMediaId(videoId)
        // Placeholder; the resolving data source swaps in the real stream URL.
        .setUri("https://music.youtube.com/watch?v=$videoId")
        .setCustomCacheKey(videoId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(credits.joinToString { it.name })
                .setAlbumTitle(albumTitle)
                .setArtworkUri(fallbackArtwork(videoId, artworkUrl).toUri())
                .setExtras(extras)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
}

fun SongItem.toMediaItem() = forgeMediaItem(
    videoId = id,
    title = title,
    credits = artists.map { Credit(it.id, it.name) },
    artworkUrl = thumbnail,
    albumId = album?.id,
    albumTitle = album?.name,
    durationSec = duration,
)

fun Song.toMediaItem() = forgeMediaItem(
    videoId = song.id,
    title = song.title,
    credits = artists.map { Credit(it.id, it.name) },
    artworkUrl = song.thumbnailUrl,
    albumId = song.albumId,
    albumTitle = song.albumName,
    durationSec = song.duration.takeIf { it > 0 },
)

val MediaMetadata.credits: List<Credit>
    get() {
        val ids = extras?.getStringArray(EXTRA_ARTIST_IDS) ?: return emptyList()
        val names = extras?.getStringArray(EXTRA_ARTIST_NAMES) ?: return emptyList()
        return ids.zip(names).map { (id, name) -> Credit(id.ifEmpty { null }, name) }
    }

val MediaMetadata.albumId: String? get() = extras?.getString(EXTRA_ALBUM_ID)

/**
 * Where a queued song came from when it wasn't simply chosen: "Radio" for radio continuation,
 * or the Home shelf it was started from. The player and queue say so. In-memory only:
 * shortcut: QueueStore saves ids, so a restored queue loses it; persist it when queue
 * provenance grows past a label.
 */
val MediaMetadata.from: String? get() = extras?.getString(EXTRA_FROM)
val MediaMetadata.isFromRadio: Boolean get() = from == FROM_RADIO

fun MediaItem.markedFrom(label: String): MediaItem = buildUpon()
    .setMediaMetadata(mediaMetadata.buildUpon().setExtras(android.os.Bundle(mediaMetadata.extras ?: android.os.Bundle()).apply { putString(EXTRA_FROM, label) }).build())
    .build()

fun MediaItem.markedFromRadio(): MediaItem = markedFrom(FROM_RADIO)
val MediaMetadata.durationSec: Int? get() = extras?.getInt(EXTRA_DURATION, -1)?.takeIf { it > 0 }

/** A video id always has a thumbnail — use it when no artwork url was supplied. */
fun fallbackArtwork(videoId: String, artworkUrl: String?): String =
    artworkUrl?.takeIf { it.isNotBlank() }?.let(::hiResArtwork)
        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

/**
 * The same artwork at about [px] pixels: Google's image server resizes on request, so a 48 dp
 * row fetches and decodes a ~128 px file instead of the 544 px player cover. Steps keep the
 * number of cached variants small. Video thumbnails (i.ytimg.com) come in fixed sizes; unchanged.
 *
 * Google avatar/cover URLs (`lh3`, `yt3`) come in two size forms: `=wNNN-hNNN` and `=sNNN`.
 * i.ytimg.com URLs with `?sqp=`/`&rs=` signatures are left untouched — see [hiResArtwork].
 */
fun sizedArtwork(url: String, px: Int): String {
    if ("i.ytimg.com" in url) return url
    val step = listOf(128, 256, 544).firstOrNull { it >= px } ?: 544
    return when {
        Regex("=w\\d+-h\\d+").containsMatchIn(url) ->
            url.replace(Regex("=w\\d+-h\\d+[^&/?]*"), "=w$step-h$step-l90-rj")
        Regex("=s\\d+").containsMatchIn(url) ->
            url.replace(Regex("=s\\d+[^&/?]*"), "=s$step-l90-rj")
        else -> url
    }
}

/**
 * YouTube's 4:3 thumbnails (default, hqdefault, sddefault) carry a 16:9 picture between black
 * bars; the 16:9 ones (mqdefault, hq720, maxresdefault) don't and must not be zoomed.
 */
fun isLetterboxedThumbnail(url: String): Boolean =
    "i.ytimg.com" in url && Regex("/(default|hqdefault|sddefault)\\.jpg").containsMatchIn(url)

/**
 * YouTube Music thumbnails come sized in the URL (`=w120-h120`). Ask for 544 px:
 * enough for the full-screen player at 1080p, and Coil downsamples for small slots.
 *
 * i.ytimg.com thumbnails come in fixed sizes; don't touch them. A URL with a signature
 * (`?sqp=` or `&rs=`) must keep its path, because the signature is bound to that path —
 * rewriting it to hqdefault.jpg would break the signature and 403.
 */
fun hiResArtwork(url: String): String =
    when {
        Regex("=w\\d+-h\\d+").containsMatchIn(url) -> url.replace(Regex("=w\\d+-h\\d+[^&/?]*"), "=w544-h544-l90-rj")
        "i.ytimg.com" in url && !url.contains("?sqp=") && !url.contains("&rs=") -> url.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/hqdefault.jpg")
        else -> url
    }
