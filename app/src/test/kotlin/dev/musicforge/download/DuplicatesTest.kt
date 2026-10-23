package dev.musicforge.download

import com.google.common.truth.Truth.assertThat
import dev.musicforge.download.Duplicates.Candidate
import org.junit.Test

class DuplicatesTest {
    private val downloaded = listOf(
        Candidate("albumId", "Roads", "Portishead", 305),
        Candidate("other", "Glory Box", "Portishead", 306),
    )

    @Test fun sameRecordingUnderAnotherIdSharesBytes() {
        assertThat(Duplicates.ownerOf(Candidate("songId", "Roads", "Portishead", 306), downloaded)).isEqualTo("albumId")
    }

    @Test fun normalizesCaseAccentsAndFeaturing() {
        val owner = listOf(Candidate("a", "Café Del Mar", "Energy 52", 240))
        assertThat(Duplicates.ownerOf(Candidate("b", "cafe del mar (feat. Someone)", "ENERGY 52", 241), owner)).isEqualTo("a")
    }

    @Test fun differentLengthIsAnotherRecording() {
        // A live or extended version: same title and artist, clearly different duration.
        assertThat(Duplicates.ownerOf(Candidate("live", "Roads", "Portishead", 362), downloaded)).isNull()
    }

    @Test fun neverItselfAndNeverWithoutDuration() {
        assertThat(Duplicates.ownerOf(Candidate("albumId", "Roads", "Portishead", 305), downloaded)).isNull()
        assertThat(Duplicates.ownerOf(Candidate("x", "Roads", "Portishead", 0), downloaded)).isNull()
    }

    @Test fun differentArtistIsNotADuplicate() {
        assertThat(Duplicates.ownerOf(Candidate("cover", "Roads", "Blindside", 305), downloaded)).isNull()
    }
}
