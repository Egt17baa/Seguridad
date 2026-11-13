package app.seguridad.ui.theme

import androidx.compose.ui.graphics.Color
import app.seguridad.settings.CustomPalette
import app.seguridad.settings.GlassLevel
import app.seguridad.settings.PaletteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * 2.0, task 5 bis (design «Aspecto «Cristal», temas y logo», R-1, R-C5, R-C6): every text reads at 4.5:1 or more on its
 * panel blended with the darkest and the lightest point of the background of its theme, in light and in dark; a bar, a
 * dialog or a menu that does not blur also over pure white and pure black (the list behind it); what is not text at 3:1;
 * «Alto contraste» at 7:1 and opaque. Measured with [failures] (ThemeSurfaces.kt), not with the corrector.
 */
class PalettesTest {
    private fun check(what: String, c: AppColors) {
        for (opaque in listOf(false, true)) {
            val bad = failures(c, opaque)
            assertTrue("$what opaque=$opaque:\n" + bad.take(12).joinToString("\n"), bad.isEmpty())
        }
    }

    @Test fun everyPaletteReadsInLightAndDark() {
        for (p in NAMED_PALETTES) for (dark in listOf(true, false)) check("$p dark=$dark", colorsFor(p, null, dark, highContrast = false))
    }

    @Test fun customIsCorrectedToContrast() {
        assertEquals(432, ALL_CUSTOM.size)
        for (c in ALL_CUSTOM) {
            val fixed = fixContrast(c)
            // Nothing to correct in what the picker offers; what is saved is what is shown.
            assertEquals(c, fixed)
            assertEquals(fixed, fixContrast(fixed))
            for (dark in listOf(true, false)) check("$c dark=$dark", colorsFor(PaletteId.CUSTOM, fixed, dark, highContrast = false))
        }
    }

    @Test fun aCustomOutOfRangeIsBroughtIntoTheTwelveTones() {
        assertEquals(CustomPalette(1, 11, GlassLevel.LOW), fixContrast(CustomPalette(13, -1, GlassLevel.LOW)))
    }

    /** R-C6: the colours Android gives from the wallpaper go through the same correction as «Personalizado». */
    @Test fun wallpaperColoursAreCorrectedToContrast() {
        for (w in WALLPAPER_SWEEP) {
            for (dark in listOf(true, false)) check("$w dark=$dark", colorsFor(PaletteId.WALLPAPER, null, dark, highContrast = false, wallpaper = w))
        }
    }

    /**
     * Review M-1: the colours Android gives are any colour, not only the sweep: [wallpaperSeeds] from extreme ones (white,
     * black, greys, pure primaries, an almost grey) and a fixed random sample off the sweep read too, and reach every
     * target on their own (no fall back to «Noche»).
     */
    @Test fun wallpaperColoursFromAnyColourRead() {
        val extremes = listOf(
            Color.White, Color.Black, Color(0xFF808080), Color(0xFF767676), Color.Red, Color.Green, Color.Blue,
            Color.Yellow, Color.Cyan, Color.Magenta, Color(0xFF7F8081), Color(0xFF010203), Color(0xFFFEFDFC),
        )
        val pairs = extremes.flatMap { a -> extremes.map { b -> a to b } }
        val random = Random(20261112)
        val sample = List(400) { Color(random.nextInt(0x1000000) or 0xFF000000.toInt()) to Color(random.nextInt(0x1000000) or 0xFF000000.toInt()) }
        for ((accent, background) in pairs + sample) {
            val w = wallpaperSeeds(accent, background)
            assertTrue("$w", w.accentHue in 0f..360f && w.backgroundHue in 0f..360f && w.saturation in 0f..1f)
            for (dark in listOf(true, false)) for (high in listOf(false, true)) {
                assertTrue("$w dark=$dark alto=$high", paletteMeets(PaletteId.WALLPAPER, null, dark, high, w))
                val c = colorsFor(PaletteId.WALLPAPER, null, dark, high, wallpaper = w)
                val bad = failures(c, opaque = false)
                assertTrue("$w dark=$dark alto=$high:\n" + bad.take(6).joinToString("\n"), bad.isEmpty())
            }
        }
        // A grey has no hue: its saturation is 0, and the wallpaper's palette is then a quiet one.
        assertEquals(0f, wallpaperSeeds(Color(0xFF808080), Color.White).saturation)
        assertEquals(240f, wallpaperSeeds(Color.Blue, Color.White).accentHue, 0.5f)
    }

