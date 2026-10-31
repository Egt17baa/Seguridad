package app.seguridad.settings

import app.seguridad.tools.ALL_TOOL_TEXTS
import app.seguridad.tools.APP_RENAMED_TEXT
import app.seguridad.tools.APP_RENAMED_TITLE
import app.seguridad.tools.ForbiddenWords
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
 * Owner's decision (31-10-2026): one notice, once, that the app used to be called «Seguridad», only for those who update
 * from an earlier version (a settings.json or a vault already there when 1.3.0 first starts); a new install never sees it.
 */
class RenameNoticeTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "settings.json")

    private fun store() = SettingsStore(file(), Dispatchers.Unconfined)

    /** As SeguridadApp starts: load, then decide with whether the vault file was there. */
    private suspend fun start(vaultExists: Boolean = false) = store().apply {
        load()
        decideRenameNotice(vaultExists)
    }

    @Test fun aNewInstallNeverSeesIt() = runTest {
        val s = start()
        assertEquals(false, s.settings.value.renameNoticePending)
        assertFalse(RenameNotice(s).showIfDue())
        // Next start (a settings.json now exists, written by this version): still decided, still not shown.
        val again = start(vaultExists = true)
        assertEquals(false, again.settings.value.renameNoticePending)
        assertFalse(RenameNotice(again).showIfDue())
    }

    @Test fun anUpdateFrom122WithItsSettingsFileSeesItOnceAcrossRestarts() = runTest {
        file().writeText("""{"lockAfter":"MIN_1","noticeAccepted":true}""") // As 1.2.2 wrote it.
        val s = start()
        assertEquals(true, s.settings.value.renameNoticePending)
        val notice = RenameNotice(s)
        assertTrue(notice.showIfDue())
        assertTrue(notice.visible.value)
        // Marked as shown at once (on disk), so a restart before «Entendido» does not show it again.
        assertEquals(false, store().apply { load() }.settings.value.renameNoticePending)
        // A rotation asks again: already taken, the dialog just stays.
        assertFalse(notice.showIfDue())
        assertTrue(notice.visible.value)
        notice.closed()
        assertFalse(notice.visible.value)
        assertFalse(notice.showIfDue())
        // Restarted: never again.
        val later = start()
        assertFalse(RenameNotice(later).showIfDue())
    }

    @Test fun anUpdateWithAVaultButNoSettingsFileSeesItToo() = runTest {
        val s = start(vaultExists = true)
        assertEquals(true, s.settings.value.renameNoticePending)
        assertTrue(RenameNotice(s).showIfDue())
    }

    @Test fun aDamagedSettingsFileCountsAsAnEarlierVersion() = runTest {
        file().writeText("{no")
        assertEquals(true, start().settings.value.renameNoticePending)
    }

    @Test fun aSettingsFileWithoutTheFieldStillReadsAndTheDecisionIsTakenOnce() = runTest {
        file().writeText("""{"noticeAccepted":true}""")
        val s = store().apply { load() }
        assertNull(s.settings.value.renameNoticePending) // Not decided yet: the default.
        s.decideRenameNotice(vaultExists = false)
        assertEquals(true, s.settings.value.renameNoticePending)
        // Deciding again changes nothing (only the first start of 1.3.0 decides).
        RenameNotice(s).showIfDue()
        s.decideRenameNotice(vaultExists = true)
        assertEquals(false, s.settings.value.renameNoticePending)
    }

    @Test fun aSaveThatFailsStillShowsItOnlyOnceInThisProcess() = runTest {
        file().writeText("""{"noticeAccepted":true}""")
        val s = start()
        file().delete()
        file().mkdir() // The next write fails.
        val notice = RenameNotice(s)
        assertTrue(notice.showIfDue())
        notice.closed()
        assertFalse(notice.showIfDue())
    }

    @Test fun theTextNamesBothNamesAndAvoidsTheForbiddenWords() {
        assertEquals("Seguridad ahora se llama Clavero. Es la misma app: tus datos, copias y archivos cifrados siguen igual.", APP_RENAMED_TEXT)
        assertEquals("Nuevo nombre", APP_RENAMED_TITLE)
        assertEquals(emptyList<String>(), ForbiddenWords.problems(APP_RENAMED_TEXT))
        assertTrue(APP_RENAMED_TEXT in ALL_TOOL_TEXTS && APP_RENAMED_TITLE in ALL_TOOL_TEXTS)
    }
}
