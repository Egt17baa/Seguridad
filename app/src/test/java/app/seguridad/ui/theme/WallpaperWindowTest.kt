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
     * split screen or a floating window (another app is behind then, O-A4), and only from Android 14 (F-1 of the final
     * audit, owner decision (b), 24-11-2026).
     */
    @Test fun theWindowAndTheThemeAgree() {
        for (style in ThemeStyle.entries) for ((reduce, high) in modes) for (sdk in listOf(26, 30, 31, 33, 34, 35)) for (multi in listOf(false, true)) {
            val look = lookFor(style, PaletteId.NIGHT, null, reduceTransparency = reduce, highContrast = high, dark = true, sdk = sdk, multiWindow = multi)
            val case = "$style reduce=$reduce high=$high sdk=$sdk multi=$multi"
            val expected = style == ThemeStyle.TRANSPARENT && !reduce && !high && !multi && sdk >= 34
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
        for (p in NAMED_PALETTES) for (dark in listOf(true, false)) for (sdk in listOf(34, 35)) {
            val l = lookFor(ThemeStyle.TRANSPARENT, p, null, reduceTransparency = false, highContrast = false, dark = dark, sdk = sdk)
            assertEquals("$p dark=$dark sdk=$sdk", minOf(1f, l.colors.scrimAlpha + 0.06f), l.layerAlpha)
        }
        val night = lookFor(ThemeStyle.TRANSPARENT, PaletteId.NIGHT, null, reduceTransparency = false, highContrast = false, dark = true, sdk = 35)
        assertEquals(0.86f, night.layerAlpha, 0.0001f)
    }

    /**
     * F-1 of the final audit, owner decision (b) (24-11-2026): before Android 14 nothing can stop Android from passing the
     * taps on the app to the wallpaper's app, so «Transparente» does not show the wallpaper there: it is drawn exactly as
     * «Cristal» (no layer, the blobs, the same colours). From Android 14 the window cuts those taps (secureWindow) and the
     * wallpaper shows.
     */
    @Test fun transparentNeedsAndroid14() {
        assertEquals(34, TRANSPARENT_MIN_SDK)
        for (p in NAMED_PALETTES) for (dark in listOf(true, false)) {
            for (sdk in 26..33) {
                val t = lookFor(ThemeStyle.TRANSPARENT, p, null, reduceTransparency = false, highContrast = false, dark = dark, sdk = sdk)
                val g = lookFor(ThemeStyle.GLASS, p, null, reduceTransparency = false, highContrast = false, dark = dark, sdk = sdk)
                val case = "$p dark=$dark sdk=$sdk"
                assertFalse(case, t.wallpaperBehind)
                assertFalse(case, windowLookFor(t).showWallpaper)
                assertEquals(case, 1f, t.layerAlpha)
                assertTrue(case, drawsBlobs(t))
                assertTrue(case, t.translucent)
                assertFalse(case, t.opaque)
                assertEquals(case, g.colors, t.colors)
                assertEquals(case, g.blur, t.blur)
            }
            for (sdk in 34..35) {
                val t = lookFor(ThemeStyle.TRANSPARENT, p, null, reduceTransparency = false, highContrast = false, dark = dark, sdk = sdk)
                assertTrue("$p dark=$dark sdk=$sdk", t.wallpaperBehind)
                assertTrue("$p dark=$dark sdk=$sdk", windowLookFor(t).showWallpaper)
                assertTrue("$p dark=$dark sdk=$sdk", t.layerAlpha < 1f)
                assertFalse("$p dark=$dark sdk=$sdk", drawsBlobs(t))
            }
        }
    }

    /**
     * F-1, owner decision (b): a «Transparente» saved (on Android 14, or restored on an older phone) is shown in Ajustes as
     * «Cristal» before Android 14, as it is drawn, and is not offered there; nothing changes what is saved (precedent of
     * shownPalette).
     */
    @Test fun theStyleShownAndOfferedFollowAndroid14() {
        assertEquals(ThemeStyle.GLASS, shownStyle(ThemeStyle.TRANSPARENT, 33))
        assertEquals(ThemeStyle.GLASS, shownStyle(ThemeStyle.TRANSPARENT, 26))
        assertEquals(ThemeStyle.TRANSPARENT, shownStyle(ThemeStyle.TRANSPARENT, 34))
        for (s in ThemeStyle.entries - ThemeStyle.TRANSPARENT) for (sdk in listOf(26, 33, 34, 35)) assertEquals(s, shownStyle(s, sdk))
        assertEquals(app.seguridad.settings.STYLE_ORDER - ThemeStyle.TRANSPARENT, offeredStyles(33))
        assertEquals(app.seguridad.settings.STYLE_ORDER - ThemeStyle.TRANSPARENT, offeredStyles(26))
        assertEquals(app.seguridad.settings.STYLE_ORDER, offeredStyles(34))
        assertEquals(app.seguridad.settings.STYLE_ORDER, offeredStyles(35))
        // What is shown as chosen is always one of the chips on offer.
        for (s in ThemeStyle.entries) for (sdk in listOf(26, 33, 34, 35)) assertTrue("$s $sdk", shownStyle(s, sdk) in offeredStyles(sdk))
    }
}
