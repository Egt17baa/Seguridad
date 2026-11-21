package app.seguridad.vault

import app.seguridad.tools.ForbiddenWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit RC-5 (D-3): deleting a folder at the top says where each thing goes, with the app's own words: its entries to
 * «Sin carpeta» ([FOLDER_NONE]) and its subfolders to «Arriba del todo» ([FOLDER_TOP]), never that they come undone.
 */
class FolderDeletionTextTest {
    @Test fun aTopFolderSaysWhereItsSubfoldersGo() {
        assertEquals("Sus entradas pasan a «Sin carpeta» y sus subcarpetas, a «Arriba del todo»", FOLDER_DELETE_TO_NONE)
        assertTrue("«$FOLDER_NONE»" in FOLDER_DELETE_TO_NONE && "«$FOLDER_TOP»" in FOLDER_DELETE_TO_NONE)
        assertEquals(emptyList<String>(), ForbiddenWords.problems(FOLDER_DELETE_TO_NONE))
    }
}
