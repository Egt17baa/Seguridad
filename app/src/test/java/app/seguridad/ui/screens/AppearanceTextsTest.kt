package app.seguridad.ui.screens

import app.seguridad.settings.STYLE_ORDER
import app.seguridad.settings.ThemeStyle
import app.seguridad.tools.ForbiddenWords
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
        assertEquals(
            "En «Cristal», «Vidrio» y «Transparente», desde Android 12, las barras y los diálogos difuminan lo que tienen " +
                "detrás; en Android 8 a 11 son más opacos. «Degradado» y «Sólido» no tienen transparencias. «Reducir " +
                "transparencias» y «Alto contraste» hacen opacos todos los paneles.",
            STYLE_MORE,
        )
        assertEquals(
            "La app no lee la imagen del fondo de pantalla ni pide permisos: Android la dibuja detrás de la app. Desde " +
                "Android 12, si el móvil lo permite, se ve difuminada. Quien mire la pantalla verá también tu fondo de " +
                "pantalla. Con un fondo de pantalla animado, el móvil puede gastar algo más de batería. Como en toda la " +
                "app, sin capturas de pantalla. Con «Reducir transparencias» o «Alto contraste», el fondo de pantalla " +
                "deja de verse.",
            STYLE_TRANSPARENT_MORE,
        )
        // R-A5: the intensity of the glass works on the three translucent styles (Palettes.kt, `nominal`), and says so.
        assertEquals(
            "La app ajusta los tonos para que el texto tenga contraste suficiente. La vista previa enseña el cambio antes " +
                "de aplicarlo. La intensidad del cristal se aplica a «Cristal», «Vidrio» y «Transparente».",
            CUSTOM_MORE,
        )
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
        assertTrue(screen, "Choices(STYLE_ORDER, settings.style" in screen)
        assertTrue(screen, "Hint(styleShort(settings.style))" in screen)
        assertTrue(screen, "MoreInfo { styleMore(settings.style).forEach { Hint(it) } }" in screen)
        assertTrue(screen, "Choices(ThemeStyle.entries" !in screen)
    }

    /**
     * The preview cannot show the wallpaper: in «Transparente» it draws the app's gradient, opaque, under the layer, so
     * nothing behind the window shows through it, and it says nothing more.
     */
    @Test fun thePreviewOfTransparentIsTheLayerOverTheGradient() {
        val screen = code("ui/screens/AppearanceScreen.kt")
        assertTrue(screen, ".drawBehind { if (look.wallpaperBehind) drawPreviewUnderlay(look, size) }" in screen)
        assertTrue(screen, "wallpaperBlur = current.wallpaperBlur" in screen)
    }
}
