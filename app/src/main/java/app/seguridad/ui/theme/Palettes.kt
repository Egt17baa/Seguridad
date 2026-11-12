package app.seguridad.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.seguridad.settings.CUSTOM_HUES
import app.seguridad.settings.CustomPalette
import app.seguridad.settings.GlassLevel
import app.seguridad.settings.PaletteId
import app.seguridad.settings.ThemeStyle
import app.seguridad.settings.saneCustom
import app.seguridad.vault.TagPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * 2.0, task 5 bis (design «Aspecto «Cristal», temas y logo»): the colours of every palette, worked out from a few tones
 * (a hue and a saturation each) and then corrected:
 * - every surface (the background, the panels, the chips) stays inside the bound of the tag colours ([TagPalette]), so
 *   a tag reads on any of them;
 * - every text colour is moved, lighter in a dark theme and darker in a light one, until it has 4.5:1 (7:1 in «Alto
 *   contraste», R-C6) on every surface it can sit on, and the outline and the accent 3:1 (R-C5);
 * - the opacity of each kind of panel is raised until that holds on the panel blended with the darkest and the lightest
 *   point of the background, and, for a bar, a dialog or a menu that does not blur, over pure white and pure black too
 *   (what scrolls behind it, R-C5).
 * The six palettes are chosen for Clavero (R-C9). «Personalizado» and the colours of the wallpaper go through the same
 * steps (R-C6). The tests measure the result on their own (PalettesTest, GlassContrastTest, TagPaletteThemeTest).
 */

/** The kinds of glass panel: cards and rows (never blurred), bars, and what floats over the screen (dialogs, sheets, menus). */
enum class PanelLevel { CARD, BAR, OVERLAY }

/** A soft spot of colour of the background: [color] at [alpha] in its centre, fading to nothing at [radius] (of the longer side). */
@Immutable
data class Blob(val color: Long, val alpha: Float, val x: Float, val y: Float, val radius: Float)

/** The background the app draws: a gradient from [top] to [bottom] and three still [blobs] (R-C7: they never move). */
@Immutable
data class BackgroundSpec(val top: Long, val bottom: Long, val blobs: List<Blob>)

/** What a palette «Colores del fondo de pantalla» takes from Android's colours (Android 12+): two hues and how vivid. */
@Immutable
data class WallpaperSeeds(val accentHue: Float, val backgroundHue: Float, val saturation: Float)

/**
 * The colours of a theme: the Material [scheme] (its surfaces are the opaque colours of the panels), the [status]
 * colours, the [background] with its blobs and how opaque each kind of panel is ([panelAlpha]). [highlightAlpha] is the
 * white of the highlight at the top of a panel; [border] and [borderWidth] (dp) its edge.
 */
@Immutable
data class AppColors(
    val scheme: ColorScheme,
    val status: StatusColors,
    val background: BackgroundSpec,
    val dark: Boolean,
    val highContrast: Boolean,
    val cardAlpha: Float,
    val barAlpha: Float,
    val overlayAlpha: Float,
    /** The least opacity of a bar, a dialog or a menu that does not blur: every text on it reads over white and over black. */
    val floorAlpha: Float,
    val highlightAlpha: Float,
    val border: Color,
    val borderWidth: Float,
)

/** The colour of a panel of [level], before its opacity. */
fun AppColors.panelColor(level: PanelLevel): Color = when (level) {
    PanelLevel.CARD, PanelLevel.BAR -> scheme.surfaceContainer
    PanelLevel.OVERLAY -> scheme.surfaceContainerHigh
}

/**
 * How opaque a panel of [level] is drawn: 1 with [opaque] panels («Sólido», «Reducir transparencias») or «Alto
 * contraste». A bar or an overlay that blurs ([blur]) has the background drawn again under it; one that does not (Android
 * 8 to 11, or before it knows where the background is) is more opaque, and at least [AppColors.floorAlpha].
 */
