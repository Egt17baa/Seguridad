package app.seguridad.ui.theme

import app.seguridad.settings.PaletteId
import app.seguridad.settings.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.1, Aspecto (task 3 and the branch review): what the window does in «Transparente», worked out on the JVM. */
class WallpaperWindowTest {
    private val modes = listOf(false to false, true to false, false to true, true to true)

    /**
     * Review of task 3 (minor 2) and branch review I-1: the window and the theme say the same. The window shows the
     * wallpaper exactly when the theme draws its layer over it, and that is only «Transparente», not opaque and not in
     * split screen or a floating window (another app is behind then, O-A4).
     */
    @Test fun theWindowAndTheThemeAgree() {
        for (style in ThemeStyle.entries) for ((reduce, high) in modes) for (sdk in listOf(26, 30, 31, 35)) for (multi in listOf(false, true)) {
            val look = lookFor(style, PaletteId.NIGHT, null, reduceTransparency = reduce, highContrast = high, dark = true, sdk = sdk, multiWindow = multi)
            val case = "$style reduce=$reduce high=$high sdk=$sdk multi=$multi"
            val expected = style == ThemeStyle.TRANSPARENT && !reduce && !high && !multi
            assertEquals(case, expected, look.wallpaperBehind)
            assertEquals(case, expected, windowLookFor(look).showWallpaper)
            // The layer is translucent exactly when the wallpaper is under it; otherwise the background is opaque.
            assertEquals(case, expected, look.layerAlpha < 1f)
        }
    }

    /** Branch review I-1: in multi-window, «Transparente» is «Cristal», translucent over the app's own background. */
    @Test fun inMultiWindowTransparentIsCristal() {
        for (dark in listOf(true, false)) for (sdk in listOf(26, 31)) {
            val t = lookFor(ThemeStyle.TRANSPARENT, PaletteId.NIGHT, null, reduceTransparency = false, highContrast = false, dark = dark, sdk = sdk, multiWindow = true)
            val g = lookFor(ThemeStyle.GLASS, PaletteId.NIGHT, null, reduceTransparency = false, highContrast = false, dark = dark, sdk = sdk, multiWindow = true)
            assertFalse(t.wallpaperBehind)
            assertTrue(t.translucent)
            assertFalse(t.opaque)
            assertTrue(drawsBlobs(t))
            assertEquals(g.colors, t.colors)
            assertEquals(1f, t.layerAlpha)
        }
    }

    /** Owner decision (24-11-2026): nothing blurs the wallpaper, so the layer is always the one for a sharp image. */
    @Test fun theLayerIsAlwaysTheOneForASharpWallpaper() {
        for (p in NAMED_PALETTES) for (dark in listOf(true, false)) for (sdk in listOf(26, 30, 31, 35)) {
            val l = lookFor(ThemeStyle.TRANSPARENT, p, null, reduceTransparency = false, highContrast = false, dark = dark, sdk = sdk)
            assertEquals("$p dark=$dark sdk=$sdk", minOf(1f, l.colors.scrimAlpha + 0.06f), l.layerAlpha)
        }
        val night = lookFor(ThemeStyle.TRANSPARENT, PaletteId.NIGHT, null, reduceTransparency = false, highContrast = false, dark = true, sdk = 35)
        assertEquals(0.86f, night.layerAlpha, 0.0001f)
    }
}
