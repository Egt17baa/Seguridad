package app.seguridad.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.1.2, V-1: the document a provider gives back for the automatic copies' folder ([documentOfTree]). Only a
 * `content:` URI of the tree's own authority is taken; the app's own files (`file:`) and another provider's folder
 * (another authority it may hold a grant for) never are. Invented provider names.
 */
class BackupFolderTest {
    private val t = "content://com.example.docs/tree/primary%3ACopias"

    @Test fun documentOfTreeRefusesFile() =
        assertFalse(documentOfTree(t, "file:///data/user/0/io.github.egt17baa.seguridad/files/vault/vault.bin"))

    @Test fun documentOfTreeRefusesAnotherAuthority() =
        assertFalse(documentOfTree(t, "content://com.other/tree/x/document/y"))

    @Test fun documentOfTreeRefusesOtherSchemes() {
        for (s in listOf("http://com.example.docs/document/1", "android.resource://com.example.docs/1", "CONTENT://com.example.docs/document/1")) {
            assertFalse(s, documentOfTree(t, s))
        }
    }

    @Test fun documentOfTreeAcceptsTreeDocumentUri() =
        assertTrue(documentOfTree(t, "content://com.example.docs/tree/primary%3ACopias/document/primary%3ACopias%2Fa.sgbak.tmp"))

    @Test fun documentOfTreeAcceptsSameAuthorityDocumentUri() =
        assertTrue(documentOfTree(t, "content://com.example.docs/document/12345"))

    /** The authority is compared as [treeKey] does: a numeric `<user>@` prefix does not count, anything else does. */
    @Test fun documentOfTreeComparesAuthorityAsTreeKey() {
        assertTrue(documentOfTree(t, "content://10@com.example.docs/document/1"))
        assertTrue(documentOfTree("content://10@com.example.docs/tree/primary%3ACopias", "content://com.example.docs/document/1"))
        for (s in listOf(
            "content://com.example.docs.evil/document/1",
            "content://com.example/document/1",
            "content://Com.Example.Docs/document/1",
            "content://x@com.example.docs/document/1",
            "content://com.example.docs@com.other/document/1",
            "content://com.example.docs:1/document/1",
            "content://com.example.docs",
            "content:///document/1",
            "content:com.example.docs/document/1",
            "",
        )) {
            assertFalse(s, documentOfTree(t, s))
        }
    }

    /** A tree that is not one ([treeKey] null) takes no document at all. */
    @Test fun documentOfTreeRefusesWhenTheTreeIsNotATree() {
        assertFalse(documentOfTree("file:///storage/emulated/0/Copias", "content://com.example.docs/document/1"))
        assertFalse(documentOfTree("content://com.example.docs/document/1", "content://com.example.docs/document/1"))
    }
}
