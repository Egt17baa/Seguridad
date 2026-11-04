package app.seguridad.imports

import app.seguridad.tools.kotlinLiterals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rulings R2, R7, R8 and R8a: the names of other products appear only in the string literals of `imports/ImportTexts.kt`
 * (Task 9 adds the screen's texts to this test). Outside it, only the exact literals of [ALLOWED] may name one: texts
 * from before 1.4.0 and a licence attribution. No word is exempted: a new literal with «Google» anywhere else fails.
 * Comments and Kotlin identifiers are not visible text and are not checked.
 */
class ImportTextsTest {
    private val names = listOf("bitwarden", "aegis", "2fas", "firefox", "mozilla", "apple", "safari", "icloud", "chrome", "chromium", "edge", "brave", "steam", "keepass", "keepassxc", "google")

    /** The only file where any literal may name another product. */
    private val TEXTS_FILE = "app/seguridad/imports/ImportTexts.kt"

    /**
     * The exact literals (as [kotlinLiterals] gives them: a chain joined by `+` is one, a `${}` template drops out)
     * that may name another product, by file (path under `app/src/main/java`):
     * - R8: «Google Authenticator» and «la copia de Google», texts from before 1.4.0 that passed the 1.3.x review;
     * - R8a: the attribution of Guava's licence, which the licence requires.
     */
    private val ALLOWED = setOf(
        "app/seguridad/ui/screens/VaultEntryScreen.kt" to "El QR de Google Authenticator no es válido o está dañado",
        "app/seguridad/ui/screens/VaultEntryScreen.kt" to "Importados  códigos de Google Authenticator. Esta entrada sigue abierta: guárdala si la has cambiado.",
        "app/seguridad/ui/screens/VaultEntryScreen.kt" to "Importar de Google Authenticator",
        "app/seguridad/tools/ToolTexts.kt" to "Guarda contraseñas, frases semilla de tus carteras de criptomonedas, notas y códigos 2FA, cifrados con " +
            "AES-256-GCM y una clave derivada de tu contraseña maestra con Argon2id. Solo se guarda en este móvil (no va a la copia " +
            "de Google); puedes exportar una copia cifrada o activar la copia automática en una carpeta que elijas.",
        "app/seguridad/ui/screens/LicensesScreen.kt" to "https://github.com/google/guava, https://github.com/JetBrains/java-annotations y https://jspecify.dev",
    )

    private fun mainRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            File(dir, "app/src/main/java").takeIf { it.isDirectory }?.let { return it }
            File(dir, "src/main/java").takeIf { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("app/src/main/java not found")
    }

    /** Every Kotlin source under `app/src/main/java`, by its path there, with its text. */
    private fun mainSources(): Map<String, String> {
        val java = mainRoot()
        return java.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(java).invariantSeparatorsPath to it.readText() }
    }

    private fun namesIn(text: String) = names.filter { Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text) }

    /** Each literal of [sources] that names another product and is neither in [TEXTS_FILE] nor in [ALLOWED]. */
    private fun violations(sources: Map<String, String>): List<String> = sources.flatMap { (path, src) ->
        if (path == TEXTS_FILE) emptyList()
        else kotlinLiterals(src).filter { (_, lit) -> namesIn(lit).isNotEmpty() && (path to lit) !in ALLOWED }
            .map { (line, lit) -> "$path:$line ${namesIn(lit)} «$lit»" }
    }

    @Test fun otherProductNamesOnlyInImportTexts() {
        val sources = mainSources()
        assertTrue("sources not found", TEXTS_FILE in sources && sources.size > 50)
        val bad = violations(sources)
        assertTrue("Other products' names outside ImportTexts.kt:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun everyAllowedLiteralStillExists() {
        // A list that no longer matches the code must not stay behind, allowing a text that is gone.
        val sources = mainSources()
        for ((path, lit) in ALLOWED) {
            val src = sources[path] ?: error("$path not found")
            assertTrue("$path: «$lit» is no longer there; take it off ALLOWED", kotlinLiterals(src).any { it.second == lit })
        }
    }

    @Test fun aNewLiteralWithANameFailsWhereverItIs() {
        val vault = "app/seguridad/ui/screens/VaultEntryScreen.kt"
        // The word alone is not allowed, not even in a file of the list or right next to an allowed literal.
        assertEquals(1, violations(mapOf(vault to "val a = \"Importar de Google Authenticator\"\nval b = \"Importar de Google Contraseñas\"")).size)
        assertEquals(1, violations(mapOf("app/seguridad/tools/Otra.kt" to "val a = \"Importar de Google Authenticator\"")).size)
        assertEquals(1, violations(mapOf("app/seguridad/imports/Detect.kt" to "val a = \"Formato de \" + \"Bitwarden\"")).size)
        assertEquals(0, violations(mapOf(TEXTS_FILE to "val a = \"importado-2fas\"", vault to "val a = \"Importar de Google Authenticator\"")).size)
    }

    @Test fun theCheckFindsANameInALiteral() {
        assertTrue(namesIn("Código de Steam").isNotEmpty())
        assertTrue(namesIn("importado-2fas").isNotEmpty())
        assertTrue(namesIn("GOOGLE").isNotEmpty())
        assertTrue(namesIn("importado-navegador").isEmpty())
    }
}
