package app.seguridad.tools.codes

import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.security.SecureRandom

class InventedAnswersTest {
    /** The asset, found from the test's working directory (jvm-check/ for the JVM check, app/ for Gradle on Android). */
    private val asset = listOf("src/main/assets", "../app/src/main/assets").map { File(it, "bip39_spanish.txt") }.first { it.exists() }
    private val list: List<String> = asset.readLines(Charsets.UTF_8).filter { it.isNotBlank() }

    @Test
    fun spanishListIsPinned() {
        // bitcoin/bips master at 5aadddb511a5dcfe4599531bd8ae5e5b96d0f793, bip-0039/spanish.txt, unchanged.
        val sha = MessageDigest.getInstance("SHA-256").digest(asset.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("46846a5a0139d1e3cb77293e521c2865f7bcdb82c44e8d0a06a2cd0ecba48c0b", sha)
        assertEquals(2048, list.size)
        assertEquals(2048, list.toSet().size)
    }

    @Test
    fun uniqueWithoutAccents() {
        val plain = list.map(::withoutAccents)
        assertEquals("Removing accents merges two words", 2048, plain.toSet().size)
        for (w in plain) assertTrue("«$w»", w.isNotEmpty() && w.all { it in 'a'..'z' })
    }

    @Test
    fun withoutAccentsMakesNAndKeepsTheRest() {
        assertEquals("nandu", withoutAccents("ñandú"))
        assertEquals("pinguino", withoutAccents("pingüino"))
        assertEquals("cancion", withoutAccents("canción"))
        assertEquals("N", withoutAccents("Ñ"))
        assertEquals("nube", withoutAccents("nube"))
        // The same result whether the accent comes composed or as a separate mark.
        assertEquals("abaco", withoutAccents("ábaco"))
        assertEquals("abaco", withoutAccents("ábaco"))
    }

    @Test
    fun exampleWordsAreInTheList() {
        for (w in listOf("nube", "tigre", "velero", "ancla")) assertTrue("«$w»", w in list)
    }

    @Test
    fun plainHasNoAccents() {
        repeat(500) {
            val answer = inventedAnswer(list)
            val words = answer.split(" ")
            assertEquals(4, words.size)
            assertTrue("«$answer»", answer.all { it in 'a'..'z' || it == ' ' })
            assertFalse(answer.startsWith(" ") || answer.endsWith(" "))
        }
    }

    @Test
    fun plainOrExactFormOfTheSameDraws() {
        // Draws 0, 1, 2, 3 of the list: «ábaco abdomen abeja abierto».
        fun scripted() = object : SecureRandom() {
            private var next = 0
            override fun nextInt(bound: Int): Int {
                assertEquals(2048, bound)
                return next++
            }
        }
        assertEquals("\u00e1baco abdomen abeja abierto", inventedAnswer(list, plain = false, random = scripted()))
        assertEquals(list.take(4).joinToString(" ") { withoutAccents(it) }, inventedAnswer(list, plain = true, random = scripted()))
        assertTrue(inventedAnswer(list, plain = true, random = scripted()).startsWith("abaco "))
    }

    @Test
    fun exactFormKeepsAccentsComposedAsAKeyboardTypesThem() {
        val answers = List(2000) { inventedAnswer(list, plain = false) }
        assertTrue(answers.any { a -> a.any { it !in 'a'..'z' && it != ' ' } })
        // The file stores accents as separate marks (NFD); what is shown and saved is NFC, so a service compares like with like.
        assertTrue(list.any { !Normalizer.isNormalized(it, Normalizer.Form.NFC) })
        for (a in answers) assertTrue("«$a»", Normalizer.isNormalized(a, Normalizer.Form.NFC))
        assertEquals("ñ", inventedAnswer(listOf("n\u0303"), plain = false).substringBefore(" "))
    }

    @Test
    fun noteFormat() {
        val note = inventedAnswersNote(
            site = "example.org",
            questions = listOf(
                InventedQuestion("¿Tu primera mascota?", "nube tigre velero ancla"),
                InventedQuestion("¿Ciudad donde naciste?", "abeja farol lluvia mesa"),
            ),
            now = 1_700_000_000_000,
            id = "id-7",
        )
        assertEquals(EntryType.NOTE, note.type)
        assertEquals("Respuestas de seguridad · example.org", note.title)
        assertEquals(
            "¿Tu primera mascota? → nube tigre velero ancla\n¿Ciudad donde naciste? → abeja farol lluvia mesa",
            note.notes,
        )
        assertEquals("id-7", note.id)
        assertEquals(1_700_000_000_000, note.createdAt)
        assertEquals(1_700_000_000_000, note.updatedAt)
        assertEquals("", note.password)
    }
}
