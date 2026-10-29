package app.seguridad.tools.share

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShareTempTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_000_000L
    private val temp by lazy { ShareTemp(tmp.root, { clock }) }
    private val dir get() = File(tmp.root, SHARE_DIR)

    private fun names() = dir.listFiles()?.map { it.name }?.sorted().orEmpty()

    @Test fun onlyOneFileAtATime() {
        val first = temp.prepare("copia-de-foto.jpg", byteArrayOf(1, 2, 3))
        assertEquals(File(dir, "copia-de-foto.jpg"), first)
        val second = temp.prepare("imagen-tachada.png", byteArrayOf(4, 5))
        assertEquals(listOf("imagen-tachada.png"), names())
        assertArrayEquals(byteArrayOf(4, 5), second.readBytes())
        // Same name again: replaced, still one file.
        temp.prepare("imagen-tachada.png", byteArrayOf(6))
        assertEquals(listOf("imagen-tachada.png"), names())
        assertArrayEquals(byteArrayOf(6), File(dir, "imagen-tachada.png").readBytes())
    }

    @Test fun prepareAlsoWipesLeftoversItDidNotWrite() {
        dir.mkdirs()
        File(dir, "otro.bin").writeText("x")
        File(dir, "sub").mkdirs()
        File(File(dir, "sub"), "y").writeText("y")
        temp.prepare("copia-de-foto.webp", byteArrayOf(9))
        assertEquals(listOf("copia-de-foto.webp"), names())
    }

    @Test fun discardRemovesOnlyThePreparedFile() {
        val outside = tmp.newFile("copia-de-foto.jpg")
        val file = temp.prepare("copia-de-foto.jpg", byteArrayOf(1))
        temp.discard(outside) // Not in compartir/: left alone.
        assertTrue(outside.exists())
        assertEquals(listOf("copia-de-foto.jpg"), names())
        temp.discard(file)
        assertEquals(emptyList<String>(), names())
        temp.discard(file) // Already gone: never fails.
    }

    @Test fun discardOfAnEarlierCopyLeavesTheNewerOne() {
        // Same fixed name twice (two shares of «Tachar»): a late discard of the first must not touch the second,
        // which the receiving app may still be reading.
        val first = temp.prepare("imagen-tachada.png", byteArrayOf(1))
        clock += 60_000L
        val second = temp.prepare("imagen-tachada.png", byteArrayOf(2))
        assertEquals(first, second) // the same path
        temp.discard(first)
        assertEquals(listOf("imagen-tachada.png"), names())
        assertArrayEquals(byteArrayOf(2), second.readBytes())
        // Its time is kept too: back within 10 minutes of the second prepare, it stays.
        clock += 9 * 60_000L
        temp.onReturn()
        assertEquals(listOf("imagen-tachada.png"), names())
        // The current one can still be discarded, and only then is it gone.
        temp.discard(second)
        assertTrue(names().isEmpty())
    }

    @Test fun returnBeforeTenMinutesKeepsIt() {
        temp.prepare("copia-de-foto.png", byteArrayOf(1))
        clock += 9 * 60_000L + 59_000L
        temp.onReturn()
        assertEquals(listOf("copia-de-foto.png"), names())
    }

    @Test fun returnAfterTenMinutesWipes() {
        temp.prepare("copia-de-foto.png", byteArrayOf(1))
        clock += SHARE_KEEP_MS
        temp.onReturn()
        assertTrue(names().isEmpty())
        // A later return with nothing prepared does nothing and does not fail.
        clock += SHARE_KEEP_MS
        temp.onReturn()
        assertTrue(names().isEmpty())
    }

    @Test fun returnWithNothingPreparedWipesLeftoversAndNeverFails() {
        // No folder at all: no crash.
        temp.onReturn()
        // Something left by an earlier process (the start wipe failed or has not run yet): stale, so it goes.
        dir.mkdirs()
        File(dir, "foto-limpia.jpg").writeText("de antes") // the name an earlier build gave the copy: nothing is special about it, whatever is in compartir/ goes
        temp.onReturn()
        assertTrue(names().isEmpty())
    }

    @Test fun clockGoingBackwardsWipes() {
        temp.prepare("copia-de-foto.jpg", byteArrayOf(1))
        clock -= 1
        temp.onReturn()
        assertTrue(names().isEmpty())
    }

    @Test fun wipeAllEmptiesTheFolder() {
        temp.prepare("imagen-tachada.jpg", byteArrayOf(1))
        File(dir, "sub").mkdirs()
        File(File(dir, "sub"), "y").writeText("y")
        temp.wipeAll()
        assertTrue(names().isEmpty())
        // Nothing to wipe: no crash.
        temp.wipeAll()
        File(tmp.root, "otra-cosa.tmp").writeText("fuera de compartir/")
        temp.wipeAll()
        assertTrue(File(tmp.root, "otra-cosa.tmp").exists())
    }

    @Test fun rejectsOtherNames() {
        val bad = listOf(
            "../x", "IMG_2026.jpg", "copia-de-foto.exe", "copia-de-foto.jpeg", "foto-limpia.jpg", "imagen-tachada.webp", "copia-de-foto.JPG",
            "copia-de-foto.jpg/", "/copia-de-foto.jpg", "sub/copia-de-foto.jpg", "../copia-de-foto.jpg", "copia-de-foto.jpg\u0000",
            "", "copia-de-foto", ".jpg",
        )
        for (name in bad) {
            try {
                temp.prepare(name, byteArrayOf(1))
                fail("accepted «$name»")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
        assertTrue(names().isEmpty())
        assertFalse(File(tmp.root, "x").exists())
    }

    @Test fun acceptsTheFixedNames() {
        for (name in listOf("copia-de-foto.jpg", "copia-de-foto.png", "copia-de-foto.webp", "imagen-tachada.jpg", "imagen-tachada.png")) {
            temp.prepare(name, byteArrayOf(1))
            assertEquals(listOf(name), names())
        }
    }

    @Test fun constants() {
        assertEquals("compartir", SHARE_DIR)
        assertEquals(600_000L, SHARE_KEEP_MS)
    }
}
