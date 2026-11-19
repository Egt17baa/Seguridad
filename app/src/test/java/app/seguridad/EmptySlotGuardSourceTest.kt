package app.seguridad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Round 2 of the fixes of the attack, informative 1 (re-check 1 of RT-22 (ii)): the guard of the empty slot lives in the
 * screens, not in the store, so a source test fixes it. In app/src/main/java/app/seguridad/ui/, every call to `.create(`
 * (on a VaultStore) and to `.importReplace(` is in VaultScreen.kt («Crea tu bóveda») or VaultBackup.kt («Importar
 * copia»), each with the refusing `!guard()` before it in the same function; `guard` is `LocalEmptySlotGuard.current`,
 * provided at the root as `container::mayFillEmptySlot`; its default refuses. Never empty: at least 2 calls.
 */
class EmptySlotGuardSourceTest {
    private val root: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }
    private val ui = File(root, "app/src/main/java/app/seguridad/ui")

    /**
     * The text with every comment (`//` to the end of the line, also after code; `/* … */`, nested) and the inside of
     * every string and character literal blanked out, the same length (so indexes stay) and with its line breaks: a KDoc,
     * a comment or a string never counts as a call or as the guard (M-5 of the director). The code inside a string
     * template (`${…}`) stays: a call there is code.
     */
    private fun code(f: File): String = blank(f.readText())

    private fun blank(src: String): String {
        val out = StringBuilder(src)
        fun hide(i: Int) {
            if (out[i] != '\n') out[i] = ' '
        }
        var i = 0
        // The open string templates: each holds the brace depth its `${` started at, and whether its string is raw.
        val templates = ArrayDeque<Pair<Int, Boolean>>()
        var depth = 0
        fun string(raw: Boolean) {
            // [i] is just after the opening quote(s).
            while (i < src.length) {
                if (raw && src.startsWith("\"\"\"", i)) {
                    i += 3
                    return
                }
                if (!raw && src[i] == '"') {
                    i++
                    return
                }
                if (!raw && src[i] == '\\') {
                    hide(i)
                    if (i + 1 < src.length) hide(i + 1)
                    i += 2
                    continue
                }
                if (src.startsWith("\${", i)) {
                    templates.addLast(depth to raw)
                    depth++
                    i += 2
                    return // Back to code until its closing brace.
                }
                hide(i)
                i++
            }
        }
        while (i < src.length) {
            when {
                src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') hide(i++)
                src.startsWith("/*", i) -> {
                    var nest = 0
                    while (i < src.length) {
                        if (src.startsWith("/*", i)) {
                            nest++
                            hide(i)
                            hide(i + 1)
                            i += 2
                        } else if (src.startsWith("*/", i)) {
                            nest--
                            hide(i)
                            hide(i + 1)
                            i += 2
                            if (nest == 0) break
                        } else {
                            hide(i++)
                        }
                    }
                }
                src.startsWith("\"\"\"", i) -> {
                    i += 3
                    string(raw = true)
                }
                src[i] == '"' -> {
                    i++
                    string(raw = false)
                }
                src[i] == '\'' -> {
                    // A character literal: 'x', '\x' or '\uXXXX'.
                    val end = src.indexOf('\'', i + if (src.getOrNull(i + 1) == '\\') 3 else 2)
                    if (end > i && end - i <= 7) {
                        for (k in i + 1 until end) hide(k)
                        i = end + 1
                    } else {
                        i++
                    }
                }
                src[i] == '{' -> {
                    depth++
                    i++
                }
                src[i] == '}' -> {
                    depth--
                    i++
                    val open = templates.lastOrNull()
                    if (open != null && open.first == depth) {
                        templates.removeLast()
                        string(open.second) // The rest of the string the template was in.
                    }
                }
                else -> i++
            }
        }
        return out.toString()
    }

    /** A dotted call to the store's create or importReplace: with backticks and spaces too (M-5). */
    private val call = Regex("""\.\s*`?(create|importReplace)`?\s*\(""")

    /** The refusing check, with any spaces. */
    private val refusing = Regex("""!\s*guard\s*\(\s*\)""")

    /** Any way of providing the guard: infix `provides` or `.provides(` (M-5). */
    private val providing = Regex("""LocalEmptySlotGuard\s*(?:`?provides`?\b|\.\s*`?provides`?\s*\()""")
    /**
     * The start of a function at the top of a file (column 0), whatever its visibility, its modifiers (`suspend`,
     * `inline`…) and its annotations on the same line (round 2, point 10 (d)).
     */
    private val topFun = Regex("""(?m)^(?:@[\w.]+(?:\([^)\n]*\))?\s+)*(?:(?:public|private|internal|protected|suspend|inline|tailrec|operator|infix|override)\s+)*fun\s""")

    /** A name create or importReplace followed by `(`, with backticks and spaces too (c, M-5); [bareCalls] keeps those with no dot. */
    private val named = Regex("""(?<![\w`])`?(create|importReplace)`?\s*\(""")

    /** The calls of [named] in [src] with no dot before them (inside `with(store)`, an extension…) (c). */
    private fun bareCalls(src: String) = named.findAll(src).filter { m ->
        var k = m.range.first - 1
        while (k >= 0 && src[k].isWhitespace()) k--
        k < 0 || src[k] != '.'
    }.map { it.value }.toList()

    /** A reference to either (c), with backticks and spaces too. */
    private val reference = Regex("""::\s*`?(create|importReplace)`?\b""")

    private fun uiFiles() = ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private fun nameOf(f: File) = "ui/" + f.relativeTo(ui).invariantSeparatorsPath

    @Test fun onlyTheTwoScreensCallTheStoreOnASlotAndEachAsksTheGuardFirst() {
        val allowed = setOf("ui/screens/VaultScreen.kt", "ui/screens/VaultBackup.kt")
        var found = 0
        uiFiles().forEach { f ->
            val name = nameOf(f)
            val src = code(f)
            for (m in call.findAll(src)) {
                found++
                assertTrue("$name calls the store's ${m.groupValues[1]} (only VaultScreen.kt and VaultBackup.kt may)", name in allowed)
                val start = topFun.findAll(src).map { it.range.first }.lastOrNull { it < m.range.first }
                assertTrue("$name: a call outside any function", start != null)
                val guard = refusing.findAll(src).map { it.range.first }.lastOrNull { it < m.range.first } ?: -1
                assertTrue("$name: ${m.groupValues[1]} without !guard() before it in the same function", guard > start!!)
            }
            // The refusing check is only where the store is called; elsewhere guard() only picks a text (HealthScreen).
            if (refusing.containsMatchIn(src)) assertTrue("$name refuses with the guard", name in allowed)
            if ("guard()" in src) assertTrue("$name reads LocalEmptySlotGuard", "val guard = LocalEmptySlotGuard.current" in src)
        }
        assertTrue("never empty: found $found", found >= 2)
        assertEquals("the two calls of today", 2, found)
    }

    @Test fun theGuardIsProvidedAtTheRootAndRefusesByDefault() {
        val nav = code(File(ui, "AppNavHost.kt"))
        assertEquals(1, Regex("""LocalEmptySlotGuard provides container::mayFillEmptySlot""").findAll(nav).count())
        val backup = code(File(ui, "screens/VaultBackup.kt"))
        assertTrue("the default fails closed", "val LocalEmptySlotGuard = staticCompositionLocalOf<suspend () -> Boolean> { { false } }" in backup)
        // Nowhere else is it provided (a second provider could open it).
        val providers = uiFiles().sumOf { providing.findAll(code(it)).count() }
        assertEquals(1, providers)
    }

    /**
     * Round 2 of the fixes of the attack, point 10 (the director's closure, duda 1): (a) the files that read the guard are
     * exactly VaultScreen.kt, VaultBackup.kt and HealthScreen.kt; (b) the two that only pick what to show from it start
     * with nothing (`produceState<Boolean?>(null, store) { value = guard() }`), so nothing false shows while the disk is
     * read; (c) nowhere in ui/ is the store's create or importReplace called with no dot before it, or referred to.
     */
    @Test fun theGuardIsReadOnlyByTheThreeScreensAndNoCallHides() {
        val readers = uiFiles().filter { "LocalEmptySlotGuard.current" in code(it) }.map(::nameOf).toSet()
        assertEquals(setOf("ui/screens/VaultScreen.kt", "ui/screens/VaultBackup.kt", "ui/screens/HealthScreen.kt"), readers)
        for (name in listOf("screens/HealthScreen.kt", "screens/VaultScreen.kt")) {
            val src = code(File(ui, name))
            assertEquals(name, 1, Regex("""produceState<Boolean\?>\(null, store\) \{ value = guard\(\) \}""").findAll(src).count())
            assertTrue(name, Regex("""produceState\((true|false)""").findAll(src).none { src.indexOf("guard()", it.range.first) in it.range.first..it.range.first + 80 })
        }
        for (f in uiFiles()) {
            val src = code(f)
            assertEquals("${nameOf(f)}: a call of the store with no dot before it", emptyList<String>(), bareCalls(src))
            assertEquals("${nameOf(f)}: a reference to the store's create or importReplace", emptyList<String>(), reference.findAll(src).map { it.value }.toList())
        }
    }

    /** (d) The start of a function is known whatever its modifiers and annotations. */
    @Test fun theStartOfAFunctionIsKnownWithItsModifiers() {
        for (line in listOf("fun a(", "private fun a(", "internal suspend fun a(", "public inline fun a(", "@Composable fun a(", "@Composable private fun a(", "@OptIn(X::class) internal suspend fun a(")) {
            assertTrue(line, topFun.containsMatchIn(line))
        }
        assertTrue("not a call", !topFun.containsMatchIn("    val x = fun2()"))
    }
}
