package dev.musicforge.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeLinkTest {
    @Test fun songLinks() {
        assertThat(YouTubeLink.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=abc")).isEqualTo(YouTubeLink.Song("dQw4w9WgXcQ"))
        assertThat(YouTubeLink.parse("youtu.be/dQw4w9WgXcQ?t=10")).isEqualTo(YouTubeLink.Song("dQw4w9WgXcQ"))
        assertThat(YouTubeLink.parse("https://www.youtube.com/shorts/abcdefghijk")).isEqualTo(YouTubeLink.Song("abcdefghijk"))
        assertThat(YouTubeLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123")).isEqualTo(YouTubeLink.Song("dQw4w9WgXcQ"))
    }

    @Test fun pageLinks() {
        assertThat(YouTubeLink.parse("https://music.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG")).isEqualTo(YouTubeLink.Playlist("PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"))
        assertThat(YouTubeLink.parse("https://music.youtube.com/browse/MPREb_abc")).isEqualTo(YouTubeLink.Album("MPREb_abc"))
        assertThat(YouTubeLink.parse("https://music.youtube.com/channel/UCabc/")).isEqualTo(YouTubeLink.Artist("UCabc"))
    }

    @Test fun plainTextIsNotALink() {
        assertThat(YouTubeLink.parse("radiohead creep")).isNull()
        assertThat(YouTubeLink.parse("https://example.com/watch?v=x")).isNull()
        assertThat(YouTubeLink.parse("https://music.youtube.com/")).isNull()
    }
}
