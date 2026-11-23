package app.seguridad.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.seguridad.settings.CustomPalette
import app.seguridad.settings.PaletteId
import app.seguridad.settings.ThemeStyle
import kotlin.math.min

/*
 * 2.0, task 5 bis: «Cristal» (design «Aspecto «Cristal», temas y logo»). Only the general technique (O-C3): the app draws
 * its own background (a gradient and three still blobs, no image, no permission), the panels are its surface colour,
 * translucent, with a lighter edge and a highlight at the top; from Android 12 the bars, the dialogs and the sheets draw
 * that background again under themselves, blurred and cut to the panel. No refraction, no lens, no shapes that melt.
 */

/*
 * 2.1, Aspecto: five styles with the same technique. «Sólido»: a plain colour and Material's opaque surfaces.
 * «Degradado»: the gradient of «Cristal» without its blobs, and the opaque surfaces. «Cristal»: as in 2.0. «Vidrio»:
 * «Cristal» with rounder panels, a wider highlight, an edge lit from the top left and a soft shadow outside the panel,
 * all fixed (O-A2: nothing follows a sensor, the finger or the scroll). «Transparente»: the gradient as a layer at
 * [Look.layerAlpha] over the wallpaper the system draws behind the window (task 3), and the panels of «Cristal» on it.
 */

/**
 * The look on screen: the [colors] of the palette, the [style], whether the panels are [opaque] («Sólido», «Degradado»,
 * «Reducir transparencias» or «Alto contraste»), whether the bars and overlays [blur] (Android 12+ and not opaque) and
 * whether the wallpaper is behind the app ([wallpaperBehind], «Transparente» only and not opaque); [wallpaperBlur] when
 * the window blurs that wallpaper (task 3).
 */
@Immutable
class Look(
    val colors: AppColors,
    val style: ThemeStyle,
    val opaque: Boolean,
    val blur: Boolean,
    val wallpaperBehind: Boolean,
    val wallpaperBlur: Boolean = false,
) {
    val dark: Boolean get() = colors.dark
    val highContrast: Boolean get() = colors.highContrast

    /** The app draws its own background: every style but «Sólido», whose screens keep Material's plain colour. */
    val drawsBackground: Boolean get() = style != ThemeStyle.SOLID

    /**
     * «Cristal», «Vidrio» and «Transparente»: the glass panels (opaque with «Reducir transparencias» or «Alto contraste»).
     * «Sólido» and «Degradado» keep Material's opaque surfaces.
     */
    val translucent: Boolean
        get() = style == ThemeStyle.GLASS || style == ThemeStyle.GLASS_STRONG || style == ThemeStyle.TRANSPARENT

    /** «Vidrio»: rounder panels, the wider highlight, the lit edge and the shadow. */
    val strong: Boolean get() = style == ThemeStyle.GLASS_STRONG

    /**
     * How opaque the background is drawn: in «Transparente» over the wallpaper, [AppColors.scrimAlpha] when the window
     * blurs it and a little more when it does not (a sharp image behind); 1 in every other case.
     */
    val layerAlpha: Float
        get() = when {
            !wallpaperBehind -> 1f
            wallpaperBlur -> colors.scrimAlpha
            else -> min(1f, colors.scrimAlpha + 0.06f)
        }

    /** Whether a panel of [level] blurs: only bars and overlays, never a card or a row of a list. */
    fun blurs(level: PanelLevel): Boolean = blur && level != PanelLevel.CARD

    /** The opacity a panel of [level] is drawn with; [blurNow] when it has the blurred background under it. */
    fun alpha(level: PanelLevel, blurNow: Boolean): Float = colors.panelAlpha(level, opaque, blurNow && blurs(level))

    /**
     * The Material colours: when the app draws its background ([drawsBackground]), the screens' own is transparent.
     * Material's contentColorFor(Transparent) finds it as `background` and gives `onBackground`, which is how the
     * transparent Scaffolds, cards and bars keep the right text colour: keep `background` the only transparent role.
     */
    val scheme: ColorScheme = if (drawsBackground) colors.scheme.copy(background = Color.Transparent) else colors.scheme
}

