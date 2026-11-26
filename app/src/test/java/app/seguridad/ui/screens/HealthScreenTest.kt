package app.seguridad.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Branch review, C m-1 (test n.º 5 of part C): «Salud» computes [app.seguridad.health.vaultHealth] once, off the main
 * thread, keyed by the data, the leaks found and the day; its missions come from that same result. Until there is one,
 * it shows neither «Sin problemas detectados.» nor any count.
 */
class HealthScreenTest {
    private val src = generateSequence(File(".").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/app/seguridad/ui/screens/HealthScreen.kt") }.first { it.isFile }.readLines()

    @Test fun vaultHealthRunsOnceOffTheMainThread() {
        // Round 2 of the fixes of the attack, point 9: through healthView (health/HealthView.kt), which calls vaultHealth.
        assertFalse(src.any { "vaultHealth(" in it && !it.trim().startsWith("//") && !it.trim().startsWith("*") })
        val calls = src.indices.filter { "healthView(" in src[it] && !src[it].trim().startsWith("//") && !src[it].trim().startsWith("*") }
        assertEquals(1, calls.size)
        for (i in calls) {
            val block = (i downTo maxOf(0, i - 4)).firstOrNull { "withContext(" in src[it] }
            assertTrue("HealthScreen.kt:${i + 1}", block != null && "withContext(Dispatchers.Default)" in src[block])
        }
        val text = src.joinToString("\n")
        assertTrue(Regex("""produceState<HealthView\?>\(null, data, breached, day, missionsOff\)""").containsMatchIn(text))
        assertFalse("remember(entries, breached, day)" in text)
    }

    @Test fun nothingIsSaidBeforeTheResult() {
        val text = src.joinToString("\n")
        assertTrue(Regex("""val items = health\?\.items""").containsMatchIn(text))
        assertTrue(Regex("""if \(items != null\) \{\s*item \{""").containsMatchIn(text))
        assertFalse("if (items.isEmpty()) Text(\"Sin problemas detectados.\")" in text.substringBefore("if (items != null)"))
    }

    /**
     * Round 2 of the fixes of the attack, point 9: out of memory, «Salud» shows LIST_NO_MEMORY and returns before its
     * LazyColumn: no mission, no count and never «Sin problemas detectados.».
     */
    @Test fun outOfMemoryShowsTheTextBeforeAnythingElse() {
        val text = src.joinToString("\n")
        val at = text.indexOf("if (health?.noMemory == true) {")
        assertTrue("the branch is there", at > 0)
        // Round 3 (authorised): the branch also stops a leak check, so it is read up to its return, not its first brace.
        val body = text.substring(at, text.indexOf("return@BackScaffold", at) + "return@BackScaffold".length)
        assertTrue(body, "Text(LIST_NO_MEMORY" in body && "LaunchedEffect(Unit) { leaks.cancel() }" in body)
        assertTrue("before the LazyColumn", at < text.indexOf("LazyColumn("))
        assertTrue("before «Sin problemas detectados.»", at < text.indexOf("Text(\"Sin problemas detectados.\")"))
        assertTrue("before the mission", at < text.indexOf("val mission = health?.missions"))
    }
}
