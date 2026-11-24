package app.seguridad.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.seguridad.settings.ThemeStyle
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * 2.1.1, task 2 (design «Barra de abajo en cápsula», option B; Abogado O-1, O-2, O-4): the bottom bar of every style but
 * «Sólido» is a capsule that floats over the app's background, with the five tabs and a pill behind the chosen one's icon.
 *
 * Own code (R-2) and own measures: 64 dp high, 16 dp from each side and 12 dp above Android's navigation bar, rounded
 * right round, as the design fixes them. They were chosen with the public guide of the floating toolbar of Material 3
 * Expressive as an orientation only (consulted on 24-11-2026; its page of measures could not be read, and no figure here
 * is taken from it). Nothing is measured from pictures of any other app, and no code of Material is copied (R-1; the
 * record is in docs/superpowers/handoff/2026-11-24-clavero-2.1.1-notas.md, «De dónde salen las medidas»).
 *
 * A still panel (O-2): it never shrinks, hides or changes its shape while it is used; its opacity is the palette's
 * ([AppColors.capsuleAlpha]), never worked out per frame; no lens, no refraction, no shader. The pill only fades from one
 * tab to the next, with no motion at all when Android asks to remove animations (R-6).
 */

/** The capsule's height with the names at their usual size (a larger font makes it taller: [capsuleHeight]). */
val CAPSULE_HEIGHT: Dp = 64.dp

/** The space between the capsule and each side of the screen. */
val CAPSULE_SIDE_MARGIN: Dp = 16.dp

/** The space between the capsule and Android's navigation bar (or the bottom of the screen). */
val CAPSULE_BOTTOM_MARGIN: Dp = 12.dp

/**
 * The pill behind the chosen tab's icon: always this size (O-2: it never stretches nor narrows). In a window so narrow
 * (under about 304 dp) that five tabs of [PILL_WIDTH] and [ITEM_SIDE] do not fit, a tab gets less than that and its pill
 * keeps its size, centred, over the edge of the next tab's space (only the chosen one's pill is drawn); never past the
 * capsule's rounded ends (FloatingBarTest.thePillNeverNarrows).
 */
internal val PILL_WIDTH: Dp = 44.dp
internal val PILL_HEIGHT: Dp = 32.dp

/** Above the pill, between the pill and the name, and below the name: 6 + 32 + 2 + 16 + 8 = 64 dp. */
internal val PILL_TOP: Dp = 6.dp
internal val NAME_GAP: Dp = 2.dp
internal val NAME_BOTTOM: Dp = 8.dp

/** The line of a tab's name at the usual font size; Android's font size makes it taller ([capsuleHeight]). */
internal val NAME_LINE: Dp = 16.dp

/** Each tab's own space on either side of its pill and its name. */
internal val ITEM_SIDE: Dp = 2.dp

/**
 * How far every icon, pill and name stays inside the capsule's edge (Task 1 review, m-3): more than half the edge line
 * (the lit edge of «Vidrio» or the border, 1 dp, or 2 dp with «Alto contraste», drawn centred on the outline), so no text
 * ever touches it. «Vidrio»'s shadow is drawn only outside the capsule, in its bottom margin, where nothing is written.
 */
internal val EDGE_CLEARANCE: Dp = 2.dp

/** The size and the line of a tab's name: 12 sp, without extra letter spacing so «Herramientas» fits on a 360 dp phone. */
internal val NAME_STYLE = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium)

/** How long the pill takes to fade from one tab to the next, when Android has animations on. */
internal const val PILL_FADE_MS = 150

/**
 * The capsule's height for a line of names [nameLine] high: [CAPSULE_HEIGHT] at the usual size, taller with a larger
 * font so that the pill and the name always fit inside it (it is fixed for a given font size: it never changes while used).
 */
fun capsuleHeight(nameLine: Dp): Dp = CAPSULE_HEIGHT + (nameLine - NAME_LINE).coerceAtLeast(0.dp)

/**
 * How far the tabs start from each rounded end of a capsule [height] high (rounded right round: a radius of half its
 * height). The corners of what a tab draws (the top of the pill, [PILL_TOP] from the edge, and the bottom of the name,
 * [NAME_BOTTOM] from it) stay at least [EDGE_CLEARANCE] inside the round end, measured straight to the outline; less the
 * tab's own [ITEM_SIDE], and rounded up to a whole dp.
 */
