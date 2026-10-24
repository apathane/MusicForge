package dev.musicforge.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MergeRecentTest {
    private val local = listOf(
        RecentPlay("sL1", 1000L, fromRemote = false),
        RecentPlay("sL2", 900L, fromRemote = false),
        RecentPlay("sShared", 500L, fromRemote = false),
    )
    private val remote = listOf(
        RecentPlay("sR1", 950L, fromRemote = true),
        RecentPlay("sR2", 50L, fromRemote = true),
        RecentPlay("sShared", 300L, fromRemote = true),
    )

    @Test
    fun mergesAndSortsNewestFirst() {
        val merged = mergeRecent(local, remote, limit = 16)
        assertThat(merged.map { it.songId }).containsExactly("sL1", "sR1", "sL2", "sShared", "sR2").inOrder()
    }

    @Test
    fun newerLocalOverridesRemote() {
        val merged = mergeRecent(local, remote, limit = 16)
        val shared = merged.first { it.songId == "sShared" }
        assertThat(shared.fromRemote).isFalse()
        assertThat(shared.playedAt).isEqualTo(500L)
    }

    @Test
    fun limitTruncates() {
        val big = (0..29).map { RecentPlay("s$it", it.toLong(), fromRemote = false) }
        assertThat(mergeRecent(big, emptyList(), limit = 10)).hasSize(10)
    }

    @Test
    fun emptyInputs() {
        assertThat(mergeRecent(emptyList(), emptyList())).isEmpty()
        assertThat(mergeRecent(local, emptyList()).map { it.songId })
            .containsExactly("sL1", "sL2", "sShared").inOrder()
        assertThat(mergeRecent(emptyList(), remote).map { it.songId })
            .containsExactly("sR1", "sShared", "sR2").inOrder()
    }

    @Test
    fun remoteNewestOnlyIsFlaggedRemotely() {
        val r = listOf(RecentPlay("sR", 800L, fromRemote = true))
        val l = listOf(RecentPlay("sL", 100L, fromRemote = false))
        val merged = mergeRecent(l, r)
        assertThat(merged.first().fromRemote).isTrue()
    }
}
