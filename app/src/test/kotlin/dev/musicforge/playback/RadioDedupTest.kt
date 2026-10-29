package dev.musicforge.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure tests for radio continuation de-duplication. Ported alongside Metrolist's queue-on-related. */
class RadioDedupTest {
    @Test
    fun keepsItemsNotInQueue() {
        val fresh = filterFreshRadioIds(listOf("c", "d", "e"), setOf("a", "b"))
        assertThat(fresh).containsExactly("c", "d", "e").inOrder()
    }

    @Test
    fun dropsItemsAlreadyQueued() {
        val fresh = filterFreshRadioIds(listOf("a", "c", "b", "d"), setOf("a", "b"))
        assertThat(fresh).containsExactly("c", "d").inOrder()
    }

    @Test
    fun emptyQueueKeepsAll() {
        assertThat(filterFreshRadioIds(listOf("a", "b"), emptySet())).containsExactly("a", "b").inOrder()
    }

    @Test
    fun allQueuedYieldsEmpty() {
        assertThat(filterFreshRadioIds(listOf("a", "b"), setOf("a", "b"))).isEmpty()
    }

    @Test
    fun filterFreshByTypeKeepsOrderAndOmitsDuplicates() {
        data class S(val id: String)
        val items = listOf(S("a"), S("c"), S("b"), S("d"))
        val fresh = items.filterFresh(setOf("a", "b")) { it.id }
        assertThat(fresh.map { it.id }).containsExactly("c", "d").inOrder()
    }
}
