package dev.musicforge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.musicforge.R

/**
 * Monochrome base (BitChord), colour only from the music itself. Inter stands in for
 * SF Pro, which can't be redistributed; tight negative tracking gives it the same heft.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.W400),
    Font(R.font.inter_medium, FontWeight.W500),
    Font(R.font.inter_semibold, FontWeight.W600),
    Font(R.font.inter_bold, FontWeight.W700),
    Font(R.font.inter_extrabold, FontWeight.W800),
)

private val ForgeTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W800, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1.0).sp),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W800, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.9).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W700, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W700, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W700, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.25).sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.15).sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.1).sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.1.sp),
)

// Signal tokens (docs/PERSONALIZED-UI-SPEC.md §3): canvas, ink, muted, line. Muted text clears
// 4.5:1 on its canvas in both themes (dark ~7.9:1, light ~5:1).
private val Ink = Color(0xFFF4F3EF)
private val InkLight = Color(0xFF151518)

private fun darkScheme(pureBlack: Boolean) = darkColorScheme(
    primary = Ink,
    onPrimary = InkLight,
    secondary = Color(0xFFD1D0CC),
    onSecondary = InkLight,
    // Pure black is black everywhere, not just the canvas: #09090B next to #000 is invisible, so
    // surfaces, cards and sheets go dark too and only hairlines separate them.
    background = if (pureBlack) Color.Black else Color(0xFF09090B),
    onBackground = Ink,
    surface = if (pureBlack) Color.Black else Color(0xFF0F0F12),
    onSurface = Ink,
    surfaceVariant = if (pureBlack) Color(0xFF111113) else Color(0xFF1C1C20),
    onSurfaceVariant = Color(0xFFA2A2AA),
    surfaceContainer = if (pureBlack) Color(0xFF0A0A0B) else Color(0xFF141417),
    surfaceContainerHigh = if (pureBlack) Color(0xFF111113) else Color(0xFF1C1C20),
    // Roles nothing here names but M3 components read (switch off track, slider ticks); left
    // unset they fall back to the baseline purple, a palette this app never auditioned.
    surfaceContainerHighest = if (pureBlack) Color(0xFF1A1A1D) else Color(0xFF26262B),
    surfaceContainerLow = if (pureBlack) Color(0xFF050506) else Color(0xFF111114),
    surfaceContainerLowest = if (pureBlack) Color.Black else Color(0xFF09090B),
    primaryContainer = if (pureBlack) Color(0xFF111113) else Color(0xFF1C1C20),
    onPrimaryContainer = Ink,
    secondaryContainer = if (pureBlack) Color(0xFF111113) else Color(0xFF1C1C20),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFFD1D0CC),
    outline = Color(0xFF2A2A2F),
    outlineVariant = Color(0xFF1F1F23),
    error = Color(0xFFFF453A),
)

private val LightScheme = lightColorScheme(
    primary = InkLight,
    onPrimary = Ink,
    secondary = Color(0xFF3A3A3E),
    onSecondary = Ink,
    background = Ink,
    onBackground = InkLight,
    surface = Color(0xFFFAF9F6),
    onSurface = InkLight,
    surfaceVariant = Color(0xFFE9E8E3),
    onSurfaceVariant = Color(0xFF68686F),
    surfaceContainer = Color(0xFFEDECE7),
    surfaceContainerHigh = Color(0xFFE4E3DE),
    surfaceContainerHighest = Color(0xFFDCDBD6),
    surfaceContainerLow = Color(0xFFF3F2EE),
    surfaceContainerLowest = Color(0xFFFAF9F6),
    primaryContainer = Color(0xFFE4E3DE),
    onPrimaryContainer = InkLight,
    secondaryContainer = Color(0xFFE4E3DE),
    onSecondaryContainer = InkLight,
    tertiary = Color(0xFF3A3A3E),
    outline = Color(0xFFD8D7D2),
    outlineVariant = Color(0xFFE4E3DE),
    error = Color(0xFFFF3B30),
)

/** Colours sampled from the current artwork. [Unset] until something plays. */
@Immutable
data class ArtworkColors(val accent: Color, val onAccent: Color, val ambient: Color) {
    companion object {
        val Unset = ArtworkColors(Color.Unspecified, Color.Unspecified, Color.Unspecified)
    }
}

val LocalArtworkColors = staticCompositionLocalOf { ArtworkColors.Unset }

enum class DarkMode { ON, OFF, AUTO }

@Composable
fun ForgeTheme(
    darkMode: DarkMode = DarkMode.AUTO,
    pureBlack: Boolean = false,
    artwork: ArtworkColors = ArtworkColors.Unset,
    content: @Composable () -> Unit,
) {
    // Pure black is a dark theme; chosen with Auto on a light phone it would otherwise do nothing.
    val dark = pureBlack || when (darkMode) {
        DarkMode.ON -> true
        DarkMode.OFF -> false
        DarkMode.AUTO -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalArtworkColors provides artwork) {
        val scheme = if (dark) darkScheme(pureBlack) else LightScheme
        MaterialTheme(colorScheme = scheme, typography = ForgeTypography) {
            // No Surface wraps the app, so Text would fall back to black in dark mode.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
