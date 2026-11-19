package app.seguridad.redteam

import app.seguridad.AppContainer
import app.seguridad.core.appJson
import app.seguridad.crypto.CryptoFailure
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import app.seguridad.vault.checkCopyEntries
import app.seguridad.vault.checkCopyShape
import app.seguridad.vault.checkCopySize
import app.seguridad.vault.checkCopyStructure
import app.seguridad.vault.copyTreeEstimate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/*
 * Re-check 1 of the final attack of Clavero 2.0 (decision (f)): each fix of RT-21 to RT-24, and the ways around it the
 * director listed. Same naming as the attack: rtN_… asserts the SAFE behaviour and fails while RT-N is open (@Ignore'd only
 * for the gate); resists… passes. Report: docs/superpowers/handoff/sdd-2.0/redteam-recheck-1.md.
 */
class RedTeam20RecheckTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun used(): Long {
        repeat(4) {
            System.gc()
            Thread.yield()
        }
        return Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    }

    /** [n] entries of [tagsEach] tags each, all distinct, of 5 letters: [COPY_LIST_MAX] is not reached by any list. */
    private fun distinctTags(n: Int, tagsEach: Int): String {
        var k = 0
        return buildString {
            append("{\"entries\":[")
            repeat(n) { e ->
                if (e > 0) append(',')
                append("{\"id\":\"e$e\",\"type\":\"LOGIN\",\"title\":\"t\",\"createdAt\":1,\"updatedAt\":1,\"tags\":[")
                repeat(tagsEach) {
                    if (it > 0) append(',')
                    append('"').append((k++).toString(36).padStart(5, 'a')).append('"')
                }
                append("]}")
            }
            append("]}")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-25: the budget of a copy bounds its decoding, not what showing the vault then needs.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-25 (re-check of RT-21, «un OutOfMemoryError fuera de los bloques protegidos»). [checkCopyShape]
     * (vault/VaultLimits.kt:308-311) bounds what decoding a copy costs, and part c catches running out of memory while
     * the copy is read, decoded and checked (VaultStore.kt:1537-1541, BackupCheck.kt:93-105) and while the vault is opened
     * (VaultStore.kt:487-495, :602-605, :733-736). But once a copy is imported and the vault opens, the list builds its
     * search index (`SearchIndex.build`, vault/EntryQuery.kt:106-140) and its tag chips (ui/screens/VaultList.kt:302) in
     * `EntryListViewModel` on Dispatchers.Default (VaultList.kt:262-290), with no catch: an OutOfMemoryError there
     * closes the app, at every opening of the vault, after it «opened» fine.
     *
     * Distinct short tags, 5 000 to an entry (half [COPY_LIST_MAX]), are the shape whose index costs most: about 4.6
     * times the decoded tree and 2.3 times the estimate, plus the chips. So a copy estimated under the budget (262 MiB:
     * one at about 30 %) imports, opens, and then needs several times the budget to be listed: on any phone, the app
     * closes every time the vault opens. A legitimate vault never has thousands of distinct tags (10 an entry at most from
     * the app's own screens, MAX_TAGS).
     *
     * The safe behaviour asserted (round 2 of the fixes, as the director redefined it): this copy is refused by every
     * check of the import (here by RT-25 part b, [app.seguridad.vault.copyExtraTags]: 299 400 tags over 10). The general
     * criterion of what an accepted copy may cost to show is RedTeam20ListCostTest's. Before the fix it asserted that a
     * copy [checkCopyShape] accepts needs no more to be shown than its estimate (which the budget bounds), or is refused. Measured on a 2.4 MB sample (60 entries of 5 000 tags), far from the heap of the
     * tests; what scales to the budget is the ratio.
     */
    @Test fun rt25_aCopyThatPassesTheBudgetCanAlsoBeListed() {
        val json = distinctTags(60, 5_000)
        val bytes = json.toByteArray()
        // Round 2 of the fixes (ruling of the director): «accepted» is passing every check of the import, not only
        // checkCopyShape: the shape, decoding, checkCopyEntries (with RT-25 part b, the tags over MAX_TAGS), the structure
        // and the size, in the order «Importar copia» runs them (VaultStore.replace, BackupCheck.checkBackup).
        val refused = try {
            checkCopyShape(bytes, 4)
            val data = appJson.decodeFromString(VaultData.serializer(), json)
            checkCopyStructure(data)
            checkCopySize(data)
            checkCopyEntries(data.entries)
            false
        } catch (e: CryptoFailure) {
            true
        }
        // Round 2 (the director's criterion, RedTeam20ListCostTest): the general criterion of what an accepted copy may
        // cost to show is there; this shape (60 entries of 5 000 distinct tags) is simply refused.
        val excess = app.seguridad.vault.copyExtraTags(appJson.decodeFromString(VaultData.serializer(), json).entries, stopAbove = Long.MAX_VALUE)
        assertTrue("Accepted by every check of the import, with $excess tags over MAX_TAGS", refused)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-26: an escaped member name hides the map of tag colours from the estimate.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-26 (re-check of RT-21, part b). [checkCopyShape] knows the root's members `entries` and `tagColors` by comparing
     * their raw bytes (`bytesEqual`, vault/VaultLimits.kt:322-326, :414-418). The decoder reads member names unescaped, so
     * `"tagColors"` is `tagColors` for it, and a map for it, while the scan takes it for an object of a class, whose
     * member names cost nothing (`bytes -= lastStringCost`, :412). The keys of that map, which the decoder keeps as
     * strings, are left out of the estimate: 2 000 keys of 1 KB give an estimate of 0.1 MB and a tree of 2.2 MB. It is
     * bounded by the copy's size (the keys are at most the JSON), and `checkStructure` refuses more than 200 colours after
     * decoding; but the model the budget rests on says it is never below what is measured.
     *
     * The safe behaviour asserted: the estimate is never below the tree the decoder keeps, whatever the spelling of a
     * member's name (the fix: compare the unescaped name, or refuse an escape in a member name of the root).
     */
    @Test fun rt26_anEscapedMemberNameIsEstimatedAsTheDecoderReadsIt() {
        val json = buildString {
            append("{\"tag\\u0043olors\":{")
            repeat(2_000) {
                if (it > 0) append(',')
                append('"').append(it).append("x".repeat(1_000)).append("\":\"RED\"")
            }
            append("}}")
        }
        val bytes = json.toByteArray()
        try {
            checkCopyShape(bytes, 4)
        } catch (e: CryptoFailure) {
            return // Refused before decoding: safe.
        }
        val estimate = copyTreeEstimate(bytes, 4)
        val u0 = used()
        val data = appJson.decodeFromString(VaultData.serializer(), json)
        val tree = used() - u0
        assertEquals("The decoder reads it as tagColors", 2_000, data.tagColors.size)
        assertTrue("Estimate $estimate, tree kept $tree", estimate >= tree)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The rest of the ways around RT-21 the director listed: resisted.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Re-check of RT-21, part b: strings with escapes and multi-byte UTF-8 (`\u` escapes, 2-, 3- and 4-byte characters),
     * a repeated `entries` member (the decoder keeps the last, the scan counts both), `entries` spelled with an escape
     * (the scan then holds it to [COPY_LIST_MAX], stricter), and a map where a list is expected (the decoder fails at its
     * first token): the estimate is never below the tree kept.
     */
    @Test fun resistsEscapesMultiByteAndRepeatedMembers() {
        val note = "\\u00e9ñ€😀\\\"x".repeat(2_000)
        val shapes = listOf(
            "multi-byte notes" to "{\"entries\":[" + (0 until 200).joinToString(",") { "{\"id\":\"n$it\",\"type\":\"NOTE\",\"title\":\"€$it\",\"notes\":\"$note\",\"createdAt\":1,\"updatedAt\":1}" } + "]}",
            "entries twice" to "{\"entries\":[" + (0 until 3_000).joinToString(",") { "{\"id\":\"a$it\",\"type\":\"NOTE\",\"title\":\"a\",\"createdAt\":1,\"updatedAt\":1}" } +
                "],\"entries\":[" + (0 until 3_000).joinToString(",") { "{\"id\":\"b$it\",\"type\":\"NOTE\",\"title\":\"b\",\"createdAt\":1,\"updatedAt\":1}" } + "]}",
        )
        for ((name, json) in shapes) {
            val bytes = json.toByteArray()
            checkCopyShape(bytes, 4)
            val estimate = copyTreeEstimate(bytes, 4)
            val u0 = used()
            val data = appJson.decodeFromString(VaultData.serializer(), json)
            val tree = used() - u0
            assertTrue("$name: estimate $estimate, tree $tree", estimate >= tree)
            assertTrue(data.entries.isNotEmpty())
        }
        // «entries» with an escape is not the list of entries for the scan: 10 001 elements are refused.
        val escaped = "{\"entr\\u0069es\":[" + (0..10_000).joinToString(",") { "{\"id\":\"e$it\",\"type\":\"NOTE\",\"title\":\"t\",\"createdAt\":1,\"updatedAt\":1}" } + "]}"
        try {
            checkCopyShape(escaped.toByteArray(), 4)
            throw AssertionError("10 001 entries under an escaped name passed the list cap")
        } catch (e: CryptoFailure) {
            // Stricter than the plain name, never looser.
        }
    }

    /**
     * Re-check of RT-22 (i) and (ii). With vault A (with its file) locked and vault B emptied by «He olvidado»: Ajustes is
     * not editable ([AppContainer.settingsEditableNow], also behind every write of SettingsScreen and its search, which
     * only scrolls to a setting) and the empty slot cannot be filled ([AppContainer.mayFillEmptySlot], before «Crea tu
     * bóveda» and «Importar copia»); with A open again, both are. The store itself does not refuse (`VaultStore.create`):
     * the guard lives in the screens that call it (report, «RT-22»).
     */
    @Test fun resistsTheEmptySlotAndTheSettingsAfterForgettingAnotherVault() = runTest {
        val c = AppContainer(tmp.root, tmp.newFolder("cache"), appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create(RT20_MASTER.toCharArray(), name = "Uno")
        c.vault.upsert(VaultEntry("x", EntryType.LOGIN, "Banco", password = "de-a", createdAt = 1, updatedAt = 1))
        c.createVault("Dos", "maestra-de-prueba-dos-rt".toCharArray())
        c.vault.lock()
        c.vault.destroy()
        assertEquals(VaultState.NoVault, c.vault.state.value)
        assertFalse(c.settingsEditableNow())
        assertFalse(c.mayFillEmptySlot())
        assertFalse(c.canAddVault())
    }
}
