package app.seguridad.ui.theme

import app.seguridad.settings.CustomPalette
import app.seguridad.settings.GlassLevel
import app.seguridad.settings.PaletteId
import app.seguridad.settings.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, task 5 bis: what the look does with the panels. «Reducir transparencias», «Alto contraste» and «Sólido» make them
 * opaque and never blurred; the blur is only from Android 12 (API 31) and never on the rows of a list or the cards; a
 * bar, a dialog or a menu that does not blur reads over anything behind it (R-C5).
 */
class GlassContrastTest {
    private val sdks = listOf(26, 30, 31, 35)
    private val palettes = NAMED_PALETTES.map { it to null } + listOf(PaletteId.CUSTOM to CustomPalette(4, 9, GlassLevel.HIGH))

    private fun look(
        palette: PaletteId = PaletteId.NIGHT,
        custom: CustomPalette? = null,
        style: ThemeStyle = ThemeStyle.GLASS,
        reduce: Boolean = false,
        high: Boolean = false,
        dark: Boolean = true,
        sdk: Int = 35,
    ) = lookFor(style, palette, custom, reduceTransparency = reduce, highContrast = high, dark = dark, sdk = sdk)

    @Test fun reduceTransparencyMakesPanelsOpaque() {
        for ((p, custom) in palettes) for (dark in listOf(true, false)) for (sdk in sdks) {
            val l = look(p, custom, reduce = true, dark = dark, sdk = sdk)
            assertTrue(l.opaque)
            assertFalse(l.blur)
            for (level in PanelLevel.entries) {
                assertFalse(l.blurs(level))
                assertEquals("$p $level", 1f, l.alpha(level, blurNow = false))
                assertEquals("$p $level", 1f, l.alpha(level, blurNow = true))
            }
            assertTrue("$p dark=$dark", failures(l.colors, opaque = true).isEmpty())
        }
    }

    @Test fun highContrastTurnsReduceTransparencyOn() {
        val l = look(high = true)
        assertTrue(l.opaque)
        assertFalse(l.blur)
        assertTrue(l.highContrast)
        for (level in PanelLevel.entries) assertEquals(1f, l.alpha(level, blurNow = false))
    }

    @Test fun solidIsOpaqueAndFlat() {
        for (sdk in sdks) {
            val l = look(style = ThemeStyle.SOLID, sdk = sdk)
            assertTrue(l.opaque)
            assertFalse(l.blur)
            assertFalse(l.glass)
            for (level in PanelLevel.entries) assertEquals(1f, l.alpha(level, blurNow = true))
        }
        assertTrue(look().glass)
    }

    /** The blur only from Android 12 (API 31), and never on the rows of the list or on the cards. */
    @Test fun blurOnlyFromApi31AndNeverOnRows() {
        for ((p, custom) in palettes) for (sdk in sdks) {
            val l = look(p, custom, sdk = sdk)
            assertEquals(sdk >= 31, l.blur)
            assertFalse(l.blurs(PanelLevel.CARD))
            assertEquals(sdk >= 31, l.blurs(PanelLevel.BAR))
            assertEquals(sdk >= 31, l.blurs(PanelLevel.OVERLAY))
        }
    }

    /**
     * R-C5: on Android 8 to 11 (and before a panel knows where the background is) a bar, a dialog or a menu does not
     * blur, so what scrolls behind it shows: it is more opaque, and every text on it reads over pure white and pure black.
     */
    @Test fun barsWithoutBlurReadOverAnyListBehind() {
        for ((p, custom) in palettes) for (dark in listOf(true, false)) {
            val l = look(p, custom, dark = dark, sdk = 30)
            for (level in listOf(PanelLevel.BAR, PanelLevel.OVERLAY)) {
                val unblurred = l.alpha(level, blurNow = false)
                assertTrue("$p $level", unblurred >= l.colors.floorAlpha)
                assertTrue("$p $level: more opaque without the blur", unblurred >= l.alpha(level, blurNow = true))
                val fill = argb(l.colors.panelColor(level))
                for (behind in listOf(WHITE, BLACK)) {
                    val panel = over(fill, unblurred.toDouble(), behind)
                    for ((name, fg) in textColors(l.colors)) {
                        val r = app.seguridad.vault.TagPalette.contrast(fg, panel)
                        assertTrue("$p $level $name dark=$dark sobre ${behind.toString(16)}: $r", r >= 4.5)
                    }
                }
            }
        }
    }

    /** The cards and rows are translucent in «Cristal»: the background shows through them. */
    @Test fun glassPanelsAreTranslucent() {
        for ((p, custom) in palettes) for (dark in listOf(true, false)) {
            val l = look(p, custom, dark = dark)
            assertFalse(l.opaque)
            for (level in PanelLevel.entries) assertTrue("$p $level", l.alpha(level, blurNow = l.blurs(level)) < 1f)
        }
    }

    /** Review M-8 (design «Movimiento»): the brief glow of a pressed panel only while Android has animations on. */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun pressGlowOnlyWithAnimations() {
        assertEquals(null, panelRipple(animationsOn = false))
        assertTrue(panelRipple(animationsOn = true) != null)
    }
}
