package app.seguridad.ui.theme

import app.seguridad.settings.CustomPalette
import app.seguridad.settings.GlassLevel
import app.seguridad.settings.PaletteId
import app.seguridad.settings.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
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
        wallpaperBlur: Boolean = false,
    ) = lookFor(style, palette, custom, reduceTransparency = reduce, highContrast = high, dark = dark, sdk = sdk, wallpaperBlur = wallpaperBlur)

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
            assertFalse(l.translucent)
            for (level in PanelLevel.entries) assertEquals(1f, l.alpha(level, blurNow = true))
        }
        assertTrue(look().translucent)
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

    /** Re-review N-2: without animations a pressed panel still marks itself, without motion; with them, the ripple does. */
    @Test fun aPressedPanelMarksItselfWithoutAnimations() {
        assertTrue(staticPressMark(pressed = true, animationsOn = false))
        assertFalse(staticPressMark(pressed = false, animationsOn = false))
        assertFalse(staticPressMark(pressed = true, animationsOn = true))
        // The mark is the accent, which reads as a non-text element (3:1) on every surface of every palette.
        for ((p, custom) in palettes) for (dark in listOf(true, false)) for (style in ThemeStyle.entries) {
            val l = look(p, custom, style = style, dark = dark)
            for (s in surfacesOf(l.colors, l.opaque)) {
                val r = app.seguridad.vault.TagPalette.contrast(argb(l.colors.scheme.primary), s.argb)
                assertTrue("$p $style dark=$dark ${s.name}: $r", r >= 3.0)
            }
        }
    }
    /**
     * 2.0, task 6: the rows of the list are glass panels of the cards' level, so they never blur and their texts are the
     * ones [failures] measures on that panel over the darkest and the lightest point of the background; what a swipe
     * uncovers ([rowRevealColors], drawn only in the strip the row leaves) reads at 4.5:1 (7:1 in «Alto contraste»).
     */
    @Test fun theRowsOfTheListRead() {
        for ((p, custom) in palettes) for (dark in listOf(true, false)) for (style in ThemeStyle.entries) for (high in listOf(false, true)) {
            val l = look(p, custom, style = style, dark = dark, high = high)
            assertFalse(l.blurs(PanelLevel.CARD))
            val text = if (l.highContrast) 7.0 else 4.5
            for (edit in listOf(true, false)) {
                val (fg, bg) = rowRevealColors(l.scheme, edit)
                val r = app.seguridad.vault.TagPalette.contrast(argb(fg), argb(bg))
                assertTrue("$p $style dark=$dark high=$high edit=$edit: $r", r >= text)
            }
            assertTrue("$p $style dark=$dark high=$high", failures(l.colors, l.opaque).isEmpty())
        }
    }

    /**
     * 2.1, Aspecto: what each style draws. «Sólido» and «Degradado» have opaque panels (Degradado over its gradient,
     * Sólido over a plain colour); «Cristal», «Vidrio» and «Transparente» translucent ones; only «Transparente» lets the
     * wallpaper show, and never when «Reducir transparencias» or «Alto contraste» make it opaque (then it is «Cristal»
     * opaque).
     */
    @Test fun eachStyleDrawsWhatItSays() {
        for ((p, custom) in palettes) for (dark in listOf(true, false)) for (sdk in sdks) {
            val solid = look(p, custom, style = ThemeStyle.SOLID, dark = dark, sdk = sdk)
            val gradient = look(p, custom, style = ThemeStyle.GRADIENT, dark = dark, sdk = sdk)
            assertTrue(solid.opaque)
            assertTrue(gradient.opaque)
            assertFalse(solid.drawsBackground)
            assertTrue(gradient.drawsBackground)
            for (l in listOf(solid, gradient)) {
                assertFalse(l.translucent)
                assertFalse(l.strong)
                assertFalse(l.blur)
                assertFalse(l.wallpaperBehind)
                assertFalse(showsWallpaper(l))
            }
            for (style in listOf(ThemeStyle.GLASS, ThemeStyle.GLASS_STRONG, ThemeStyle.TRANSPARENT)) {
                val l = look(p, custom, style = style, dark = dark, sdk = sdk)
                assertTrue("$style", l.translucent)
                assertTrue("$style", l.drawsBackground)
                assertFalse("$style", l.opaque)
                assertEquals("$style", style == ThemeStyle.TRANSPARENT, l.wallpaperBehind)
                assertEquals("$style", style == ThemeStyle.TRANSPARENT, showsWallpaper(l))
                assertEquals("$style", style == ThemeStyle.GLASS_STRONG, l.strong)
            }
            for ((reduce, high) in listOf(true to false, false to true, true to true)) {
                val l = look(p, custom, style = ThemeStyle.TRANSPARENT, reduce = reduce, high = high, dark = dark, sdk = sdk)
                assertTrue(l.opaque)
                assertFalse(l.wallpaperBehind)
                assertFalse(showsWallpaper(l))
                assertTrue(l.translucent) // drawn as «Cristal» opaque
                assertTrue(l.drawsBackground)
            }
        }
    }

    /** The opaque styles keep every panel fully opaque, with or without the blur. */
    @Test fun opaqueStylesHaveOpaquePanels() {
        for ((p, custom) in palettes) for (dark in listOf(true, false)) for (sdk in sdks) {
            for (style in listOf(ThemeStyle.SOLID, ThemeStyle.GRADIENT)) {
                val l = look(p, custom, style = style, dark = dark, sdk = sdk)
                for (level in PanelLevel.entries) for (blurNow in listOf(false, true)) {
                    assertEquals("$p $style $level", 1f, l.alpha(level, blurNow))
                }
            }
        }
    }

    /** No blur before Android 12 in any style, and the blur from Android 12 only in the translucent ones. */
    @Test fun noStyleBlursOnAndroid11() {
        for (style in ThemeStyle.entries) for (dark in listOf(true, false)) {
            assertFalse("$style", look(style = style, dark = dark, sdk = 30).blur)
            assertEquals("$style", style in listOf(ThemeStyle.GLASS, ThemeStyle.GLASS_STRONG, ThemeStyle.TRANSPARENT), look(style = style, dark = dark, sdk = 31).blur)
        }
    }

    /**
     * «Transparente»: the layer is drawn at [AppColors.scrimAlpha] when the window blurs the wallpaper and a little more
     * opaque when it does not; the other styles have no layer (1). Every text still reads on the layer at that opacity,
     * over pure white and pure black, at every point of the background (the measure of task 1, at [Look.layerAlpha]).
     * With «Alto contraste» (or «Reducir transparencias») there is no wallpaper and no layer: it is opaque.
     */
    @Test fun theTransparentLayerIsMoreOpaqueWithoutTheWindowBlur() {
        for ((p, custom) in palettes) for (dark in listOf(true, false)) {
            val blurred = look(p, custom, style = ThemeStyle.TRANSPARENT, dark = dark, wallpaperBlur = true)
            val plain = look(p, custom, style = ThemeStyle.TRANSPARENT, dark = dark, wallpaperBlur = false)
            assertEquals(blurred.colors.scrimAlpha, blurred.layerAlpha)
            if (blurred.layerAlpha < 1f) assertTrue("$p dark=$dark", plain.layerAlpha > blurred.layerAlpha) else assertEquals(1f, plain.layerAlpha)
            assertTrue(plain.layerAlpha <= 1f)
            for (l in listOf(blurred, plain)) {
                for ((name, fg) in textColors(l.colors)) for (under in layerPoints(l)) {
                    val r = app.seguridad.vault.TagPalette.contrast(fg, under)
                    assertTrue("$p $name dark=$dark a=${l.layerAlpha} sobre ${under.toString(16)}: $r", r >= 4.5)
                }
            }
            for (wallpaperBlur in listOf(false, true)) for ((reduce, high) in listOf(false to true, true to false, true to true)) {
                val opaque = look(p, custom, style = ThemeStyle.TRANSPARENT, reduce = reduce, high = high, dark = dark, wallpaperBlur = wallpaperBlur)
                assertFalse("$p dark=$dark reduce=$reduce high=$high", opaque.wallpaperBehind)
                assertEquals("$p dark=$dark reduce=$reduce high=$high", 1f, opaque.layerAlpha)
            }
        }
        for (style in ThemeStyle.entries - ThemeStyle.TRANSPARENT) for (wallpaperBlur in listOf(false, true)) {
            assertEquals(1f, look(style = style, wallpaperBlur = wallpaperBlur).layerAlpha)
        }
    }

    /** The layer of «Transparente» at [Look.layerAlpha] over pure white and pure black, at the four points of the background. */
    private fun layerPoints(l: Look): List<Long> {
        val a = l.layerAlpha.toDouble()
        val (darkest, lightest) = bgBounds(l.colors.background)
        return listOf(l.colors.background.top, l.colors.background.bottom, darkest, lightest).flatMap { e -> listOf(over(e, a, WHITE), over(e, a, BLACK)) }
    }

    /**
     * Review I-1 and I-3 of task 2 (M-1): in «Transparente» a tag never sits on the bare layer. The container of an
     * unselected tag chip of the list's filters is [tagChipContainer], the card panel's fill at its opacity without blur
     * (no highlight, no edge of its own: Material's outline is the chip's only edge); every tag colour reads at 4.5:1 on
     * exactly that fill over the layer at [Look.layerAlpha] over pure white and pure black, at every point of the
     * background.
     */
    @Test fun tagsReadOnACardOverTheTransparentLayer() {
        for ((p, custom) in palettes) for (dark in listOf(true, false)) for (wallpaperBlur in listOf(false, true)) {
            val l = look(p, custom, style = ThemeStyle.TRANSPARENT, dark = dark, wallpaperBlur = wallpaperBlur)
            val fill = argb(l.colors.panelColor(PanelLevel.CARD))
            val alpha = l.alpha(PanelLevel.CARD, blurNow = false).toDouble()
            for (under in layerPoints(l)) {
                val panel = over(fill, alpha, under)
                for (c in app.seguridad.vault.TagColor.entries) {
                    val fg = if (dark) app.seguridad.vault.TagPalette.dark(c) else app.seguridad.vault.TagPalette.light(c)
                    val r = app.seguridad.vault.TagPalette.contrast(fg, panel)
                    assertTrue("$p dark=$dark blur=$wallpaperBlur $c sobre ${panel.toString(16)}: $r", r >= 4.5)
                }
            }
        }
    }

    /** Review I-2 of task 2: «Vidrio»'s lit edge never replaces the edge of «Alto contraste» (R-C5), in any style. */
    @Test fun theLitEdgeIsOnlyForVidrioWithoutHighContrast() {
        for (style in ThemeStyle.entries) for (dark in listOf(true, false)) {
            assertFalse("$style", litEdge(look(style = style, high = true, dark = dark)))
            assertFalse("$style", litEdge(look(style = style, high = true, reduce = true, dark = dark)))
            assertEquals("$style", style == ThemeStyle.GLASS_STRONG, litEdge(look(style = style, dark = dark)))
            // «Reducir transparencias» alone keeps it: the panel is opaque, the edge is still decoration.
            assertEquals("$style", style == ThemeStyle.GLASS_STRONG, litEdge(look(style = style, reduce = true, dark = dark)))
        }
    }

    /** Review m-4 of task 2: «Vidrio» rounds its cards (22 dp) and its overlays (32 dp); the bars stay straight. */
    @Test fun vidrioShapes() {
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(22.dp), panelShape(PanelLevel.CARD, strong = true))
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(32.dp), panelShape(PanelLevel.OVERLAY, strong = true))
        assertEquals(androidx.compose.ui.graphics.RectangleShape, panelShape(PanelLevel.BAR, strong = true))
        assertEquals(SectionShape, panelShape(PanelLevel.CARD))
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(28.dp), panelShape(PanelLevel.OVERLAY))
        assertEquals(androidx.compose.ui.graphics.RectangleShape, panelShape(PanelLevel.BAR))
    }
}
