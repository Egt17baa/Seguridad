package app.seguridad.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.seguridad.ui.theme.CAPSULE_BOTTOM_MARGIN
import app.seguridad.ui.theme.capsuleHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.1.1, task 3 (design, option B): the content never passes under the capsule. The app has no Compose UI test library
 * nor Robolectric (ruling D-1, dependencies frozen), so the space under the content is pinned here as the pure function
 * the NavHost gets its padding from ([navHostPadding]) and the capsule's place on the screen as FloatingNavigationBar
 * lays it out (Android's navigation bar, [CAPSULE_BOTTOM_MARGIN] and [capsuleHeight], FloatingBarTest); where each is
 * used, as what AppNavHost.kt has to say. The phone checks of «Pruebas de la 2.1.1» (task 4) look at the same.
 */
class BottomBarSpaceTest {
    private val screenHeight = 640.dp
    private val rowHeight = 56.dp

    /** The status bar on top, Android's navigation bar [nav] at the bottom: what the Scaffold gives without a bottom bar. */
    private fun scaffoldPadding(nav: Dp, materialBar: Boolean) =
        PaddingValues(top = 24.dp, bottom = if (materialBar) 80.dp + nav else nav)

    /** A tab's name line at Android's font size [fontScale] (linear here: the phone's curve gives less, never more). */
    private fun nameLine(fontScale: Float): Dp = with(Density(1f, fontScale)) { app.seguridad.ui.theme.NAME_STYLE.lineHeight.toDp() }

    /** What capsuleSpace() gives (FloatingBarTest pins its body): the capsule, its margin and the navigation bar. */
    private fun capsuleSpace(fontScale: Float, nav: Dp) = capsuleHeight(nameLine(fontScale)) + CAPSULE_BOTTOM_MARGIN + nav

    /** Where the capsule's top is on the screen: FloatingNavigationBar sits on the navigation bar, its margin and its height. */
    private fun capsuleTop(fontScale: Float, nav: Dp) = screenHeight - nav - CAPSULE_BOTTOM_MARGIN - capsuleHeight(nameLine(fontScale))

    /** The bottom of the last of [rows] rows of a list in the NavHost scrolled to its end (the list fills the NavHost). */
    private fun lastRowBottom(padding: PaddingValues, rows: Int = 50): Dp {
        val viewportTop = padding.calculateTopPadding()
        val viewportBottom = screenHeight - padding.calculateBottomPadding()
        val content = rowHeight * rows
        val scrolled = (content - (viewportBottom - viewportTop)).coerceAtLeast(0.dp)
        return viewportTop + content - scrolled
    }

    private fun padding(floating: Boolean, fontScale: Float, nav: Dp) =
        navHostPadding(scaffoldPadding(nav, materialBar = !floating), floating, capsuleSpace(fontScale, nav), LayoutDirection.Ltr)

    /** Review Focus 1: at the end of a list, its last row is whole above the capsule, with a large font and any navigation bar. */
    @Test fun lastItemEndsAboveTheCapsule() {
        for (fontScale in listOf(1f, 1.3f, 2f)) {
            for (nav in listOf(0.dp, 24.dp, 48.dp)) {
                val last = lastRowBottom(padding(floating = true, fontScale, nav))
                // Within a hundredth of a dp: the float sums of dp (the phone rounds both to the same whole pixels).
                assertTrue("fontScale=$fontScale nav=$nav: $last > ${capsuleTop(fontScale, nav)}", last.value <= capsuleTop(fontScale, nav).value + 0.01f)
                // And it ends right there: no gap of more (the list uses the room down to the capsule).
                assertEquals(capsuleTop(fontScale, nav).value, last.value, 0.01f)
            }
        }
    }

    /**
     * Review Focus 3: from «Sólido» to «Cristal» and back with the list at its end. «Sólido» keeps the Scaffold's padding
     * of 2.1 (Material's bar and the navigation bar) untouched; the capsule styles take its bottom from capsuleSpace()
     * alone (not added to the Scaffold's, which would leave a gap), and keep its top and sides.
     */
    @Test fun switchingSolidToGlassKeepsTheListClear() {
        for (nav in listOf(0.dp, 48.dp)) {
            val solidScaffold = scaffoldPadding(nav, materialBar = true)
            assertEquals(solidScaffold, navHostPadding(solidScaffold, false, capsuleSpace(1f, nav), LayoutDirection.Ltr))
            val glass = padding(floating = true, 1f, nav)
            assertEquals(capsuleSpace(1f, nav), glass.calculateBottomPadding())
            assertEquals(24.dp, glass.calculateTopPadding())
            for (floating in listOf(false, true, false, true)) {
                val p = padding(floating, 1f, nav)
                val barTop = if (floating) capsuleTop(1f, nav) else screenHeight - 80.dp - nav
                assertTrue("floating=$floating nav=$nav", lastRowBottom(p).value <= barTop.value + 0.01f)
            }
        }
        // The sides: a navigation bar on one side (three buttons, landscape) stays with the NavHost, in either direction.
        val side = PaddingValues(start = 48.dp, top = 24.dp, end = 0.dp, bottom = 0.dp)
        for (dir in LayoutDirection.entries) {
            val p = navHostPadding(side, true, 76.dp, dir)
            assertEquals(side.calculateLeftPadding(dir), p.calculateLeftPadding(dir))
            assertEquals(side.calculateRightPadding(dir), p.calculateRightPadding(dir))
            assertEquals(76.dp, p.calculateBottomPadding())
        }
    }

    private val navHost: String by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "app/src/main/java/app/seguridad").isDirectory) dir = dir.parentFile
        File(dir, "app/src/main/java/app/seguridad/ui/AppNavHost.kt").readText()
    }

    private fun screen(name: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "app/src/main/java/app/seguridad").isDirectory) dir = dir.parentFile
        return File(dir, "app/src/main/java/app/seguridad/ui/screens/$name").readText()
    }

    /**
     * Review Focus 5: the buttons of «Bóveda» and «Contactos» and their notices (Snackbar) are in the Scaffold of their
     * screen, inside the NavHost, which ends above the capsule; they add no inset of their own (it is consumed by the
     * NavHost) and are not moved down. So a button 16 dp above the NavHost's bottom is above the capsule, at any font.
     */
    @Test fun fabAndSnackbarSitAboveTheCapsule() {
        for (name in listOf("VaultScreen.kt", "ContactsScreen.kt")) {
            val src = screen(name)
            assertTrue(name, "contentWindowInsets = WindowInsets(0, 0, 0, 0)" in src)
            assertTrue(name, "snackbarHost = { SnackbarHost(snackbar) }" in src)
            for (bad in listOf("navigationBarsPadding", "WindowInsets.navigationBars", ".offset(", "zIndex", "Popup(")) {
                assertFalse("$bad in $name", bad in src)
            }
        }
        assertTrue("composable(\"vault\") { VaultScreen(" in navHost && "composable(\"contacts\") {\n                        ContactsScreen(" in navHost)
        for (fontScale in listOf(1f, 2f)) {
            for (nav in listOf(0.dp, 48.dp)) {
                val fabBottom = screenHeight - padding(floating = true, fontScale, nav).calculateBottomPadding() - 16.dp
                assertTrue(fabBottom < capsuleTop(fontScale, nav))
            }
        }
    }
}
