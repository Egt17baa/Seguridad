package app.seguridad.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.seguridad.settings.ThemeMode

/** Colors for results (match, error, warning) that Material 3 has no role for; one set per theme. */
@Immutable
data class StatusColors(val positive: Color, val negative: Color, val caution: Color)

// Contrast (WCAG): every text color is at least 4.5:1 (AA) on the background and the surface it sits on.
// Dark: on #161B22 text 14:1, secondary 7:1, primary 11:1, positive 9:1, negative 6:1, caution 11:1.
// Light: on #FFFFFF text 15:1, secondary 6:1, primary 5.5:1, positive 7:1, negative 6.5:1, caution 7:1 (all ≥ 4.5:1 on #E7ECF0).
private val DarkStatus = StatusColors(positive = Color(0xFF4ADE80), negative = Color(0xFFF87171), caution = Color(0xFFFCD34D))
private val LightStatus = StatusColors(positive = Color(0xFF166534), negative = Color(0xFFB91C1C), caution = Color(0xFF854D0E))

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF0E1116),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF9DA7B3),
    onSecondary = Color(0xFF0E1116),
    secondaryContainer = Color(0xFF2D333B),
    onSecondaryContainer = Color(0xFFE6EDF3),
    tertiary = Color(0xFF93C5FD),
    onTertiary = Color(0xFF0E1116),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF9DA7B3),
    surfaceTint = Color(0xFF5EEAD4),
    surfaceContainerLowest = Color(0xFF0E1116),
    surfaceContainerLow = Color(0xFF13171D),
    surfaceContainer = Color(0xFF161B22),
    surfaceContainerHigh = Color(0xFF1C2128),
    surfaceContainerHighest = Color(0xFF21262D),
    inverseSurface = Color(0xFFE6EDF3),
    inverseOnSurface = Color(0xFF161B22),
    inversePrimary = Color(0xFF0F766E),
    outline = Color(0xFF6E7681),
    outlineVariant = Color(0xFF30363D),
    error = Color(0xFFF87171),
    onError = Color(0xFF0E1116),
    errorContainer = Color(0xFF5C1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF57606A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7ECF0),
    onSecondaryContainer = Color(0xFF1F2328),
    tertiary = Color(0xFF1D4ED8),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEFF2F5),
    onSurfaceVariant = Color(0xFF57606A),
    surfaceTint = Color(0xFF0F766E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F8FA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF2F5),
    surfaceContainerHighest = Color(0xFFE7ECF0),
    inverseSurface = Color(0xFF1F2328),
    inverseOnSurface = Color(0xFFF6F8FA),
    inversePrimary = Color(0xFF5EEAD4),
    outline = Color(0xFF8C959F),
    outlineVariant = Color(0xFFD0D7DE),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF450A0A),
)

private val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

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

@Composable
fun SeguridadTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark()
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
