package app.seguridad.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0, tarea 10: el modo sencillo solo filtra la lista de Herramientas (diseño «Ayuda y personalización»; Abogado
 * Digital, O-7: oculta herramientas, nunca avisos ni ajustes de protección). Toda ruta sigue registrada y nada más lee
 * el ajuste. Los destinos de `MissionAction` (fase B) se añadirán a [everyToolRouteStaysReachable] con la fase B.
 */
class SimpleModeTest {
    private val main: File = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/app/seguridad") }.first { it.isDirectory }

    private fun code(path: String) = File(main, path).readText().lines()
        .filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")

    @Test fun simpleShowsTheFourOfTheDesign() {
        assertEquals(
            listOf("tools/text", "tools/file", "tools/generator", "vault/health"),
            visibleTools(simple = true).map { it.route },
        )
        assertEquals(SIMPLE_TOOL_ROUTES.toSet(), visibleTools(simple = true).map { it.route }.toSet())
        assertEquals("Off, every tool", TOOLS, visibleTools(simple = false))
        assertTrue("Simple mode is a subset", TOOLS.containsAll(visibleTools(simple = true)))
        assertEquals("No route twice", TOOLS.size, TOOLS.map { it.route }.toSet().size)
    }

    /** Every tool, hidden or not, is a destination of the navigation: simple mode never removes a route. */
    @Test fun everyToolRouteStaysReachable() {
        val nav = code("ui/AppNavHost.kt")
        for (t in TOOLS) assertTrue("${t.route} is not a destination", "composable(\"${t.route}\")" in nav)
        for (word in listOf("simpleMode", "simpleOn", "visibleTools", "SIMPLE_TOOL")) {
            assertFalse("The navigation must not read simple mode ($word)", word in nav)
        }
    }

    /**
     * Only the Herramientas list (and the switch in Ajustes, its search entry and where it is kept) reads simple mode:
     * no notice («Antes de empezar», the new name, the new look, the upgrade, the copies, A6, the clipboard) and no setting
     * of protection can depend on it (O-7).
     */
    @Test fun onlyTheToolsListReadsIt() {
        val allowed = setOf(
            "settings/Settings.kt", "settings/SettingsStore.kt", "settings/SettingsSearch.kt",
            "tools/ToolCatalog.kt", "ui/screens/ToolsScreen.kt", "ui/screens/SettingsScreen.kt",
        )
        val readers = main.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> Regex("""\b(simpleMode|simpleOn|visibleTools)\b""").containsMatchIn(f.readText()) }
            .map { it.relativeTo(main).invariantSeparatorsPath }.toSet()
        assertTrue("Readers of simple mode: ${readers - allowed}", (readers - allowed).isEmpty())
        val settings = code("ui/screens/SettingsScreen.kt").lines().filterNot { it.startsWith("import ") }.joinToString("\n")
        assertEquals("Ajustes only reads it for its own switch", 2, Regex("""\bsimpleOn\b|\bsimpleMode\b""").findAll(settings).count())
        val tools = code("ui/screens/ToolsScreen.kt")
        assertTrue("The list is the filtered one", "visibleTools(" in tools)
        assertFalse("Never the whole list on screen", "items(TOOLS" in tools)
        // The card about how the data is protected (a notice) is always there, outside the filter.
        assertTrue("key = \"about\"" in tools)
        assertFalse("The card is not inside the filter", Regex("""if \(simple\)[^\n]*\n[^\n]*key = "about"""").containsMatchIn(tools))
    }

    /** The screen says there are more tools, and where to show them (Ajustes, by the switch's own name). */
    @Test fun theListSaysWhereTheOthersAre() {
        assertTrue("Ajustes" in SIMPLE_MODE_MORE_TOOLS)
        assertTrue("«$SIMPLE_MODE_TITLE»" in SIMPLE_MODE_MORE_TOOLS)
        assertTrue("SIMPLE_MODE_MORE_TOOLS" in code("ui/screens/ToolsScreen.kt"))
        for (t in listOf(SIMPLE_MODE_TITLE, SIMPLE_MODE_SHORT, SIMPLE_MODE_MORE_TOOLS)) {
            assertEquals(t, emptyList<String>(), ForbiddenWords.problems(t))
        }
        // O-7, in its own words on view: it hides tools, never notices nor settings.
        assertTrue("No oculta avisos ni ajustes" in SIMPLE_MODE_SHORT)
    }
}