fun AppColors.panelAlpha(level: PanelLevel, opaque: Boolean, blur: Boolean): Float {
    if (opaque || highContrast) return 1f
    val blurred = when (level) {
        PanelLevel.CARD -> return cardAlpha
        PanelLevel.BAR -> barAlpha
        PanelLevel.OVERLAY -> overlayAlpha
    }
    return if (blur) blurred else max(min(1f, blurred + UNBLURRED_EXTRA), floorAlpha)
}

/** How much more opaque a bar or an overlay is without the blur (design: «En Android 8 a 11, el mismo panel, más opaco»). */
private const val UNBLURRED_EXTRA = 0.1f

/**
 * The colours of [palette] in the dark or the light theme. [custom] is used with [PaletteId.CUSTOM] (after
 * [fixContrast]) and [wallpaper] with [PaletteId.WALLPAPER]; without them, the palette is «Noche». Never throws.
 */
fun colorsFor(palette: PaletteId, custom: CustomPalette?, dark: Boolean, highContrast: Boolean, wallpaper: WallpaperSeeds? = null): AppColors {
    val spec = when (palette) {
        PaletteId.WALLPAPER -> wallpaper?.let(::wallpaperSpec) ?: NAMED.getValue(PaletteId.NIGHT)
        PaletteId.CUSTOM -> custom?.let { customSpec(fixContrast(it)) } ?: NAMED.getValue(PaletteId.NIGHT)
        else -> NAMED.getValue(palette)
    }
    return cached(spec, dark, highContrast).colors
}

/**
 * «Personalizado» as it is saved and shown: its tones brought into the twelve, and its glass lowered (more opaque) if
 * some text would not reach 4.5:1 with it in the light or the dark theme. The lightness of every colour is corrected by
 * [colorsFor]; this keeps the choice that is saved one that reads, so a combination that does not read is never kept.
 */
fun fixContrast(custom: CustomPalette): CustomPalette {
    var c = saneCustom(custom)
    while (c.glass != GlassLevel.LOW && !(cached(customSpec(c), true, false).met && cached(customSpec(c), false, false).met)) {
        c = c.copy(glass = GlassLevel.entries[c.glass.ordinal - 1])
    }
    return c
}

/** The names in Ajustes → Aspecto. «Colores del fondo de pantalla» names no product (O-C4). */
fun PaletteId.label(): String = when (this) {
    PaletteId.NIGHT -> "Noche"
    PaletteId.OCEAN -> "Océano"
    PaletteId.FOREST -> "Bosque"
    PaletteId.SUNSET -> "Atardecer"
    PaletteId.GRAPHITE -> "Grafito"
    PaletteId.SAND -> "Arena"
    PaletteId.WALLPAPER -> "Colores del fondo de pantalla"
    PaletteId.CUSTOM -> "Personalizado"
}

fun ThemeStyle.label(): String = when (this) {
    ThemeStyle.GLASS -> "Cristal"
    ThemeStyle.SOLID -> "Sólido"
}

fun GlassLevel.label(): String = when (this) {
    GlassLevel.LOW -> "Baja"
    GlassLevel.MEDIUM -> "Media"
    GlassLevel.HIGH -> "Alta"
}

/** The name of each of the twelve tones (0 is red, then every 30°): the picker writes it, the colour is never the only sign. */
fun hueName(i: Int): String = HUE_NAMES[i.mod(CUSTOM_HUES)]

private val HUE_NAMES = listOf("Rojo", "Naranja", "Amarillo", "Lima", "Verde", "Esmeralda", "Cian", "Celeste", "Azul", "Violeta", "Magenta", "Rosa")

/** The colour of tone [i] as the picker shows it (a swatch next to its name). */
fun hueSwatch(i: Int, dark: Boolean): Color = Color(Tone(i.mod(CUSTOM_HUES) * 30.0, 0.75).withLuminance(if (dark) 0.35 else 0.25))

/** The two hues and the saturation Android gives from the wallpaper, from its accent and its secondary container colours. */
fun wallpaperSeeds(accent: Color, background: Color): WallpaperSeeds {
    val a = rgbOf(accent)
    val b = rgbOf(background)
    return WallpaperSeeds(hueOf(a).toFloat(), hueOf(b).toFloat(), satOf(a).toFloat())
}

