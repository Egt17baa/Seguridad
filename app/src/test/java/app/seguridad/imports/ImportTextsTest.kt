package app.seguridad.imports

import app.seguridad.tools.kotlinLiterals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rulings R2 and R7: the names of other products appear only in the string literals of `imports/ImportTexts.kt`
 * (Task 9 adds the screen's texts to this test). Comments and Kotlin identifiers are not visible text and are not checked.
 */
class ImportTextsTest {
    private val names = listOf("bitwarden", "aegis", "2fas", "firefox", "mozilla", "apple", "safari", "icloud", "chrome", "chromium", "edge", "brave", "steam", "keepass", "keepassxc", "google")

    /** Names that existing texts outside the import already use: «Google Authenticator» (VaultEntryScreen, ToolTexts). */
    private val usedBefore = setOf("google")

    private fun mainRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            File(dir, "app/src/main/java").takeIf { it.isDirectory }?.let { return it }
            File(dir, "src/main/java").takeIf { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("app/src/main/java not found")
    }

    private fun namesIn(text: String) = names.filter { Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text) }

    @Test fun otherProductNamesOnlyInImportTexts() {
        val java = mainRoot()
        val files = java.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val imports = files.filter { it.parentFile?.name == "imports" }
        assertTrue("imports/ sources not found", imports.any { it.name == "ImportTexts.kt" } && imports.size > 3)
        val bad = mutableListOf<String>()
        for (f in files) {
            if (f.parentFile?.name == "imports" && f.name == "ImportTexts.kt") continue
            val inImports = f.parentFile?.name == "imports"
            for ((line, lit) in kotlinLiterals(f.readText())) {
                val found = namesIn(lit).filter { inImports || it !in usedBefore }
                if (found.isNotEmpty()) bad += "${f.name}:$line $found «$lit»"
            }
        }
        assertTrue("Other products' names outside ImportTexts.kt:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun theCheckFindsANameInALiteral() {
        assertTrue(namesIn("Código de Steam").isNotEmpty())
        assertTrue(namesIn("importado-2fas").isNotEmpty())
        assertTrue(namesIn("importado-navegador").isEmpty())
    }
}