/**
 * The [Look] for these settings on a phone with Android [sdk]. «Alto contraste» turns «Reducir transparencias» on; either
 * makes «Transparente» opaque, without the wallpaper (it is then «Cristal» opaque). [wallpaperBlur]: the window blurs the
 * wallpaper behind «Transparente» (task 3).
 */
fun lookFor(
    style: ThemeStyle,
    palette: PaletteId,
    custom: CustomPalette?,
    reduceTransparency: Boolean,
    highContrast: Boolean,
    dark: Boolean,
    sdk: Int,
    wallpaper: WallpaperSeeds? = null,
    wallpaperBlur: Boolean = false,
): Look {
    val colors = colorsFor(palette, custom, dark, highContrast, wallpaper, style)
    val opaque = style == ThemeStyle.SOLID || style == ThemeStyle.GRADIENT || reduceTransparency || highContrast
    val wallpaperBehind = style == ThemeStyle.TRANSPARENT && !opaque
    return Look(
        colors, style, opaque,
        blur = !opaque && sdk >= Build.VERSION_CODES.S,
        wallpaperBehind = wallpaperBehind,
        wallpaperBlur = wallpaperBehind && wallpaperBlur,
    )
}

/** Whether the window lets the wallpaper show behind the app (task 3 sets the window from this). */
fun showsWallpaper(look: Look): Boolean = look.wallpaperBehind

internal val LocalLook = staticCompositionLocalOf {
    lookFor(ThemeStyle.GLASS, PaletteId.NIGHT, null, reduceTransparency = false, highContrast = false, dark = true, sdk = 26)
}

/** The look on screen. */
val currentLook: Look
    @Composable get() = LocalLook.current

/** Whether the theme on screen is a dark one. */
val isDarkTheme: Boolean
    @Composable get() = LocalLook.current.dark

/** Where the app's background is on the screen, so a panel (also one in a dialog's window) can draw it again under itself. */
@Stable
internal class Backdrop {
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
}

internal val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Stable
private class PanelPlace {
    var at by mutableStateOf<Offset?>(null)
}

/**
 * The background the app draws behind every screen ([drawAppBackground]), which never moves (R-C7). It tells the panels
 * inside where it is ([LocalBackdrop]).
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val look = LocalLook.current
    val backdrop = remember { Backdrop() }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                backdrop.origin = it.positionOnScreen()
                backdrop.size = it.size
            }
            .drawBehind { drawAppBackground(look, size) },
    ) {
        CompositionLocalProvider(LocalBackdrop provides backdrop) { content() }
    }
}

/**
 * The background of [look] on an area of [area]: «Sólido», a plain colour; «Degradado», the gradient without blobs;
 * «Cristal», «Vidrio» and «Transparente» made opaque, the gradient with its three blobs; «Transparente» over the
 * wallpaper, the gradient alone at [Look.layerAlpha] (the wallpaper is under it, and the layer is what was measured).
 */
internal fun DrawScope.drawAppBackground(look: Look, area: Size) {
    val bg = look.colors.background
    if (!look.drawsBackground) {
        drawRect(Color(bg.top), size = area)
        return
    }
    val gradient = Brush.verticalGradient(listOf(Color(bg.top), Color(bg.bottom)), startY = 0f, endY = area.height)
    if (look.wallpaperBehind) {
        drawRect(gradient, size = area, alpha = look.layerAlpha)
        return
    }
    drawRect(gradient, size = area)
    if (look.style == ThemeStyle.GRADIENT) return
    val reach = maxOf(area.width, area.height)
    for (b in bg.blobs) {
        val center = Offset(b.x * area.width, b.y * area.height)
        val radius = b.radius * reach
        val c = Color(b.color)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = b.alpha), c.copy(alpha = 0f)), center, radius), radius, center)
    }
}

