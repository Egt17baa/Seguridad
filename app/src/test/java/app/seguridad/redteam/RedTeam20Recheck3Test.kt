package app.seguridad.redteam

import app.seguridad.core.appJson
import app.seguridad.crypto.CryptoFailure
import app.seguridad.health.healthView
import app.seguridad.imports.DuplicateChoice
import app.seguridad.imports.IMPORT_NO_MEMORY
import app.seguridad.imports.ImportFormat
import app.seguridad.imports.ImportRead
import app.seguridad.imports.ImportSession
import app.seguridad.imports.ImportStep
import app.seguridad.imports.ImportedItem
import app.seguridad.imports.PreviewEstimate
import app.seguridad.imports.ReadResult
import app.seguridad.imports.beforeImportWork
import app.seguridad.imports.planMerge
import app.seguridad.imports.planMergeOrNoMemory
import app.seguridad.imports.previewEstimateOrNoMemory
import app.seguridad.net.PwnedPasswords
import app.seguridad.vault.COPY_DAMAGED
import app.seguridad.vault.EntryType
import app.seguridad.vault.EntryVersion
import app.seguridad.vault.VaultData
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultLimits
import app.seguridad.vault.VaultState
import app.seguridad.vault.VaultStore
import app.seguridad.vault.checkBackup
import app.seguridad.vault.checkCopyData
import app.seguridad.vault.fittedHistory
import app.seguridad.vault.historyEncodings
import app.seguridad.vault.isVaultTag
import app.seguridad.vault.jsonBytes
import app.seguridad.vault.listTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Random
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/*
 * Re-check 3 of the final attack of Clavero 2.0 (decision (f)), narrowed by the owner's decision of 19-11-2026 (to go
 * faster): only the fixes of round 3 (row 63, RT-27, RT-28, RT-29 and «Salud») and that RT-21 to RT-29 do not come back;
 * no new attack on other parts of the app. Same naming as the attack: rtN_… asserts the SAFE behaviour and fails while
 * RT-N is open (@Ignore'd only for the gate); resists… passes; informative… fixes what is accepted as it is today. No
 * test of times: counts, exact results and source anchors only. Invented data only. Report:
 * docs/superpowers/handoff/sdd-2.0/redteam-recheck-3.md.
 */
class RedTeam20Recheck3Test {
    @get:Rule val tmp = TemporaryFolder()

