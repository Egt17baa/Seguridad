package app.seguridad.tools

import app.seguridad.tools.photo.Found
import app.seguridad.tools.photo.PhotoFormat
import app.seguridad.tools.photo.PhotoReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class ToolTextsTest {
    private val annexPath = "docs/superpowers/specs/2026-10-27-seguridad-1.3.0-textos.md"

    /** The annex, found from the test's working directory (app/ for Gradle on Android, jvm-check/ for the JVM check). */
    private fun annex(): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, annexPath)
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("Annex not found: $annexPath")
    }

    /** Top-level «…» quotes (nested ones stay inside) of the tool sections of the annex. */
    private fun annexTexts(): List<String> {
        val text = annex()
        val tools = text.substring(text.indexOf("\n## Limpiar foto"), text.indexOf("\n## Cambios en los documentos"))
        val out = mutableListOf<String>()
        for (line in tools.lines()) {
            // A rule for the implementer, not a text shown on screen.
            if (line.trimStart().startsWith("- Los acortadores")) continue
            var depth = 0
            val sb = StringBuilder()
            for (c in line) {
                when (c) {
                    '«' -> { if (depth > 0) sb.append(c); depth++ }
                    '»' -> { depth--; if (depth > 0) sb.append(c) else { out += sb.toString(); sb.clear() } }
                    else -> if (depth > 0) sb.append(c)
                }
            }
            assertEquals("Unbalanced quotes in: $line", 0, depth)
        }
        // «Sin avisos (… sin ✓ ni «limpio»)»: a word the annex forbids, not a text.
        return out - setOf("limpio")
    }

    @Test
    fun everyAnnexTextIsInTheListVerbatim() {
        val texts = annexTexts()
        assertTrue("Annex parse found too few texts: ${texts.size}", texts.size >= 50)
        val missing = texts.filter { it !in ALL_TOOL_TEXTS }
        assertTrue("Annex texts missing from ToolTexts (or not verbatim):\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun noForbiddenWords() {
        // Explicitly allowed by the annex: «Sin avisos» names «seguro» only to deny it, and Tachar says «no se puede
        // recuperar» about the covered pixels of the copy.
        val exceptions = setOf(QR_NO_ALERTS, REDACT_EXPLANATION)
        val banned = listOf("anónim", "imposible", "100 %", "100%", "de verdad", "validez legal", "limpio", "sospechos")
        for (text in ALL_TOOL_TEXTS - exceptions) {
            val t = text.lowercase()
            for (b in banned) assertFalse("«$b» in: $text", b in t)
            Regex("garantiza").findAll(t).forEach { m ->
                assertTrue("«garantiza» without «no» in: $text", t.substring(0, m.range.first).endsWith("no "))
            }
            // «Chip seguro (StrongBox)» is the annex's name for the hardware, not a claim about the app.
            val claims = t.replace("chip seguro", "")
            if ("seguro" in claims) assertTrue("«seguro» in: $text", "no garantiza que sea seguro" in claims)
        }
    }

    @Test
    fun exceptionsAreTheAnnexOnes() {
        assertTrue("no garantiza que sea seguro" in QR_NO_ALERTS)
        assertTrue("no se puede recuperar" in REDACT_EXPLANATION)
    }

    @Test
    fun gpsIsRoundedWithSpanishDecimalComma() {
        assertEquals("Ubicación GPS: cerca de 40,42, −3,70 (redondeado)", photoGpsLine(40.4168, -3.7038))
        assertEquals("Ubicación GPS: cerca de −33,87, 151,21 (redondeado)", photoGpsLine(-33.8688, 151.2093))
        // Never a «−0,00».
        assertEquals("Ubicación GPS: cerca de 0,00, 0,00 (redondeado)", photoGpsLine(-0.001, 0.0))
    }

    @Test
    fun foundLinesFollowTheAnnex() {
        val all = Found.entries.toSet()
        val lines = photoFoundLines(PhotoReport(PhotoFormat.JPEG, all, 40.42 to -3.70))
        assertEquals(
            listOf(
                "Ubicación GPS: cerca de 40,42, −3,70 (redondeado)", PHOTO_DEVICE, PHOTO_DATE_SOFTWARE, PHOTO_THUMBNAIL,
                PHOTO_XMP_COMMENTS, PHOTO_ICC_KEPT, PHOTO_MOTION_VIDEO, PHOTO_C2PA, PHOTO_AUTHOR_RIGHTS, PHOTO_OTHER,
            ),
            lines,
        )
        // The colour profile is kept, and the screen says so whenever it is there.
        assertEquals(listOf(PHOTO_ICC_KEPT), photoFoundLines(PhotoReport(PhotoFormat.PNG, setOf(Found.ICC_KEPT), null)))
        // GPS in XMP without readable coordinates: no numbers.
        assertEquals(listOf(PHOTO_GPS_NO_COORDS), photoFoundLines(PhotoReport(PhotoFormat.JPEG, setOf(Found.GPS), null)))
        assertTrue(photoFoundLines(PhotoReport(PhotoFormat.WEBP, emptySet(), null)).isEmpty())
    }

    @Test
    fun nothingToRemoveIgnoresTheKeptProfile() {
        assertFalse(photoHasSomethingToRemove(PhotoReport(PhotoFormat.JPEG, setOf(Found.ICC_KEPT), null)))
        assertFalse(photoHasSomethingToRemove(PhotoReport(PhotoFormat.JPEG, emptySet(), null)))
        assertTrue(photoHasSomethingToRemove(PhotoReport(PhotoFormat.JPEG, setOf(Found.ICC_KEPT, Found.OTHER), null)))
    }

    @Test
    fun doneListNamesWhatWasRemovedButNotTheProfile() {
        val report = PhotoReport(PhotoFormat.JPEG, setOf(Found.GPS, Found.ICC_KEPT, Found.THUMBNAIL), 1.0 to 2.0)
        assertEquals(
            "Copia lista, sin estos datos: ubicación GPS; miniatura incrustada. El original no se ha tocado y sigue " +
                "teniéndolos: comparte la copia, no el original.",
            photoDoneMessage(report),
        )
        // Nothing removed: a short line, not the card's «No hemos encontrado…» again.
        assertEquals(PHOTO_DONE_SHORT, photoDoneMessage(PhotoReport(PhotoFormat.PNG, setOf(Found.ICC_KEPT), null)))
        assertEquals("Copia lista.", PHOTO_DONE_SHORT)
    }

    @Test
    fun convertedPhotoNeverClaimsEverythingWentAndNamesTheKeptProfile() {
        val plain = photoDoneMessage(PhotoReport(PhotoFormat.JPEG, emptySet(), null), converted = true)
        assertEquals(PHOTO_DONE_CONVERTED, plain)
        val withIcc = photoDoneMessage(PhotoReport(PhotoFormat.JPEG, setOf(Found.ICC_KEPT), null), converted = true)
        assertEquals("$PHOTO_DONE_CONVERTED $PHOTO_ICC_KEPT.", withIcc)
        for (t in listOf(plain, withIcc)) assertFalse(t, "todos" in t)
    }

    @Test
    fun everyTextConstantIsChecked() {
        // A new text in ToolTexts.kt cannot escape noForbiddenWords: every String constant there is in the list
        // (private fragments, such as the GPS line's prefix, inside one of its texts).
        val fields = Class.forName("app.seguridad.tools.ToolTextsKt").declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .onEach { it.isAccessible = true }
        assertTrue("Too few constants found: ${fields.size}", fields.size >= 60)
        for (f in fields) {
            val v = f.get(null) as String
            if (Modifier.isPrivate(f.modifiers)) {
                assertTrue("${f.name} in no text", ALL_TOOL_TEXTS.any { v in it })
            } else {
                assertTrue("${f.name} missing from ALL_TOOL_TEXTS", v in ALL_TOOL_TEXTS)
            }
        }
        assertTrue(ALL_TOOL_TEXTS.containsAll(PHOTO_FOUND_TEXTS.values))
        assertTrue(ALL_TOOL_TEXTS.containsAll(PHOTO_REMOVED_NAMES.values))
    }
}
