package app.seguridad.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0, tarea 10 (fase B): lo que se enseña al abrir, de uno en uno ([openingItem]), en todas las combinaciones. «Antes
 * de empezar» va antes que todo (O-7: saltar la guía nunca lo salta); la guía nunca retrasa el paso a v4 ni su copia,
 * nunca tapa el bloqueo ni una entrega pendiente, y solo sale en una pestaña.
 */
class OpeningTest {
    private fun all(): Sequence<OpeningState> = sequence {
        val b = listOf(false, true)
        for (notice in b) for (phase in VaultPhase.entries) for (up in b) for (upDone in b) for (master in b) for (vaults in b)
            for (a6 in b) for (rename in b) for (look in b) for (guide in listOf(null) + GuideKind.entries) for (tab in b) for (delivery in b)
                yield(OpeningState(notice, phase, up, upDone, master, vaults, a6, rename, look, guide, tab, delivery))
    }

    /** What each item needs to be true to be shown at all. */
    private fun wanted(s: OpeningState, item: OpeningItem): Boolean = when (item) {
        OpeningItem.NOTICE -> !s.noticeAccepted
        OpeningItem.UPGRADE -> s.upgradePending && s.vault == VaultPhase.OPEN
        OpeningItem.UPGRADE_DONE -> s.upgradeDone && s.vault == VaultPhase.OPEN
        OpeningItem.MASTER_CHANGED -> s.masterChangedOpen
        OpeningItem.VAULTS_NOTICE -> s.vaultsNotice
        OpeningItem.A6_OUTCOME -> s.a6Outcome
        OpeningItem.RENAME -> s.renameDue && s.vault.calm
        OpeningItem.APPEARANCE -> s.appearanceDue && s.vault.calm
        OpeningItem.GUIDE -> s.guide != null && s.vault.calm && s.onTabRoot && !s.deliveryPending
    }

    private val VaultPhase.calm get() = this == VaultPhase.OPEN || this == VaultPhase.NONE

    @Test fun oneAtATimeInEveryCombination() {
        var n = 0
        for (s in all()) {
            n++
            val shown = openingItem(s)
            // The first of the order that is wanted, and only that one.
            assertEquals(s.toString(), OpeningItem.entries.firstOrNull { wanted(s, it) }, shown)
        }
        assertEquals(2 * 4 * 512 * 3, n)
    }

    @Test fun theNoticeBeforeAnything() {
        for (s in all().filter { !it.noticeAccepted }) assertEquals(OpeningItem.NOTICE, openingItem(s))
    }

    @Test fun theGuideNeverDelaysTheUpgradeNorItsCopy() {
        for (s in all().filter { it.noticeAccepted && it.vault == VaultPhase.OPEN && (it.upgradePending || it.upgradeDone) }) {
            assertTrue(s.toString(), openingItem(s) in setOf(OpeningItem.UPGRADE, OpeningItem.UPGRADE_DONE))
        }
    }

    @Test fun theGuideNeverCoversTheLockNorADelivery() {
        for (s in all()) {
            if (openingItem(s) != OpeningItem.GUIDE) continue
            assertTrue(s.vault == VaultPhase.OPEN || s.vault == VaultPhase.NONE)
            assertTrue(s.onTabRoot && !s.deliveryPending)
        }
        val ready = OpeningState(true, VaultPhase.OPEN, false, false, false, false, false, false, false, GuideKind.WHATS_NEW, true, false)
        assertEquals(OpeningItem.GUIDE, openingItem(ready))
        assertNull(openingItem(ready.copy(vault = VaultPhase.LOCKED)))
        assertNull(openingItem(ready.copy(vault = VaultPhase.LOADING)))
        assertNull(openingItem(ready.copy(deliveryPending = true)))
        assertNull(openingItem(ready.copy(onTabRoot = false)))
        // A new install sees the whole guide before «Crea tu bóveda», after «Antes de empezar».
        assertEquals(OpeningItem.GUIDE, openingItem(ready.copy(vault = VaultPhase.NONE, guide = GuideKind.FULL)))
        assertEquals(OpeningItem.NOTICE, openingItem(ready.copy(noticeAccepted = false, vault = VaultPhase.NONE, guide = GuideKind.FULL)))
        // The new look's line first, then what is new.
        assertEquals(OpeningItem.APPEARANCE, openingItem(ready.copy(appearanceDue = true)))
    }

