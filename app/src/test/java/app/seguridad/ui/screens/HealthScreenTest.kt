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
        val calls = src.indices.filter { "vaultHealth(" in src[it] && !src[it].trim().startsWith("//") && !src[it].trim().startsWith("*") }
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
}
