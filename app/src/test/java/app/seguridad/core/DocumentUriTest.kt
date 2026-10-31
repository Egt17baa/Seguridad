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
     * The lint-like check (M4 of the F1 review), on the whole text of each file, so a call split over lines is caught:
     * outside ui/screens/Pickers.kt, no file or folder picker is registered or launched by any other way than
     * rememberDocumentPicker, which runs the guard before the screen sees the URI: not with another launcher, a picker
     * intent (as a constant or a string), a contract of one's own, nor the old startActivityForResult / onActivityResult /
     * activityResultRegistry route (N2 of the F1 re-review).
     */
    @Test fun everyDocumentPickerGoesThroughTheGuard() {
        val files = mainRoot().walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "Pickers.kt" }.toList()
        assertTrue("Too few sources: ${files.size}", files.size > 50)
        val offending = files.flatMap { f -> pickerViolations(f.readText()).map { "${f.name}: $it" } }
        assertTrue("Pickers not guarded:\n" + offending.joinToString("\n"), offending.isEmpty())
        val guarded = files.sumOf { guardedPicker.findAll(it.readText()).count() }
        assertEquals("Number of guarded pickers changed: update this test", 25, guarded)
    }

    @Test fun theCheckCatchesEveryWayAroundTheGuard() {
        val bad = listOf(
            // A launcher split over two lines, with a document contract or with a generic one.
            "val pick = rememberLauncherForActivityResult(\n    ActivityResultContracts.OpenDocument()\n) { uri -> }",
            "val pick = rememberLauncherForActivityResult(\n    ActivityResultContracts.StartActivityForResult(),\n) { r -> }",
            "val pick = registerForActivityResult(\n  ActivityResultContracts.CreateDocument(\"text/plain\")) { }",
            "val pick = rememberLauncherForActivityResult(MyPicker()) { }",
            // The picker's intent built by hand, or a contract of one's own.
            "launcher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE))",
            "Intent(Intent.ACTION_CREATE_DOCUMENT)", "Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)",
            "Intent(Intent.ACTION_GET_CONTENT)", "Intent(Intent.ACTION_PICK)",
            "Intent(\"android.intent.action.OPEN_DOCUMENT\")",
            "object Picker : ActivityResultContract<String, Uri?>() { }",
            "val c = ActivityResultContracts.OpenDocument()",
            "ActivityResultContracts.GetContent()", "ActivityResultContracts.PickVisualMedia()",
            // The old Activity result API, with the action written as a string (N2 of the F1 re-review).
            "startActivityForResult(Intent(\"android.provider.action.PICK_IMAGES\"), 1)",
            "startActivityForResult (intent, 2)",
            "startIntentSenderForResult(sender, 3, null, 0, 0, 0)",
            "override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { }",
            "activityResultRegistry.register(\"k\", contract) { }",
            "Intent(\"android.provider.action.PICK_IMAGES\")",
        )
        for (text in bad) assertTrue("not caught: $text", pickerViolations(text).isNotEmpty())
        val good = listOf(
            "val scanner = rememberLauncherForActivityResult(ScanContract()) { result -> }",
            "val scanner = rememberLauncherForActivityResult(\n    ScanContract()\n) { result -> }",
            "val pick = rememberDocumentPicker(ActivityResultContracts.OpenDocument()) { uri -> }",
            "val save = rememberDocumentPicker(\n    ActivityResultContracts.CreateDocument(\"image/png\"),\n    onSaveTarget,\n)",
            "import androidx.activity.compose.rememberLauncherForActivityResult\nif (uri == null) return@rememberLauncherForActivityResult",
        )
        for (text in good) assertEquals(text, emptyList<String>(), pickerViolations(text))
    }

    private val guardedPicker = Regex("""rememberDocumentPicker\(\s*ActivityResultContracts\.""")

    /** What in [text] (a whole source file) registers or launches a picker without rememberDocumentPicker. */
    private fun pickerViolations(text: String): List<String> {
        val out = mutableListOf<String>()
        // Any launcher other than the QR scanner's.
        for (m in Regex("""\b(rememberLauncherForActivityResult|registerForActivityResult)\s*\(""").findAll(text)) {
            if (!Regex("""^\s*ScanContract\(\)""").containsMatchIn(text.substring(m.range.last + 1))) out += m.value
        }
        // A document or content contract anywhere but right inside rememberDocumentPicker(.
        val contracts = Regex("""ActivityResultContracts\.(OpenDocument|OpenMultipleDocuments|CreateDocument|OpenDocumentTree|GetContent|GetMultipleContents|PickVisualMedia|PickMultipleVisualMedia|StartActivityForResult|StartIntentSenderForResult)\b""")
        val guarded = guardedPicker.findAll(text).map { it.range.last - "ActivityResultContracts.".length + 1 }.toSet()
        for (m in contracts.findAll(text)) if (m.range.first !in guarded) out += m.value
        // Pickers built by hand: their intents (constants or strings), a contract of one's own, or the old Activity result API.
        val byHand = Regex(
            """ACTION_OPEN_DOCUMENT|ACTION_CREATE_DOCUMENT|ACTION_GET_CONTENT|ACTION_PICK|intent\.action\.(OPEN_DOCUMENT|CREATE_DOCUMENT|GET_CONTENT|PICK)""" +
                """|provider\.action\.PICK_IMAGES|StartActivityForResult|StartIntentSenderForResult|ActivityResultContract\s*<""" +
                """|\bstartActivityForResult\s*\(|\bstartIntentSenderForResult\s*\(|\bonActivityResult\s*\(|\bactivityResultRegistry\b""",
        )
        for (m in byHand.findAll(text)) out += m.value
        return out
    }
}
