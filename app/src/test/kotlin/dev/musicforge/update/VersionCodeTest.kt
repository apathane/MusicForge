package dev.musicforge.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VersionCodeTest {
    @Test fun newPreviewTag() = assertThat(versionCodeOf("p4-0.14.0")).isEqualTo(1404)

    @Test fun legacyPreviewTagStillWorks() = assertThat(versionCodeOf("v0.14.0-preview.3")).isEqualTo(1403)

    @Test fun stableReleaseOutranksPreviews() =
        assertThat(versionCodeOf("v0.14.0")).isEqualTo(1409)

    @Test fun patchReleaseAdvancesVersionBand() =
        assertThat(versionCodeOf("v0.14.1")).isEqualTo(1419)

    @Test fun namedPatchRelease() =
        assertThat(versionCodeOf("v0.14.1-the-bug-has-left-the-building")).isEqualTo(1419)

    @Test fun explicitCodeInReleaseNotesWinsOverTagFallback() =
        assertThat(versionCodeOf("v0.14.0", "Version 0.14.0 (code 1408)")).isEqualTo(1408)

    @Test fun stableReleaseCanSupersedePreview() =
        assertThat(versionCodeOf("v0.14.0", "Version 0.14.0 (code 1409)"))
            .isGreaterThan(versionCodeOf("p4-0.14.0")!!)

    @Test fun laterPreviewWins() =
        assertThat(versionCodeOf("p5-0.14.0")).isGreaterThan(versionCodeOf("p4-0.14.0")!!)

    @Test fun tagWithoutV() = assertThat(versionCodeOf("0.14.0")).isEqualTo(1409)

    @Test fun nonZeroMajorIsIgnored() = assertThat(versionCodeOf("v1.0.0")).isNull()

    @Test fun missingPatchNumber() = assertThat(versionCodeOf("v0.14")).isNull()

    @Test fun garbageTag() = assertThat(versionCodeOf("latest")).isNull()
}
