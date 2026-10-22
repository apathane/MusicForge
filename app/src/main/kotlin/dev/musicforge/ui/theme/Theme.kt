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

private fun darkScheme(pureBlack: Boolean) = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFD1D1D6),
    onSecondary = Color.Black,
    background = if (pureBlack) Color.Black else Color(0xFF050506),
    onBackground = Color.White,
    surface = if (pureBlack) Color.Black else Color(0xFF0D0D0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    surfaceContainer = Color(0xFF151517),
    surfaceContainerHigh = Color(0xFF1C1C1E),
    outline = Color(0xFF2C2C2E),
    outlineVariant = Color(0xFF1F1F22),
    error = Color(0xFFFF453A),
)

private val LightScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color(0xFF3A3A3C),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF7F7F9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE9E9EE),
    outline = Color(0xFFE5E5EA),
    outlineVariant = Color(0xFFEFEFF4),
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
    val dark = when (darkMode) {
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
