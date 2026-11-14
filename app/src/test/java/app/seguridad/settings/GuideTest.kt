package app.seguridad.settings

import app.seguridad.tools.ForbiddenWords
import app.seguridad.vault.VaultLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 2.0, tarea 10 (fase B): las dos guías. La completa para una instalación nueva y la de novedades para quien actualiza
 * desde la 1.x (dueño, 14-11-2026). «Vista» se guarda al terminar o saltar; si no se puede guardar, la guía vuelve en el
 * siguiente arranque. Las dos se abren desde Ajustes.
 */
class GuideTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "settings.json")
    private fun store(f: File = file()) = SettingsStore(f, Dispatchers.Unconfined, heapKiB = { 1L shl 30 })

    @Test fun theDueGuideIsShownOnceAndSeenIsSaved() = runTest {
        val s = store().apply { load(); decideRenameNotice(vaultExists = false) }
        val g = GuideHolder(s, this)
        assertEquals(GuideKind.FULL, g.current(s.settings.value))
        g.done()
        testScheduler.advanceUntilIdle()
        assertNull("Not again in this run", g.current(s.settings.value))
        assertEquals(GUIDE_CURRENT, store().apply { load() }.settings.value.guideSeen)
        assertNull("Nor at the next start", GuideHolder(store().apply { load() }, backgroundScope).let { it.current(store().apply { load() }.settings.value) })
    }

    @Test fun whoUpdatedGetsWhatIsNew() = runTest {
        file().writeText(javaClass.getResourceAsStream("/seguridad/settings-1.4.3.json")!!.use { it.readBytes() }.decodeToString())
        val s = store().apply { load(); decideRenameNotice(vaultExists = true) }
        assertEquals(GuideKind.WHATS_NEW, GuideHolder(s, this).current(s.settings.value))
    }

    /** A save that fails: not again in this run (no loop), but the guide comes back at the next start. */
    @Test fun aFailedSaveBringsItBackAtTheNextStart() = runTest {
        val notADir = File(tmp.root, "no-es-carpeta").apply { writeText("x") }
        val s = store(File(notADir, "settings.json")).apply { load() }
        val g = GuideHolder(s, this)
        assertEquals(GuideKind.FULL, g.current(s.settings.value))
        g.done()
        testScheduler.advanceUntilIdle()
        assertNull(g.current(s.settings.value))
        assertEquals("Nothing saved", GUIDE_NONE, s.settings.value.guideSeen)
        val next = store(File(notADir, "settings.json")).apply { load() }
        assertEquals(GuideKind.FULL, GuideHolder(next, this).current(next.settings.value))
    }

    /** From Ajustes: either guide, whenever, also once seen; closing it hides it again. */
    @Test fun ajustesOpensEither() = runTest {
        val s = store().apply { load(); update { it.withGuideSeen() } }
        val g = GuideHolder(s, this)
        assertNull(g.current(s.settings.value))
        g.request(GuideKind.WHATS_NEW)
        assertEquals(GuideKind.WHATS_NEW, g.current(s.settings.value))
        g.done()
        assertNull(g.current(s.settings.value))
        g.request(GuideKind.FULL)
        assertEquals(GuideKind.FULL, g.current(s.settings.value))
    }

    /** The plan's four screens for a new install; at most four, of one or two lines, for what is new. */
    @Test fun thePages() {
        assertEquals(4, FULL_GUIDE.size)
        assertTrue(WHATS_NEW_GUIDE.size in 1..4)
        for (p in FULL_GUIDE + WHATS_NEW_GUIDE) {
            assertTrue(p.title, p.title.isNotBlank() && p.text.isNotBlank())
            for (t in listOf(p.title, p.text)) assertEquals(t, emptyList<String>(), ForbiddenWords.problems(t))
            // Several vaults are never a way to hide one (O-1); the guide hides nothing either (O-7).
            assertFalse(p.text, Regex("ocult|escond|secret", RegexOption.IGNORE_CASE).containsMatchIn(p.text))
        }
        for (p in WHATS_NEW_GUIDE) {
            assertTrue("One or two sentences: ${p.text}", p.text.split(". ").size <= 2 && p.text.length <= 160)
        }
        // What they say is what the code does.
        val all = (FULL_GUIDE + WHATS_NEW_GUIDE).joinToString(" ") { it.text }
        assertTrue("${VaultLimits.VAULTS_MAX} bóvedas" in all)
        assertTrue("${VaultLimits.TRASH_DAYS} días" in all)
        assertTrue("${VaultLimits.BACKUP_TARGETS_MAX} carpetas" in all)
        assertTrue("cada 7 días" in all)
        // O-4: the copies with a calendar only when Clavero is opened.
        assertTrue("al abrir Clavero" in all)
        // Nothing outside 2.0 (the icon that can be changed is not in it).
        assertFalse(Regex("icono", RegexOption.IGNORE_CASE).containsMatchIn(all))
    }

    @Test fun theWordsOfTheGuide() {
        assertEquals("Saltar", GUIDE_SKIP)
        assertEquals("Siguiente", GUIDE_NEXT)
        assertEquals("Entendido", GUIDE_DONE)
        for (t in listOf(GUIDE_SKIP, GUIDE_NEXT, GUIDE_DONE, GUIDE_SECTION_TITLE, GUIDE_OPEN_FULL, GUIDE_OPEN_NEW)) {
            assertEquals(t, emptyList<String>(), ForbiddenWords.problems(t))
        }
    }
}