// ---- The tones of each palette -------------------------------------------------------------------------------------

/** A hue (degrees) and a saturation (0 to 1) of HSL: the lightness is found for each use. */
private data class Tone(val h: Double, val s: Double)

private data class Spec(val accent: Tone, val bg: Tone, val bg2: Tone, val blobs: List<Tone>, val glass: GlassLevel)

private val NAMED: Map<PaletteId, Spec> = mapOf(
    // Añil y oro, the logo's (the default).
    PaletteId.NIGHT to Spec(Tone(38.0, 0.90), Tone(234.0, 0.45), Tone(276.0, 0.45), listOf(Tone(40.0, 0.85), Tone(272.0, 0.60), Tone(228.0, 0.60)), GlassLevel.MEDIUM),
    PaletteId.OCEAN to Spec(Tone(186.0, 0.75), Tone(212.0, 0.55), Tone(196.0, 0.55), listOf(Tone(190.0, 0.70), Tone(220.0, 0.60), Tone(165.0, 0.50)), GlassLevel.MEDIUM),
    PaletteId.FOREST to Spec(Tone(88.0, 0.60), Tone(150.0, 0.35), Tone(170.0, 0.35), listOf(Tone(140.0, 0.50), Tone(95.0, 0.50), Tone(180.0, 0.40)), GlassLevel.MEDIUM),
    PaletteId.SUNSET to Spec(Tone(16.0, 0.85), Tone(328.0, 0.40), Tone(12.0, 0.45), listOf(Tone(22.0, 0.85), Tone(340.0, 0.60), Tone(45.0, 0.80)), GlassLevel.MEDIUM),
    PaletteId.GRAPHITE to Spec(Tone(172.0, 0.65), Tone(215.0, 0.14), Tone(215.0, 0.10), listOf(Tone(210.0, 0.15), Tone(172.0, 0.25), Tone(230.0, 0.12)), GlassLevel.MEDIUM),
    PaletteId.SAND to Spec(Tone(24.0, 0.65), Tone(38.0, 0.30), Tone(28.0, 0.28), listOf(Tone(40.0, 0.50), Tone(20.0, 0.45), Tone(55.0, 0.40)), GlassLevel.MEDIUM),
)

private fun customSpec(c: CustomPalette): Spec {
    val a = c.accentHue * 30.0
    val b = c.backgroundHue * 30.0
    return Spec(Tone(a, 0.75), Tone(b, 0.40), Tone(b + 30, 0.40), listOf(Tone(a, 0.70), Tone(b - 35, 0.55), Tone(b + 35, 0.55)), c.glass)
}

private fun wallpaperSpec(w: WallpaperSeeds): Spec {
    val s = w.saturation.toDouble().coerceIn(0.0, 1.0)
    val a = w.accentHue.toDouble()
    val b = w.backgroundHue.toDouble()
    val accent = Tone(a, 0.25 + 0.6 * s)
    val back = 0.10 + 0.30 * s
    return Spec(accent, Tone(b, back), Tone(b + 25, back), listOf(Tone(a, accent.s), Tone(b - 35, back + 0.15), Tone(b + 35, back + 0.15)), GlassLevel.MEDIUM)
}

/** Where the three blobs are (fractions of the width and the height) and how far they reach (of the longer side). */
private val BLOB_PLACES = listOf(Triple(0.18f, 0.10f, 0.55f), Triple(0.92f, 0.42f, 0.60f), Triple(0.28f, 0.88f, 0.60f))

// ---- Building and correcting --------------------------------------------------------------------------------------

private class Built(val colors: AppColors, val met: Boolean)

/** The last palettes worked out (each takes about a millisecond): a theme, its preview and «Personalizado» ask again often. */
private val cache = object : LinkedHashMap<Triple<Spec, Boolean, Boolean>, Built>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<Spec, Boolean, Boolean>, Built>?) = size > 64
}

private fun cached(spec: Spec, dark: Boolean, highContrast: Boolean): Built {
    val key = Triple(spec, dark, highContrast)
    synchronized(cache) { cache[key] }?.let { return it }
    val made = build(spec, dark, highContrast)
    synchronized(cache) { cache[key] = made }
    return made
}