/**
 * The brief glow of a pressed panel (design: «Movimiento»): Material's ripple while Android has animations on, and none
 * when it asks to remove them (review M-8; [ValueAnimator.areAnimatorsEnabled] is false with the animator scale at 0).
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun panelRipple(animationsOn: Boolean): RippleConfiguration? = if (animationsOn) RippleConfiguration() else null

/**
 * Whether Android has animations on, for the whole app ([SeguridadTheme] provides it). Re-review N-1: the setting
 * changes only in Android's Settings, so it is read when the app starts and again each time it comes back
 * ([Lifecycle.Event.ON_RESUME]); a recomposition alone never reads the system.
 */
internal val LocalAnimationsOn = staticCompositionLocalOf { true }

/** The system's setting, kept as state and read again on every return to the app ([LocalAnimationsOn]). */
@Composable
internal fun animationsOnSetting(): Boolean {
    var on by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { on = ValueAnimator.areAnimatorsEnabled() }
    return on
}

/**
 * Re-review N-2: with animations removed the ripple goes, so a pressed panel marks itself without any motion: its edge
 * in the accent colour, 2 dp, for as long as it is pressed (the accent has 3:1 or more on every surface, PalettesTest).
 * It is not drawn while animations are on: the ripple is the sign then.
 */
internal fun staticPressMark(pressed: Boolean, animationsOn: Boolean): Boolean = pressed && !animationsOn

