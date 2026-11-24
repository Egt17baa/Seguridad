package app.seguridad.ui.theme

import app.seguridad.settings.CustomPalette
import app.seguridad.settings.PaletteId
import app.seguridad.settings.ThemeStyle
import app.seguridad.vault.TagPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.1.1, task 1 (design «Barra de abajo en cápsula», objective 5 and its correction, option B): the bottom bar of the four
 * styles that are not «Sólido» floats as a capsule over the app's background only (the content never passes under it).
 * Its names and icons ([onSurfaceVariant][androidx.compose.material3.ColorScheme.onSurfaceVariant], and [primary] for the
 * chosen one) read at 4.5:1 (7:1 with «Alto contraste») on its colour blended with each end of the background (in
 * «Transparente», its layer over pure white and pure black), with and without the blur; the pill ([primary], with
 * [onPrimary] on it, controller Ruling) at 3:1 against it. It is more see-through than the bar of 2.1, whose opacities do
 * not change. Measured with the helpers of ThemeSurfaces.kt, not with the corrector (O-C6).
 */
class CapsuleColorsTest {
    private val translucentStyles = listOf(ThemeStyle.GLASS, ThemeStyle.GLASS_STRONG, ThemeStyle.TRANSPARENT)

    private fun look(
        style: ThemeStyle,
        palette: PaletteId,
        custom: CustomPalette? = null,
        dark: Boolean,
        high: Boolean = false,
        reduce: Boolean = false,
        wallpaper: WallpaperSeeds? = null,
        multiWindow: Boolean = false,
        sdk: Int = 35,
    ) = lookFor(style, palette, custom, reduceTransparency = reduce, highContrast = high, dark = dark, sdk = sdk, wallpaper = wallpaper, multiWindow = multiWindow)

    /** What can be behind the capsule: the ends of the background, or the layer of «Transparente» as it is drawn. */
    private fun behind(l: Look): List<Long> = if (l.wallpaperBehind) {
        (drawnLayer(l.colors, l.colors.scrimAlpha.toDouble()) + drawnLayer(l.colors, l.layerAlpha.toDouble())).distinct()
    } else {
        bgBounds(l.colors.background).toList()
    }

    /** What fails on the capsule of [l], with and without the blur. Empty when it reads. */
    private fun capsuleFailures(l: Look, what: String): List<String> {
        val bad = mutableListOf<String>()
        val c = l.colors
        val text = if (c.highContrast) 7.0 else 4.5
        val fill = argb(c.panelColor(PanelLevel.CAPSULE))
        val onVariant = argb(c.scheme.onSurfaceVariant)
        val primary = argb(c.scheme.primary)
        val onPrimary = argb(c.scheme.onPrimary)
        if (TagPalette.contrast(onPrimary, primary) < text) bad += "$what onPrimary/primary"
        for (blurNow in listOf(true, false)) {
            val alpha = l.alpha(PanelLevel.CAPSULE, blurNow).toDouble()
            for (b in behind(l)) {
                val panel = over(fill, alpha, b)
                for (p in listOf(panel, over(WHITE, c.highlightAlpha.toDouble(), panel))) {
                    val where = "$what blur=$blurNow α=$alpha sobre ${b.toString(16)} → ${p.toString(16)}"
                    for ((n, fg) in listOf("onSurfaceVariant" to onVariant, "primary" to primary)) {
                        val r = TagPalette.contrast(fg, p)
                        if (r < text) bad += "$where $n: $r"
                    }
                    val pill = TagPalette.contrast(primary, p)
                    if (pill < 3.0) bad += "$where pastilla: $pill"
                }
            }
        }
        return bad
    }

    /** The six palettes, «Cristal», «Vidrio» and «Transparente» (over the wallpaper and, in split screen, without it). */
    private fun fixedLooks(): List<Pair<String, Look>> = NAMED_PALETTES.flatMap { p ->
        listOf(true, false).flatMap { dark ->
            listOf(false, true).flatMap { high ->
                translucentStyles.map { st -> "$st $p dark=$dark alto=$high" to look(st, p, dark = dark, high = high) } +
                    ("TRANSPARENT multi $p dark=$dark alto=$high" to look(ThemeStyle.TRANSPARENT, p, dark = dark, high = high, multiWindow = true))
            }
        }
    }

    @Test fun capsuleReadsOverTheBackground() {
        val bad = fixedLooks().flatMap { (what, l) -> capsuleFailures(l, what) }
        assertTrue("${bad.size}:\n" + bad.take(20).joinToString("\n"), bad.isEmpty())
    }

    @Test fun pillReads() {
        for ((what, l) in fixedLooks()) {
            val text = if (l.highContrast) 7.0 else 4.5
            val r = TagPalette.contrast(argb(l.colors.scheme.onPrimary), argb(l.colors.scheme.primary))
            assertTrue("$what onPrimary sobre primary: $r", r >= text)
        }
        // The 3:1 of the pill against the capsule is measured in capsuleFailures (capsuleReadsOverTheBackground).
    }

