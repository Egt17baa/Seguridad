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

    /**
     * R-5: every picker that is launched with `VaultLock.keepOpenOnce = true` takes the flag back as soon as its result
     * comes (whatever it is: a document, a cancel, a refused URI), as `VaultPickerScreen` does. Otherwise, back from a
     * picker when MainActivity.onStart did not run (a result delivered to a re-created activity, split screen), the flag
     * could keep the vault open on the next time the app is left.
     */
    @Test fun pickerCallbacksClearKeepOpenOnce() {
        val expected = mapOf(
            "ui/screens/VaultAttachments.kt" to 2,
            "ui/screens/VaultBackup.kt" to 2,
            "ui/screens/BackupCheckScreen.kt" to 1,
            "ui/screens/ImportScreen.kt" to 2,
            "ui/screens/VaultUpgrade.kt" to 1,
            "ui/screens/VaultPickerScreen.kt" to 1,
        )
        val picker = Regex("""rememberDocumentPicker\([^\n]*\{\s*\w+\s*->[ \t]*\n\s*([^\n]*)""")
        for ((path, count) in expected) {
            val firsts = picker.findAll(code(path)).map { it.groupValues[1].trim() }.toList()
            assertEquals(path, count, firsts.size)
            for (first in firsts) assertTrue("$path: $first", first.startsWith("VaultLock.keepOpenOnce = false"))
        }
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
}
