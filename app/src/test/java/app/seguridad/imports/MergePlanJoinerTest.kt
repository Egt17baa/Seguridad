package app.seguridad.imports

import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.1.2, C-4: a host with U+200C (ZWNJ) or U+200D (ZWJ) is a different name for a browser, so the merge does not take
 * it as the same web as the host without it, and the duplicate's row shows the hidden character with the inspector's
 * mark («⟨U+200C⟩»). Invented data.
 */
class MergePlanJoinerTest {
    private val plainUrl = "https://\u0645\u06CC\u062E\u0648\u0627\u0647\u0645.ir/login"
    private val zwnjUrl = "https://\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645.ir/login"

    private fun login(id: String, url: String, pw: String) = VaultEntry(
        id = id, type = EntryType.LOGIN, title = "Banco", username = "ana", url = url, password = pw, createdAt = 0L, updatedAt = 0L,
    )

    private fun plan(incoming: VaultEntry, vault: VaultEntry) =
        planMerge(ImportRead(ImportFormat.BITWARDEN, listOf(ImportedItem(0, incoming, emptyList())), emptyList(), 0), listOf(vault), 1)

    @Test
    fun zwnjFileLoginIsNotADuplicate() {
        val p = plan(login("f1", zwnjUrl, "file-pw"), login("v1", plainUrl, "vault-pw"))
        assertEquals(1, p.newItems.size)
        assertTrue(p.duplicates.isEmpty())
        assertTrue(urlKey(zwnjUrl) != urlKey(plainUrl))
    }

    @Test
    fun zwjLatinFileLoginIsNotADuplicateOfBanco() {
        val p = plan(login("f1", "https://ban\u200Dco.es/", "file-pw"), login("v1", "https://banco.es/", "vault-pw"))
        assertEquals(1, p.newItems.size)
        assertTrue(p.duplicates.isEmpty())
    }

    @Test
    fun theSameZwnjHostIsStillADuplicate() {
        val p = plan(login("f1", zwnjUrl, "file-pw"), login("v1", zwnjUrl, "vault-pw"))
        assertEquals(1, p.duplicates.size)
    }

    @Test
    fun zwnjDuplicateRowMarksTheHiddenChar() {
        val row = duplicateSubline(login("f1", zwnjUrl, "file-pw"))
        assertEquals("ana · \u0645\u06CC⟨U+200C⟩\u062E\u0648\u0627\u0647\u0645.ir", row)
        assertEquals("ana · ban⟨U+200D⟩co.es", duplicateSubline(login("f2", "https://ban\u200Dco.es/", "x")))
        assertEquals("ana · http://ban⟨U+200D⟩co.es", duplicateSubline(login("f3", "http://ban\u200Dco.es/", "x")))
    }

    @Test
    fun aPlainHostRowHasNoMark() {
        val row = duplicateSubline(login("f1", "https://banco.es/", "x"))
        assertEquals("ana · banco.es", row)
        assertFalse('⟨' in row)
        // A soft hyphen is dropped by a browser: the host really is banco.es, and the row says so.
        assertEquals("ana · banco.es", duplicateSubline(login("f2", "https://ban­co.es/", "x")))
    }
}
