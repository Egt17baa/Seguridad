package app.seguridad

import app.seguridad.health.MISSION_REUSED
import app.seguridad.vault.PRIMARY_VAULT_ID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 2.0, tarea 10 (fase B): «Ahora no» es de cada bóveda y dura hasta su siguiente desbloqueo, también al cambiar de bóveda. */
class MissionsVaultTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun putOffIsPerVaultAndEndsWithItsLock() = runTest {
        val c = AppContainer(tmp.root, tmp.newFolder("cache"), appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-de-prueba-a".toCharArray(), name = "Uno")
        val a = c.vault
        val p = c.missionPostponements
        p.postpone(a.id, a.lockEpoch, MISSION_REUSED)
        assertEquals(setOf(MISSION_REUSED), p.postponed(a.id, a.lockEpoch))
        val b = c.createVault("Dos", "maestra-de-prueba-b".toCharArray())
        assertEquals("Another vault does not see it", emptySet<String>(), p.postponed(b, c.vault.lockEpoch))
        c.switchTo(PRIMARY_VAULT_ID)
        c.vault.unlock("maestra-de-prueba-a".toCharArray())
        assertEquals("The switch locked A: its next unlock sees every mission", emptySet<String>(), p.postponed(a.id, c.vault.lockEpoch))
    }
}
