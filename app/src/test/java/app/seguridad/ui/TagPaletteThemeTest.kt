package app.seguridad.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import app.seguridad.ui.theme.DarkColors
import app.seguridad.ui.theme.LightColors
import app.seguridad.vault.TagColor
import app.seguridad.vault.TagPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, review M-8 of task 4: the tag colours measured against the app's own themes (ui/theme/Theme.kt), not against a
 * copy of their values: every surface a tag sits on (the list, the chips, the dialogs) gives 4.5:1 or more, and the
 * backgrounds [TagPalette] lists are all in the theme, so a change of the theme (task 10) fails here first.
 */
class TagPaletteThemeTest {
    private fun argb(c: androidx.compose.ui.graphics.Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

    private fun ColorScheme.backgrounds(): Set<Long> = listOf(
        surface, background, surfaceVariant, surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
        surfaceContainerHigh, surfaceContainerHighest, secondaryContainer,
    ).mapTo(LinkedHashSet(), ::argb)

    @Test fun everyTagColourReadsOnEveryThemeSurface() {
        for ((scheme, colour) in listOf(LightColors to TagPalette::light, DarkColors to TagPalette::dark)) {
            for (bg in scheme.backgrounds()) {
                for (c in TagColor.entries) {
                    val r = TagPalette.contrast(colour(c), bg)
                    assertTrue("$c sobre ${bg.toString(16)}: $r", r >= 4.5)
                }
            }
        }
    }

    @Test fun thePaletteBackgroundsAreTheTheme() {
        assertTrue(LightColors.backgrounds().containsAll(TagPalette.LIGHT_BACKGROUNDS))
        assertTrue(DarkColors.backgrounds().containsAll(TagPalette.DARK_BACKGROUNDS))
    }
}
