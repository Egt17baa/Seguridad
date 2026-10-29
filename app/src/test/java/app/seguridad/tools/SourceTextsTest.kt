package app.seguridad.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every string literal in app/src/main/java passes the forbidden-word check, not only ToolTexts.kt.
 *
 * The scan lexes Kotlin (comments, normal and raw strings, `${}` templates, char literals). Exclusions, all of them:
 * - comments (// and block, KDoc): never shown;
 * - the BIP-39 list, the licence texts and the other assets: they are in `assets/`, not in `src/main/java`;
 * - literals with no space ([isNotUi]: JSON keys, format tags such as «seguridad-vault» or «SG1:», MIME types, URLs,
 *   route names) are not counted as texts, but one that contains a forbidden word is still reported;
 * - no source file is excluded ([EXCLUDED_FILES] is empty on purpose); no code logs (CLAUDE.md), so no log message.
 */
class SourceTextsTest {
    /** Files whose literals are data and not text for the user: none. */
    private val EXCLUDED_FILES = emptySet<String>()

    /** A literal without a space cannot be a sentence (tag, key, name, URL); it is only counted apart, never skipped if it has a banned word. */
    private fun isNotUi(lit: String) = ' ' !in lit && ForbiddenWords.problems(lit).isEmpty()

    private fun mainRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java")
            if (f.isDirectory) return f
            val g = File(dir, "src/main/java")
            if (g.isDirectory) return g
            dir = dir.parentFile
        }
        error("app/src/main/java not found")
    }

    @Test
    fun noUserVisibleLiteralUsesAForbiddenWord() {
        val files = mainRoot().walkTopDown().filter { it.isFile && it.extension == "kt" && it.name !in EXCLUDED_FILES }.toList()
        assertTrue("Too few sources: ${files.size}", files.size > 50)
        var checked = 0
        val bad = mutableListOf<String>()
        for (f in files) {
            for ((line, lit) in kotlinLiterals(f.readText())) {
                if (isNotUi(lit)) continue
                checked++
                val p = ForbiddenWords.problems(lit)
                if (p.isNotEmpty()) bad += "${f.name}:$line $p «$lit»"
            }
        }
        assertTrue("Too few literals checked: $checked", checked > 500)
        assertTrue("Forbidden words in screen texts:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun theLexerFindsLiteralsAndSkipsComments() {
        val src = "val a = \"uno \\\"dos\\\"\" // \"seguro\"\n/* \"seguro\" /* \"x\" */ */ val b = \"\"\"raw \"q\" \n y\"\"\" + 'x' + '\"' + \"t \${f(\"in ner\")} fin \$v\""
        val lits = kotlinLiterals(src).map { it.second }
        assertEquals(listOf("uno \"dos\"", "raw \"q\" \n y", "in ner", "t  fin \$v"), lits)
    }

    @Test
    fun theWordsAreCaughtInAScreenString() {
        // Mutation check in code: a screen sentence with «seguro» must be reported.
        assertTrue(ForbiddenWords.problems("🎲 Generar una segura").isNotEmpty())
        assertTrue(ForbiddenWords.problems("Apúntala en un lugar seguro").isNotEmpty())
        assertTrue(ForbiddenWords.problems("¿Borrarla de verdad?").isNotEmpty())
        assertTrue(ForbiddenWords.problems("imposible de adivinar").isNotEmpty())
        assertTrue(ForbiddenWords.problems("Nota segura").isNotEmpty())
        assertTrue(ForbiddenWords.problems("Seguridad y limpiar foto, no garantiza que sea seguro").isEmpty())
    }
}

/** (line, text) of each string literal of [src] outside comments; template expressions are scanned for nested literals. */
internal fun kotlinLiterals(src: String): List<Pair<Int, String>> {
    val out = mutableListOf<Pair<Int, String>>()
    var i = 0
    var line = 1

    fun skipBlock() {
        var depth = 1
        i += 2
        while (i < src.length && depth > 0) {
            when {
                src.startsWith("/*", i) -> { depth++; i += 2 }
                src.startsWith("*/", i) -> { depth--; i += 2 }
                else -> { if (src[i] == '\n') line++; i++ }
            }
        }
    }

    // Reads a string body from i (after the opening quote(s)); static parts go to the result, nested ones to [out].
    fun readString(raw: Boolean): String {
        val startLine = line
        val sb = StringBuilder()
        val nested = mutableListOf<Pair<Int, String>>()
        while (i < src.length) {
            val c = src[i]
            when {
                raw && src.startsWith("\"\"\"", i) -> {
                    i += 3
                    while (i < src.length && src[i] == '"') { sb.append('"'); i++ } // closing run: extra quotes belong to the text
                    break
                }
                !raw && c == '"' -> { i++; break }
                !raw && c == '\\' -> { sb.append(src[i + 1]); i += 2 }
                c == '$' && i + 1 < src.length && src[i + 1] == '{' -> {
                    i += 2
                    var depth = 1
                    while (i < src.length && depth > 0) {
                        when {
                            src[i] == '"' -> {
                                val raw2 = src.startsWith("\"\"\"", i)
                                i += if (raw2) 3 else 1
                                val l = line
                                nested += l to readString(raw2)
                            }
                            src[i] == '{' -> { depth++; i++ }
                            src[i] == '}' -> { depth--; i++ }
                            else -> { if (src[i] == '\n') line++; i++ }
                        }
                    }
                }
                else -> { if (c == '\n') line++; sb.append(c); i++ }
            }
        }
        // The nested literals of the template expressions are reported before the string that holds them.
        out += nested
        return sb.toString()
    }

    while (i < src.length) {
        when {
            src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') i++
            src.startsWith("/*", i) -> skipBlock()
            src.startsWith("\"\"\"", i) -> { val l = line; i += 3; out += l to readString(true) }
            src[i] == '"' -> { val l = line; i++; out += l to readString(false) }
            src[i] == '\'' -> {
                // Char literal: 'x', '\n', 'A', '"'.
                i++
                if (i < src.length && src[i] == '\\') i += 2
                while (i < src.length && src[i] != '\'') i++
                i++
            }
            else -> { if (src[i] == '\n') line++; i++ }
        }
    }
    return out
}