    /** The capsule is more see-through than the bar of 2.1 in every fixed palette, light and dark. */
    @Test fun capsuleIsLighterThanThe21Bar() {
        val bad = mutableListOf<String>()
        for (st in translucentStyles) for (p in NAMED_PALETTES) for (dark in listOf(true, false)) {
            val c = colorsFor(p, null, dark, highContrast = false, style = st)
            if (!(c.capsuleAlpha < c.barAlpha)) bad += "$st $p dark=$dark capsule ${c.capsuleAlpha} bar ${c.barAlpha}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun highContrastAndReducedTransparencyMakeTheCapsuleOpaque() {
        for (st in ThemeStyle.entries) for (p in NAMED_PALETTES) for (dark in listOf(true, false)) for (sdk in listOf(30, 35)) {
            for ((high, reduce) in listOf(true to false, false to true, true to true)) {
                val l = look(st, p, dark = dark, high = high, reduce = reduce, sdk = sdk)
                for (blurNow in listOf(true, false)) assertEquals("$st $p dark=$dark alto=$high reduce=$reduce", 1f, l.alpha(PanelLevel.CAPSULE, blurNow))
            }
            if (st == ThemeStyle.GRADIENT) {
                val l = look(st, p, dark = dark, sdk = sdk)
                for (blurNow in listOf(true, false)) assertEquals("$p dark=$dark", 1f, l.alpha(PanelLevel.CAPSULE, blurNow))
            }
        }
        for (dark in listOf(true, false)) assertEquals(1f, colorsFor(PaletteId.NIGHT, null, dark, highContrast = true).capsuleAlpha)
    }

    @Test fun solidHasNoFloatingBar() {
        for (st in ThemeStyle.entries) for (reduce in listOf(false, true)) {
            assertEquals("$st", st != ThemeStyle.SOLID, look(st, PaletteId.NIGHT, dark = true, reduce = reduce).floatingBar)
        }
    }

    /** The capsule blurs like a bar: from Android 12, never with opaque panels; without the blur it is more opaque. */
    @Test fun theCapsuleBlursLikeABar() {
        for (st in ThemeStyle.entries) for (sdk in listOf(30, 31, 35)) {
            val l = look(st, PaletteId.NIGHT, dark = true, sdk = sdk)
            assertEquals("$st $sdk", l.blurs(PanelLevel.BAR), l.blurs(PanelLevel.CAPSULE))
        }
        for (st in translucentStyles) for (p in NAMED_PALETTES) for (dark in listOf(true, false)) {
            val l = look(st, p, dark = dark)
            val blurred = l.alpha(PanelLevel.CAPSULE, blurNow = true)
            val clear = l.alpha(PanelLevel.CAPSULE, blurNow = false)
            assertEquals("$st $p dark=$dark", l.colors.capsuleAlpha, blurred)
            assertEquals("$st $p dark=$dark", maxOf(minOf(1f, blurred + 0.1f), l.colors.floorAlpha), clear)
            assertEquals(l.colors.scheme.surfaceContainer, l.colors.panelColor(PanelLevel.CAPSULE))
        }
    }

    /** Review Focus 4: the 432 of «Personalizado» and the fixed sample of the wallpaper's colours. */
    @Test fun customAndWallpaperCapsulesRead() {
        val bad = mutableListOf<String>()
        for (dark in listOf(true, false)) for (high in listOf(false, true)) {
            for (st in translucentStyles) {
                for (c in ALL_CUSTOM) bad += capsuleFailures(look(st, PaletteId.CUSTOM, c, dark = dark, high = high), "$st $c dark=$dark alto=$high")
                for (w in WALLPAPER_SWEEP.filterIndexed { i, _ -> i % 7 == 0 }) {
                    bad += capsuleFailures(look(st, PaletteId.WALLPAPER, dark = dark, high = high, wallpaper = w), "$st $w dark=$dark alto=$high")
                }
            }
        }
        assertTrue("${bad.size}:\n" + bad.take(20).joinToString("\n"), bad.isEmpty())
    }

    /**
     * The opacities of 2.1 (taken from the code at a29d88c, task 1 report) do not change: card, bar, overlay, floor and the
     * layer of «Transparente», in every fixed palette and style, light and dark; with «Alto contraste», all 1.
     */
    @Test fun the21PanelsDoNotChange() {
        fun floor(st: ThemeStyle, p: PaletteId, dark: Boolean): Float = when {
            st == ThemeStyle.TRANSPARENT && dark -> if (p == PaletteId.OCEAN) 0.92f else 0.93f
            st == ThemeStyle.TRANSPARENT -> if (p == PaletteId.FOREST) 0.91f else 0.9f
            dark -> 0.94f
            else -> if (p == PaletteId.GRAPHITE) 0.9f else 0.91f
        }
        for (st in ThemeStyle.entries) for (p in NAMED_PALETTES) for (dark in listOf(true, false)) {
            val what = "$st $p dark=$dark"
            val c = colorsFor(p, null, dark, highContrast = false, style = st)
            val strong = st == ThemeStyle.GLASS_STRONG
            assertEquals(what, if (strong) 0.48f else 0.6f, c.cardAlpha)
            assertEquals(what, if (strong) 0.68f else 0.76f, c.barAlpha)
            assertEquals(what, if (strong) 0.79f else 0.85f, c.overlayAlpha)
            assertEquals(what, floor(st, p, dark), c.floorAlpha)
            assertEquals(what, if (st == ThemeStyle.TRANSPARENT) 0.8f else 1f, c.scrimAlpha)
            val h = colorsFor(p, null, dark, highContrast = true, style = st)
            for (v in listOf(h.cardAlpha, h.barAlpha, h.overlayAlpha, h.floorAlpha, h.scrimAlpha, h.capsuleAlpha)) assertEquals(what, 1f, v)
        }
        assertFalse(colorsFor(PaletteId.NIGHT, null, true, false).capsuleAlpha >= 1f)
    }
}
