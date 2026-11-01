package dev.musicforge.data.local

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deletion rules for the local scanner: when in doubt, keep the row. */
class LocalScanRulesTest {

    private val primary = "content://media/external/audio/media"
    private val sdCard = "content://media/1234-5678/audio/media"
    private fun id(collection: String, n: Long) = "$collection/$n"

    @Test
    fun incompleteListingRemovesNothing() {
        val removed = LocalAudioScanner.idsToRemove(
            existing = setOf(id(primary, 1), id(primary, 2)),
            present = setOf(id(primary, 1)),
            queriedCollections = listOf(primary),
            listingComplete = false,
        )
        assertThat(removed).isEmpty()
    }

    @Test
    fun emptyListingWithExistingRowsRemovesNothing() {
        val removed = LocalAudioScanner.idsToRemove(
            existing = setOf(id(primary, 1)),
            present = emptySet(),
            queriedCollections = listOf(primary),
            listingComplete = true,
        )
        assertThat(removed).isEmpty()
    }

    @Test
    fun deletedFileInAQueriedVolumeIsRemoved() {
        val removed = LocalAudioScanner.idsToRemove(
            existing = setOf(id(primary, 1), id(primary, 2)),
            present = setOf(id(primary, 1)),
            queriedCollections = listOf(primary),
            listingComplete = true,
        )
        assertThat(removed).containsExactly(id(primary, 2))
    }

    @Test
    fun songOnAnUnqueriedVolumeIsKept() {
        val removed = LocalAudioScanner.idsToRemove(
            existing = setOf(id(primary, 1), id(sdCard, 1)),
            present = setOf(id(primary, 1)),
            queriedCollections = listOf(primary),
            listingComplete = true,
        )
        assertThat(removed).isEmpty()
    }

    @Test
    fun presentButUnchangedSongIsKept() {
        // The old incremental scan only saw changed rows here and deleted the rest.
        val removed = LocalAudioScanner.idsToRemove(
            existing = setOf(id(primary, 1), id(primary, 2)),
            present = setOf(id(primary, 1), id(primary, 2)),
            queriedCollections = listOf(primary),
            listingComplete = true,
        )
        assertThat(removed).isEmpty()
    }
}

/** The folder clause must travel as selection args, or Axl's apostrophe breaks the query. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocalFolderSelectionTest {

    @Test
    fun apostropheInFolderNameStaysOutOfTheSql() {
        val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic%2FGuns%20N%27%20Roses")
        val (selection, args) = LocalAudioScanner.FolderMode.Folder(tree).selection("base", arrayOf("1"))

        assertThat(selection).doesNotContain("'")
        assertThat(selection).contains("?")
        assertThat(args.asList()).containsAtLeast("1", "Music/Guns N' Roses%", "%/Music/Guns N' Roses/%")
    }
}
