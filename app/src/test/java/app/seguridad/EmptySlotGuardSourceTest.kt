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

    /** The text with comments blanked out (same length, so indexes stay), so a KDoc never counts as a call. */
    private fun code(f: File): String = f.readText().lines().joinToString("\n") { l ->
        val t = l.trimStart()
        if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) " ".repeat(l.length) else l
    }

    private val call = Regex("""\.(create|importReplace)\(""")
    private val topFun = Regex("""(?m)^(?:private |internal )?fun """)

    @Test fun onlyTheTwoScreensCallTheStoreOnASlotAndEachAsksTheGuardFirst() {
        val allowed = setOf("ui/screens/VaultScreen.kt", "ui/screens/VaultBackup.kt")
        var found = 0
        ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val name = "ui/" + f.relativeTo(ui).invariantSeparatorsPath
            val src = code(f)
            for (m in call.findAll(src)) {
                found++
                assertTrue("$name calls the store's ${m.groupValues[1]} (only VaultScreen.kt and VaultBackup.kt may)", name in allowed)
                val start = topFun.findAll(src).map { it.range.first }.lastOrNull { it < m.range.first }
                assertTrue("$name: a call outside any function", start != null)
                val guard = src.lastIndexOf("!guard()", m.range.first)
                assertTrue("$name: ${m.groupValues[1]} without !guard() before it in the same function", guard > start!!)
            }
            // The refusing check is only where the store is called; elsewhere guard() only picks a text (HealthScreen).
            if ("!guard()" in src) assertTrue("$name refuses with the guard", name in allowed)
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
        val providers = ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.sumOf { Regex("""LocalEmptySlotGuard provides""").findAll(code(it)).count() }
        assertEquals(1, providers)
    }
}
