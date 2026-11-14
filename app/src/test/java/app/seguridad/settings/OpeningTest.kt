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
        for (item in OpeningItem.entries - setOf(OpeningItem.NOTICE, OpeningItem.UPGRADE, OpeningItem.UPGRADE_DONE)) {
            assertTrue(item.name, "showing == OpeningItem.${item.name}" in main)
        }
        assertTrue("openingItem(" in main)
        // «Antes de empezar» returns before any of them; the upgrade's choice is VaultGate's.
        assertTrue(main.indexOf("NoticeScreen(") < main.indexOf("openingItem("))
    }
}
