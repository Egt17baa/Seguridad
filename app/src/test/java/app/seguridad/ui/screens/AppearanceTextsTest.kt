package app.seguridad.ui.screens

import app.seguridad.settings.STYLE_ORDER
import app.seguridad.settings.Settings
import app.seguridad.settings.ThemeStyle
import app.seguridad.tools.ForbiddenWords
import app.seguridad.tools.kotlinLiterals
import app.seguridad.tools.lookNameProblems
import app.seguridad.ui.theme.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 2.1, Aspecto, tarea 4: los textos de Ajustes → Aspecto, exactos como los fija el §4 del dictamen del Abogado. */
class AppearanceTextsTest {
    private val main: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/app/seguridad") }.first { it.isDirectory }

    private fun code(path: String) = File(main, path).readText().lines()
        .filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")

    @Test fun theShortLinesAreTheLawyersOnes() {
        assertEquals("Sólido: paneles opacos sobre un fondo liso, sin transparencias.", STYLE_SOLID_SHORT)
        assertEquals("Degradado: paneles opacos, sin transparencias, sobre un fondo de color en degradado.", STYLE_GRADIENT_SHORT)
        assertEquals("Cristal: paneles translúcidos sobre un fondo de color.", STYLE_GLASS_SHORT)
        assertEquals("Vidrio: como «Cristal», con paneles más translúcidos y bordes iluminados.", STYLE_GLASS_STRONG_SHORT)
        assertEquals("Transparente: deja ver, atenuado, el fondo de pantalla del móvil detrás de la app.", STYLE_TRANSPARENT_SHORT)
    }

    @Test fun theFoldedTextsAreTheLawyersOnes() {
        // Owner decision (24-11-2026): the wallpaper is sharp; branch review I-1: not in split screen or a floating window.
        assertEquals(
            "La app no lee la imagen del fondo de pantalla ni pide permisos: Android la dibuja detrás de la app. Se ve " +
                "nítida, atenuada por una capa de color. Quien mire la pantalla verá también tu fondo de pantalla. Con un " +
                "fondo de pantalla animado, el móvil puede gastar algo más de batería. En pantalla dividida o en una " +
                "ventana flotante, el fondo de pantalla no se ve. Como en toda la app, sin capturas de pantalla. Con " +
                "«Reducir transparencias» o «Alto contraste», el fondo de pantalla deja de verse.",
            STYLE_TRANSPARENT_MORE,
        )
        assertTrue("difumina" !in STYLE_TRANSPARENT_MORE)
        // R-A5: the intensity of the glass works on the three translucent styles (Palettes.kt, `nominal`), and says so.
        assertEquals(
            "La app ajusta los tonos para que el texto tenga contraste suficiente. La vista previa enseña el cambio antes " +
                "de aplicarlo. La intensidad del cristal se aplica a «Cristal», «Vidrio» y «Transparente».",
            CUSTOM_MORE,
        )
    }

    /**
     * 2.1.1, tarea 4 (O-6 del dictamen del principio, con la opción B del dueño): la barra de abajo flota sobre el fondo de
     * la app, nunca sobre el contenido; desde Android 12 difumina, como las demás barras, el fondo de la app. Sin «siempre»
     * más translúcida (m-2 de la tarea 1) ni pastilla que «se funde» (m-5 de la tarea 2).
     */
    @Test fun styleMoreSaysWhatTheBottomBarDoes() {
        assertEquals(
            "En «Cristal», «Vidrio» y «Transparente», la barra de abajo flota sobre el fondo de la app. Desde Android 12, " +
                "esa barra, las demás barras y los diálogos difuminan el fondo de la app que tienen detrás; en Android 8 a 11 " +
                "son más opacos. «Degradado» tiene la misma barra flotante, opaca. «Degradado» y «Sólido» no tienen " +
                "transparencias. «Reducir transparencias» y «Alto contraste» hacen opacos todos los paneles, y «Alto " +
                "contraste» quita también los bordes iluminados de «Vidrio».",
            STYLE_MORE,
        )
        val t = STYLE_MORE.lowercase()
        for (bad in listOf("siempre", "funde", "contenido", "por debajo")) assertTrue(bad, bad !in t)
    }

