package dev.musicforge.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rules for what the updater may offer. Fixtures are trimmed GitHub /releases responses:
 * only the fields pickNewerRelease reads. Running build in these tests is Toccata P3 (1503).
 * (Test joke: every fixture is a release that has never once been shipped to a real phone.)
 */
class ReleasePickerTest {

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        withApk: Boolean = true,
    ): String {
        val assets = if (withApk) {
            """[
              {"name":"MusicForge-$tag.apk","browser_download_url":"https://example.test/$tag.apk"},
              {"name":"SHA256SUMS.txt","browser_download_url":"https://example.test/$tag.sums"}
            ]"""
        } else {
            """[{"name":"notes.txt","browser_download_url":"https://example.test/notes.txt"}]"""
        }
        return """{"tag_name":"$tag","name":"$tag","body":"","prerelease":$prerelease,"draft":$draft,"assets":$assets}"""
    }

    private fun list(vararg releases: String) = releases.joinToString(prefix = "[", postfix = "]")

    private val running = 1503

    @Test fun newerStableIsOffered() {
        val picked = pickNewerRelease(list(release("v0.15.0")), includePreviews = false, currentVersionCode = running)
        assertThat(picked?.tagName).isEqualTo("v0.15.0")
        assertThat(picked?.apkAssetName).isEqualTo("MusicForge-v0.15.0.apk")
    }

    @Test fun sameOrOlderReleaseIsNotOffered() {
        val picked = pickNewerRelease(list(release("p3-0.15.0")), includePreviews = false, currentVersionCode = running)
        assertThat(picked).isNull()
    }

    @Test fun previewIsHiddenUntilOptedIn() {
        val json = list(release("p4-0.15.0", prerelease = true))
        assertThat(pickNewerRelease(json, includePreviews = false, currentVersionCode = running)).isNull()
        assertThat(pickNewerRelease(json, includePreviews = true, currentVersionCode = running)?.tagName)
            .isEqualTo("p4-0.15.0")
    }

    @Test fun stableBeatsPreviewAtTheSamePhase() {
        // v0.15.0 is code 1509, p4-0.15.0 is 1504: the stable one wins even when previews are on.
        val json = list(release("p4-0.15.0", prerelease = true), release("v0.15.0"))
        assertThat(pickNewerRelease(json, includePreviews = true, currentVersionCode = running)?.tagName)
            .isEqualTo("v0.15.0")
    }

    @Test fun draftsAreNeverOffered() {
        val json = list(release("v0.15.0", draft = true))
        assertThat(pickNewerRelease(json, includePreviews = false, currentVersionCode = running)).isNull()
    }

    @Test fun releaseWithoutApkIsNotOffered() {
        val json = list(release("v0.15.0", withApk = false))
        assertThat(pickNewerRelease(json, includePreviews = false, currentVersionCode = running)).isNull()
    }

    @Test fun highestQualifyingVersionWins() {
        val json = list(release("v0.15.0"), release("v0.15.1"), release("v0.14.0"))
        assertThat(pickNewerRelease(json, includePreviews = false, currentVersionCode = running)?.tagName)
            .isEqualTo("v0.15.1")
    }

    @Test fun emptyListMeansUpToDate() {
        assertThat(pickNewerRelease("[]", includePreviews = true, currentVersionCode = running)).isNull()
    }
}
