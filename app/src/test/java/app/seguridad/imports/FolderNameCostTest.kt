package app.seguridad.imports

import app.seguridad.vault.EntryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.1.2, C-3 (audit of 2.1.1): a folder or group name of millions of characters in a JSON export (Aegis, Bitwarden,
 * 2FAS) made the plan of «Importar de otra app» cost the name's length times the number of items, and «Cancelar» did
 * not stop it. Now [folderNames] drops a name longer than [MAX_TITLE] (the item enters without that folder),
 * [importTags] is worked out once per list of folders in each plan or merge, a repeated item is counted before its
 * size is measured, and the plan stops when its coroutine is cancelled. Made-up data only.
 */
class FolderNameCostTest {
    /** A made-up 2FA secret for element [i], a different one each (equal secrets would make the items duplicates). */
    private fun secret(i: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val tail = (0 until 4).map { alphabet[(i shr (5 * it)) and 31] }.joinToString("")
        return "JBSWY3DPEHPK$tail"
    }

    /** An Aegis export in the clear with one group named [name], shared by [entries] 2FA entries. */
    private fun aegis(name: String, entries: Int): ByteArray {
        val sb = StringBuilder()
        sb.append("{\"version\":1,\"header\":{\"slots\":null,\"params\":null},\"db\":{\"version\":3,\"groups\":[{\"uuid\":\"g\",\"name\":\"")
        sb.append(name)
        sb.append("\"}],\"entries\":[")
        for (i in 0 until entries) {
            if (i > 0) sb.append(',')
            sb.append("{\"type\":\"totp\",\"issuer\":\"Servicio $i\",\"groups\":[\"g\"],\"info\":{\"secret\":\"${secret(i)}\"}}")
        }
        sb.append("]}}")
        return sb.toString().toByteArray()
    }

    private fun read(name: String, entries: Int): ImportRead {
        val r = readImport(aegis(name, entries))
        assertTrue("$r", r is ReadResult.Ok)
        return (r as ReadResult.Ok).read
    }

    /** The best of a few runs of [planMerge], in nanoseconds, after warming up. */
    private fun bestPlanNanos(read: ImportRead): Long {
        repeat(3) { planMerge(read, emptyList(), 0) }
        var best = Long.MAX_VALUE
        repeat(5) {
            val t0 = System.nanoTime()
            planMerge(read, emptyList(), 0)
            best = minOf(best, System.nanoTime() - t0)
        }
        return best
    }

    @Test fun aHugeSharedFolderNameIsDropped() {
        val huge = read("b".repeat(2_000_000), 20)
        assertEquals(20, huge.items.size)
        assertTrue("the huge group is no folder", huge.items.all { it.folders.isEmpty() })
        val plan = planMerge(huge, emptyList(), 0)
        assertEquals(20, plan.newItems.size)
        assertEquals(setOf(ORIGIN_TAG_AEGIS), plan.newTags)
        var n = 0
        val merged = mergeImport(plan, DuplicateChoice.SKIP, emptyMap(), emptyList(), 1L, { "id-${n++}" })
        assertTrue(merged.entries.all { it.tags == listOf(ORIGIN_TAG_AEGIS) })
        // The cost: about what a short name costs (the audit measured about 42 ms per item before).
        val short = bestPlanNanos(read("Trabajo123", 20))
        val long = bestPlanNanos(huge)
        assertTrue("huge $long ns, short $short ns", long < 10 * maxOf(short, 1_000_000L))
    }

    /** Each reader that takes [folderNames]: the cap is [MAX_TITLE] characters, as for a title. */
    @Test fun folderNamesDropsANameOverTheCap() {
        fun group(id: String, name: String) = JsonObject(mapOf("id" to JsonPrimitive(id), "name" to JsonPrimitive(name)))
        val atCap = "c".repeat(MAX_TITLE)
        val list = JsonArray(listOf(group("a", atCap), group("b", "d".repeat(MAX_TITLE + 1)), group("c", "Casa")))
        val names = folderNames(list, idKey = "id")
        assertEquals(setOf("a", "c"), names.keys)
        assertTrue(names["a"] == atCap && names["c"] == "Casa")
    }

    @Test fun aNormalJsonFolderStillGivesItsTag() {
        val names = listOf("Trabajo", "Café ☕", "e".repeat(MAX_TITLE))
        for (name in names) {
            val read = read(name, 2)
            assertTrue(name, read.items.all { it.folders == listOf(name) })
            val expected = importTags(listOf(name), ImportFormat.AEGIS)
            assertEquals(name, 2, expected.size)
            val plan = planMerge(read, emptyList(), 0)
            assertEquals(name, expected.toSet(), plan.newTags)
            var n = 0
            val merged = mergeImport(plan, DuplicateChoice.SKIP, emptyMap(), emptyList(), 1L, { "id-${n++}" })
            assertTrue(name, merged.entries.all { it.tags == expected })
        }
    }