private const val WHITE = 0xFFFFFFFFL
private const val BLACK = 0xFF000000L

/** The nominal opacity of a card, a bar and an overlay for each intensity of the glass, before the correction. */
private fun nominal(level: PanelLevel, glass: GlassLevel): Double = when (level) {
    PanelLevel.CARD -> when (glass) { GlassLevel.LOW -> 0.78; GlassLevel.MEDIUM -> 0.60; GlassLevel.HIGH -> 0.42 }
    PanelLevel.BAR -> when (glass) { GlassLevel.LOW -> 0.86; GlassLevel.MEDIUM -> 0.76; GlassLevel.HIGH -> 0.66 }
    PanelLevel.OVERLAY -> when (glass) { GlassLevel.LOW -> 0.92; GlassLevel.MEDIUM -> 0.85; GlassLevel.HIGH -> 0.78 }
}

private fun build(spec: Spec, dark: Boolean, hc: Boolean): Built {
    var met = true
    val textRatio = if (hc) 7.0 else 4.5
    // Inside the bound of the tag colours, with a margin; the background is kept further in, for the highlight and the panels.
    val withinTags: (Long) -> Boolean =
        if (dark) { c -> lum(c) <= TagPalette.DARK_MAX_BACKGROUND_LUMINANCE } else { c -> lum(c) >= TagPalette.LIGHT_MIN_BACKGROUND_LUMINANCE }
    fun l(dk: Double, lt: Double) = if (dark) dk else lt

    // The background: the gradient, then the blobs, made fainter until the whole drawing stays inside the bound.
    val top = spec.bg.withLuminance(l(0.010, 0.80))
    val bottom = spec.bg2.withLuminance(l(0.020, 0.75))
    val blobColours = spec.blobs.map { it.withLuminance(l(0.07, 0.70)) }
    var blobAlpha = l(0.75, 0.70)
    fun background(a: Double) = BackgroundSpec(top, bottom, blobColours.mapIndexed { i, c -> BLOB_PLACES[i].let { (x, y, r) -> Blob(c, a.toFloat(), x, y, r) } })
    val bgLimit = l(0.055, 0.71)
    while (blobAlpha > 0.04) {
        val (darkest, lightest) = bounds(background(blobAlpha))
        if (if (dark) lum(lightest) <= bgLimit else lum(darkest) >= bgLimit) break
        blobAlpha -= 0.02
    }
    val bg = background(blobAlpha)
    val (bgDarkest, bgLightest) = bounds(bg)

    // The opaque surfaces of the panels and of what is drawn on them.
    val st = Tone(spec.bg.h, min(spec.bg.s, l(0.30, 0.35)))
    val sLowest = st.withLuminance(l(0.008, 0.97))
    val sDim = st.withLuminance(l(0.010, 0.80))
    val sLow = st.withLuminance(l(0.016, 0.91))
    val sSurface = st.withLuminance(l(0.020, 0.93))
    val sContainer = st.withLuminance(l(0.026, 0.92))
    val sHigh = st.withLuminance(l(0.034, 0.89))
    val sHighest = st.withLuminance(l(0.044, 0.84))
    val sBright = st.withLuminance(l(0.050, 0.95))
    val secondaryContainer = Tone(spec.accent.h, 0.35).withLuminance(l(0.055, 0.76))
    val primaryContainer = Tone(spec.accent.h, 0.60).withLuminance(l(0.050, 0.76))
    val tertiaryContainer = Tone(spec.accent.h + 40, 0.50).withLuminance(l(0.050, 0.76))
    val errorContainer = Tone(0.0, 0.60).withLuminance(l(0.050, 0.76))
    val outlineVariant = st.withLuminance(l(0.060, 0.62)) // dividers only: no text on it
    val highlight = if (hc) 0.0 else l(0.04, 0.35)

    // Every colour a text can sit on: the background's ends, the surfaces, and the panels' colours with the highlight.
    val against = mutableListOf(
        bgDarkest, bgLightest, sLowest, sDim, sLow, sSurface, sContainer, sHigh, sHighest, sBright, secondaryContainer,
    )
    for (c in listOf(sContainer, sHigh, bgDarkest, bgLightest)) against += blend(WHITE, highlight, c)
    for (c in against) if (!withinTags(c)) met = false

    fun text(t: Tone, start: Double, ratio: Double = textRatio, on: List<Long> = against): Long =
        t.reaching(start, lighter = dark, ratio, on).also { if (!meets(it, ratio, on)) met = false }

    val onSurface = text(Tone(spec.bg.h, 0.12), l(0.80, 0.015))
    val onSurfaceVariant = text(Tone(spec.bg.h, 0.12), l(0.36, 0.10))
    val primary = text(spec.accent, l(0.42, 0.10))
    val secondary = text(Tone(spec.bg.h, 0.25), l(0.36, 0.10))
    val tertiary = text(Tone(spec.accent.h + 40, 0.60), l(0.40, 0.10))
    val error = text(Tone(0.0, l(0.85, 0.75)), l(0.30, 0.10))
    val positive = text(Tone(140.0, l(0.60, 0.70)), l(0.36, 0.09))
    val caution = text(Tone(l(44.0, 32.0), 0.90), l(0.45, 0.09))
    val outline = text(Tone(spec.bg.h, 0.12), l(0.16, 0.24), ratio = 3.0)

    // A text on its own container: dark on the accent in a dark theme, light on it in a light one.
    fun onColour(t: Tone, start: Double, under: Long) =
        t.reaching(start, lighter = !dark, textRatio, listOf(under)).also { if (!meets(it, textRatio, listOf(under))) met = false }
    fun onContainer(t: Tone, under: Long) =
        t.reaching(l(0.75, 0.02), lighter = dark, textRatio, listOf(under)).also { if (!meets(it, textRatio, listOf(under))) met = false }

    val onPrimary = onColour(Tone(spec.accent.h, 0.40), l(0.012, 0.97), primary)
    val onSecondary = onColour(Tone(spec.bg.h, 0.30), l(0.012, 0.97), secondary)
    val onTertiary = onColour(Tone(spec.accent.h + 40, 0.40), l(0.012, 0.97), tertiary)
    val onError = onColour(Tone(0.0, 0.40), l(0.012, 0.97), error)
    val onPrimaryContainer = onContainer(Tone(spec.accent.h, 0.50), primaryContainer)
    val onSecondaryContainer = onContainer(Tone(spec.accent.h, 0.25), secondaryContainer)
    val onTertiaryContainer = onContainer(Tone(spec.accent.h + 40, 0.40), tertiaryContainer)
    val onErrorContainer = onContainer(Tone(0.0, 0.70), errorContainer)
    val inverseSurface = Tone(spec.bg.h, 0.12).withLuminance(l(0.85, 0.02))
    val inverseOnSurface = Tone(spec.bg.h, 0.20).reaching(l(0.02, 0.88), lighter = !dark, textRatio, listOf(inverseSurface))
    val inversePrimary = spec.accent.reaching(l(0.12, 0.40), lighter = !dark, textRatio, listOf(inverseSurface))
    if (!meets(inverseOnSurface, textRatio, listOf(inverseSurface)) || !meets(inversePrimary, textRatio, listOf(inverseSurface))) met = false

    // How opaque each kind of panel must be for all of that to hold on it.
    val texts = listOf(onSurface, onSurfaceVariant, primary, secondary, tertiary, error, positive, caution)
    fun reads(c: Long) = withinTags(c) && meets(texts, textRatio, c) && contrast(outline, c) >= 3.0 && contrast(primary, c) >= 3.0
    // In hundredths, checked with the very Float that is drawn (a Double next to it can round a channel the other way).
    fun opacity(from: Double, fill: Long, behind: List<Long>): Float {
        var hundredths = (from * 100).roundToLong().toInt()
        fun ok(a: Float) = behind.all { b -> blend(fill, a.toDouble(), b).let { p -> reads(p) && reads(blend(WHITE, highlight, p)) } }
        while (hundredths < 100 && !ok(hundredths / 100f)) hundredths++
        return min(100, hundredths) / 100f
    }
    val ends = listOf(bgDarkest, bgLightest)
    val anything = ends + listOf(WHITE, BLACK)
    val card = if (hc) 1f else opacity(nominal(PanelLevel.CARD, spec.glass), sContainer, ends)
    val bar = if (hc) 1f else opacity(nominal(PanelLevel.BAR, spec.glass), sContainer, ends)
    val overlay = if (hc) 1f else opacity(nominal(PanelLevel.OVERLAY, spec.glass), sHigh, ends)
    val floor = if (hc) 1f else max(opacity(0.5, sContainer, anything), opacity(0.5, sHigh, anything))
    if (!reads(sContainer) || !reads(sHigh)) met = false

    val c = { v: Long -> Color(v) }
    val scheme = if (dark) {
        darkColorScheme(
            primary = c(primary), onPrimary = c(onPrimary), primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
            inversePrimary = c(inversePrimary), secondary = c(secondary), onSecondary = c(onSecondary),
            secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer), tertiary = c(tertiary),
            onTertiary = c(onTertiary), tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
            background = c(top), onBackground = c(onSurface), surface = c(sSurface), onSurface = c(onSurface),
            surfaceVariant = c(sHighest), onSurfaceVariant = c(onSurfaceVariant), surfaceTint = c(primary),
            inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface), error = c(error), onError = c(onError),
            errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer), outline = c(outline),
            outlineVariant = c(outlineVariant), scrim = Color.Black, surfaceBright = c(sBright), surfaceContainer = c(sContainer),
            surfaceContainerHigh = c(sHigh), surfaceContainerHighest = c(sHighest), surfaceContainerLow = c(sLow),
            surfaceContainerLowest = c(sLowest), surfaceDim = c(sDim),
        )
    } else {
        lightColorScheme(
            primary = c(primary), onPrimary = c(onPrimary), primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
            inversePrimary = c(inversePrimary), secondary = c(secondary), onSecondary = c(onSecondary),
            secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer), tertiary = c(tertiary),
            onTertiary = c(onTertiary), tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
            background = c(top), onBackground = c(onSurface), surface = c(sSurface), onSurface = c(onSurface),
            surfaceVariant = c(sHighest), onSurfaceVariant = c(onSurfaceVariant), surfaceTint = c(primary),
            inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface), error = c(error), onError = c(onError),
            errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer), outline = c(outline),
            outlineVariant = c(outlineVariant), scrim = Color.Black, surfaceBright = c(sBright), surfaceContainer = c(sContainer),
            surfaceContainerHigh = c(sHigh), surfaceContainerHighest = c(sHighest), surfaceContainerLow = c(sLow),
            surfaceContainerLowest = c(sLowest), surfaceDim = c(sDim),
        )
    }
    val colors = AppColors(
        scheme = scheme,
        status = StatusColors(positive = c(positive), negative = c(error), caution = c(caution)),
        background = bg,
        dark = dark,
        highContrast = hc,
        cardAlpha = card,
        barAlpha = bar,
        overlayAlpha = overlay,
        floorAlpha = floor,
        highlightAlpha = highlight.toFloat(),
        border = if (hc) c(onSurfaceVariant) else Color.White.copy(alpha = if (dark) 0.14f else 0.75f),
        borderWidth = if (hc) 2f else 1f,
    )
    return Built(colors, met)
}

