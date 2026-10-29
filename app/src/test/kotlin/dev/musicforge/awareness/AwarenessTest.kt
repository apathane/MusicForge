package dev.musicforge.awareness

import com.google.common.truth.Truth.assertThat
import dev.musicforge.awareness.Awareness.Situation
import org.junit.Test

class AwarenessTest {
    @Test fun allWellSaysNothing() = assertThat(Awareness.message(Situation())).isNull()

    @Test fun offlineWinsOverEverything() = assertThat(
        Awareness.message(Situation(online = false, metered = true, batterySaver = true, lowStorage = true, failingStreams = true)),
    ).isEqualTo("You're offline. Your downloads still play.")

    @Test fun failingStreamsBeforeStorage() = assertThat(
        Awareness.message(Situation(failingStreams = true, lowStorage = true)),
    ).startsWith("Streaming is struggling")

    @Test fun storageBeforeBatterySaver() = assertThat(
        Awareness.message(Situation(lowStorage = true, batterySaver = true)),
    ).startsWith("Storage is almost full")

    @Test fun batterySaverBeforeMetered() = assertThat(
        Awareness.message(Situation(batterySaver = true, metered = true)),
    ).startsWith("Battery saver is on")

    @Test fun meteredAlone() = assertThat(Awareness.message(Situation(metered = true))).startsWith("On mobile data")
}
