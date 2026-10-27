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
        val first = temp.prepare("foto-limpia.jpg", byteArrayOf(1, 2, 3))
        assertEquals(File(dir, "foto-limpia.jpg"), first)
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
        temp.prepare("foto-limpia.webp", byteArrayOf(9))
        assertEquals(listOf("foto-limpia.webp"), names())
    }

    @Test fun discardRemovesOnlyThePreparedFile() {
        val outside = tmp.newFile("foto-limpia.jpg")
        val file = temp.prepare("foto-limpia.jpg", byteArrayOf(1))
        temp.discard(outside) // Not in compartir/: left alone.
        assertTrue(outside.exists())
        assertEquals(listOf("foto-limpia.jpg"), names())
        temp.discard(file)
        assertEquals(emptyList<String>(), names())
        temp.discard(file) // Already gone: never fails.
    }

    @Test fun returnBeforeTenMinutesKeepsIt() {
        temp.prepare("foto-limpia.png", byteArrayOf(1))
        clock += 9 * 60_000L + 59_000L
        temp.onReturn()
        assertEquals(listOf("foto-limpia.png"), names())
    }

    @Test fun returnAfterTenMinutesWipes() {
        temp.prepare("foto-limpia.png", byteArrayOf(1))
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
        File(dir, "foto-limpia.jpg").writeText("de antes")
        temp.onReturn()
        assertTrue(names().isEmpty())
    }

    @Test fun clockGoingBackwardsWipes() {
        temp.prepare("foto-limpia.jpg", byteArrayOf(1))
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
            "../x", "IMG_2026.jpg", "foto-limpia.exe", "foto-limpia.jpeg", "imagen-tachada.webp", "foto-limpia.JPG",
            "foto-limpia.jpg/", "/foto-limpia.jpg", "sub/foto-limpia.jpg", "../foto-limpia.jpg", "foto-limpia.jpg\u0000",
            "", "foto-limpia", ".jpg",
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
        for (name in listOf("foto-limpia.jpg", "foto-limpia.png", "foto-limpia.webp", "imagen-tachada.jpg", "imagen-tachada.png")) {
            temp.prepare(name, byteArrayOf(1))
            assertEquals(listOf(name), names())
        }
    }

    @Test fun constants() {
        assertEquals("compartir", SHARE_DIR)
        assertEquals(600_000L, SHARE_KEEP_MS)
    }
}
