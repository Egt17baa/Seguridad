package app.seguridad.ui.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.seguridad.settings.PaletteId
import app.seguridad.settings.ThemeStyle
import app.seguridad.vault.TagPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * 2.1.1, task 2: the floating capsule of the bottom bar (FloatingBar.kt).
 *
 * The app has no Compose UI test library nor Robolectric, and the plan adds no dependency (Global Constraints), so what a
 * UI test would watch on the screen is pinned here as the pure functions the capsule is drawn with (its measures, the
 * width of each tab, its fill, the pill's fade) and, for the taps and TalkBack, as what the source has to say. The phone
 * checks of «Pruebas de la 2.1.1» (task 4) look at the same on the screen.
 */
class FloatingBarTest {
    private val source: String by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "app/src/main/java/app/seguridad").isDirectory) dir = dir.parentFile
        File(dir, "app/src/main/java/app/seguridad/ui/theme/FloatingBar.kt").readText()
    }

    /** The body of the function [name] in FloatingBar.kt, without its comments. */
    private fun body(name: String): String = source.substringAfter(name).substringBefore("\n}\n")
        .lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }.joinToString("\n")

    private fun look(style: ThemeStyle, palette: PaletteId = PaletteId.NIGHT, dark: Boolean = true, high: Boolean = false, reduce: Boolean = false) =
        lookFor(style, palette, null, reduceTransparency = reduce, highContrast = high, dark = dark, sdk = 35)

    @Test fun capsuleHasTheFloatingToolbarMeasures() {
        assertEquals(64.dp, CAPSULE_HEIGHT)
        assertEquals(16.dp, CAPSULE_SIDE_MARGIN)
        assertEquals(12.dp, CAPSULE_BOTTOM_MARGIN)
        // At the usual font size the pill and the name fill the 64 dp exactly; a larger font makes it taller, never smaller.
        assertEquals(CAPSULE_HEIGHT, PILL_TOP + PILL_HEIGHT + NAME_GAP + NAME_LINE + NAME_BOTTOM)
        assertEquals(64.dp, capsuleHeight(NAME_LINE))
        assertEquals(64.dp, capsuleHeight(12.dp))
        assertEquals(80.dp, capsuleHeight(32.dp))
        // Rounded right round (task 1 review, m-3): the corner radius is half the height, in «Vidrio» too.
        for (strong in listOf(false, true)) {
            val shape = panelShape(PanelLevel.CAPSULE, strong)
            assertEquals(RoundedCornerShape(percent = 50), shape)
            for (h in listOf(64f, 80f)) {
                val outline = shape.createOutline(Size(328f, h), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
                assertEquals(h / 2, outline.roundRect.topLeftCornerRadius.x, 0.001f)
                assertEquals(h / 2, outline.roundRect.bottomRightCornerRadius.y, 0.001f)
            }
        }
        // capsuleSpace is the capsule, its margin and the navigation bar.
        val space = body("fun capsuleSpace()")
        assertTrue(space, "capsuleHeight(nameLine()) + CAPSULE_BOTTOM_MARGIN + WindowInsets.navigationBars" in space)
    }

    /**
     * Task 1 review, m-3: no icon, pill or name comes nearer the capsule's edge than [EDGE_CLEARANCE], which is more than
     * the inner half of its edge line (1 dp, 2 dp with «Alto contraste»), at the rounded ends too, with any font size.
     */
    @Test fun nothingIsWrittenOnTheEdge() {
        val widest = listOf(true, false).flatMap { dark ->
            listOf(false, true).flatMap { high -> ThemeStyle.entries.map { look(it, dark = dark, high = high).colors.borderWidth } }
        }.max()
        assertTrue("$widest", EDGE_CLEARANCE.value > widest / 2)
        for (line in 16..40) {
            val h = capsuleHeight(line.dp).value
            val r = h / 2
            val x = (capsuleEndPadding(h.dp) + ITEM_SIDE).value // where a tab's pill or name can start, at the left end
            for (y in listOf(PILL_TOP.value, h - NAME_BOTTOM.value)) {
                val inside = r - hypot(r - x, r - y) // how far that corner of the content is inside the rounded end
                assertTrue("line=$line h=$h y=$y: $inside", inside >= EDGE_CLEARANCE.value)
            }
        }
        // The pill's focus ring (3 dp out, 2 dp wide) stays inside too: it is a stadium round the pill, at the pill's centre.
        assertEquals(16.dp, capsuleEndPadding(64.dp))
    }

    /**
     * O-4 and Review Focus 2: the whole strip (the capsule, its rounded edge, its margins) takes every touch that goes down
     * on it, after the tabs have seen it, and does not share it with what is under it.
     */
    @Test fun aTapOnTheCapsuleNeverReachesWhatIsUnder() {
        val bar = body("fun FloatingNavigationBar(")
        val swallow = bar.indexOf(".pointerInput(Unit) { swallowTaps() }")
        assertTrue(bar, swallow > 0)
        // Before the insets and the margins: the margins are part of what it covers.
        assertTrue(swallow < bar.indexOf(".windowInsetsPadding("))
        assertTrue(swallow < bar.indexOf(".padding(start = CAPSULE_SIDE_MARGIN"))
        assertTrue(swallow < bar.indexOf(".then(surface)"))
        val taps = body("suspend fun PointerInputScope.swallowTaps()")
        assertTrue(taps, "awaitPointerEvent(PointerEventPass.Main)" in taps)
        assertTrue(taps, "changedToDownIgnoreConsumed()) it.consume()" in taps)
        assertFalse("sharePointerInputWithSiblings" in source)
    }

    /** O-4: TalkBack reads the tabs as one group of tabs, announces the chosen one and reads a cut name whole. */
    @Test fun selectedTabIsAnnouncedAndLongLabelIsReadWhole() {
        assertTrue(".selectableGroup()" in body("fun FloatingNavigationBar("))
        val item = body("fun CapsuleScope.FloatingNavigationItem(")
        assertTrue(item, ".selectable(selected = selected, onClick = onClick, role = Role.Tab" in item)
        assertTrue(item, "Text(label, style = NAME_STYLE, color = nameColour, maxLines = 1, overflow = TextOverflow.Ellipsis" in item)
        // Nothing replaces what the text says (a cut text keeps all of it in its semantics).
        for (bad in listOf("clearAndSetSemantics", "contentDescription", "invisibleToUser", "hideFromAccessibility")) {
            assertFalse(bad, bad in item)
        }
        // With the font twice as big (Android's largest), the five tabs still share the width and none overlaps: the
        // narrow ones keep their width, the wide ones get an equal share, and it adds up to the width there is.
        val h = capsuleHeight(NAME_LINE * 2)
        val available = capsuleRowWidth(360.dp, h).value
        val natural = naturalWidths(scale = 2f)
        val slots = capsuleSlots(natural, available)
        assertEquals(available, slots.sum(), 0.01f)
        for (i in slots.indices) assertTrue("$i ${slots[i]}", slots[i] >= minOf(natural[i], available / slots.size) - 0.01f)
    }

    /**
     * The widths of the five names, in ems: measured with Liberation Sans Bold (the metrics of Arial Bold), wider than
     * Roboto Medium, Android's usual font for a 12 sp label, so what fits with them fits with Android's own font.
     */
    private val namesInEms = mapOf("Bóveda" to 3.612f, "Códigos" to 4.0f, "Contactos" to 4.889f, "Herramientas" to 6.392f, "Ajustes" to 3.612f)

    private fun naturalWidths(scale: Float): List<Float> = namesInEms.values.map { em ->
        maxOf(em * 12f * scale, PILL_WIDTH.value) + 2 * ITEM_SIDE.value
    }

    @Test fun herramientasFitsAt360dp() {
        val available = capsuleRowWidth(360.dp, CAPSULE_HEIGHT).value
        assertEquals(360f - 32f - 32f, available, 0.001f)
        val natural = naturalWidths(scale = 1f)
        val slots = capsuleSlots(natural, available)
        for ((i, name) in namesInEms.keys.withIndex()) assertTrue("$name: ${slots[i]} < ${natural[i]}", slots[i] >= natural[i])
        assertEquals(available, slots.sum(), 0.01f)
    }

    @Test fun slotsShareTheWidthFairly() {
        assertEquals(emptyList<Float>(), capsuleSlots(emptyList(), 100f))
        // Everything fits: each keeps its width and the rest is shared.
        assertEquals(listOf(30f, 50f), capsuleSlots(listOf(20f, 40f), 80f))
        // It does not: the narrow ones keep theirs, the wide ones share what is left equally.
        assertEquals(listOf(20f, 40f, 40f), capsuleSlots(listOf(20f, 100f, 60f), 100f))
        assertEquals(listOf(25f, 25f, 25f, 25f), capsuleSlots(listOf(30f, 30f, 30f, 30f), 100f))
        assertEquals(listOf(0f, 0f), capsuleSlots(listOf(10f, 10f), -5f))
    }

    @Test fun gradientCapsuleIsOpaqueWithItsGradient() {
        for (p in NAMED_PALETTES) for (dark in listOf(true, false)) for (high in listOf(false, true)) {
            val l = look(ThemeStyle.GRADIENT, p, dark, high)
            val bg = l.colors.background
            assertEquals("$p", Brush.horizontalGradient(listOf(Color(bg.top), Color(bg.bottom))), capsuleFill(l))
            assertEquals(0xFFL, (bg.top shr 24) and 0xFF)
            assertEquals(0xFFL, (bg.bottom shr 24) and 0xFF)
            // Its names and icons, the chosen name and the pill read on every point of that gradient.
            val text = if (high) 7.0 else 4.5
            val (darkest, lightest) = bgBounds(BackgroundSpec(bg.top, bg.bottom, emptyList()))
            val s = l.colors.scheme
            for (under in listOf(bg.top, bg.bottom, darkest, lightest)) {
                for ((n, fg) in listOf("onSurfaceVariant" to s.onSurfaceVariant, "primary" to s.primary)) {
                    val r = TagPalette.contrast(argb(fg), under)
                    assertTrue("$p dark=$dark alto=$high $n: $r", r >= text)
                }
            }
            assertTrue(TagPalette.contrast(argb(s.onPrimary), argb(s.primary)) >= text)
        }
        // The other styles: the capsule's colour at its opacity (Modifier.glass draws the glass ones).
        val glass = look(ThemeStyle.GLASS)
        assertEquals(SolidColor(glass.colors.panelColor(PanelLevel.CAPSULE).copy(alpha = glass.colors.capsuleAlpha)), capsuleFill(glass))
        val opaque = look(ThemeStyle.GLASS, reduce = true)
        assertEquals(1f, (capsuleFill(opaque) as SolidColor).value.alpha)
        // In the source: the glass styles use Modifier.glass with the capsule's level and shape; «Degradado» its fill and border.
        val bar = body("fun FloatingNavigationBar(")
        assertTrue(bar, "look.translucent -> Modifier.glass(PanelLevel.CAPSULE, shape)" in bar)
        assertTrue(bar, ".background(capsuleFill(look), shape)" in bar)
        assertTrue(bar, ".border(look.colors.borderWidth.dp, look.colors.border, shape)" in bar)
    }

    /** R-6: the pill fades with animations on and changes at once when Android asks to remove them. */
    @Test fun noAnimationWhenAndroidAsksForNone() {
        assertTrue(pillAnimation<Color>(false) is SnapSpec)
        val on = pillAnimation<Color>(true)
        assertTrue(on is TweenSpec)
        assertEquals(PILL_FADE_MS, (on as TweenSpec).durationMillis)
        val item = body("fun CapsuleScope.FloatingNavigationItem(")
        assertTrue(item, "val animationsOn = LocalAnimationsOn.current" in item)
        assertEquals(3, Regex("""pillAnimation\(animationsOn\)""").findAll(item).count())
        // O-2: nothing else moves: no other animation, no shader, nothing that follows the scroll.
        for (bad in listOf("animateDpAsState", "animateFloatAsState", "AnimatedVisibility", "RuntimeShader", "nestedScroll", "graphicsLayer")) {
            assertFalse(bad, bad in source)
        }
    }
}