    companion object {
        private val root: File = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/AndroidManifest.xml").isFile }
        private val main = File(root, "app/src/main/java/app/seguridad")

        /** A source file without its comments (lines starting with `//` or `*`, and what follows `//`). */
        private fun code(path: String): String = File(main, path).readLines()
            .filterNot { val t = it.trimStart(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }
            .joinToString("\n") { it.substringBefore("// ") }

        private val HEX = "0123456789abcdef0123456789abcdef"
    }

    private fun store() =
        VaultStore(File(tmp.newFolder(), "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, kdf = { RT20_KDF }).apply { runBlocking { load() } }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-27: the state machine of IMPORT_NO_MEMORY.
    // ---------------------------------------------------------------------------------------------------------------

    private val one = ImportRead(ImportFormat.PASSWORDS_CSV, listOf(ImportedItem(0, VaultEntry("i", EntryType.LOGIN, "Nuevo", createdAt = 1, updatedAt = 1), emptyList())), emptyList(), 0)

    /** A session at its preview, with the plan of [one] against an empty vault. */
    private fun preview(): Pair<ImportSession, app.seguridad.imports.MergePlan> {
        val s = ImportSession()
        val run = s.begin(Any())
        s.hold(run, byteArrayOf(1))
        s.onRead(run, ReadResult.Ok(one))
        val plan = planMerge(one, emptyList(), 0)
        s.onPlan(run, plan, emptyList())
        assertEquals(ImportStep.Preview, s.step)
        return s to plan
    }

    /** A session reading, with [one] read: the run, waiting for its plan. */
    private fun reading(): Pair<ImportSession, Long> {
        val s = ImportSession()
        val run = s.begin(Any())
        s.hold(run, byteArrayOf(1))
        assertTrue(s.onRead(run, ReadResult.Ok(one)) != null)
        return s to run
    }

    /**
     * RT-27, the plan: a lack of memory says IMPORT_NO_MEMORY and holds nothing, only for the run still reading. A lock, a
     * «Cancelar» or another file meanwhile drop it (the run moved on): the old result never shows the text over the new
     * state, and never brings back a plan.
     */
    @Test fun resistsAPlanWithoutMemoryOnlyForTheRunStillReading() {
        reading().let { (s, run) ->
            s.planNoMemory(run)
            assertEquals(IMPORT_NO_MEMORY, s.error)
            assertEquals(ImportStep.Pick, s.step)
            assertNull(s.plan)
            assertTrue(s.existing.isEmpty())
            assertFalse(s.canConfirm)
            assertNull(s.startSaving())
        }
        reading().let { (s, run) ->
            s.vaultStateIs(false)
            s.planNoMemory(run)
            assertFalse("a lock meanwhile", s.error == IMPORT_NO_MEMORY)
        }
        reading().let { (s, run) ->
            s.cancel()
            s.planNoMemory(run)
            assertFalse("a cancel meanwhile", s.error == IMPORT_NO_MEMORY)
            assertEquals(null, s.error)
        }
        reading().let { (s, run) ->
            val other = s.begin(Any())
            s.planNoMemory(run)
            assertEquals("another file meanwhile: still reading it", ImportStep.Reading, s.step)
            assertEquals(null, s.error)
            assertTrue(s.isRun(other))
        }
    }

    /**
     * RT-27, the estimate: out of memory keeps «Importar» off and never «Calculando» for ever; a stale one (other choices,
     * another plan, a lock) changes nothing; a change of choice tries again.
     */
    @Test fun resistsAnEstimateWithoutMemoryKeepsImportOffAndAStaleOneIsDropped() {
        val (s, plan) = preview()
        s.estimateNoMemory(plan, s.default, s.overrides)
        assertTrue(s.estimateNoMemory)
        assertNull(s.overBytes)
        assertFalse(s.canConfirm)
        assertNull(s.startSaving())
        // A change of choice tries again: the text goes and a fitting estimate turns «Importar» on.
        s.chooseAll(DuplicateChoice.KEEP_BOTH)
        assertFalse(s.estimateNoMemory)
        s.estimated(plan, s.default, s.overrides, PreviewEstimate(0L, emptySet(), 0))
        assertTrue(s.canConfirm)
        // A stale result out of memory (the choices of before) changes nothing.
        s.estimateNoMemory(plan, DuplicateChoice.SKIP, emptyMap())
        assertFalse(s.estimateNoMemory)
        assertTrue(s.canConfirm)
        // Another plan (another file) or a lock: the old result is dropped.
        val (s2, plan2) = preview()
        s2.begin(Any())
        s2.estimateNoMemory(plan2, DuplicateChoice.SKIP, emptyMap())
        assertFalse(s2.estimateNoMemory)
        val (s3, plan3) = preview()
        s3.vaultStateIs(false)
        s3.estimateNoMemory(plan3, DuplicateChoice.SKIP, emptyMap())
        assertFalse(s3.estimateNoMemory)
        assertFalse(s3.canConfirm)
    }

    /**
     * RT-27, informative: the session does not hold «Importar nunca activo con estimateNoMemory» on its own. After
     * [ImportSession.estimateNoMemory], an [ImportSession.estimated] for the SAME choices sets overBytes and leaves
     * estimateNoMemory on: «Importar» is on while the preview shows IMPORT_NO_MEMORY (its first branch). Not reachable
     * from the screen: its only caller is the one `collectLatest` over `snapshotFlow` of (plan, default, overrides), which
     * cancels the estimate of before at each change (and withContext drops a result after a cancel), so two estimates of
     * the same choices never both arrive. Asserted as it is today; the one-line hardening (estimated clears the flag, or
     * canConfirm reads it) is for the round of fixes.
     */
    @Test fun informativeAnEstimateAfterNoMemoryForTheSameChoicesTurnsImportOn() {
        val (s, plan) = preview()
        s.estimateNoMemory(plan, s.default, s.overrides)
        s.estimated(plan, s.default, s.overrides, PreviewEstimate(0L, emptySet(), 0))
        assertTrue("still flagged", s.estimateNoMemory)
        assertTrue("«Importar» on with IMPORT_NO_MEMORY on view (session only)", s.canConfirm)
        // Not reachable from the screen: one caller of each, in the collectLatest of the estimate.
        val src = code("ui/screens/ImportScreen.kt")
        assertEquals(1, Regex("""session\.estimated\(""").findAll(src).count())
        assertEquals(1, Regex("""session\.estimateNoMemory\(""").findAll(src).count())
        val flow = src.indexOf("snapshotFlow { EstimateInput(session.plan, session.default, session.overrides) }.collectLatest { input ->")
        val call = src.indexOf("if (estimate == null) session.estimateNoMemory(plan, default, overrides) else session.estimated(plan, default, overrides, estimate)")
        assertTrue(flow in 0 until call)
        assertEquals("no other estimated( in the app", 1, File(main, "").walk().filter { it.isFile && it.extension == "kt" }.sumOf { Regex("""\.estimated\(""").findAll(it.readText()).count() })
    }

    /**
     * RT-27: the wrappers catch only OutOfMemoryError; a cancel goes out as it is (never IMPORT_NO_MEMORY), and the screen
     * routes a null plan to planNoMemory and a null estimate to estimateNoMemory, with CancellationException rethrown
     * before `catch (e: Exception)`. The plan and the vault the estimate uses come from the one snapshot (ruling R16).
     */
    @Test fun resistsTheWrappersLetACancelOutAndTheScreenRoutesNullToNoMemory() {
        val plan = planMerge(one, emptyList(), 0)
        try {
            beforeImportWork = { throw CancellationException("cancelled") }
            try {
                planMergeOrNoMemory(one, emptyList(), 0)
                fail("planMergeOrNoMemory swallowed a cancel")
            } catch (e: CancellationException) {
                assertEquals("cancelled", e.message)
            }
            try {
                previewEstimateOrNoMemory(plan, DuplicateChoice.SKIP, emptyMap(), emptyList(), 1L)
                fail("previewEstimateOrNoMemory swallowed a cancel")
            } catch (e: CancellationException) {
                assertEquals("cancelled", e.message)
            }
            beforeImportWork = { throw StackOverflowError() }
            try {
                planMergeOrNoMemory(one, emptyList(), 0)
                fail("another Error became null")
            } catch (e: StackOverflowError) {
                // As it must: only running out of memory gives null.
            }
        } finally {
            beforeImportWork = {}
        }
        assertTrue(planMergeOrNoMemory(one, emptyList(), 0) != null)
        val wrappers = code("imports/ImportMemory.kt")
        assertEquals(2, Regex("""catch \(""").findAll(wrappers).count())
        assertEquals(2, Regex("""catch \(e: OutOfMemoryError\)""").findAll(wrappers).count())
        val src = code("ui/screens/ImportScreen.kt")
        val flat = src.replace(Regex("""\s+"""), " ")
        assertTrue("if (plan == null) session.planNoMemory(run) else session.onPlan(run, plan, snap.entries)" in src)
        assertEquals("one snapshot", 1, Regex("""importSnapshot\(""").findAll(src).count())
        assertTrue("} catch (e: CancellationException) { throw e } catch (e: Exception) { PreviewEstimate(0L, null, 0) }" in flat)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-28: a result of before that prunes the filter of now.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * RT-28: the effect's rule (`f.tags - result.goneFilterTags`, VaultList.kt) against the rule of before
     * (`f.tags.filter { it in result.tags }`), with the result worked out for a filter [f1] and the filter of now [f2].
     * The same when the filter did not change; when it did (a stale result), the new rule keeps a superset of the old,
     * and what it keeps more is only tags the result was not worked out for (f2 \ f1), which the next result, worked out
     * for f2, takes away. It never drops a tag that the vault has. 3 000 cases, Random(20261119L).
     */
    @Test fun resistsAStaleResultNeverPrunesMoreThanTheRuleOfBefore() {
        val rnd = Random(20261119L)
        val alphabet = List(30) { "t$it" }
        fun subset(p: Int) = alphabet.filterTo(LinkedHashSet()) { rnd.nextInt(100) < p }
        var stale = 0
        var kept = 0
        repeat(3_000) { c ->
            val entries = List(rnd.nextInt(12)) { i ->
                VaultEntry("e$i", EntryType.LOGIN, "t", createdAt = 1, updatedAt = 1, tags = subset(10).toList(), deletedAt = null)
            }
            val f1 = subset(20)
            val f2: Set<String> = if (rnd.nextBoolean()) f1 else subset(20)
            val r = listTags(entries, f1)
            val vaultTags = entries.flatMapTo(HashSet()) { it.tags }
            assertEquals("case $c: the chips", vaultTags.sorted(), r.sorted)
            assertEquals("case $c: gone", f1.filterTo(HashSet()) { it !in vaultTags }, r.goneFromFilter)
            val new = if (r.goneFromFilter.isEmpty()) f2 else f2 - r.goneFromFilter
            val old = f2.filter { it in r.sorted }.toSet()
            if (f2 == f1) {
                assertEquals("case $c: same filter, same rule", old, new)
            } else {
                stale++
                assertTrue("case $c: a superset", new.containsAll(old))
                val more = new - old
                assertTrue("case $c: more only outside f1", (f2 - f1).containsAll(more))
                if (more.isNotEmpty()) kept++
            }
            assertTrue("case $c: never drops a tag of the vault", new.containsAll(f2.filter { it in vaultTags }))
        }
        assertTrue("stale cases covered: $stale, $kept keep more", stale > 500 && kept > 50)
        // The effect is that rule, run once per result; compute builds it from the filter it works out (one listTags).
        val src = code("ui/screens/VaultList.kt")
        assertTrue("val tags = if (result.goneFilterTags.isEmpty()) f.tags else f.tags - result.goneFilterTags" in src)
        assertEquals(1, Regex("""\blistTags\(""").findAll(src).count())
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RT-29: the exactness of fittedHistory at its borders, and what rt29_ counts.
    // ---------------------------------------------------------------------------------------------------------------

    private val historySerializer = ListSerializer(EntryVersion.serializer())

    /** fittedHistory before RT-29 (db66179^, VaultLimits.kt), the reference. */
    private fun VaultEntry.before(): VaultEntry {
        if (history.isEmpty()) return this
        var kept = history
        if (kept.size > VaultLimits.HISTORY_MAX_VERSIONS) {
            val doomed = kept.withIndex().sortedBy { it.value.savedAt }.take(kept.size - VaultLimits.HISTORY_MAX_VERSIONS).mapTo(HashSet()) { it.index }
            kept = kept.filterIndexed { i, _ -> i !in doomed }
        }
        while (kept.isNotEmpty() && jsonBytes(historySerializer, kept) > VaultLimits.HISTORY_MAX_BYTES) {
            val oldest = kept.indices.minBy { kept[it].savedAt }
            kept = kept.filterIndexed { i, _ -> i != oldest }
        }
        return if (kept.size == history.size) this else copy(history = kept)
    }

    /** An entry of [k] versions whose history list is exactly [total] bytes of JSON (k ≥ 1), with [savedAt] for each. */
    private fun entryOfHistory(k: Int, total: Long, savedAt: (Int) -> Long): VaultEntry {
        val base = VaultEntry("e", EntryType.LOGIN, "t", createdAt = 1, updatedAt = 1)
        val each = (total / k).toInt()
        var history = List(k) { i -> EntryVersion(savedAt(i), base.copy(password = "p$i", notes = "n".repeat(maxOf(0, each - 120)))) }
        val pad = total - jsonBytes(historySerializer, history)
        check(pad >= 0) { "k=$k total=$total pad=$pad" }
        history = history.mapIndexed { i, v -> if (i == k - 1) v.copy(entry = v.entry.copy(notes = v.entry.notes + "n".repeat(pad.toInt()))) else v }
        check(jsonBytes(historySerializer, history) == total)
        return base.copy(history = history)
    }

    /**
     * RT-29: at exactly 64 KiB and one byte over, for 1 to 25 versions, with distinct dates and all equal: the same
     * versions as the loop of before, the same object when it fits, one version over the limit alone leaves the history
     * empty, and at most 20 and 64 KiB after. And the count `rt29_` relies on is exact, not only a bound: one encoding
     * per version kept after the cut to 20, min(k, 20), and none for an empty history.
     */
    @Test fun resistsFittedHistoryIsExactAtItsBordersAndEncodesEachVersionOnce() {
        val limit = VaultLimits.HISTORY_MAX_BYTES.toLong()
        var trimmed = 0
        for (k in 1..25) for (ties in listOf(false, true)) for (delta in listOf(0L, 1L)) {
            val e = entryOfHistory(k, limit + delta) { i -> if (ties) 7L else (i * 7919L) % 101 }
            val expected = e.before()
            historyEncodings.set(0)
            val got = e.fittedHistory()
            val encodings = historyEncodings.get()
            val label = "k=$k ties=$ties +$delta"
            assertEquals(label, expected, got)
            assertEquals("$label: one encoding per version kept after the cut", minOf(k, VaultLimits.HISTORY_MAX_VERSIONS).toLong(), encodings)
            if (k <= VaultLimits.HISTORY_MAX_VERSIONS && delta == 0L) assertSame("$label: fits, the same object", e, got)
            if (delta == 1L) {
                trimmed++
                assertTrue("$label: something dropped", got.history.size < minOf(k, VaultLimits.HISTORY_MAX_VERSIONS) || k > VaultLimits.HISTORY_MAX_VERSIONS)
            }
            if (k == 1 && delta == 1L) assertTrue("$label: one version over the limit alone goes", got.history.isEmpty())
            assertTrue(label, got.history.size <= VaultLimits.HISTORY_MAX_VERSIONS)
            assertTrue(label, jsonBytes(historySerializer, got.history) <= limit)
        }
        assertTrue(trimmed == 50)
        historyEncodings.set(0)
        val empty = VaultEntry("e", EntryType.LOGIN, "t", createdAt = 1, updatedAt = 1)
        assertSame(empty, empty.fittedHistory())
        assertEquals("an empty history encodes nothing", 0L, historyEncodings.get())
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Row 63: the three gates with tags and dates, and isVaultTag at its borders.
    // ---------------------------------------------------------------------------------------------------------------

    private fun copyOf(data: VaultData): ByteArray = rt20Copy(rt20HostileFile(appJson.encodeToString(VaultData.serializer(), data)).first)

    private fun checked(copy: ByteArray): String = try {
        val report = runBlocking { checkBackup(ByteArrayInputStream(copy), RT20_MASTER.toCharArray(), tmp.newFolder(), Dispatchers.Unconfined, Dispatchers.Unconfined) }
        if (report.problems.isEmpty()) "OK" else "problems: ${report.problems}"
    } catch (e: CryptoFailure) {
        e.message ?: "?"
    }

    /** «Importar copia» ([newTag] false) or «Importar como bóveda nueva» (true, as AppContainer.importAsNewVault): a refusal changes nothing. */
    private fun imported(copy: ByteArray, newTag: Boolean): Pair<String, VaultData?> {
        val s = store()
        val before = s.state.value
        return try {
            runBlocking { s.importReplace(ByteArrayInputStream(copy), RT20_MASTER.toCharArray(), newTag = newTag) }
            "OK" to (s.state.value as VaultState.Unlocked).data
        } catch (e: CryptoFailure) {
            assertEquals("a refusal changes nothing", before, s.state.value)
            (e.message ?: "?") to null
        }
    }

    private fun gates(copy: ByteArray): List<String> = listOf(checked(copy), imported(copy, false).first, imported(copy, true).first)

    /** One note, with [tag] and [checkedAt]; its notes padded so the data as written are [size] bytes when given. */
    private fun note(tag: String, checkedAt: Long, size: Long? = null): VaultData {
        val rnd = Random(20261119L)
        val one = VaultData(entries = listOf(VaultEntry(UUID(rnd.nextLong(), rnd.nextLong()).toString(), EntryType.NOTE, "Nota", notes = "n", createdAt = 1, updatedAt = 1)), vaultTag = tag, lastCheckedBackupAt = checkedAt)
        if (size == null) return one
        val data = one.copy(entries = listOf(one.entries[0].copy(notes = "n".repeat((size - jsonBytes(one)).toInt() + 1))))
        check(jsonBytes(data) == size)
        return data
    }

    /**
     * Row 63, isVaultTag at its borders: only 32 ASCII `0-9a-f`. Upper case, 31 and 33, other digits (Arabic, Persian,
     * full width), a letter past `f`, a NUL, a surrogate pair and the empty string are not a tag the app writes.
     */
    @Test fun resistsIsVaultTagAtItsBorders() {
        assertTrue(isVaultTag(HEX))
        assertTrue(isVaultTag("0".repeat(32)) && isVaultTag("f".repeat(32)))
        for (bad in listOf("", HEX.uppercase(), HEX.dropLast(1), HEX + "0", "٣".repeat(32), "۳".repeat(32), "０".repeat(32), "ａ".repeat(32), "g".repeat(32),
            HEX.dropLast(1) + "\u0000", HEX.dropLast(2) + "😀", " " + HEX.drop(1), HEX.dropLast(1) + "\n", "/".repeat(32), ":".repeat(32), "`".repeat(32))) {
            assertFalse("«$bad»", isVaultTag(bad))
        }
    }

    /**
     * Row 63, the differential of the three gates («Comprobar», «Importar copia» and «Importar como bóveda nueva»): with
     * each tag (none, as the app writes it, and eight it never writes) and each date of a check (none, and one), the
     * three say the same, exactly OK or COPY_DAMAGED; a refusal changes nothing; and what «Importar como bóveda nueva»
     * seals has a tag of its own, no date of a check, is no bigger than what was checked and passes the same checks.
     */
    @Test fun resistsTheThreeGatesSayTheSameWithEachTagAndDate() {
        val tags = listOf("", HEX, HEX.uppercase(), HEX.dropLast(1), HEX + "a", "٣".repeat(32), "ａ".repeat(32), "g".repeat(32), "<script>", HEX.dropLast(1) + "\u0000")
        for (tag in tags) for (checkedAt in listOf(0L, 1_800_000_000_000L)) {
            val data = note(tag, checkedAt)
            val copy = copyOf(data)
            val expected = if (tag.isEmpty() || isVaultTag(tag)) "OK" else COPY_DAMAGED
            assertEquals("«$tag» $checkedAt", List(3) { expected }, gates(copy))
            if (expected == "OK") {
                val sealed = imported(copy, true).second!!
                assertTrue(isVaultTag(sealed.vaultTag) && sealed.vaultTag != tag)
                assertEquals(0L, sealed.lastCheckedBackupAt)
                checkCopyData(sealed)
                val kept = imported(copy, false).second!!
                assertTrue("«Importar copia» keeps a valid tag, or makes one", if (tag.isEmpty()) isVaultTag(kept.vaultTag) else kept.vaultTag == tag)
            }
        }
    }

    /**
     * Row 63, the one size case that tells the gates apart: a v4 copy with no tag, whose data grow when the tag is made
     * (Vault.kt, readAsImported). At the limit counting the tag the app makes, the three pass and what is sealed is no
     * bigger than what was checked; one byte over, the three refuse. The tag is made before the checks in all three.
     */
    @Test fun resistsAV4CopyWithoutATagAtTheLimitIsCountedWithItsTagByTheThreeGates() {
        val limit = VaultLimits.VAULT_MAX_DATA_BYTES + VaultLimits.COPY_DATA_MARGIN_BYTES.toLong()
        val grows = jsonBytes(note(HEX, 0L)) - jsonBytes(note("", 0L))
        assertTrue("the tag adds $grows bytes", grows > 32)
        val atLimit = copyOf(note("", 0L, limit - grows))
        assertEquals(listOf("OK", "OK", "OK"), gates(atLimit))
        val sealed = imported(atLimit, true).second!!
        assertTrue("sealed ${jsonBytes(sealed)} <= checked $limit", jsonBytes(sealed) <= limit)
        checkCopyData(sealed)
        assertEquals(List(3) { COPY_DAMAGED }, gates(copyOf(note("", 0L, limit - grows + 1))))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // «Salud»: no request after the cancel of NO_MEMORY, and no CancellationException swallowed.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * «Salud»: the stop of NO_MEMORY is `job?.cancel()` on the check (HealthScreen.kt), whose only network call is
     * `pwned.times(…)` in its loop. That call is a prompt cancellation point: cancelled while its request is in flight,
     * the loop sends nothing more (MockWebServer: exactly 1 request of 5). The screen itself is anchored by source
     * (RedTeam20Recheck2Test.informativeSaludWithoutMemoryHidesTheCancelOfARunningCheck): none after the cancel, which
     * runs in a LaunchedEffect once NO_MEMORY is composed.
     */
    @Test fun resistsNoRequestGoesAfterTheCancelOfALeakCheck() {
        val inFlight = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                inFlight.countDown()
                release.await(10, TimeUnit.SECONDS)
                return MockResponse().setResponseCode(200).setBody("")
            }
        }
        server.start()
        try {
            val pwned = PwnedPasswords(OkHttpClient(), server.url("/"))
            runBlocking {
                val job = launch(Dispatchers.Default) {
                    for (i in 0 until 5) pwned.times("contraseña-$i".toCharArray())
                }
                assertTrue(inFlight.await(10, TimeUnit.SECONDS))
                job.cancel()
                release.countDown()
                job.join()
                assertTrue(job.isCancelled)
            }
            assertEquals("no request after the cancel", 1, server.requestCount)
        } finally {
            release.countDown()
            server.shutdown()
        }
        val src = code("ui/screens/HealthScreen.kt")
        assertEquals("one network call in the screen", 1, Regex("""pwned\.times\(""").findAll(src).count())
        val noMemory = src.indexOf("if (health?.noMemory == true)")
        assertTrue("LaunchedEffect(Unit) { job?.cancel() }" in src.substring(noMemory, src.indexOf("return@BackScaffold", noMemory)))
    }

    /**
     * «Salud»: a cancel from `active`, at any of its calls (between entries, or right before the missions), goes out
     * as it is, never as NO_MEMORY; healthView catches only OutOfMemoryError, and the screen passes `work.ensureActive()`.
     */
    @Test fun resistsNoCancellationExceptionIsSwallowedBySalud() {
        val data = VaultData(entries = List(600) { i -> VaultEntry("e$i", EntryType.LOGIN, "t$i", password = "p$i", createdAt = 1, updatedAt = 1) })
        var calls = 0
        healthView(data, 1L, emptyMap(), missionsOff = false) { calls++ }
        assertTrue("$calls calls", calls >= 3)
        for (k in 1..calls) {
            var n = 0
            try {
                healthView(data, 1L, emptyMap(), missionsOff = false) { if (++n == k) throw CancellationException("k=$k") }
                fail("k=$k: not cancelled")
            } catch (e: CancellationException) {
                assertEquals("k=$k", e.message)
            }
        }
        val view = code("health/HealthView.kt")
        assertEquals(1, Regex("""catch \(""").findAll(view).count())
        assertTrue("catch (e: OutOfMemoryError)" in view)
        assertTrue("healthView(data, System.currentTimeMillis(), breached, missionsOff) { work.ensureActive() }" in code("ui/screens/HealthScreen.kt"))
    }
}