/** Draws [staticPressMark] over a pressable panel of [shape] that reports its presses to [interaction]. */
@Composable
internal fun Modifier.pressSignal(interaction: InteractionSource, shape: Shape, animationsOn: Boolean): Modifier {
    if (animationsOn) return this
    val pressed by interaction.collectIsPressedAsState()
    val accent = LocalLook.current.colors.scheme.primary
    return drawWithContent {
        drawContent()
        if (staticPressMark(pressed, animationsOn)) {
            drawOutline(shape.createOutline(size, layoutDirection, this), accent, style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * Whether a panel draws «Vidrio»'s lit edge (white at the top left fading to [AppColors.border]). Never with «Alto
 * contraste»: its edge is the plain [AppColors.border] at its own width (R-C5), in every style. «Reducir transparencias»
 * alone keeps it (the panel is opaque; the edge is still decoration).
 */
internal fun litEdge(look: Look): Boolean = look.strong && !look.highContrast

/**
 * The shape of each kind of panel; «Vidrio» ([strong]) rounds the cards and the overlays more. The bars stay straight,
 * and the rows of the list keep their own RowShape (12 dp) in every style.
 */
fun panelShape(level: PanelLevel, strong: Boolean = false): Shape = when (level) {
    PanelLevel.CARD -> if (strong) StrongCardShape else SectionShape
    PanelLevel.BAR -> RectangleShape
    PanelLevel.OVERLAY -> RoundedCornerShape(if (strong) 32.dp else 28.dp)
}

private val StrongCardShape = RoundedCornerShape(22.dp)

/** A glass panel of [level] around [content] (in «Sólido» and «Degradado», the opaque surface colour). */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, level: PanelLevel = PanelLevel.CARD, content: @Composable BoxScope.() -> Unit) {
    val look = LocalLook.current
    val shape = panelShape(level, look.strong)
    CompositionLocalProvider(LocalContentColor provides look.colors.scheme.onSurface) {
        Box(if (look.translucent) modifier.glass(level, shape) else modifier.background(look.colors.panelColor(level), shape), content = content)
    }
}

/**
 * Draws a glass panel of [level] with [shape] behind what this modifies: its translucent colour, the highlight at the top
 * and the edge (the bars, a thin line above and below). A bar or an overlay on Android 12+ first draws the app's
 * background again, blurred and cut to the panel; until it knows where it is (the first frame), and on Android 8 to 11,
 * it is more opaque instead ([Look.alpha]). Nothing in «Sólido» and «Degradado»: the Material component keeps its own
 * colour. «Vidrio» ([Look.strong]) draws its highlight down to 0.7 of the height, its edge as a fixed gradient from the
 * top left (white) to the bottom right ([AppColors.border]; not with «Alto contraste», [litEdge]), and a soft shadow only
 * outside the panel (not on the bars), so the panel's measured colour is never touched.
 */
@Composable
fun Modifier.glass(level: PanelLevel, shape: Shape): Modifier {
    val look = LocalLook.current
    if (!look.translucent) return this
    val colors = look.colors
    val backdrop = LocalBackdrop.current
    val blurs = look.blurs(level) && backdrop != null
    val place = remember { PanelPlace() }
    val placed = if (blurs) onGloballyPositioned { place.at = it.positionOnScreen() } else this
    return placed.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val at = place.at
        val ready = blurs && at != null && backdrop.size != IntSize.Zero
        val alpha = look.alpha(level, blurNow = ready)
        val layer = if (ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurredBackdrop(this, look, backdrop, at) else null
        val clip = Path().apply { addOutline(outline) }
        val fill = colors.panelColor(level).copy(alpha = alpha)
        val strong = look.strong
        val highlight = Brush.verticalGradient(
            0f to Color.White.copy(alpha = colors.highlightAlpha),
            (if (strong) 0.7f else 0.5f) to Color.White.copy(alpha = 0f), // white fading out (review M-2), as PalettesTest measures it
            startY = 0f,
            endY = size.height,
        )
        val edge = colors.borderWidth.dp.toPx()
        val edgeBrush = if (litEdge(look)) {
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = if (look.dark) 0.30f else 0.90f), colors.border),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
        } else {
            null
        }
        // «Vidrio»: the panel's outline 2 dp lower, in faint black, drawn only where the panel is not (ClipOp.Difference),
        // so the panel's measured colour is never touched. It does cover a strip of 2 dp of the background under the panel:
        // no text may sit closer than that to a panel. Not on the bars, which run edge to edge and have their own lines.
        val shadowDrop = 2.dp.toPx()
        val shadow = Color.Black.copy(alpha = if (look.dark) 0.10f else 0.06f)
        onDrawBehind {
            if (strong && level != PanelLevel.BAR) {
                clipPath(clip, ClipOp.Difference) { translate(0f, shadowDrop) { drawOutline(outline, shadow) } }
            }
            if (layer != null) clipPath(clip) { drawLayer(layer) }
            drawOutline(outline, fill)
            if (colors.highlightAlpha > 0f) drawOutline(outline, highlight)
            if (level == PanelLevel.BAR) {
                drawRect(colors.border, size = Size(size.width, edge))
                drawRect(colors.border, topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge))
            } else if (edgeBrush != null) {
                drawOutline(outline, edgeBrush, style = Stroke(edge))
            } else {
                drawOutline(outline, colors.border, style = Stroke(edge))
            }
        }
    }
}

/**
 * The app's background as it is behind the panel at [at], blurred: a layer of the panel's size. Android 12+ only. In
 * «Transparente» it is the layer alone at [Look.layerAlpha] ([drawAppBackground]), not the wallpaper: what shows through
 * it is the wallpaper already under the app's layer, so it is dimmed twice (this layer and the app's); the panel's
 * opacity over that layer is the one the colours measure.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun blurredBackdrop(scope: CacheDrawScope, look: Look, backdrop: Backdrop, at: Offset): GraphicsLayer {
    val layer = scope.obtainGraphicsLayer()
    val radius = with(scope) { 24.dp.toPx() }
    layer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
    layer.record(scope, scope.layoutDirection, IntSize(scope.size.width.toInt().coerceAtLeast(1), scope.size.height.toInt().coerceAtLeast(1))) {
        translate(backdrop.origin.x - at.x, backdrop.origin.y - at.y) { drawAppBackground(look, backdrop.size.toSize()) }
    }
    return layer
}