fun capsuleEndPadding(height: Dp): Dp {
    val r = height.value / 2f
    val d = min(PILL_TOP.value, NAME_BOTTOM.value)
    val c = EDGE_CLEARANCE.value
    val x = r - sqrt(((r - c) * (r - c) - (r - d) * (r - d)).coerceAtLeast(0f))
    return ceil(x - ITEM_SIDE.value).coerceAtLeast(0f).dp
}

/** The width the five tabs share on a screen [screenWidth] wide, in a capsule [height] high. */
fun capsuleRowWidth(screenWidth: Dp, height: Dp): Dp =
    (screenWidth - CAPSULE_SIDE_MARGIN * 2 - capsuleEndPadding(height) * 2).coerceAtLeast(0.dp)

/**
 * The width of each tab, from what each needs ([natural]: its name or its pill, whichever is wider, and [ITEM_SIDE] on
 * each side) and the width there is ([available]). When everything fits, each gets what it needs and the rest is shared
 * equally. When it does not (a larger font), the narrow tabs keep what they need and the wide ones share what is left
 * equally (their names end in «…»): no tab ever overlaps another or is squeezed below its fair share. Always adds up to
 * [available].
 */
fun capsuleSlots(natural: List<Float>, available: Float): List<Float> {
    if (natural.isEmpty()) return emptyList()
    val room = available.coerceAtLeast(0f)
    val total = natural.sum()
    if (total <= room) {
        val extra = (room - total) / natural.size
        return natural.map { it + extra }
    }
    var rest = room
    var left = natural.size
    for (w in natural.sorted()) {
        if (w > rest / left) break
        rest -= w
        left--
    }
    val cap = rest / left
    return natural.map { min(it, cap) }
}

/**
 * The fill of the capsule where the app draws it itself: in «Degradado», the gradient of the palette's two background
 * colours from side to side, opaque (its texts read on every point of the background, which is that gradient); in the
 * other styles, [PanelLevel.CAPSULE]'s colour at its opacity (with the blur; [Modifier.glass] draws the glass styles).
 */
fun capsuleFill(look: Look): Brush = if (look.style == ThemeStyle.GRADIENT) {
    Brush.horizontalGradient(listOf(Color(look.colors.background.top), Color(look.colors.background.bottom)))
} else {
    SolidColor(look.colors.panelColor(PanelLevel.CAPSULE).copy(alpha = look.alpha(PanelLevel.CAPSULE, blurNow = true)))
}

/** The pill's fade (R-6): Material's short fade with animations on, and none at all when Android asks to remove them. */
internal fun <T> pillAnimation(animationsOn: Boolean): AnimationSpec<T> = if (animationsOn) tween(PILL_FADE_MS) else snap()

/** A tab's name line at the current font size (Android's font scale, also its non-linear curve). */
@Composable
private fun nameLine(): Dp = with(LocalDensity.current) { NAME_STYLE.lineHeight.toDp() }

/**
 * What the capsule takes from the bottom of the screen: its height, its margin and Android's navigation bar. Task 3
 * leaves this much space under the content, which therefore never passes under the capsule.
 */
@Composable
fun capsuleSpace(): Dp =
    capsuleHeight(nameLine()) + CAPSULE_BOTTOM_MARGIN + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * O-4: no tap on the capsule, its rounded edge or its margins ever reaches what is under it. The strip (capsule and
 * margins) is hit first, so nothing beside it under it is hit at all; and every touch that goes down on it is consumed
 * after the tabs have seen it (the main pass runs from the tab up to here), so no parent takes it for its own either.
 * Only the downs: consuming a move or a lift would cancel a tab's own tap.
 */
internal suspend fun PointerInputScope.swallowTaps() {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            event.changes.forEach { if (it.changedToDownIgnoreConsumed()) it.consume() }
        }
    }
}

/** Where the tabs of [FloatingNavigationBar] go: [FloatingNavigationItem] only makes sense inside it. */
interface CapsuleScope

private object CapsuleScopeInstance : CapsuleScope

/**
 * The floating capsule with the tabs [content] (each a [FloatingNavigationItem]), its margins and Android's navigation
 * bar below. «Cristal», «Vidrio» and «Transparente»: a glass panel of [PanelLevel.CAPSULE] ([Modifier.glass]: on Android
 * 12+ the app's background blurred behind it, never the wallpaper; «Vidrio»'s lit edge and shadow). «Degradado»: opaque,
 * [capsuleFill] with its border. Opaque with «Reducir transparencias» and «Alto contraste» ([Look.alpha]).
 *
 * The tabs are a selectable group, which TalkBack reads as one list of tabs and in which it announces the chosen one.
 */
