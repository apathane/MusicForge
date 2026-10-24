package dev.musicforge.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpgradesTest {
    @Test
    fun keysRoundTrip() {
        assertThat(Upgrades.key("abc")).isEqualTo("abc#hq")
        assertThat(Upgrades.isUpgrade("abc#hq")).isTrue()
        assertThat(Upgrades.isUpgrade("abc")).isFalse()
        assertThat(Upgrades.base("abc#hq")).isEqualTo("abc")
        assertThat(Upgrades.base("abc")).isEqualTo("abc")
    }

    @Test
    fun onlyLowKnownBitratesAreOffered() {
        assertThat(Upgrades.isLow(50_000)).isTrue() // itag 249
        assertThat(Upgrades.isLow(70_000)).isTrue() // itag 250
        assertThat(Upgrades.isLow(130_000)).isFalse() // itag 251
        assertThat(Upgrades.isLow(0)).isFalse()
        assertThat(Upgrades.isLow(null)).isFalse()
    }

    @Test
    fun swapOnlyForARealImprovement() {
        assertThat(Upgrades.isBetter(130_000, 50_000)).isTrue()
        assertThat(Upgrades.isBetter(50_000, 50_000)).isFalse()
        assertThat(Upgrades.isBetter(null, 50_000)).isFalse()
        assertThat(Upgrades.isBetter(130_000, null)).isTrue()
    }
}
