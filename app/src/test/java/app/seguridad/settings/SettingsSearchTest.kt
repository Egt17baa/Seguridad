package app.seguridad.settings

import app.seguridad.tools.ForbiddenWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 2.0, tarea 10: el buscador de Ajustes, sobre los textos fijos que pintan sus pantallas. */
class SettingsSearchTest {
    private val main: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/app/seguridad") }.first { it.isDirectory }

    private fun code(path: String) = File(main, path).readText().lines()
        .filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")

    @Test fun findsByTitleAndLine() {
        assertEquals(SettingItem.CLIPBOARD, SettingsSearch.find("portapapeles").first())
        assertEquals(SettingItem.KDF, SettingsSearch.find("argon2id").first())
        assertEquals(SettingItem.SIMPLE_MODE, SettingsSearch.find("Modo sencillo").first())
        // By a line or a button under the title.
        assertEquals(SettingItem.VAULT_COPY, SettingsSearch.find("exportar").first())
        assertEquals(SettingItem.VAULT_COPY, SettingsSearch.find("contraseña maestra").first())
        assertEquals(SettingItem.HISTORY, SettingsSearch.find("historial").first())
        assertEquals(SettingItem.TRANSPARENCY, SettingsSearch.find("alto contraste").first())
        assertEquals(SettingItem.PRIVACY, SettingsSearch.find("privacidad").first())
        // Accents and case do not count.
        assertEquals(SettingItem.KDF, SettingsSearch.find("COSTE").first())
        assertEquals(SettingItem.VAULT_COPY, SettingsSearch.find("boveda copia").first())
    }

    @Test fun toleratesTypo() {
        assertEquals(SettingItem.CLIPBOARD, SettingsSearch.find("portapapels").first())
        assertEquals(SettingItem.SIMPLE_MODE, SettingsSearch.find("sencilo").first())
        assertEquals(SettingItem.HISTORY, SettingsSearch.find("hsitorial").first())
        assertTrue(SettingsSearch.find("zzzzqqqq").isEmpty())
    }

    @Test fun aBlankQueryFindsNothing() {
        for (q in listOf("", " ", "\t\n", "​")) assertTrue("«$q»", SettingsSearch.find(q).isEmpty())
    }

    /** The query is cut before FuzzySearch sees it: a paste of a whole text costs no more than [SettingsSearch.QUERY_MAX]. */
    @Test fun aHugeQueryIsCut() {
        assertEquals(SettingsSearch.QUERY_MAX, SettingsSearch.cut("a".repeat(1_000_000)).length)
        val emoji = "😀".repeat(100)
        assertEquals(SettingsSearch.QUERY_MAX, SettingsSearch.cut(emoji).codePointCount(0, SettingsSearch.cut(emoji).length))
        assertEquals("Never half a pair", 0, SettingsSearch.cut(emoji).length % 2)
        assertEquals("portapapeles", SettingsSearch.cut("portapapeles"))
        val started = System.nanoTime()
        SettingsSearch.find("portapapeles " + "x".repeat(2_000_000))
        assertTrue((System.nanoTime() - started) < 2_000_000_000L)
        assertEquals(SettingItem.CLIPBOARD, SettingsSearch.find("portapapeles" + " ".repeat(SettingsSearch.QUERY_MAX) + "qqqqqqqq").first())
    }

    /**
     * Every section that Ajustes, «Aspecto» and «Acerca de» draw takes its title from the index, so a setting added
     * without its entry makes this fail; and every entry of the index is drawn (the reverse). Only the sections of each
     * folder of the copies (their title is «Carpeta N», under «Copia automática») and «Personalizado» (a palette, under
     * «Paleta») are not entries of their own.
     */
    @Test fun everySectionIsIndexed() {
        val allowed = setOf("autoBackupTargetTitle(index + 1)", "PaletteId.CUSTOM.label()")
        val screens = listOf("ui/screens/SettingsScreen.kt", "ui/screens/AppearanceScreen.kt")
        val used = HashSet<String>()
        var sections = 0
        for (path in screens) {
            val src = code(path)
            for (m in Regex("""(?<![A-Za-z_])Section\(([^{]*?)\)\s*\{""").findAll(src)) {
                val arg = m.groupValues[1].substringBefore(", anchor(").trim()
                if (arg.startsWith("title: String")) continue // The definition itself.
                sections++
                val item = Regex("""^SettingItem\.(\w+)\.title$""").find(arg)?.groupValues?.get(1)
                assertTrue("$path: Section($arg) has no entry in SettingsSearch", item != null || arg in allowed)
                item?.let { used += it }
            }
        }
        assertTrue("The scan found the sections", sections >= SettingItem.entries.size)
        assertEquals("Entries no screen draws", emptySet<String>(), SettingItem.entries.map { it.name }.toSet() - used)
        // Each entry where the screen scrolls to it is also its anchor there.
        val settings = code("ui/screens/SettingsScreen.kt")
        for (item in SettingItem.entries.filter { it.place == SettingPlace.SETTINGS }) {
            assertTrue("${item.name} has no anchor", Regex("""anchor\(SettingItem\.${item.name}\)""").containsMatchIn(settings))
        }
    }

    /** Only fixed texts: no template, nothing of a folder, a vault or an entry. */
    @Test fun indexesFixedTextsOnly() {
        val src = File(main, "settings/SettingsSearch.kt").readText()
        for (literal in Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(src)) assertFalse("A template in ${literal.value}", "$" in literal.value)
        for (word in listOf("VaultData", "VaultEntry", "VaultStore", "displayName", "SafBackupTarget", "appearanceSummary", "Folder", "plainReportText")) {
            assertFalse("The index reads $word", Regex("""\b$word\b""").containsMatchIn(src))
        }
        for (item in SettingItem.entries) {
            for (t in listOf(item.title) + item.lines) assertEquals(t, emptyList<String>(), ForbiddenWords.problems(t))
            assertTrue(item.title.isNotBlank())
        }
        assertEquals("Titles are unique", SettingItem.entries.size, SettingItem.entries.map { it.title }.toSet().size)
    }

    /**
     * «La letra sigue la escala del sistema» (R-1, letra al 200 %): nothing in the app sets its own font scale or density,
     * and the screens of this task (Ajustes, «Aspecto», Herramientas) never cut a text to a number of lines: at 200 % a
     * text wraps and the screen scrolls. The check on the phone is a row of pendientes.md.
     */
    @Test fun textFollowsTheSystemScale() {
        val ui = File(main, "ui")
        for (f in ui.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            val src = f.readText()
            assertFalse("${f.name} sets the font scale", "fontScale" in src)
            assertFalse("${f.name} sets the density", "LocalDensity provides" in src)
        }
        for (path in listOf("ui/screens/SettingsScreen.kt", "ui/screens/AppearanceScreen.kt", "ui/screens/ToolsScreen.kt")) {
            val src = code(path)
            assertFalse("$path cuts a text", "maxLines" in src || "TextOverflow" in src)
        }
        assertTrue("Ajustes scrolls", "verticalScroll(" in code("ui/screens/SettingsScreen.kt"))
        // The cards of Herramientas grow with their text: a minimum height, never a fixed one.
        val tools = code("ui/screens/ToolsScreen.kt")
        assertTrue("heightIn(min" in tools)
        assertFalse(Regex("""\.height\(""").containsMatchIn(tools))
    }
}