@Composable
fun FloatingNavigationBar(modifier: Modifier = Modifier, content: @Composable CapsuleScope.() -> Unit) {
    val look = LocalLook.current
    val height = capsuleHeight(nameLine())
    val shape = panelShape(PanelLevel.CAPSULE)
    val surface = when {
        look.translucent -> Modifier.glass(PanelLevel.CAPSULE, shape)
        else -> Modifier
            .background(capsuleFill(look), shape)
            .border(look.colors.borderWidth.dp, look.colors.border, shape)
    }
    Box(
        modifier
            .fillMaxWidth()
            .pointerInput(Unit) { swallowTaps() }
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(start = CAPSULE_SIDE_MARGIN, end = CAPSULE_SIDE_MARGIN, bottom = CAPSULE_BOTTOM_MARGIN),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .then(surface)
                .padding(horizontal = capsuleEndPadding(height)),
        ) {
            Layout(
                content = { CapsuleScopeInstance.content() },
                modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true }.selectableGroup(),
            ) { measurables, constraints ->
                val h = constraints.maxHeight
                val slots = capsuleSlots(measurables.map { it.maxIntrinsicWidth(h).toFloat() }, constraints.maxWidth.toFloat())
                // Whole pixels that still add up to the width: each tab ends where the running sum is rounded.
                var sum = 0f
                val edges = listOf(0) + slots.map { sum += it; sum.roundToInt() }
                val placeables = measurables.mapIndexed { i, m ->
                    m.measure(Constraints.fixed((edges[i + 1] - edges[i]).coerceAtLeast(0), h))
                }
                layout(constraints.maxWidth, h) {
                    placeables.forEachIndexed { i, p -> p.placeRelative(edges[i], 0) }
                }
            }
        }
    }
}

/**
 * A tab of the capsule: its [icon] (a Material icon, no description: the [label] names it) on a pill and its [label]
 * below, on one line ending in «…» if it does not fit. TalkBack reads the label whole even then (the text's semantics
 * keep all of it), with the role of a tab and whether it is the chosen one. The chosen tab's pill is the palette's
 * [primary][androidx.compose.material3.ColorScheme.primary] with its icon in onPrimary, and its name in primary (all
 * measured against the capsule, task 1). The keyboard focus draws a 2 dp ring around the pill in onSurfaceVariant (3:1 or
 * more against the capsule, O-5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapsuleScope.FloatingNavigationItem(selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit, label: String) {
    val scheme = LocalLook.current.colors.scheme
    val animationsOn = LocalAnimationsOn.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pill by animateColorAsState(if (selected) scheme.primary else scheme.primary.copy(alpha = 0f), pillAnimation(animationsOn), label = "pill")
    val iconColour by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, pillAnimation(animationsOn), label = "icon")
    val nameColour by animateColorAsState(if (selected) scheme.primary else scheme.onSurfaceVariant, pillAnimation(animationsOn), label = "name")
    val ring = scheme.onSurfaceVariant
    val pillShape = RoundedCornerShape(percent = 50)
    // Branch review BR-2 (as review M-8, Components.kt): Material's ripple only while Android has animations on; without
    // them, only the still press mark.
    CompositionLocalProvider(LocalRippleConfiguration provides panelRipple(animationsOn)) { Column(
        Modifier
            .fillMaxSize()
            .selectable(selected = selected, onClick = onClick, role = Role.Tab, interactionSource = interaction, indication = null)
            .padding(horizontal = ITEM_SIDE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(PILL_TOP))
        Box(
            Modifier
                .requiredSize(PILL_WIDTH, PILL_HEIGHT)
                .drawBehind {
                    if (focused) {
                        val out = 3.dp.toPx()
                        drawRoundRect(
                            ring,
                            topLeft = Offset(-out, -out),
                            size = Size(size.width + 2 * out, size.height + 2 * out),
                            cornerRadius = CornerRadius(size.height / 2 + out),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                }
                .clip(pillShape)
                .background(pill)
                // On the chosen tab's pill (primary), the ripple and the press mark without animations are onPrimary.
                .indication(interaction, ripple(color = if (selected) scheme.onPrimary else Color.Unspecified))
                .pressSignal(interaction, pillShape, animationsOn, mark = if (selected) scheme.onPrimary else scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides iconColour) { icon() }
        }
        Spacer(Modifier.height(NAME_GAP))
        Text(label, style = NAME_STYLE, color = nameColour, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    } }
}
