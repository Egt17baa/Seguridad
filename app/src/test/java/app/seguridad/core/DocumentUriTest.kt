package app.seguridad.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ANDROID-2 (+CADENAS-1/2): what a system picker hands back is used only if it is another app's content:// document. */
class DocumentUriTest {
    private val pkg = "io.github.egt17baa.seguridad"

    @Test fun rejectsFilesOwnProvidersAndOddForms() {
        val bad = listOf(
            "file" to "",
            "file" to null,
            "FILE" to "",
            "android.resource" to pkg,
            "content" to null,
            "content" to "",
            "content" to pkg,
            "content" to "$pkg.compartir",
            "content" to "$pkg.androidx-startup",
            "content" to "IO.GITHUB.EGT17BAA.SEGURIDAD.COMPARTIR",
            "content" to "x@$pkg.compartir:80",
            "content" to "@:",
            "CONTENT" to "com.android.externalstorage.documents",
            null to "com.android.externalstorage.documents",
        )
        for ((scheme, authority) in bad) assertFalse("$scheme://$authority", isAcceptableDocumentUri(scheme, authority, pkg))
    }

    @Test fun acceptsOtherAppsDocumentProviders() {
        val good = listOf(
            "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents",
            "com.android.providers.media.documents",
            "com.google.android.apps.docs.storage",
            "${pkg}x.documents", // Another app whose name only starts like ours.
        )
        for (a in good) assertTrue(a, isAcceptableDocumentUri("content", a, pkg))
    }

    @Test fun ownPackageMatchIsCaseInsensitive() {
        assertFalse(isAcceptableDocumentUri("content", "io.github.egt17baa.seguridad.compartir", "IO.github.egt17baa.Seguridad"))
    }

    private fun mainRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java")
            if (f.isDirectory) return f
            dir = dir.parentFile
        }
        error("app/src/main/java not found")
    }

    /**
     * The lint-like check: no picker of files or folders is registered directly; all go through rememberDocumentPicker
     * (ui/screens/Pickers.kt), which runs the guard before the screen sees the URI.
     */
    @Test fun everyDocumentPickerGoesThroughTheGuard() {
        val contracts = Regex("""ActivityResultContracts\.(OpenDocument|OpenMultipleDocuments|CreateDocument|OpenDocumentTree|GetContent|GetMultipleContents|PickVisualMedia|PickMultipleVisualMedia)\b""")
        val direct = Regex("""(rememberLauncherForActivityResult|registerForActivityResult)\s*\(\s*ActivityResultContracts\.""")
        val files = mainRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val offending = mutableListOf<String>()
        var guarded = 0
        for (f in files) {
            val text = f.readText()
            if (f.name == "Pickers.kt") continue
            text.lines().forEachIndexed { i, line ->
                if (direct.containsMatchIn(line)) offending += "${f.name}:${i + 1}: $line"
                if (contracts.containsMatchIn(line) && "rememberDocumentPicker(" !in line) offending += "${f.name}:${i + 1}: $line"
                if ("rememberDocumentPicker(ActivityResultContracts." in line) guarded++
            }
        }
        assertTrue("Pickers not guarded:\n" + offending.joinToString("\n"), offending.isEmpty())
        assertEquals("Number of guarded pickers changed: update this test", 25, guarded)
    }
}
