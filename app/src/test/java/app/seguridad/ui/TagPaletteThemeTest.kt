package app.seguridad.ui

import app.seguridad.settings.PaletteId
import app.seguridad.ui.theme.ALL_CUSTOM
import app.seguridad.ui.theme.AppColors
import app.seguridad.ui.theme.NAMED_PALETTES
import app.seguridad.ui.theme.WALLPAPER_SWEEP
import app.seguridad.ui.theme.colorsFor
import app.seguridad.ui.theme.surfacesOf
import app.seguridad.vault.TagColor
import app.seguridad.vault.TagPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, review M-8 of task 4, widened by task 5 bis: the tag colours (fixed, ruling T4) measured against the app's own
 * themes, not against a copy of their values: every surface a tag can sit on (the list on the background, the cards, the
 * chips, the dialogs and menus, translucent or opaque, blurred or not, with the highlight) in every palette («Noche» to
 * «Arena», the 432 «Personalizado» and a sweep of «Colores del fondo de pantalla»), in light and in dark, «Cristal» and
 * «Sólido», with and without «Alto contraste», gives 4.5:1 or more. A change of a theme fails here first.
 */
class TagPaletteThemeTest {
    private fun check(what: String, c: AppColors) {
        val colour = if (c.dark) TagPalette::dark else TagPalette::light
        for (opaque in listOf(false, true)) {
            for (s in surfacesOf(c, opaque)) {
                for (t in TagColor.entries) {
                    val r = TagPalette.contrast(colour(t), s.argb)
                    assertTrue("$what: $t sobre ${s.name} ${s.argb.toString(16)}: $r", r >= 4.5)
                }
            }
        }
    }

    @Test fun everyTagColourReadsOnEveryThemeSurface() {
        for (dark in listOf(true, false)) for (high in listOf(false, true)) {
            for (p in NAMED_PALETTES) check("$p dark=$dark alto=$high", colorsFor(p, null, dark, high))
            for (c in ALL_CUSTOM) check("$c dark=$dark alto=$high", colorsFor(PaletteId.CUSTOM, c, dark, high))
            for (w in WALLPAPER_SWEEP.filterIndexed { i, _ -> i % 7 == 0 }) {
                check("$w dark=$dark alto=$high", colorsFor(PaletteId.WALLPAPER, null, dark, high, wallpaper = w))
            }
        }
    }

    /** Every surface stays inside the bound that [TagPalette] proves enough for its eight colours. */
    @Test fun everySurfaceIsInsideTheTagBound() {
        fun inside(what: String, c: AppColors) {
            for (opaque in listOf(false, true)) for (s in surfacesOf(c, opaque)) {
                val l = TagPalette.luminance(s.argb)
                if (c.dark) assertTrue("$what ${s.name}: $l", l <= TagPalette.DARK_MAX_BACKGROUND_LUMINANCE)
                else assertTrue("$what ${s.name}: $l", l >= TagPalette.LIGHT_MIN_BACKGROUND_LUMINANCE)
            }
        }
        // Review M-6: every palette and style, the 432 «Personalizado» and the whole sweep of the wallpaper's colours.
        for (dark in listOf(true, false)) for (high in listOf(false, true)) {
            for (p in NAMED_PALETTES) inside("$p dark=$dark alto=$high", colorsFor(p, null, dark, high))
            for (c in ALL_CUSTOM) inside("$c dark=$dark alto=$high", colorsFor(PaletteId.CUSTOM, c, dark, high))
            for (w in WALLPAPER_SWEEP) inside("$w dark=$dark alto=$high", colorsFor(PaletteId.WALLPAPER, null, dark, high, wallpaper = w))
        }
    }
}
