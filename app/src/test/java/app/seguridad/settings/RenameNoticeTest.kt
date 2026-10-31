package app.seguridad.settings

import app.seguridad.tools.ALL_TOOL_TEXTS
import app.seguridad.tools.APP_RENAMED_TEXT
import app.seguridad.tools.APP_RENAMED_TITLE
import app.seguridad.tools.ForbiddenWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
@OptIn(ExperimentalCoroutinesApi::class)
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
        assertFalse(RenameNotice(s, backgroundScope).showIfDue())
        // Next start (a settings.json now exists, written by this version): still decided, still not shown.
        val again = start(vaultExists = true)
        assertEquals(false, again.settings.value.renameNoticePending)
        assertFalse(RenameNotice(again, backgroundScope).showIfDue())
    }

    // m5 + C2 (controller decision): marked as shown when «Entendido» is tapped, not when it appears; a restart before
    // that shows it again (better seen twice than never); after it, never again.
    @Test fun anUpdateFrom122SeesItUntilEntendidoAndNeverAfter() = runTest {
        file().writeText("""{"lockAfter":"MIN_1","noticeAccepted":true}""") // As 1.2.2 wrote it.
        val s = start()
        assertEquals(true, s.settings.value.renameNoticePending)
        val notice = RenameNotice(s, backgroundScope)
        assertTrue(notice.showIfDue())
        assertTrue(notice.visible.value)
        // A rotation asks again: already taken, the dialog just stays.
        assertFalse(notice.showIfDue())
        assertTrue(notice.visible.value)
        // The process dies before «Entendido»: the next start shows it again.
        val restarted = start()
        assertEquals(true, restarted.settings.value.renameNoticePending)
        val second = RenameNotice(restarted, backgroundScope)
        assertTrue(second.showIfDue())
        second.closed()
        runCurrent()
        assertFalse(second.visible.value)
        assertEquals(false, restarted.settings.value.renameNoticePending) // In memory: a later write never puts it back.
        assertEquals(false, store().apply { load() }.settings.value.renameNoticePending) // On disk.
        assertFalse(second.showIfDue())
        assertFalse(RenameNotice(start(), backgroundScope).showIfDue()) // Restarted: never again.
    }

    // m5: the mark is written in the app's scope, not the screen's: a screen that goes away (a rotation) cannot cut it.
    @Test fun theMarkIsWrittenInTheGivenScopeEvenIfTheScreenGoes() = runTest {
        file().writeText("""{"noticeAccepted":true}""")
        val s = start()
        val app = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val notice = RenameNotice(s, app)
        val screen = launch { notice.showIfDue() }
        screen.join()
        notice.closed()
        screen.cancel() // The composition that showed it is gone; the write does not depend on it.
        advanceUntilIdle()
        assertEquals(false, store().apply { load() }.settings.value.renameNoticePending)
        app.cancel()
    }

    @Test fun anUpdateWithAVaultButNoSettingsFileSeesItToo() = runTest {
        val s = start(vaultExists = true)
        assertEquals(true, s.settings.value.renameNoticePending)
        assertTrue(RenameNotice(s, backgroundScope).showIfDue())
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
        s.decideRenameNotice(vaultExists = true)
        assertEquals(true, s.settings.value.renameNoticePending)
    }

    @Test fun aSaveThatFailsStillShowsItOnlyOnceInThisProcess() = runTest {
        file().writeText("""{"noticeAccepted":true}""")
        val s = start()
        file().delete()
        file().mkdir() // The next write fails.
        val notice = RenameNotice(s, backgroundScope)
        assertTrue(notice.showIfDue())
        notice.closed()
        runCurrent()
        assertFalse(notice.showIfDue())
    }

    @Test fun theTextNamesBothNamesAndAvoidsTheForbiddenWords() {
        assertEquals("Seguridad ahora se llama Clavero. Es la misma app: tus datos, copias y archivos cifrados siguen igual.", APP_RENAMED_TEXT)
        assertEquals("Nuevo nombre", APP_RENAMED_TITLE)
        assertEquals(emptyList<String>(), ForbiddenWords.problems(APP_RENAMED_TEXT))
        assertTrue(APP_RENAMED_TEXT in ALL_TOOL_TEXTS && APP_RENAMED_TITLE in ALL_TOOL_TEXTS)
    }
}
