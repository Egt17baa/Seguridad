package app.seguridad.vault

import kotlin.math.pow

/**
 * 2.0, task 4: the 8 fixed colours of a tag (design «Etiquetas de color»), as ARGB, one for each theme. A tag is drawn
 * as its name (with «#») in its colour, on the list's surfaces, so every colour has a contrast of at least 4.5:1 (WCAG
 * 2.2 AA for text) on each of them, in the light theme and in the dark one ([TagPaletteTest]). The colour is never the
 * only sign of anything (R-1): the name of the tag is always written, and the picker names each colour
 * ([tagColorName]). Measured against the real themes too (`TagPaletteThemeTest`, review M-8), which also checks that the
 * backgrounds listed here are the theme's. The dynamic colour of Android 12 (task 10) does not change these: they are fixed.
 */
object TagPalette {
    private val LIGHT = mapOf(
        TagColor.RED to 0xFFB91C1CL,
        TagColor.ORANGE to 0xFF9A3412L,
        TagColor.YELLOW to 0xFF854D0EL,
        TagColor.GREEN to 0xFF166534L,
        TagColor.TEAL to 0xFF115E59L,
        TagColor.BLUE to 0xFF1D4ED8L,
        TagColor.PURPLE to 0xFF7E22CEL,
        TagColor.GRAY to 0xFF4B5563L,
    )

    private val DARK = mapOf(
        TagColor.RED to 0xFFFCA5A5L,
        TagColor.ORANGE to 0xFFFDBA74L,
        TagColor.YELLOW to 0xFFFDE047L,
        TagColor.GREEN to 0xFF86EFACL,
        TagColor.TEAL to 0xFF5EEAD4L,
        TagColor.BLUE to 0xFF93C5FDL,
        TagColor.PURPLE to 0xFFD8B4FEL,
        TagColor.GRAY to 0xFFC9D1D9L,
    )

    /**
     * What a tag sits on in the light theme (ui/theme/Theme.kt): the surface, the background, the variant surface and the
     * container of a selected chip.
     */
    val LIGHT_BACKGROUNDS = listOf(0xFFFFFFFFL, 0xFFF6F8FAL, 0xFFEFF2F5L, 0xFFE7ECF0L)

    /** The same in the dark theme. */
    val DARK_BACKGROUNDS = listOf(0xFF161B22L, 0xFF0E1116L, 0xFF21262DL, 0xFF2D333BL)

    fun light(c: TagColor): Long = LIGHT.getValue(c)

    fun dark(c: TagColor): Long = DARK.getValue(c)

    private fun channel(v: Long): Double {
        val s = (v and 0xFF) / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** Relative luminance (WCAG 2.2) of an opaque ARGB colour. */
    fun luminance(argb: Long): Double = 0.2126 * channel(argb shr 16) + 0.7152 * channel(argb shr 8) + 0.0722 * channel(argb)

    /** The contrast ratio (WCAG 2.2) of two opaque ARGB colours, from 1 to 21. */
    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}

/** The name of each colour in the picker and for TalkBack: the colour is never the only sign (R-1). */
fun tagColorName(c: TagColor): String = when (c) {
    TagColor.RED -> "Rojo"
    TagColor.ORANGE -> "Naranja"
    TagColor.YELLOW -> "Amarillo"
    TagColor.GREEN -> "Verde"
    TagColor.TEAL -> "Turquesa"
    TagColor.BLUE -> "Azul"
    TagColor.PURPLE -> "Morado"
    TagColor.GRAY -> "Gris"
}