    /** Refuerzo 28: an item equal to an earlier one is counted as repeated, even one that would be too big. */
    @Test fun aRepeatedItemIsCountedBeforeItsSize() {
        val first = ImportedItem(0, importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = "https://banco.example/"), emptyList())
        // Same title, user and host, so the same item for the duplicate rule; its password alone passes 64 KiB.
        val again = ImportedItem(1, importedEntry(EntryType.LOGIN, "Banco", username = "ana", url = "https://banco.example/", password = "p".repeat(70_000)), emptyList())
        val plan = planMerge(ImportRead(ImportFormat.BITWARDEN, listOf(first, again), emptyList(), 0), emptyList(), 0)
        assertEquals(1, plan.newItems.size)
        assertEquals(1, plan.repeatedInFile)
        assertTrue(plan.skipped.isEmpty())
    }

    /**
     * «Cancelar» stops the plan: run as the import screen runs it ([planMergeOrNoMemory] on [Dispatchers.Default]), a
     * plan of thousands of large items cancelled after 50 ms ends at once.
     */
    @Test fun planningCanBeCancelled() {
        val notes = "n".repeat(50_000)
        val items = List(8_000) { i -> ImportedItem(i, importedEntry(EntryType.LOGIN, "Cuenta $i", username = "u$i", notes = notes), listOf("Carpeta $i")) }
        val big = ImportRead(ImportFormat.BITWARDEN, items, emptyList(), 0)
        val (stillRunning, ms) = runBlocking {
            val job = launch { withContext(Dispatchers.Default) { planMergeOrNoMemory(big, emptyList(), 0) } }
            delay(50)
            val running = job.isActive
            val t0 = System.nanoTime()
            job.cancelAndJoin()
            running to (System.nanoTime() - t0) / 1_000_000
        }
        assertTrue("the plan was still running when cancelled", stillRunning)
        assertTrue("cancelAndJoin took $ms ms", ms < 200)
    }

    // Ronda de arreglos (review of task 5): every reader drops a folder name over MAX_TITLE, as [folderNames] does.

    private val atCap = "f".repeat(MAX_TITLE)
    private val overCap = "g".repeat(MAX_TITLE + 1)

    private fun ok(text: String): ImportRead {
        val r = readImport(text.toByteArray(Charsets.UTF_8))
        assertTrue("$r", r is ReadResult.Ok)
        return (r as ReadResult.Ok).read
    }

    /** Aegis' older `group` (a name per entry, version 1 and 2 backups). */
    @Test fun aegisLegacyGroupOverTheCapGivesNoFolder() {
        fun entry(i: Int, group: String) =
            "{\"type\":\"totp\",\"issuer\":\"Servicio $i\",\"group\":\"$group\",\"info\":{\"secret\":\"${secret(i)}\"}}"
        val read = ok("{\"version\":1,\"header\":{\"slots\":null,\"params\":null},\"db\":{\"version\":2,\"entries\":[" +
            entry(0, atCap) + "," + entry(1, overCap) + "]}}")
        assertEquals(listOf(listOf(atCap), emptyList()), read.items.map { it.folders })
    }

    /** The CSV with the browsers' columns plus `folder` and `totp`: the login and its 2FA both enter, without it. */
    @Test fun passwordsCsvFolderOverTheCapGivesNoFolder() {
        val read = ok(
            "name,url,username,password,note,folder,totp\n" +
                "Uno,https://uno.example/,ana,clave1,,$atCap,JBSWY3DPEHPK3PXP\n" +
                "Dos,https://dos.example/,ana,clave2,,$overCap,JBSWY3DPEHPK3PXQ\n",
        )
        assertEquals(ImportFormat.PASSWORDS_CSV, read.format)
        assertEquals(
            listOf("Uno" to listOf(atCap), "Uno" to listOf(atCap), "Dos" to emptyList(), "Dos" to emptyList<String>()),
            read.items.map { it.entry.title to it.folders },
        )
        assertTrue(read.skipped.isEmpty())
    }

    /** Bitwarden's CSV, with `folder` and with the organisation's `collections`. */
    @Test fun bitwardenCsvFolderOverTheCapGivesNoFolder() {
        for (place in listOf("folder", "collections")) {
            val read = ok(
                "$place,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n" +
                    "$atCap,,login,Uno,,,0,https://uno.example/,ana,clave1,\n" +
                    "$overCap,,login,Dos,,,0,https://dos.example/,ana,clave2,\n",
            )
            assertEquals(place, ImportFormat.BITWARDEN, read.format)
            assertEquals(place, listOf("Uno" to listOf(atCap), "Dos" to emptyList<String>()), read.items.map { it.entry.title to it.folders })
            assertTrue(place, read.skipped.isEmpty())
        }
    }

    /**
     * The memo of [importTags]: thousands of items that share one folder of [MAX_TITLE] characters (equal lists, one per
     * item, as a reader makes them) work it out once in the plan and once in the merge.
     */
    @Test fun importTagsIsWorkedOutOncePerListOfFolders() {
        val items = List(3_000) { i ->
            ImportedItem(i, importedEntry(EntryType.LOGIN, "Cuenta $i", username = "u$i"), listOf(String(atCap.toCharArray())))
        }
        val read = ImportRead(ImportFormat.BITWARDEN, items, emptyList(), 0)
        var calls = 0
        val counting = { folders: List<String>, format: ImportFormat -> calls++; importTags(folders, format) }
        val plan = planMergeWith(read, emptyList(), 0, tagsOf = counting)
        assertEquals(1, calls)
        assertEquals(planMerge(read, emptyList(), 0), plan)
        calls = 0
        var n = 0
        val merged = mergeImportWith(plan, DuplicateChoice.SKIP, emptyMap(), emptyList(), 1L, { "id-${n++}" }, tagsOf = counting)
        assertEquals(1, calls)
        assertEquals(3_000, merged.entries.size)
        assertTrue(merged.entries.all { it.tags == importTags(listOf(atCap), ImportFormat.BITWARDEN) })
    }
}
