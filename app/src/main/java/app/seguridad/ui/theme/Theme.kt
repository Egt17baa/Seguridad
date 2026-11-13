package app.seguridad.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.seguridad.settings.PaletteId
import app.seguridad.settings.Settings
import app.seguridad.settings.ThemeMode

/** Colors for results (match, error, warning) that Material 3 has no role for; one set per theme. */
@Immutable
data class StatusColors(val positive: Color, val negative: Color, val caution: Color)

// 2.0, task 5 bis: the colours come from the palette (Palettes.kt), corrected for contrast and measured by PalettesTest:
// every text at 4.5:1 or more (7:1 in «Alto contraste») on everything it can sit on, in every palette, light and dark.

private val LocalStatusColors = staticCompositionLocalOf { DEFAULT_STATUS }
private val DEFAULT_STATUS = StatusColors(Color(0xFF4ADE80), Color(0xFFF87171), Color(0xFFFCD34D))

/** Green for a match or success, in the current theme. */
val PositiveColor: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.positive

/** Red for errors, warnings and destructive actions, in the current theme. */
val NegativeColor: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.negative

/** Amber for "acceptable, could be better". */
val CautionColor: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.caution

/** Whether [mode] shows the dark palette right now. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/**
 * The app's theme from [settings]: the mode, the style, the palette (the wallpaper's colours from Android 12, read from
 * the dynamic colour Android works out), «Reducir transparencias» and «Alto contraste»; and the background behind it all.
 */
@Composable
fun SeguridadTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = settings.theme.isDark()
    val wallpaper = wallpaperFromSystem(settings.palette, dark)
    val look = remember(settings.style, settings.palette, settings.custom, settings.reduceTransparency, settings.highContrast, dark, wallpaper) {
        lookFor(
            settings.style, settings.palette, settings.custom, settings.reduceTransparency, settings.highContrast, dark,
            sdk = Build.VERSION.SDK_INT, wallpaper = wallpaper,
        )
    }
    // Re-review N-1: Android's animations setting, read on start and on every return to the app.
    CompositionLocalProvider(LocalAnimationsOn provides animationsOnSetting()) { ProvideLook(look, content) }
}

/** [look] for [content]: the Material colours, the status colours, the glass and the background drawn behind. */
@Composable
fun ProvideLook(look: Look, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLook provides look, LocalStatusColors provides look.colors.status) {
        MaterialTheme(colorScheme = look.scheme) { GlassBackground(content = content) }
    }
}

/** The two hues Android works out from the wallpaper (its dynamic colour), only with that palette and from Android 12. */
@Composable
private fun wallpaperFromSystem(palette: PaletteId, dark: Boolean): WallpaperSeeds? {
    if (palette != PaletteId.WALLPAPER || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(dark, configuration) {
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        wallpaperSeeds(dynamic.primary, dynamic.secondaryContainer)
    }
}
