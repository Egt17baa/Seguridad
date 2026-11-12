package app.seguridad.settings

import kotlinx.serialization.Serializable

/**
 * 2.0, task 5 bis (design «Aspecto «Cristal», temas y logo»): how the app looks. Only the look: never a secret, nor
 * anything of a vault (R-C10: kept in the app's private storage, not encrypted, never sent anywhere).
 */
@Serializable
enum class ThemeStyle {
    /** «Cristal»: translucent panels over a background the app draws (the default, also after updating). */
    GLASS,

    /** «Sólido»: opaque panels on a plain background, without transparencies (the look of 1.4.x, with the palette's colours). */
    SOLID,
}

/** The palette: the six with a name, the colours Android takes from the wallpaper (Android 12+) and «Personalizado». */
@Serializable
enum class PaletteId { NIGHT, OCEAN, FOREST, SUNSET, GRAPHITE, SAND, WALLPAPER, CUSTOM }

/** How see-through the glass of «Personalizado» is. */
@Serializable
enum class GlassLevel { LOW, MEDIUM, HIGH }

/** How many tones «Personalizado» offers for the accent and for the background: one every 30°. */
const val CUSTOM_HUES = 12

/**
 * «Personalizado»: the accent and the background, each one of the [CUSTOM_HUES] tones (0 is red, then every 30°), and
 * the intensity of the glass. The app works the colours out and corrects their lightness so that every text reads
 * (ui/theme/Palettes.kt, `fixContrast`).
 */
@Serializable
data class CustomPalette(val accentHue: Int, val backgroundHue: Int, val glass: GlassLevel)

/** [c] with its tones brought into 0 until [CUSTOM_HUES] (a settings file is not trusted to have them there). */
fun saneCustom(c: CustomPalette): CustomPalette {
    val a = c.accentHue.mod(CUSTOM_HUES)
    val b = c.backgroundHue.mod(CUSTOM_HUES)
    return if (a == c.accentHue && b == c.backgroundHue) c else c.copy(accentHue = a, backgroundHue = b)
}
