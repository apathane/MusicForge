package dev.musicforge.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VersionCodeTest {
    @Test fun previewTag() = assertThat(versionCodeOf("v0.14.0-preview.4")).isEqualTo(1404)

    @Test fun namedRelease() = assertThat(versionCodeOf("v0.13.1-little-bug-boy")).isEqualTo(1301)

    @Test fun plainReleaseWithoutDeclaredCode() = assertThat(versionCodeOf("v0.14.0")).isEqualTo(1400)

    @Test fun explicitCodeInReleaseNotesWinsOverTagFallback() =
        assertThat(versionCodeOf("v0.14.0", "Version 0.14.0-sonata.5 (code 1405)")).isEqualTo(1405)

    @Test fun stableReleaseCanSupersedePreview() =
        assertThat(versionCodeOf("v0.14.0", "Version 0.14.0-sonata.5 (code 1405)"))
            .isGreaterThan(versionCodeOf("v0.14.0-preview.4")!!)

    @Test fun previewBeatsReleaseFallback() =
        assertThat(versionCodeOf("v0.14.0-preview.4")).isGreaterThan(versionCodeOf("v0.14.0")!!)

    @Test fun laterPreviewWins() =
        assertThat(versionCodeOf("v0.14.0-preview.10")).isGreaterThan(versionCodeOf("v0.14.0-preview.4")!!)

    @Test fun tagWithoutV() = assertThat(versionCodeOf("0.14.0")).isEqualTo(1400)

    @Test fun nonZeroMajorIsIgnored() = assertThat(versionCodeOf("v1.0.0")).isNull()

    @Test fun missingBuildNumber() = assertThat(versionCodeOf("v0.14")).isNull()

    @Test fun garbageTag() = assertThat(versionCodeOf("latest")).isNull()
}
