package app.seguridad.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0, tarea 4: los 8 colores de etiqueta tienen contraste de texto (WCAG 2.2 AA, 4,5:1) en el tema claro y en el oscuro (R-1). */
class TagPaletteTest {
    /**
     * 2.0, task 5 bis: the themes are many (six palettes, «Personalizado», the wallpaper's colours), so a tag no longer
     * lists their surfaces: it gives a bound. Every surface of a light theme is at least [TagPalette.LIGHT_MIN_BACKGROUND_LUMINANCE]
     * and of a dark one at most [TagPalette.DARK_MAX_BACKGROUND_LUMINANCE] (TagPaletteThemeTest), and on a surface at that
     * bound every colour still has 4.5:1. Contrast depends only on the two luminances, so a grey at the bound is the
     * worst case.
     */
    @Test fun textContrastInLightAndDark() {
        fun grey(l: Double): Long {
            var lo = 0; var hi = 255
            while (lo < hi) {
                val mid = (lo + hi) / 2
                val g = 0xFF000000L or (mid.toLong() shl 16) or (mid.toLong() shl 8) or mid.toLong()
                if (TagPalette.luminance(g) < l) lo = mid + 1 else hi = mid
            }
            return 0xFF000000L or (lo.toLong() shl 16) or (lo.toLong() shl 8) or lo.toLong()
        }
        val light = grey(TagPalette.LIGHT_MIN_BACKGROUND_LUMINANCE) // the first grey at or above the bound
        val dark = grey(TagPalette.DARK_MAX_BACKGROUND_LUMINANCE).let { g -> if (TagPalette.luminance(g) > TagPalette.DARK_MAX_BACKGROUND_LUMINANCE) g - 0x010101 else g }
        for (c in TagColor.entries) {
            val rl = minOf(TagPalette.contrast(TagPalette.light(c), light), contrastAt(TagPalette.light(c), TagPalette.LIGHT_MIN_BACKGROUND_LUMINANCE))
            assertTrue("$c claro: $rl", rl >= 4.5)
            val rd = minOf(TagPalette.contrast(TagPalette.dark(c), dark), contrastAt(TagPalette.dark(c), TagPalette.DARK_MAX_BACKGROUND_LUMINANCE))
            assertTrue("$c oscuro: $rd", rd >= 4.5)
        }
        // The 1.4.x surfaces are inside the bounds.
        for (bg in listOf(0xFFFFFFFFL, 0xFFF6F8FAL, 0xFFEFF2F5L, 0xFFE7ECF0L)) assertTrue(TagPalette.luminance(bg) >= TagPalette.LIGHT_MIN_BACKGROUND_LUMINANCE)
        for (bg in listOf(0xFF161B22L, 0xFF0E1116L, 0xFF21262DL, 0xFF2D333BL)) assertTrue(TagPalette.luminance(bg) <= TagPalette.DARK_MAX_BACKGROUND_LUMINANCE)
    }

    private fun contrastAt(fg: Long, bgLuminance: Double): Double {
        val l = TagPalette.luminance(fg)
        return (maxOf(l, bgLuminance) + 0.05) / (minOf(l, bgLuminance) + 0.05)
    }

    @Test fun eightNamedColours() {
        assertEquals(8, TagColor.entries.size)
        // The colour is never the only sign: each has a name for the picker and TalkBack, all different.
        assertEquals(8, TagColor.entries.map { tagColorName(it) }.toSet().size)
        assertEquals(8, TagColor.entries.map { TagPalette.light(it) }.toSet().size)
        assertEquals(8, TagColor.entries.map { TagPalette.dark(it) }.toSet().size)
    }

    @Test fun contrastFormula() {
        assertEquals(21.0, TagPalette.contrast(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, TagPalette.contrast(0xFF777777, 0xFF777777), 0.001)
    }
}
