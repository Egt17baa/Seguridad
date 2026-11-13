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

/*
 * 2.0, task 5 bis: «Cristal» (design «Aspecto «Cristal», temas y logo»). Only the general technique (O-C3): the app draws
 * its own background (a gradient and three still blobs, no image, no permission), the panels are its surface colour,
 * translucent, with a lighter edge and a highlight at the top; from Android 12 the bars, the dialogs and the sheets draw
 * that background again under themselves, blurred and cut to the panel. No refraction, no lens, no shapes that melt.
 */

/**
 * The look on screen: the [colors] of the palette, the [style], whether the panels are [opaque] («Sólido», «Reducir
 * transparencias» or «Alto contraste») and whether the bars and overlays [blur] (Android 12+ and not opaque).
 */
@Immutable
class Look(val colors: AppColors, val style: ThemeStyle, val opaque: Boolean, val blur: Boolean) {
    val dark: Boolean get() = colors.dark
    val highContrast: Boolean get() = colors.highContrast

    /** «Cristal»: the background with blobs and the glass panels. «Sólido» keeps Material's opaque surfaces. */
    val glass: Boolean get() = style == ThemeStyle.GLASS

    /** Whether a panel of [level] blurs: only bars and overlays, never a card or a row of a list. */
    fun blurs(level: PanelLevel): Boolean = blur && level != PanelLevel.CARD

    /** The opacity a panel of [level] is drawn with; [blurNow] when it has the blurred background under it. */
    fun alpha(level: PanelLevel, blurNow: Boolean): Float = colors.panelAlpha(level, opaque, blurNow && blurs(level))

    /**
     * The Material colours: in «Cristal» the background is the app's drawing, so the screens' own is transparent.
     * Material's contentColorFor(Transparent) finds it as `background` and gives `onBackground`, which is how the
     * transparent Scaffolds, cards and bars keep the right text colour: keep `background` the only transparent role.
     */
    val scheme: ColorScheme = if (glass) colors.scheme.copy(background = Color.Transparent) else colors.scheme
}

/** The [Look] for these settings on a phone with Android [sdk]. «Alto contraste» turns «Reducir transparencias» on. */
fun lookFor(
    style: ThemeStyle,
    palette: PaletteId,
    custom: CustomPalette?,
    reduceTransparency: Boolean,
    highContrast: Boolean,
    dark: Boolean,
    sdk: Int,
    wallpaper: WallpaperSeeds? = null,
): Look {
    val colors = colorsFor(palette, custom, dark, highContrast, wallpaper)
    val opaque = style == ThemeStyle.SOLID || reduceTransparency || highContrast
    return Look(colors, style, opaque, blur = !opaque && sdk >= Build.VERSION_CODES.S)
}

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
 * The background the app draws behind every screen: in «Cristal» the gradient with its three blobs, which never move
 * (R-C7); in «Sólido» a plain colour. It tells the panels inside where it is ([LocalBackdrop]).
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

/** The background of [look] on an area of [area]. */
internal fun DrawScope.drawAppBackground(look: Look, area: Size) {
    val bg = look.colors.background
    if (!look.glass) {
        drawRect(Color(bg.top), size = area)
        return
    }
    drawRect(Brush.verticalGradient(listOf(Color(bg.top), Color(bg.bottom)), startY = 0f, endY = area.height), size = area)
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

/** The shape of each kind of panel. */
fun panelShape(level: PanelLevel): Shape = when (level) {
    PanelLevel.CARD -> SectionShape
    PanelLevel.BAR -> RectangleShape
    PanelLevel.OVERLAY -> RoundedCornerShape(28.dp)
}

/** A glass panel of [level] around [content] (in «Sólido», the opaque surface colour). */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, level: PanelLevel = PanelLevel.CARD, content: @Composable BoxScope.() -> Unit) {
    val look = LocalLook.current
    val shape = panelShape(level)
    CompositionLocalProvider(LocalContentColor provides look.colors.scheme.onSurface) {
        Box(if (look.glass) modifier.glass(level, shape) else modifier.background(look.colors.panelColor(level), shape), content = content)
    }
}

/**
 * Draws a glass panel of [level] with [shape] behind what this modifies: its translucent colour, the highlight at the top
 * and the edge (the bars, a thin line above and below). A bar or an overlay on Android 12+ first draws the app's
 * background again, blurred and cut to the panel; until it knows where it is (the first frame), and on Android 8 to 11,
 * it is more opaque instead ([Look.alpha]). Nothing in «Sólido»: the Material component keeps its own colour.
 */
@Composable
fun Modifier.glass(level: PanelLevel, shape: Shape): Modifier {
    val look = LocalLook.current
    if (!look.glass) return this
    val colors = look.colors
    val backdrop = LocalBackdrop.current
    val blurs = look.blurs(level) && backdrop != null
    val place = remember { PanelPlace() }
    val placed = if (blurs) onGloballyPositioned { place.at = it.positionOnScreen() } else this
    return placed.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val at = place.at
        val ready = blurs && at != null && backdrop!!.size != IntSize.Zero
        val alpha = look.alpha(level, blurNow = ready)
        val layer = if (ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurredBackdrop(this, look, backdrop!!, at!!) else null
        val clip = Path().apply { addOutline(outline) }
        val fill = colors.panelColor(level).copy(alpha = alpha)
        val highlight = Brush.verticalGradient(
            0f to Color.White.copy(alpha = colors.highlightAlpha),
            0.5f to Color.White.copy(alpha = 0f), // white fading out (review M-2), as PalettesTest measures it
            startY = 0f,
            endY = size.height,
        )
        val edge = colors.borderWidth.dp.toPx()
        onDrawBehind {
            if (layer != null) clipPath(clip) { drawLayer(layer) }
            drawOutline(outline, fill)
            if (colors.highlightAlpha > 0f) drawOutline(outline, highlight)
            if (level == PanelLevel.BAR) {
                drawRect(colors.border, size = Size(size.width, edge))
                drawRect(colors.border, topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge))
            } else {
                drawOutline(outline, colors.border, style = Stroke(edge))
            }
        }
    }
}

/** The app's background as it is behind the panel at [at], blurred: a layer of the panel's size. Android 12+ only. */
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
