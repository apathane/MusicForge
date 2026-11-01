package dev.musicforge.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExperienceTest {
    @Test fun unknownOrMissingFallsBackToClassic() {
        assertThat(Experience.of(null)).isEqualTo(Experience.CLASSIC)
        assertThat(Experience.of("NOPE")).isEqualTo(Experience.CLASSIC)
        assertThat(Experience.of("FORGE")).isEqualTo(Experience.FORGE)
    }
}
