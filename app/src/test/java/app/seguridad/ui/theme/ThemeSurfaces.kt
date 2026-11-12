package app.seguridad.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.seguridad.settings.CUSTOM_HUES
import app.seguridad.settings.CustomPalette
import app.seguridad.settings.GlassLevel
import app.seguridad.settings.PaletteId
import app.seguridad.vault.TagPalette
import kotlin.math.roundToLong

/**
 * 2.0, task 5 bis: what the contrast tests measure, written here and not taken from the corrector in Palettes.kt (O-C6:
 * the test measures what is claimed). Every colour a text can sit on is listed, as Android draws it: a translucent panel
 * is its colour blended in sRGB over what is behind it, and the highlight is white blended over the panel.
 *
 * What can be behind:
 * - the background: its darkest and its lightest point, channel by channel over every corner of the drawing (each
 *   gradient end, each blob at 0 or at its peak); any point of it is a blend of those, so it is never darker or lighter;
 * - for a bar, a dialog or a menu that does not blur (Android 8 to 11, or no backdrop yet), anything: the list, its texts,
 *   another app's colours. Pure white and pure black are the worst cases (R-C5);
 * - for one that blurs, the background drawn again under it (it covers what is behind).
 */
internal const val WHITE = 0xFFFFFFFFL
internal const val BLACK = 0xFF000000L

