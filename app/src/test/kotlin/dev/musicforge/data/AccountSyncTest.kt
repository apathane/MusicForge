package dev.musicforge.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccountSyncTest {
    @Test
    fun firstSyncNeverRemovesAnything() {
        assertThat(AccountSync.removedRemotely(listOf("a", "b"), previous = null, current = setOf("a"))).isEmpty()
    }

    @Test
    fun removesOnlyWhatYouTubeHadAndDropped() {
        val removed = AccountSync.removedRemotely(
            local = listOf("kept", "gone", "phoneOnly"),
            previous = setOf("kept", "gone"),
            current = setOf("kept", "new"),
        )
        assertThat(removed).containsExactly("gone")
    }
}
