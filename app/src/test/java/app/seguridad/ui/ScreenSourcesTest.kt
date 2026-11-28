package app.seguridad.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.1.2, R-5, R-6 and R-9: lifecycle code of the screens that the JVM cannot run, checked in its source, as
 * `PlatformSourcesTest` does. The behaviour itself is in the owner's phone test.
 */
class ScreenSourcesTest {
    private fun main(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/app/seguridad/$path")
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("$path not found")
    }

    /** Code only: comments say what it does, but they must not make the check pass. */
    private fun code(path: String) = main(path).lines()
        .filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
        .joinToString("\n")

    /** Every screen of `ui/screens`, by its file name. */
    private fun screens(): List<File> {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val d = File(dir, "app/src/main/java/app/seguridad/ui/screens")
            if (d.isDirectory) return d.listFiles { f -> f.name.endsWith(".kt") }!!.sortedBy { it.name }
            dir = dir.parentFile
        }
        error("ui/screens not found")
    }

    /**
     * Launchers that never set `VaultLock.keepOpenOnce`, so their callback has nothing to take back: the file and a text of
     * the call's own line that names it, and why.
     */
    private val neverKeepsOpen = mapOf(
        // «Elegir imagen» of the inspector, like «Limpiar foto»: the picker does not keep the vault open (the inspector
        // does not need it); only its camera scanner does, and that one takes the flag back.
        "QrInspectScreen.kt" to "PickerPlace.QR_IMAGE",
    )

    /**
     * R-5 (and m-1 of its review): in every screen that sets `VaultLock.keepOpenOnce = true` (it keeps the vault open
     * while a picker or the scanner is on top), every launcher's callback (`rememberDocumentPicker`,
     * `rememberLauncherForActivityResult`) takes the flag back as its first statement, whatever the result is (a
     * document, a cancel, a refused URI), as `VaultPickerScreen` does. Otherwise, back from a picker when
     * MainActivity.onStart did not run (a result delivered to a re-created activity, split screen), the flag could keep
     * the vault open the next time the app is left. The files are found, not listed by hand; the call may span lines.
     */
    @Test fun pickerCallbacksClearKeepOpenOnce() {
        val launcher = Regex("""\b(rememberDocumentPicker|rememberLauncherForActivityResult)\(""")
        // From the call's `(` to its callback's `{ x ->`, over several lines, then the callback's first statement.
        val callback = Regex("""^[^{]*\{\s*\w+\s*->[ \t]*(?://[^\n]*)?\n\s*([^\n]*)""")
        val files = screens().filter { "keepOpenOnce = true" in code("ui/screens/${it.name}") }
        assertTrue("screens that keep the vault open: ${files.map { it.name }}", files.size >= 10)
        var checked = 0
        var excluded = 0
        for (f in files) {
            val src = code("ui/screens/${f.name}")
            for (m in launcher.findAll(src)) {
                val rest = src.substring(m.range.last + 1)
                val first = callback.find(rest)?.groupValues?.get(1)?.trim() ?: error("${f.name}: no callback after ${m.value}")
                val header = rest.substringBefore("->")
                if (neverKeepsOpen[f.name]?.let { it in header } == true) {
                    excluded++
                    continue
                }
                assertTrue("${f.name}: ${m.value} starts with «$first»", first.startsWith("VaultLock.keepOpenOnce = false"))
                checked++
            }
        }
        assertEquals("every exclusion still names a launcher", neverKeepsOpen.size, excluded)
        assertTrue("launchers checked: $checked", checked >= 20)
    }

    /** R-6: the 2FA QR of an entry goes when the app stops (Home, screen off, another app), as the wifi QR does. */
    @Test fun otpQrHidesOnStop() {
        val src = code("ui/screens/OtpQrScreen.kt")
        assertTrue(Regex("""LifecycleEventEffect\(Lifecycle\.Event\.ON_STOP\)\s*\{\s*shown = false\s*\}""").containsMatchIn(src))
    }

    /**
     * R-9: leaving the QR inspector (back, another tab, a target that never composed) drops a text no screen reached; a
     * change of configuration does not. A target reaches it in its first composition, before the inspector leaves the
     * composition: the 2FA and wifi forms take it in their ViewModel (QrPendingText), «Añadir contacto» takes it in a
     * `remember`, and «Abrir lo recibido», which reads it once the vault opens, claims it outside its gate.
     */
    @Test fun qrInspectorClearsHandoffOnDispose() {
        val src = code("ui/screens/QrInspectScreen.kt")
        val inspector = src.substringAfter("fun QrInspectScreen(").substringBefore("\nfun ")
        assertTrue(
            Regex("""DisposableEffect\(Unit\)\s*\{\s*onDispose\s*\{\s*if\s*\(activity\?\.isChangingConfigurations != true\)\s*QrHandoff\.clearUnclaimed\(\)\s*\}\s*\}""")
                .containsMatchIn(inspector),
        )
        val contact = code("ui/screens/ContactAddScreen.kt")
        assertTrue(Regex("""remember\s*\{\s*QrHandoff\.take\(QrTarget\.CARD\)\s*\}""").containsMatchIn(contact))
        val received = code("ui/screens/PkActionsScreen.kt").substringAfter("fun OpenReceivedScreen(").substringBefore("\nfun ")
        assertTrue(Regex("""remember\s*\{\s*QrHandoff\.claim\(QrTarget\.RECEIVED\)\s*\}""").containsMatchIn(received.substringBefore("ContactsFrame(")))
    }

    /**
     * R-27: each row of old passwords (the detail's «Contraseñas anteriores» and the history screen) is keyed by its
     * `changedAt`, so the «Ver» of a row stays with its password when the list changes, and never shows another one.
     */
    @Test fun oldPasswordRowsAreKeyed() {
        val rows = mapOf(
            "ui/screens/VaultEntryScreen.kt" to Regex("""history\.forEach\s*\{\s*h\s*->\s*key\(h\.changedAt\)\s*\{"""),
            "ui/screens/EntryHistoryScreen.kt" to Regex("""old\.forEach\s*\{\s*h\s*->\s*key\(h\.changedAt\)\s*\{"""),
        )
        for ((path, row) in rows) {
            val src = code(path)
            assertTrue(path, row.containsMatchIn(src))
            assertTrue(path, "import androidx.compose.runtime.key" in src)
        }
    }

    /**
     * R-33: «Limpiar foto» and «Tachar» put their own copy of the picked file's bytes to zero once it is no longer needed
     * (after cleaning or decoding), in `finally`, as `QrImageRead` does: the bytes are kept as soon as they are read, inside
     * the IO block, so a cancellation as it returns still reaches them.
     */
    @Test fun pickedPhotoBytesAreZeroed() {
        for (path in listOf("ui/screens/PhotoCleanScreen.kt", "ui/screens/RedactScreen.kt")) {
            val open = code(path).substringAfter("fun open(uri: Uri)").substringBefore("\n    }\n")
            assertTrue(path, Regex("""var picked: ByteArray\? = null""").containsMatchIn(open))
            assertTrue(path, Regex("""readPhoto\(appContext, uri\)\.also \{ picked = it \}""").containsMatchIn(open))
            val fin = open.substringAfterLast("} finally {")
            assertTrue(path, Regex("""picked\?\.fill\(0\)""").containsMatchIn(fin))
        }
    }
}
