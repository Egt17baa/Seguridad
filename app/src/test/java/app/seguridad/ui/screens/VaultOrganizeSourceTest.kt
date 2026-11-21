package app.seguridad.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Anchors in the source of «Organizar» (VaultOrganize.kt): the repository has no Compose UI tests. */
class VaultOrganizeSourceTest {
    private fun source() = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/app/seguridad/ui/screens/VaultOrganize.kt") }.first { it.isFile }.readText()

    /**
     * Audit D-4: in «Borrar carpeta», choosing the other option clears the refusal of the first (the row and its radio
     * button), and the button is off while a deletion runs, as [NameDialog] does: a double tap never starts two.
     */
    @Test fun deleteFolderClearsTheRefusalAndWaits() {
        val body = source().substringAfter("private fun DeleteFolderDialog(").substringBefore("\n@Composable\n")
        assertEquals("the row and its radio button", 2, Regex("""\{ to = option; error = null \}""").findAll(body).count())
        assertTrue("var busy by remember { mutableStateOf(false) }" in body)
        assertTrue(body, Regex("""TextButton\(enabled = !busy, onClick = \{\s*busy = true""").containsMatchIn(body))
        val launch = body.indexOf("scope.launch {", body.indexOf("enabled = !busy"))
        val fin = body.indexOf("} finally {", launch)
        assertTrue(launch >= 0 && fin > launch && "busy = false" in body.substring(fin, fin + 60))
    }
}