    /** 2.1.1, O-6: ningún texto de Aspecto dice que algo difumina el fondo de pantalla (en «Transparente» se ve nítido). */
    @Test fun noTextSaysTheBarBlursTheWallpaper() {
        val texts = kotlinLiterals(File(main, "ui/screens/AppearanceScreen.kt").readText()).map { it.second.lowercase() }
        assertTrue(texts.size > 10)
        assertTrue(texts.any { "difumin" in it } && texts.any { "fondo de pantalla" in it })
        val bad = texts.filter { "difumin" in it && "fondo de pantalla" in it }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun eachStyleHasItsLine() {
        assertEquals(
            listOf(STYLE_SOLID_SHORT, STYLE_GRADIENT_SHORT, STYLE_GLASS_SHORT, STYLE_GLASS_STRONG_SHORT, STYLE_TRANSPARENT_SHORT),
            STYLE_ORDER.map { styleShort(it) },
        )
        // Each line starts with the name the chips show.
        for (s in ThemeStyle.entries) assertTrue(styleShort(s), styleShort(s).startsWith(s.label() + ": "))
    }

    @Test fun onlyTransparentAddsItsFoldedText() {
        for (s in ThemeStyle.entries) {
            val expected = if (s == ThemeStyle.TRANSPARENT) listOf(STYLE_MORE, STYLE_TRANSPARENT_MORE) else listOf(STYLE_MORE)
            assertEquals(s.name, expected, styleMore(s))
        }
    }

    @Test fun theNewTextsAreClean() {
        val texts = ThemeStyle.entries.map { styleShort(it) } + STYLE_MORE + STYLE_TRANSPARENT_MORE + CUSTOM_MORE
        for (t in texts) {
            assertTrue("$t: ${ForbiddenWords.problems(t)}", ForbiddenWords.problems(t).isEmpty())
            assertTrue("$t: ${lookNameProblems(t)}", lookNameProblems(t).isEmpty())
        }
    }

    /** The screen offers the five styles in their order, the line of the chosen one and its folded texts (M-3). */
    @Test fun theScreenUsesTheOrderTheLineAndTheFoldedTexts() {
        val screen = code("ui/screens/AppearanceScreen.kt")
        // F-1, owner decision (b): the styles on offer and the one marked follow Android 14 (offeredStyles, shownStyle).
        assertTrue(screen, "val shownStyle = shownStyle(settings.style, Build.VERSION.SDK_INT)" in screen)
        assertTrue(screen, "Choices(offeredStyles(Build.VERSION.SDK_INT), shownStyle" in screen)
        // TalkBack reads the new line when a style is chosen (review of task 4), without changing how it looks.
        assertTrue(screen, "Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { Hint(styleShort(shownStyle)) }" in screen)
        assertTrue(screen, "MoreInfo { styleMore(shownStyle).forEach { Hint(it) } }" in screen)
        assertTrue(screen, "if (Build.VERSION.SDK_INT < TRANSPARENT_MIN_SDK) Hint(TRANSPARENT_FROM_14)" in screen)
        assertTrue(screen, "Choices(ThemeStyle.entries" !in screen)
        assertTrue(screen, "Choices(STYLE_ORDER" !in screen)
        assertTrue(screen, "styleShort(settings.style)" !in screen && "styleMore(settings.style)" !in screen)
    }

    /** F-1, owner decision (b): the line before Android 14, like the one of the wallpaper's colours before Android 12. */
    @Test fun theLineOfTransparentBeforeAndroid14() {
        assertEquals("«Transparente», desde Android 14.", TRANSPARENT_FROM_14)
        assertTrue(ForbiddenWords.problems(TRANSPARENT_FROM_14).isEmpty())
        assertTrue(lookNameProblems(TRANSPARENT_FROM_14).isEmpty())
    }

    /** F-1, owner decision (b): Ajustes sums up the look as it is drawn; a saved «Transparente» is «Cristal» before 14. */
    @Test fun theSummaryNamesTheStyleAsItIsDrawn() {
        val s = Settings(style = ThemeStyle.TRANSPARENT)
        assertTrue(appearanceSummary(s, 33), appearanceSummary(s, 33).startsWith("Cristal · "))
        assertTrue(appearanceSummary(s, 26), appearanceSummary(s, 26).startsWith("Cristal · "))
        assertTrue(appearanceSummary(s, 34), appearanceSummary(s, 34).startsWith("Transparente · "))
        assertTrue(appearanceSummary(s.copy(style = ThemeStyle.GLASS_STRONG), 33).startsWith("Vidrio · "))
    }

    /**
     * The preview cannot show the wallpaper: in «Transparente» it draws the app's gradient, opaque, under the layer, so
     * nothing behind the window shows through it, and it says nothing more.
     */
    @Test fun thePreviewOfTransparentIsTheLayerOverTheGradient() {
        val screen = code("ui/screens/AppearanceScreen.kt")
        // Review of task 4: the brush is made once per size and look (drawWithCache), not on every frame.
        assertTrue(screen, ".drawWithCache {" in screen)
        assertTrue(screen, "val underlay = if (look.wallpaperBehind) previewUnderlay(look, size) else null" in screen)
        assertTrue(screen, "onDrawBehind { underlay?.let { drawRect(it, size = size) } }" in screen)
        assertTrue(screen, ".drawBehind {" !in screen)
        assertTrue(screen, "multiWindow = current.multiWindow" in screen)
    }
}
