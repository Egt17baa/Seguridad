package app.seguridad.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** [writeTextAtomically] and [writeBytesAtomically]: the old file or the new one, and (2.1.2, R-18) no `.tmp` left behind. */
class AtomicFileTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writesAndReplaces() {
        val f = File(tmp.root, "ajustes.json")
        f.writeTextAtomically("uno")
        f.writeTextAtomically("dos")
        assertEquals("dos", f.readText())
        assertFalse(File("${f.path}.tmp").exists())
    }

    /**
     * 2.1.2, R-18: the move over the old file fails (here the destination is a folder with something inside, which no move
     * replaces; works as root too): the error is thrown as it was, and the temporary file with the new data is gone.
     */
    @Test fun failedAtomicWriteLeavesNoTmp() {
        val dest = File(tmp.root, "vault.bin").apply { mkdirs() }
        File(dest, "dentro").writeText("x")
        for (write in listOf<(File) -> Unit>({ it.writeTextAtomically("datos nuevos") }, { it.writeBytesAtomically(byteArrayOf(1, 2, 3)) })) {
            try {
                write(dest)
                fail("The move must fail")
            } catch (e: IOException) {
                // Thrown again, as before.
            }
            assertFalse("No temporary file left", File("${dest.path}.tmp").exists())
            assertTrue(File(dest, "dentro").exists())
        }
    }

    /** A `.tmp` that is not a file this write made (a folder) is never deleted: the write just fails, as before. */
    @Test fun aFolderInTheTmpPlaceIsLeftAlone() {
        val f = File(tmp.root, "vault.bin").apply { writeBytes(byteArrayOf(9)) }
        val blocker = File("${f.path}.tmp").apply { mkdirs() }
        try {
            f.writeBytesAtomically(byteArrayOf(1))
            fail("The temporary file cannot be made")
        } catch (e: IOException) {
            // FileNotFoundException.
        }
        assertTrue(blocker.isDirectory)
        assertArrayEquals(byteArrayOf(9), f.readBytes())
    }
}
