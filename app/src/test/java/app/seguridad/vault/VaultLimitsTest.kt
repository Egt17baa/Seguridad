package app.seguridad.vault

import app.seguridad.core.appJson
import app.seguridad.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 2.0, tarea 1: los topes del formato v4 (decisión (a) de la 1.4.0) y el recorte del historial. */
class VaultLimitsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun entry(id: String, notes: String = "", history: List<EntryVersion> = emptyList(), fields: List<CustomField> = emptyList()) =
        VaultEntry(id, EntryType.NOTE, "Entrada $id", notes = notes, createdAt = 0, updatedAt = 0, history = history, fields = fields)

    private fun versions(of: String, n: Int, from: Long, size: Int = 10) =
        (0 until n).map { EntryVersion(from + it, entry(of, notes = "v$it ".padEnd(size, 'x'))) }

    private fun bytes(data: VaultData) = appJson.encodeToString(VaultData.serializer(), data).toByteArray(Charsets.UTF_8).size.toLong()
    private fun historyBytes(e: VaultEntry) = appJson.encodeToString(e.history).toByteArray(Charsets.UTF_8).size

    @Test fun theLimitsAreTheDesignOnes() {
        assertEquals(64 * 1024, VaultLimits.ENTRY_MAX_JSON_BYTES)
        assertEquals(30, VaultLimits.FIELDS_MAX)
        assertEquals(100, VaultLimits.FIELD_NAME_MAX)
        assertEquals(10_000, VaultLimits.FIELD_VALUE_MAX)
        assertEquals(20, VaultLimits.HISTORY_MAX_VERSIONS)
        assertEquals(64 * 1024, VaultLimits.HISTORY_MAX_BYTES)
        assertEquals(24 * 1024 * 1024, VaultLimits.VAULT_MAX_DATA_BYTES)
        assertEquals(500, VaultLimits.FOLDERS_MAX)
        assertEquals(5, VaultLimits.FOLDER_DEPTH_MAX)
        assertEquals(50, VaultLimits.VIEWS_MAX)
        assertEquals(200, VaultLimits.TAG_COLORS_MAX)
        assertEquals(10, VaultLimits.VAULTS_MAX)
        assertEquals(3, VaultLimits.BACKUP_TARGETS_MAX)
        assertEquals(30, VaultLimits.TRASH_DAYS)
    }

    @Test fun fittedTrimsOldestHistoryFirst() {
        // Per entry: at most 20 versions, the newest kept (the list is newest first, as passwordHistory).
        val many = entry("a", history = versions("a", 25, from = 1_000).reversed())
        val fitted = VaultData(entries = listOf(many)).fitted()
        assertEquals((1_024L downTo 1_005L).toList(), fitted.entries[0].history.map { it.savedAt })
        // Per entry: at most 64 KiB of history, the oldest go first.
        val heavy = entry("b", history = versions("b", 10, from = 2_000, size = 10_000).reversed())
        val light = VaultData(entries = listOf(heavy)).fitted().entries[0]
        assertTrue(historyBytes(light) <= VaultLimits.HISTORY_MAX_BYTES)
        assertEquals((2_009L downTo 2_004L).toList(), light.history.map { it.savedAt })
        // The whole vault: over 24 MiB, the oldest history of the vault goes first, whichever entry holds it.
        val big = (0 until 400).map { i -> entry("e$i", history = versions("e$i", 6, from = i * 10L, size = 10_600).reversed()) }
        val data = VaultData(entries = big)
        assertTrue(bytes(data) > VaultLimits.VAULT_MAX_DATA_BYTES)
        val trimmed = data.fitted()
        assertTrue(bytes(trimmed) <= VaultLimits.VAULT_MAX_DATA_BYTES)
        val kept = trimmed.entries.flatMap { it.history }.map { it.savedAt }
        val dropped = big.flatMap { it.history }.map { it.savedAt } - kept.toSet()
        assertTrue(dropped.isNotEmpty())
        assertTrue("only the oldest go", dropped.max() < kept.min())
        assertEquals(big.map { it.copy(history = emptyList()) }, trimmed.entries.map { it.copy(history = emptyList()) })
        // What already fits is the same object.
        val small = VaultData(entries = listOf(entry("s")))
        assertSame(small, small.fitted())
        small.checkLimits()
    }

    @Test fun checkLimitsRejectsTooManyFields() {
        fun fields(n: Int) = (1..n).map { CustomField("f$it", "Campo $it", FieldKind.TEXT, "valor") }
        VaultData(entries = listOf(entry("a", fields = fields(30)))).checkLimits()
        val e = assertThrows(VaultLimitExceeded::class.java) { VaultData(entries = listOf(entry("a", fields = fields(31)))).checkLimits() }
        assertEquals(TOO_MANY_FIELDS, e.message)
        assertTrue(e is IllegalStateException)
        // The name and the value of a field, in code points.
        val longName = CustomField("f", "n".repeat(101), FieldKind.TEXT, "")
        assertEquals(FIELD_NAME_TOO_LONG, assertThrows(VaultLimitExceeded::class.java) { VaultData(entries = listOf(entry("a", fields = listOf(longName)))).checkLimits() }.message)
        val longValue = CustomField("f", "n", FieldKind.HIDDEN, "😀".repeat(10_001))
        assertEquals(FIELD_VALUE_TOO_LONG, assertThrows(VaultLimitExceeded::class.java) { VaultData(entries = listOf(entry("a", fields = listOf(longValue)))).checkLimits() }.message)
        VaultData(entries = listOf(entry("a", fields = listOf(longValue.copy(value = "😀".repeat(10_000)))))).checkLimits()
        // An entry over 64 KiB of JSON, without its history.
        assertEquals(ENTRY_TOO_BIG, assertThrows(VaultLimitExceeded::class.java) { VaultData(entries = listOf(entry("a", notes = "x".repeat(70_000)))).checkLimits() }.message)
    }

    @Test fun checkLimitsRejectsDeepFolders() {
        fun chain(n: Int) = (1..n).map { Folder("f$it", "Carpeta $it", parentId = if (it == 1) null else "f${it - 1}") }
        VaultData(folders = chain(5)).checkLimits()
        assertEquals(FOLDERS_TOO_DEEP, assertThrows(VaultLimitExceeded::class.java) { VaultData(folders = chain(6)).checkLimits() }.message)
        val wide = (1..501).map { Folder("f$it", "Carpeta $it") }
        assertEquals(TOO_MANY_FOLDERS, assertThrows(VaultLimitExceeded::class.java) { VaultData(folders = wide).checkLimits() }.message)
        val views = (1..51).map { SavedView("v$it", "Vista $it", EntryFilter()) }
        assertEquals(TOO_MANY_VIEWS, assertThrows(VaultLimitExceeded::class.java) { VaultData(views = views).checkLimits() }.message)
        val colors = (1..201).associate { "t$it" to TagColor.entries[it % TagColor.entries.size] }
        assertEquals(TOO_MANY_TAG_COLORS, assertThrows(VaultLimitExceeded::class.java) { VaultData(tagColors = colors).checkLimits() }.message)
        assertEquals(8, TagColor.entries.size)
    }

    @Test fun checkLimitsRejectsCycles() {
        val self = listOf(Folder("a", "A", parentId = "a"))
        assertEquals(FOLDER_CYCLE, assertThrows(VaultLimitExceeded::class.java) { VaultData(folders = self).checkLimits() }.message)
        val two = listOf(Folder("a", "A", parentId = "b"), Folder("b", "B", parentId = "a"))
        assertEquals(FOLDER_CYCLE, assertThrows(VaultLimitExceeded::class.java) { VaultData(folders = two).checkLimits() }.message)
        val loopUnderARoot = listOf(Folder("r", "R"), Folder("a", "A", parentId = "c"), Folder("b", "B", parentId = "a"), Folder("c", "C", parentId = "b"))
        assertEquals(FOLDER_CYCLE, assertThrows(VaultLimitExceeded::class.java) { VaultData(folders = loopUnderARoot).checkLimits() }.message)
    }

    @Test fun vaultOverLimitIsRefusedWithText() = runTest {
        val s = VaultStore(File(tmp.root, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { 1_800_000_000_000L }, kdf = { KdfParams(1024, 1, 1) })
        s.load()
        s.create("una-maestra-de-prueba".toCharArray())
        s.upsert(entry("primera", notes = "hola"))
        val before = File(tmp.root, "vault.bin").readBytes()
        // 25 MiB of entries, each under its own limit, and no history to trim.
        val big = (0 until 410).map { entry("e$it", notes = "x".repeat(64_000)) }
        val e = assertThrows(VaultLimitExceeded::class.java) { runBlocking { s.addAll(big) } }
        assertEquals(VAULT_TOO_BIG, e.message)
        assertArrayEquals(before, File(tmp.root, "vault.bin").readBytes())
        assertEquals(listOf("primera"), (s.state.value as VaultState.Unlocked).entries.map { it.id })
        // One entry over its own limit is refused too, and nothing is saved.
        assertThrows(VaultLimitExceeded::class.java) { runBlocking { s.upsert(entry("grande", notes = "x".repeat(70_000))) } }
        assertArrayEquals(before, File(tmp.root, "vault.bin").readBytes())
    }
}
