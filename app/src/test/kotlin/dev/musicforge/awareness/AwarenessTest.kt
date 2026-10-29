package dev.musicforge.awareness

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AwarenessTest {

    @Test
    fun offlineShowsDownloadedMessage() {
        val s = Awareness.Situation(network = Awareness.NetworkState.Offline)
        assertThat(Awareness.situationMessage(s)).isEqualTo("You're offline. Showing what's downloaded.")
    }

    @Test
    fun meteredShowsLighterArtworkMessage() {
        val s = Awareness.Situation(network = Awareness.NetworkState.Metered)
        assertThat(Awareness.situationMessage(s)).isEqualTo("Metered network. Artwork is lighter.")
    }

    @Test
    fun validatedButSlowShowsSlowMessage() {
        val s = Awareness.Situation(network = Awareness.NetworkState.Online, validatedButSlow = true)
        assertThat(Awareness.situationMessage(s)).isEqualTo("Network is slow. Artwork is lighter.")
    }

    @Test
    fun batterySaverShowsLighterArtworkMessage() {
        val s = Awareness.Situation(network = Awareness.NetworkState.Online, batterySaver = true)
        assertThat(Awareness.situationMessage(s)).isEqualTo("Battery saver is on, so artwork is lighter.")
    }

    @Test
    fun lowStorageShowsPausedMessage() {
        val s = Awareness.Situation(network = Awareness.NetworkState.Online, lowStorage = true)
        assertThat(Awareness.situationMessage(s)).isEqualTo("Low storage. Downloads paused.")
    }

    @Test
    fun streamFailuresShowsCachedMessage() {
        val s = Awareness.Situation(network = Awareness.NetworkState.Online, recentStreamFailures = 3)
        assertThat(Awareness.situationMessage(s)).isEqualTo("Streaming is having trouble. Playing what's cached.")
    }

    @Test
    fun syncFailingShowsOutOfDateMessage() {
        val s = Awareness.Situation(network = Awareness.NetworkState.Online, syncFailing = true)
        assertThat(Awareness.situationMessage(s)).isEqualTo("Account sync is failing. Some things may be out of date.")
    }

    @Test
    fun syncStaleShowsPullToRefreshMessage() {
        val s = Awareness.Situation(network = Awareness.NetworkState.Online, syncStale = true)
        assertThat(Awareness.situationMessage(s)).isEqualTo("Account sync hasn't run in a while. Pull to refresh.")
    }

    @Test
    fun healthyReturnsNull() {
        val s = Awareness.Situation(network = Awareness.NetworkState.Online)
        assertThat(Awareness.situationMessage(s)).isNull()
    }

    @Test
    fun unknownReturnsNull() {
        val s = Awareness.Situation.Unknown
        assertThat(Awareness.situationMessage(s)).isNull()
    }

    @Test
    fun priorityOrderOfflineBeatsMetered() {
        // Offline should win over metered when both apply
        val s = Awareness.Situation(network = Awareness.NetworkState.Offline)
        assertThat(Awareness.situationMessage(s)).contains("offline")
    }

    @Test
    fun priorityOrderBatterySaverBeatsValidatedButSlow() {
        // Battery saver should win over slow network
        val s = Awareness.Situation(
            network = Awareness.NetworkState.Online,
            validatedButSlow = true,
            batterySaver = true,
        )
        assertThat(Awareness.situationMessage(s)).contains("Battery saver")
    }
}