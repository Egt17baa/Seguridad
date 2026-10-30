package app.seguridad.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every user-visible text in app/src/main passes the forbidden-word check, not only ToolTexts.kt: the string literals
 * of every Kotlin file under `java/`, the strings of `res/values*` and any label written in `AndroidManifest.xml`.
 *
 * The scan lexes Kotlin (comments, normal and raw strings, escapes, `${}` templates, char literals) and judges each
 * chain of literals joined by `+` as one text ([kotlinLiterals]), so a word split across pieces is still caught.
 * Exclusions, all of them:
 * - comments (// and block, KDoc): never shown;
 * - the BIP-39 list, the licence texts and the other assets: they are in `assets/`, which is not scanned;
 * - the other resources (drawables, `res/xml`): they hold no text for the user;
 * - texts with no space ([isNotUi]: JSON keys, format tags such as «seguridad-vault» or «SG1:», MIME types, URLs,
 *   route names) are not counted as texts, but one that contains a forbidden word is still reported;
 * - no source file is excluded ([EXCLUDED_FILES] is empty on purpose); no code logs (CLAUDE.md), so no log message.
 *
 * Limits, on purpose: pieces are joined only when `+` stands between two literals. A word split around anything else
 * (`"segur" + x + "o"`, `"segur$o"`, `buildString`, `String.format`, a function that returns a piece) is not joined;
 * each piece is still checked on its own.
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
        val java = mainRoot()
        val files = java.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name !in EXCLUDED_FILES }.toList()
        assertTrue("Too few sources: ${files.size}", files.size > 50)
        val res = File(java.parentFile, "res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .flatMap { it.listFiles { f -> f.extension == "xml" }.orEmpty().toList() }
        assertTrue("strings.xml not found", res.any { it.name == "strings.xml" })
        val sources = files.map { it to kotlinLiterals(it.readText()) } +
            (res + File(java.parentFile, "AndroidManifest.xml")).map { it to xmlTexts(it.readText()) }
        var checked = 0
        val bad = mutableListOf<String>()
        for ((f, found) in sources) {
            for ((line, lit) in found) {
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
        // The raw string, the two chars and the last string are one chain; the template's own text comes first.
        assertEquals(listOf("uno \"dos\"", "in ner", "raw \"q\" \n yx\"t  fin \$v"), lits)
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

    private fun texts(src: String) = kotlinLiterals(src).map { it.second }
    private fun reported(src: String) = texts(src).any { ForbiddenWords.problems(it).isNotEmpty() }

    @Test
    fun aWordSplitAcrossConcatenatedLiteralsIsCaught() {
        assertEquals(listOf("No es seguro"), texts("val t = \"No es \" +\n    \"segu\" + \"ro\""))
        // Comments between the pieces do not break the chain.
        assertTrue(reported("val t = \"es segu\" + /* x */ \"ro\""))
        assertTrue(reported("val t = \"es segu\" + // x\n \"ro\""))
        // Char literals join the chain too.
        assertTrue(reported("val t = \"es segur\" + 'o'"))
        // A literal on its own line, then «+» at the start of the next.
        assertTrue(reported("val t = \"es segu\"\n    + \"ro\""))
    }

    @Test
    fun aCharLiteralInsideATemplateIsReadAndDoesNotBreakTheLexer() {
        assertEquals(listOf("es seguro"), texts("val t = \"es segur\${'o'}\""))
        // A quote or a brace as a char literal inside \${…} must not end the template or the string.
        assertEquals(listOf("a \" b"), texts("val t = \"a \${'\"'} b\""))
        assertEquals(listOf("} es seguro"), texts("val t = \"\${'}'} es seguro\""))
        // A string literal that is the whole template expression is part of the text as well.
        assertEquals(listOf("es seguro"), texts("val t = \"es segur\${\"o\"}\""))
    }

    @Test
    fun garantizaSplitAcrossPiecesIsJudgedOnTheWholeText() {
        assertTrue(reported("val t = \"Esto garan\" + \"tiza que funcione\""))
        assertTrue(reported("val t = \"Esto \" + \"garantiza que funcione\""))
        // «no garantiza» split after «no» is the allowed phrase, not a forbidden «garantiza».
        assertFalse(reported("val t = \"Eso no \" +\n \"garantiza que sea seguro\""))
    }

    @Test
    fun escapesAreDecoded() {
        assertEquals(listOf("es seguro"), texts("val t = \"es segur\\u006F\""))
        assertTrue(reported("val t = \"es segur\" + '\\u006F'"))
        assertEquals(listOf("a\nb \$c"), texts("val t = \"a\\nb \\\$c\""))
    }

    @Test
    fun aCharLiteralAloneIsNotAText() {
        assertEquals(emptyList<String>(), texts("val p = s.split(',') + 'x'"))
    }

    @Test
    fun resourceStringsAreRead() {
        val xml = "<resources>\n<string name=\"a\">Nota segura</string>\n<string name=\"b\" translatable=\"false\">" +
            "Rock &amp; roll</string>\n<plurals name=\"c\"><item quantity=\"one\">Una de verdad</item></plurals>\n</resources>"
        assertEquals(listOf("Nota segura", "Rock & roll", "Una de verdad"), xmlTexts(xml).map { it.second })
        assertEquals(listOf(2, 3, 4), xmlTexts(xml).map { it.first })
        // A label written in the manifest itself (not a @string reference) is a text too.
        assertEquals(listOf("Abrir seguro"), xmlTexts("<activity android:label=\"Abrir seguro\" android:icon=\"@x\"/>\n<a android:label=\"@string/app_name\"/>").map { it.second })
    }
}

/**
 * (line, text) of each user-visible string of a resource XML: the body of `<string>` and `<item>` (plurals, arrays;
 * a theme's `<item>` values have no space and so do not count as texts), and an `android:label` or
 * `android:description` written literally instead of as a `@string/` reference. Inner tags are dropped, the XML and
 * Android escapes decoded.
 */
internal fun xmlTexts(xml: String): List<Pair<Int, String>> {
    val body = Regex("<(string|item)\\b[^>]*>(.*?)</\\1\\s*>", RegexOption.DOT_MATCHES_ALL)
    val attr = Regex("android:(?:label|description)\\s*=\\s*\"([^\"@][^\"]*)\"")
    val found = body.findAll(xml).map { it.range.first to it.groupValues[2] } +
        attr.findAll(xml).map { it.range.first to it.groupValues[1] }
    fun lineAt(pos: Int) = 1 + xml.substring(0, pos).count { it == '\n' }
    return found.sortedBy { it.first }.map { (pos, raw) -> lineAt(pos) to decodeXml(raw) }.toList()
}

private fun decodeXml(raw: String): String = raw
    .replace(Regex("<[^>]+>"), "")
    .replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m ->
        String(Character.toChars(m.groupValues[2].toInt(if (m.groupValues[1].isEmpty()) 10 else 16)))
    }
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
    .replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("\\@", "@")
    .replace("&amp;", "&")

/**
 * (line, text) of each user-visible text of the Kotlin source [src], outside comments.
 *
 * A text is a chain of literals joined by `+`: the pieces of `"No es " + "seg" + "uro"` are judged as one, so a word
 * cannot be split across them, and «no » + «garantiza…» is read as the allowed «no garantiza». Char literals join a
 * chain ('o' after "segur"), but a chain with no string literal is not a text. Escapes (`\n`, `\u006F`, `\$`) are
 * decoded. In a `${…}` template the expression is lexed as code: when it is only literals (`${'o'}`, `${"o"}`) their
 * text is part of the string; otherwise it adds nothing to the string, and the texts inside it are reported on their
 * own, before the string that holds them. A simple `$name` stays as written.
 */
internal fun kotlinLiterals(src: String): List<Pair<Int, String>> = KotlinLexer(src).code(inTemplate = false).texts

private class KotlinLexer(private val src: String) {
    private var i = 0
    private var line = 1

    /** The texts of a stretch of code; [only] is its text when the stretch is nothing but one chain of literals. */
    class Code(val texts: List<Pair<Int, String>>, val only: String?, val onlyIsInTexts: Boolean)

    /** Lexes code from [i] to the end, or (in a template) to the `}` that closes it, which is consumed. */
    fun code(inTemplate: Boolean): Code {
        val texts = mutableListOf<Pair<Int, String>>()
        var chain: StringBuilder? = null
        var chainLine = 0
        var chainHasString = false
        var joinNext = false // a «+» right after a literal: the next literal belongs to the same chain
        var afterLiteral = false
        var depth = 0
        var chains = 0
        var otherTokens = false
        var lastText: String? = null
        var lastHadString = false

        fun flush() {
            val c = chain ?: return
            lastText = c.toString()
            lastHadString = chainHasString
            if (chainHasString) texts += chainLine to c.toString()
            chain = null
            chainHasString = false
        }
        fun other() {
            flush()
            joinNext = false
            afterLiteral = false
            otherTokens = true
        }
        fun piece(text: String, isString: Boolean, at: Int) {
            if (chain == null || !joinNext) {
                flush()
                chain = StringBuilder()
                chainLine = at
                chains++
            }
            chain!!.append(text)
            if (isString) chainHasString = true
            joinNext = false
            afterLiteral = true
        }

        while (i < src.length) {
            val c = src[i]
            when {
                c == '\n' -> { line++; i++ }
                c.isWhitespace() -> i++
                src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') i++
                src.startsWith("/*", i) -> skipBlock()
                src.startsWith("\"\"\"", i) -> { val l = line; i += 3; piece(string(raw = true, sink = texts), true, l) }
                c == '"' -> { val l = line; i++; piece(string(raw = false, sink = texts), true, l) }
                c == '\'' -> { val l = line; piece(char(), false, l) }
                c == '+' && afterLiteral && !src.startsWith("+=", i) && !src.startsWith("++", i) -> {
                    joinNext = true
                    afterLiteral = false
                    i++
                }
                c == '{' -> { other(); depth++; i++ }
                c == '}' && depth == 0 && inTemplate -> { i++; break }
                c == '}' -> { other(); depth--; i++ }
                else -> { other(); i++ }
            }
        }
        flush()
        val only = if (!otherTokens && chains == 1) lastText else null
        return Code(texts, only, only != null && lastHadString)
    }

    private fun skipBlock() {
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

    /** Reads an escape at [i] (on the backslash) and returns what it stands for. */
    private fun escape(): String {
        val e = src.getOrNull(i + 1) ?: run { i++; return "" }
        if (e == 'u' && i + 6 <= src.length) {
            val hex = src.substring(i + 2, i + 6)
            if (hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                i += 6
                return hex.toInt(16).toChar().toString()
            }
        }
        i += 2
        return when (e) {
            'n' -> "\n"
            't' -> "\t"
            'r' -> "\r"
            'b' -> "\b"
            else -> e.toString() // \" \' \\ \$
        }
    }

    /** A char literal at [i] (on the opening quote): 'x', '\n', '\u006F', '"', '}'. */
    private fun char(): String {
        i++
        val text = if (src.getOrNull(i) == '\\') escape() else src.getOrNull(i++)?.toString() ?: ""
        while (i < src.length && src[i] != '\'') i++
        i++
        return text
    }

    /** A string body from [i] (after the opening quote(s)); the texts of its templates go to [sink]. */
    private fun string(raw: Boolean, sink: MutableList<Pair<Int, String>>): String {
        val sb = StringBuilder()
        while (i < src.length) {
            val c = src[i]
            when {
                raw && src.startsWith("\"\"\"", i) -> {
                    i += 3
                    while (i < src.length && src[i] == '"') { sb.append('"'); i++ } // closing run: extra quotes belong to the text
                    break
                }
                !raw && c == '"' -> { i++; break }
                !raw && c == '\\' -> sb.append(escape())
                c == '$' && src.getOrNull(i + 1) == '{' -> {
                    i += 2
                    val inner = code(inTemplate = true)
                    if (inner.only != null) {
                        sb.append(inner.only)
                        sink += if (inner.onlyIsInTexts) inner.texts.dropLast(1) else inner.texts
                    } else {
                        sink += inner.texts
                    }
                }
                else -> { if (c == '\n') line++; sb.append(c); i++ }
            }
        }
        return sb.toString()
    }
}