    /** Review M-1: every palette reaches every target by itself, so the fall back to «Noche» never hides a palette today. */
    @Test fun everyPaletteMeetsItsTargetsItself() {
        for (dark in listOf(true, false)) for (high in listOf(false, true)) {
            for (p in NAMED_PALETTES) assertTrue("$p dark=$dark alto=$high", paletteMeets(p, null, dark, high))
            for (c in ALL_CUSTOM) assertTrue("$c dark=$dark alto=$high", paletteMeets(PaletteId.CUSTOM, c, dark, high))
            for (w in WALLPAPER_SWEEP) assertTrue("$w dark=$dark alto=$high", paletteMeets(PaletteId.WALLPAPER, null, dark, high, w))
        }
    }

    /** Review M-5: Ajustes names the palette that is drawn. */
    @Test fun theShownPaletteIsTheDrawnOne() {
        assertEquals(PaletteId.NIGHT, shownPalette(PaletteId.WALLPAPER, null, sdk = 30))
        assertEquals(PaletteId.WALLPAPER, shownPalette(PaletteId.WALLPAPER, null, sdk = 31))
        assertEquals(PaletteId.NIGHT, shownPalette(PaletteId.CUSTOM, null, sdk = 35))
        assertEquals(PaletteId.CUSTOM, shownPalette(PaletteId.CUSTOM, CustomPalette(1, 2, GlassLevel.LOW), sdk = 26))
        for (p in NAMED_PALETTES) for (sdk in listOf(26, 35)) assertEquals(p, shownPalette(p, null, sdk))
    }

    /** Without the colours of the wallpaper (Android 8 to 11) the palette is «Noche»; «Personalizado» without tones too. */
    @Test fun withoutItsColoursAPaletteIsNight() {
        for (dark in listOf(true, false)) {
            val night = colorsFor(PaletteId.NIGHT, null, dark, highContrast = false)
            assertEquals(night, colorsFor(PaletteId.WALLPAPER, null, dark, highContrast = false))
            assertEquals(night, colorsFor(PaletteId.CUSTOM, null, dark, highContrast = false))
        }
    }

    /** R-C6: «Alto contraste» aims at 7:1 and its panels are opaque, in every palette. */
    @Test fun highContrastIsOpaque() {
        val all = NAMED_PALETTES.map { it to null } + ALL_CUSTOM.filter { it.glass == GlassLevel.HIGH }.map { PaletteId.CUSTOM to it }
        for ((p, custom) in all) {
            for (dark in listOf(true, false)) {
                val c = colorsFor(p, custom, dark, highContrast = true)
                for (level in PanelLevel.entries) for (blur in listOf(true, false)) {
                    assertEquals("$p $custom $level", 1f, c.panelAlpha(level, opaque = false, blur = blur))
                }
                assertEquals(0f, c.highlightAlpha)
                val bad = failures(c, opaque = false, text = 7.0)
                assertTrue("$p $custom dark=$dark:\n" + bad.take(12).joinToString("\n"), bad.isEmpty())
            }
        }
    }

    /** The six palettes are six: each of its own, in light and in dark (R-C9: chosen for Clavero). */
    @Test fun sixDifferentPalettes() {
        assertEquals(6, NAMED_PALETTES.size)
        for (dark in listOf(true, false)) {
            val accents = NAMED_PALETTES.map { argb(colorsFor(it, null, dark, false).scheme.primary) }.toSet()
            val backs = NAMED_PALETTES.map { colorsFor(it, null, dark, false).background.top }.toSet()
            assertEquals(6, accents.size)
            assertEquals(6, backs.size)
        }
        assertNotEquals(colorsFor(PaletteId.NIGHT, null, true, false), colorsFor(PaletteId.NIGHT, null, false, false))
    }

    /** The glass of «Personalizado» is more see-through as its intensity goes up. */
    @Test fun moreGlassIsMoreSeeThrough() {
        for (dark in listOf(true, false)) {
            val a = GlassLevel.entries.map { colorsFor(PaletteId.CUSTOM, CustomPalette(1, 8, it), dark, false).cardAlpha }
            assertTrue("$a", a[0] > a[1] && a[1] > a[2])
        }
    }

    /** The window before Compose draws (and the splash of Android 12+) is the background of «Noche», the default. */
    @Test fun theWindowIsTheNightBackground() {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "app/src/main/res/values/colors.xml").isFile) dir = dir.parentFile
        val xml = File(dir, "app/src/main/res/values/colors.xml").readText()
        val ink = Regex("""<color name="ink">#([0-9A-Fa-f]{8})</color>""").find(xml)?.groupValues?.get(1)?.toLong(16) ?: error("ink")
        assertEquals(colorsFor(PaletteId.NIGHT, null, dark = true, highContrast = false).background.top.toString(16), ink.toString(16))
    }
}
