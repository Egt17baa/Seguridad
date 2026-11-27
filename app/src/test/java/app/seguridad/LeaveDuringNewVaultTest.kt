package app.seguridad

import app.seguridad.vault.EntryType
import app.seguridad.vault.LockReason
import app.seguridad.vault.VaultEntry
import app.seguridad.vault.VaultState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

/**
 * 2.1.2, C-1 (option A, with the Abogado's O-4): leaving the app (MainActivity.onStop, [AppContainer.lockOnLeave])
 * while «Bóveda nueva» or «Importar como bóveda nueva» runs leaves the new vault made, with its name, active and
 * locked; without leaving, it is open as before. From the audit's proofs (the copy read of importAsNewVault, and the
 * switch of createVault parked behind a save of the vault of before). Invented data only.
 */
class LeaveDuringNewVaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private class LeaveOnFirstRead(inner: InputStream, val onLeave: () -> Unit) : FilterInputStream(inner) {
        var left = false
        private fun leave() {
            if (!left) {
                left = true
                onLeave()
            }
        }
        override fun read(): Int {
            leave()
            return super.read()
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            leave()
            return super.read(b, off, len)
        }
    }

    /** Default, but while [held] is true each dispatch is parked until [release]. */
    private class GateDispatcher : CoroutineDispatcher() {
        @Volatile var held = false
        val parked = ConcurrentLinkedQueue<Pair<CoroutineContext, Runnable>>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (held) parked.add(context to block) else Dispatchers.Default.dispatch(context, block)
        }
        fun release() {
            held = false
            while (true) {
                val (c, b) = parked.poll() ?: break
                Dispatchers.Default.dispatch(c, b)
            }
        }
    }

    private fun vaultFile(c: AppContainer, id: String) = File(c.registry.dirOf(id), "vault.bin")

    private suspend fun containerWithCopy(scope: CoroutineScope): Pair<AppContainer, ByteArray> {
        val c = AppContainer(tmp.root, tmp.newFolder("cache"), appVersion = "test", appScope = scope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-ficticia-uno".toCharArray(), name = "Uno")
        c.vault.upsert(VaultEntry("x1", EntryType.NOTE, "Nota ficticia", notes = "dato inventado", createdAt = 0, updatedAt = 0))
        val copy = ByteArrayOutputStream().also { assertTrue(c.vault.exportBackup(it)) }.toByteArray()
        c.foreground = true
        return c to copy
    }

    @Test fun leavingDuringImportAsNewVaultLeavesItLocked() = runTest {
        val (c, copy) = containerWithCopy(backgroundScope)
        val before = c.activeVaultId
        val stream = LeaveOnFirstRead(ByteArrayInputStream(copy)) {
            // MainActivity.onStop, keepOpenOnce false: the vault of before is still the active one here.
            c.foreground = false
            c.lockOnLeave()
        }
        val id = c.importAsNewVault(stream, "maestra-ficticia-uno".toCharArray(), "Importada")
        assertTrue(stream.left)
        assertNotEquals(before, id)
        assertEquals(id, c.activeVaultId)
        assertTrue(vaultFile(c, id).exists())
        assertEquals(VaultState.Locked, c.vault.state.value)
        assertEquals(LockReason.LEFT_APP, c.vault.lastLockReason)
        c.vault.lockIfIdle() // MainActivity.onStart's only lock check
        assertEquals(VaultState.Locked, c.vault.state.value)
        // O-4: made ran, so the name chosen is kept.
        c.vault.unlock("maestra-ficticia-uno".toCharArray())
        val st = c.vault.state.value as VaultState.Unlocked
        assertEquals("Importada", st.data.name)
        assertEquals(1, st.data.live.size)
    }

    @Test fun importAsNewVaultWithoutLeavingOpensIt() = runTest {
        val (c, copy) = containerWithCopy(backgroundScope)
        val id = c.importAsNewVault(ByteArrayInputStream(copy), "maestra-ficticia-uno".toCharArray(), "Importada")
        assertEquals(id, c.activeVaultId)
        val st = c.vault.state.value
        assertTrue(st is VaultState.Unlocked)
        assertEquals("Importada", (st as VaultState.Unlocked).data.name)
    }

    @Test fun leavingDuringCreateVaultSwitchLeavesItLocked() = runBlocking {
        val gate = GateDispatcher()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val c = AppContainer(tmp.root, tmp.newFolder("cache"), appVersion = "test", appScope = scope, vaultCompute = gate)
            c.settings.load()
            c.vault.load()
            c.vault.create("maestra-de-prueba-uno".toCharArray(), name = "Uno")
            c.foreground = true
            val old = c.vault
            val before = c.activeVaultId
            assertTrue(old.state.value is VaultState.Unlocked)

            // A save of «Uno» in flight: holds its mutex while its compute step is parked.
            gate.held = true
            val save = scope.async { old.upsert(VaultEntry("n1", EntryType.NOTE, "Nota", notes = "dato inventado", createdAt = 0, updatedAt = 0)) }
            withTimeout(10_000) { while (gate.parked.isEmpty()) delay(5) }

            // «Bóveda nueva»: passes canAddVault (Uno open), switchTo locks Uno and waits in awaitIdle.
            val made = scope.async { c.createVault("Dos", "maestra-de-prueba-dos".toCharArray()) }
            withTimeout(10_000) { while (old.state.value !is VaultState.Locked) delay(5) }
            delay(100)

            // MainActivity.onStop, keepOpenOnce false: leaving the app.
            c.foreground = false
            c.lockOnLeave()

            gate.release()
            save.await()
            val id = made.await()
            assertNotEquals(before, id)
            assertEquals(id, c.activeVaultId)
            // O-4: made ran, so the vault was really made, not left as an empty slot.
            assertTrue(vaultFile(c, id).exists())
            assertEquals(VaultState.Locked, c.vault.state.value)
            assertEquals(LockReason.LEFT_APP, c.vault.lastLockReason)
            c.vault.unlock("maestra-de-prueba-dos".toCharArray())
            assertEquals("Dos", (c.vault.state.value as VaultState.Unlocked).data.name)
        } finally {
            scope.cancel()
        }
    }

    @Test fun createVaultWithoutLeavingOpensIt() = runTest {
        val c = AppContainer(tmp.root, tmp.newFolder("cache"), appVersion = "test", appScope = backgroundScope)
        c.settings.load()
        c.vault.load()
        c.vault.create("maestra-de-prueba-uno".toCharArray(), name = "Uno")
        c.foreground = true
        val id = c.createVault("Dos", "maestra-de-prueba-dos".toCharArray())
        assertEquals(id, c.activeVaultId)
        val st = c.vault.state.value
        assertTrue(st is VaultState.Unlocked)
        assertEquals("Dos", (st as VaultState.Unlocked).data.name)
    }
}