    /** The navigation draws each dialog only when [openingItem] picks it (one at a time). */
    @Test fun theScreenDrawsWhatItPicks() {
        val main = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "app/src/main/java/app/seguridad/ui/AppNavHost.kt") }.first { it.isFile }.readText()
        // Ronda 2 (n-1): every item but «Antes de empezar», the upgrade's two included (ronda 1, I-1).
        for (item in OpeningItem.entries - OpeningItem.NOTICE) {
            assertTrue(item.name, "showing == OpeningItem.${item.name}" in main)
        }
        assertTrue("openingItem(" in main)
        // «Antes de empezar» returns before any of them, in AppNavHost.
        assertTrue(main.indexOf("NoticeScreen(") < main.indexOf("openingItem("))
    }

    /**
     * Ronda 1 (m-7, I-1): every dialog of the queue is drawn once in ui/, and that one call is behind its own
     * `showing == OpeningItem.X` (on its line or the one before): none can be drawn on its own again. The upgrade's two
     * are drawn by the navigation too, so they show over every screen with the vault open, never over the gate.
     */
    @Test fun eachDialogIsDrawnOnceBehindItsCondition() {
        val ui = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "app/src/main/java/app/seguridad/ui") }.first { it.isDirectory }
        val calls = mapOf(
            "VaultUpgradeDialog(" to OpeningItem.UPGRADE,
            "VaultUpgradeDoneNotice(" to OpeningItem.UPGRADE_DONE,
            "MasterChangedDialog(" to OpeningItem.MASTER_CHANGED,
            "vaultsNotice?.let" to OpeningItem.VAULTS_NOTICE,
            "ReplaceOutcomeDialog(" to OpeningItem.A6_OUTCOME,
            "RenameNoticeDialog(" to OpeningItem.RENAME,
            "AppearanceNoticeDialog(" to OpeningItem.APPEARANCE,
            "GuideDialog(" to OpeningItem.GUIDE,
        )
        val files = ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        for ((call, item) in calls) {
            val found = files.flatMap { f ->
                val lines = f.readLines()
                lines.withIndex().filter { (_, l) ->
                    val t = l.trimStart()
                    call in l && !t.startsWith("*") && !t.startsWith("//") && !t.startsWith("/*") && "fun ${call.removeSuffix("(")}(" !in l
                }.map { (i, l) -> Triple(f.name, l, if (i > 0) lines[i - 1] else "") }
            }
            assertEquals("$call: ${found.map { it.first }}", 1, found.size)
            val (file, line, before) = found.single()
            assertEquals(call, "AppNavHost.kt", file)
            assertTrue("$call is not behind its condition", "showing == OpeningItem.${item.name}" in line || "showing == OpeningItem.${item.name}" in before)
        }
    }

    /**
     * Ronda 1 (m-1): a guide asked for from Ajustes is kept only while it can be shown there (on the tab of Ajustes, with
     * the vault open or with none); anywhere else it is forgotten, so it never comes up later by itself (over the gate,
     * or out of a tab).
     */
    @Test fun aRequestIsKeptOnlyWhereItWasAsked() {
        for (phase in VaultPhase.entries) for (tab in listOf(null, "vault", "codes", "contacts", "tools", "settings")) {
            val kept = guideRequestKept(tab, phase)
            assertEquals("$tab $phase", tab == "settings" && (phase == VaultPhase.OPEN || phase == VaultPhase.NONE), kept)
        }
    }

    /**
     * Ronda 1 (m-9): the two snackbars that come by themselves (what the calendar copied, and «Activar huella» cut short)
     * are taken only once nothing of the queue is on screen: the navigation says so, and the vault's screen waits for it
     * before taking them (taking one marks it as seen).
     */
    @Test fun theSnackbarsWaitForTheQueue() {
        fun src(path: String) = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "app/src/main/java/app/seguridad/$path") }.first { it.isFile }.readText()
        assertTrue("LocalOpeningBusy provides (showing != null)" in src("ui/AppNavHost.kt"))
        val vault = src("ui/screens/VaultScreen.kt")
        val wait = "snapshotFlow { openingBusy.value }.first { !it }"
        assertEquals(2, Regex(Regex.escape(wait)).findAll(vault).count())
        assertTrue(vault.indexOf(wait) < vault.indexOf("BiometricVault.interruptionShown(message)"))
        assertTrue(vault.lastIndexOf(wait) < vault.indexOf("autoBackup.takeScheduled(scheduled)"))
    }
}
