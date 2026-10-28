package dev.musicforge.data.lyrics

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricsTest {
    @Test fun rejectsInvalidSecondsAndUntimedText() {
        assertThat(Lrc.parse("plain text\n[00:60.00]invalid\n[01:99]also invalid")).isNull()
    }

    @Test fun parsesFractionalTimestampsAndSortsLines() {
        assertThat(Lrc.parse("[00:02.345]世界\n[00:01.23]two\n[00:01.2]one")).containsExactly(
            Lyrics.Line(1_200, "one"),
            Lyrics.Line(1_230, "two"),
            Lyrics.Line(2_345, "世界"),
        ).inOrder()
    }

    @Test fun appliesMultipleTimestampsToOneLine() {
        assertThat(Lrc.parse("[00:01.00][00:02.00]Same lyric")).containsExactly(
            Lyrics.Line(1_000, "Same lyric"),
            Lyrics.Line(2_000, "Same lyric"),
        ).inOrder()
    }

    @Test fun allowsLrcMinutesBeyondAnHour() {
        assertThat(Lrc.parse("[100:01.00]Long recording")).containsExactly(
            Lyrics.Line(6_001_000, "Long recording"),
        )
    }

    @Test fun unknownDurationPreservesProviderRelevanceOrder() {
        val first = JSONObject().put("duration", 0).put("syncedLyrics", "[00:01.00]First result")
        val second = JSONObject().put("duration", 210).put("syncedLyrics", "[00:02.00]Second result")

        assertThat(chooseLyricsSearchResult(listOf(first, second), null)).isEqualTo("[00:01.00]First result")
    }

    @Test fun knownDurationChoosesClosestValidDuration() {
        val missing = JSONObject().put("duration", 0).put("syncedLyrics", "[00:01.00]Missing duration")
        val far = JSONObject().put("duration", 210).put("syncedLyrics", "[00:02.00]Far result")
        val close = JSONObject().put("duration", 181).put("syncedLyrics", "[00:03.00]Close result")

        assertThat(chooseLyricsSearchResult(listOf(missing, far, close), 183)).isEqualTo("[00:03.00]Close result")
    }

    @Test fun fallsBackToFirstLyricResultWhenNoCandidateHasUsableDuration() {
        val noDuration = JSONObject().put("syncedLyrics", "[00:01.00]First")
        val zeroDuration = JSONObject().put("duration", 0).put("plainLyrics", "Second")

        assertThat(chooseLyricsSearchResult(listOf(noDuration, zeroDuration), 180)).isEqualTo("[00:01.00]First")
    }

    @Test fun ignoresSearchResultsWithoutLyrics() {
        val empty = JSONObject().put("duration", 180)
        val valid = JSONObject().put("duration", 182).put("plainLyrics", "Plain fallback")

        assertThat(chooseLyricsSearchResult(listOf(empty, valid), 181)).isEqualTo("Plain fallback")
    }
}