// ---- Colour arithmetic (sRGB, WCAG 2.2 luminance as in TagPalette) --------------------------------------------------

private fun lum(c: Long) = TagPalette.luminance(c)

private fun contrast(a: Long, b: Long) = TagPalette.contrast(a, b)

private fun meets(fg: Long, ratio: Double, on: List<Long>) = on.all { contrast(fg, it) >= ratio }

private fun meets(fgs: List<Long>, ratio: Double, on: Long) = fgs.all { contrast(it, on) >= ratio }

private fun ch(v: Long, shift: Int): Long = (v shr shift) and 0xFF

/** [top] at [alpha] over the opaque [under], channel by channel in sRGB, as Android blends a translucent fill. */
private fun blend(top: Long, alpha: Double, under: Long): Long {
    fun mix(s: Int) = (alpha * ch(top, s) + (1 - alpha) * ch(under, s)).roundToLong().coerceIn(0, 255)
    return (0xFFL shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

/**
 * The darkest and the lightest the background can be, channel by channel: each point is the gradient (between its two
 * ends) with each blob over it at an opacity from 0 to its peak, so its channels lie between those of these corners.
 */
private fun bounds(bg: BackgroundSpec): Pair<Long, Long> {
    var corners = listOf(bg.top, bg.bottom)
    for (b in bg.blobs) corners = corners.flatMap { listOf(it, blend(b.color, b.alpha.toDouble(), it)) }
    fun pick(f: (List<Long>) -> Long) =
        (0xFFL shl 24) or (f(corners.map { ch(it, 16) }) shl 16) or (f(corners.map { ch(it, 8) }) shl 8) or f(corners.map { ch(it, 0) })
    return pick { it.min() } to pick { it.max() }
}

private fun hsl(h: Double, s: Double, l: Double): Long {
    val c = (1 - abs(2 * l - 1)) * s
    val hp = (((h % 360) + 360) % 360) / 60
    val x = c * (1 - abs(hp % 2 - 1))
    val (r1, g1, b1) = when (hp.toInt()) {
        0 -> Triple(c, x, 0.0)
        1 -> Triple(x, c, 0.0)
        2 -> Triple(0.0, c, x)
        3 -> Triple(0.0, x, c)
        4 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    val m = l - c / 2
    fun q(v: Double) = ((v + m) * 255).roundToLong().coerceIn(0, 255)
    return (0xFFL shl 24) or (q(r1) shl 16) or (q(g1) shl 8) or q(b1)
}

/** The colour of this tone whose luminance is the least at or above [target] (luminance only rises with HSL lightness). */
private fun Tone.withLuminance(target: Double): Long = hsl(h, s, lightnessFor(target))

private fun Tone.lightnessFor(target: Double): Double {
    var lo = 0.0
    var hi = 1.0
    repeat(26) {
        val mid = (lo + hi) / 2
        if (lum(hsl(h, s, mid)) < target) lo = mid else hi = mid
    }
    return hi
}

/**
 * This tone from the lightness that gives [start], moved [lighter] (or darker) only as far as needed to have [ratio] on
 * every colour of [on]; white or black if even that does not reach it (the caller then knows it was not met).
 */
private fun Tone.reaching(start: Double, lighter: Boolean, ratio: Double, on: List<Long>): Long {
    val from = lightnessFor(start)
    if (meets(hsl(h, s, from), ratio, on)) return hsl(h, s, from)
    var bad = from
    var good = if (lighter) 1.0 else 0.0
    if (!meets(hsl(h, s, good), ratio, on)) return hsl(h, s, good)
    repeat(26) {
        val mid = (bad + good) / 2
        if (meets(hsl(h, s, mid), ratio, on)) good = mid else bad = mid
    }
    return hsl(h, s, good)
}

private fun rgbOf(c: Color): Long {
    fun q(v: Float) = (v * 255).toLong().coerceIn(0, 255)
    return (0xFFL shl 24) or (q(c.red) shl 16) or (q(c.green) shl 8) or q(c.blue)
}

private fun hueOf(v: Long): Double {
    val r = ch(v, 16) / 255.0
    val g = ch(v, 8) / 255.0
    val b = ch(v, 0) / 255.0
    val mx = maxOf(r, g, b)
    val mn = minOf(r, g, b)
    val d = mx - mn
    if (d == 0.0) return 0.0
    val h = when (mx) {
        r -> ((g - b) / d).mod(6.0)
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    }
    return h * 60
}

private fun satOf(v: Long): Double {
    val r = ch(v, 16) / 255.0
    val g = ch(v, 8) / 255.0
    val b = ch(v, 0) / 255.0
    val mx = maxOf(r, g, b)
    val mn = minOf(r, g, b)
    val l = (mx + mn) / 2
    val d = mx - mn
    return if (d == 0.0) 0.0 else d / (1 - abs(2 * l - 1))
}
