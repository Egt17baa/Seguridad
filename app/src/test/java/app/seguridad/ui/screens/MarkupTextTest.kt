package app.seguridad.ui.screens

import app.seguridad.vault.LimitedMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0, task 5: the notes with format become styled text and nothing else (decision (c) of 1.4.0): no annotation of any
 * kind (so no link, nothing to tap), whatever the note holds, and no code that finds links or reads HTML.
 */
class MarkupTextTest {
    @Test fun linksAndHtmlGetNoAnnotation() {
        val note = "# Título\n- [a](https://x) y https://x\n1. <b>no</b> **www.ejemplo.es**\nmailto:a@b.es tel:600000000 *javascript:alert(1)*"
        val styled = markupAnnotated(LimitedMarkup.parse(note))
        assertEquals("Título\n•  [a](https://x) y https://x\n1.  <b>no</b> www.ejemplo.es\nmailto:a@b.es tel:600000000 javascript:alert(1)", styled.text)
        assertTrue(styled.getStringAnnotations(0, styled.length).isEmpty())
        assertTrue(styled.getLinkAnnotations(0, styled.length).isEmpty())
        assertTrue(styled.spanStyles.isNotEmpty()) // Styles only.
        assertTrue(styled.paragraphStyles.isEmpty())
    }

    @Test fun theCardNeverFindsLinks() {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "app/src/main/java/app/seguridad").isDirectory) dir = dir.parentFile
        val root = File(dir, "app/src/main/java/app/seguridad/ui/screens")
        val src = listOf("MarkupText.kt", "EntryFields.kt", "VaultEntryScreen.kt").joinToString("\n") { File(root, it).readText() }
        for (banned in listOf("Linkify", "LinkAnnotation", "UrlAnnotation", "addStringAnnotation", "pushStringAnnotation", "WebView", "fromHtml", "AnnotatedString.fromHtml", "LocalUriHandler", "autoLink")) {
            assertFalse(banned, banned in src)
        }
    }
}
