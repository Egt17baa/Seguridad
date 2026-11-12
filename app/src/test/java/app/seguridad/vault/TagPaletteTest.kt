package app.seguridad.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0, tarea 4: los 8 colores de etiqueta tienen contraste de texto (WCAG 2.2 AA, 4,5:1) en el tema claro y en el oscuro (R-1). */
class TagPaletteTest {
    @Test fun textContrastInLightAndDark() {
        for (c in TagColor.entries) {
            for (bg in TagPalette.LIGHT_BACKGROUNDS) {
                val r = TagPalette.contrast(TagPalette.light(c), bg)
                assertTrue("$c claro sobre ${bg.toString(16)}: $r", r >= 4.5)
            }
            for (bg in TagPalette.DARK_BACKGROUNDS) {
                val r = TagPalette.contrast(TagPalette.dark(c), bg)
                assertTrue("$c oscuro sobre ${bg.toString(16)}: $r", r >= 4.5)
            }
        }
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