internal fun argb(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

/** [top] at [alpha] over the opaque [under], per channel in sRGB (how a View or Compose blends a translucent fill). */
internal fun over(top: Long, alpha: Double, under: Long): Long {
    fun ch(v: Long, s: Int) = (v shr s) and 0xFF
    fun mix(s: Int) = (alpha * ch(top, s) + (1 - alpha) * ch(under, s)).roundToLong().coerceIn(0, 255)
    return (0xFFL shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

/** The darkest and the lightest point the background can have, channel by channel (see the class comment). */
internal fun bgBounds(bg: BackgroundSpec): Pair<Long, Long> {
    var corners = listOf(bg.top, bg.bottom)
    for (b in bg.blobs) corners = corners.flatMap { listOf(it, over(b.color, b.alpha.toDouble(), it)) }
    fun ch(v: Long, s: Int) = (v shr s) and 0xFF
    fun pick(f: (List<Long>) -> Long) =
        (0xFFL shl 24) or (f(corners.map { ch(it, 16) }) shl 16) or (f(corners.map { ch(it, 8) }) shl 8) or f(corners.map { ch(it, 0) })
    return pick { it.min() } to pick { it.max() }
}

internal data class Surface(val name: String, val argb: Long)

/** Every colour a text of [c] can sit on, with panels opaque ([opaque]) or translucent. */
internal fun surfacesOf(c: AppColors, opaque: Boolean): List<Surface> {
    val s = c.scheme
    val (darkest, lightest) = bgBounds(c.background)
    val out = mutableListOf(Surface("fondo, punto más oscuro", darkest), Surface("fondo, punto más claro", lightest), Surface("fondo sólido", c.background.top))
    listOf(
        "surface" to s.surface, "surfaceVariant" to s.surfaceVariant, "surfaceContainerLowest" to s.surfaceContainerLowest,
        "surfaceContainerLow" to s.surfaceContainerLow, "surfaceContainer" to s.surfaceContainer,
        "surfaceContainerHigh" to s.surfaceContainerHigh, "surfaceContainerHighest" to s.surfaceContainerHighest,
        "surfaceBright" to s.surfaceBright, "surfaceDim" to s.surfaceDim, "secondaryContainer" to s.secondaryContainer,
    ).forEach { (n, v) -> out += Surface(n, argb(v)) }
    for (level in PanelLevel.entries) {
        for (blur in listOf(true, false)) {
            val alpha = c.panelAlpha(level, opaque, blur).toDouble()
            val fill = argb(c.panelColor(level))
            val behind = listOf("fondo oscuro" to darkest, "fondo claro" to lightest) +
                if (level != PanelLevel.CARD && !blur) listOf("blanco detrás" to WHITE, "negro detrás" to BLACK) else emptyList()
            for ((bn, b) in behind) {
                val panel = over(fill, alpha, b)
                out += Surface("$level blur=$blur α=$alpha sobre $bn", panel)
                out += Surface("$level blur=$blur α=$alpha sobre $bn, con el brillo", over(WHITE, c.highlightAlpha.toDouble(), panel))
            }
        }
    }
    return out
}

/** The colours written as text on panels and on the background. */
internal fun textColors(c: AppColors): Map<String, Long> = with(c.scheme) {
    mapOf(
        "onSurface" to onSurface, "onSurfaceVariant" to onSurfaceVariant, "onBackground" to onBackground,
        "primary" to primary, "secondary" to secondary, "tertiary" to tertiary, "error" to error,
        "positive" to c.status.positive, "negative" to c.status.negative, "caution" to c.status.caution,
    ).mapValues { argb(it.value) }
}

/** A text (first) on its own container (second): buttons, chips, banners, the snackbar, the red buttons with the surface colour. */
internal fun containerPairs(c: AppColors): Map<String, Pair<Long, Long>> = with(c.scheme) {
    mapOf(
        "onPrimary/primary" to (onPrimary to primary),
        "onSecondary/secondary" to (onSecondary to secondary),
        "onTertiary/tertiary" to (onTertiary to tertiary),
        "onError/error" to (onError to error),
        "onPrimaryContainer/primaryContainer" to (onPrimaryContainer to primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (onSecondaryContainer to secondaryContainer),
        "onTertiaryContainer/tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
        "onErrorContainer/errorContainer" to (onErrorContainer to errorContainer),
        "inverseOnSurface/inverseSurface" to (inverseOnSurface to inverseSurface),
        "inversePrimary/inverseSurface" to (inversePrimary to inverseSurface),
        "surface/negative" to (surface to c.status.negative),
        "onSurface/surfaceVariant" to (onSurface to surfaceVariant),
    ).mapValues { (_, v) -> argb(v.first) to argb(v.second) }
}

/**
 * What fails in [c]: each text at [text] (4.5:1, or 7:1 in «Alto contraste») on every surface, each container pair at
 * [text], and the elements that are not text (the outline of fields, the accent of switches, checks and the focus) at
 * 3:1 (R-C5, WCAG 1.4.11). Empty when everything reads.
 */
internal fun failures(c: AppColors, opaque: Boolean, text: Double = if (c.highContrast) 7.0 else 4.5): List<String> {
    val bad = mutableListOf<String>()
    val surfaces = surfacesOf(c, opaque)
    for ((name, fg) in textColors(c)) {
        for (s in surfaces) {
            val r = TagPalette.contrast(fg, s.argb)
            if (r < text) bad += "$name ${fg.toString(16)} sobre ${s.name} ${s.argb.toString(16)}: $r"
        }
    }
    for ((name, pair) in containerPairs(c)) {
        val r = TagPalette.contrast(pair.first, pair.second)
        if (r < text) bad += "$name: $r"
    }
    for ((name, fg) in listOf("outline" to argb(c.scheme.outline), "primary" to argb(c.scheme.primary))) {
        for (s in surfaces) {
            val r = TagPalette.contrast(fg, s.argb)
            if (r < 3.0) bad += "$name (no texto) sobre ${s.name}: $r"
        }
    }
    return bad
}

/** The six palettes with a name. */
internal val NAMED_PALETTES = PaletteId.entries.filter { it != PaletteId.WALLPAPER && it != PaletteId.CUSTOM }

/** The 12 × 12 × 3 «Personalizado». */
internal val ALL_CUSTOM: List<CustomPalette> = (0 until CUSTOM_HUES).flatMap { a ->
    (0 until CUSTOM_HUES).flatMap { b -> GlassLevel.entries.map { g -> CustomPalette(a, b, g) } }
}

/** A sweep of what Android could give from a wallpaper: every 15° of accent and of background, in four saturations. */
internal val WALLPAPER_SWEEP: List<WallpaperSeeds> = (0 until 360 step 15).flatMap { a ->
    (0 until 360 step 15).flatMap { b -> listOf(0f, 0.15f, 0.5f, 1f).map { s -> WallpaperSeeds(a.toFloat(), b.toFloat(), s) } }
}
