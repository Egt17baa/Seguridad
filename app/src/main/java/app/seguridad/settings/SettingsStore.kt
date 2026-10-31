package app.seguridad.settings

import app.seguridad.core.appJson
import app.seguridad.core.writeTextAtomically
import app.seguridad.vault.ReplacePrepared
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class SettingsStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val heapKiB: () -> Long = { Runtime.getRuntime().maxMemory() / 1024 },
) {
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    private val mutex = Mutex()

    /** Reads the saved settings; a missing, damaged or unreadable file just leaves the defaults. */
    suspend fun load() = mutex.withLock {
        val loaded = withContext(io) {
            try {
                if (file.exists()) appJson.decodeFromString<Settings>(file.readText()) else Settings()
            } catch (e: Exception) {
                Settings()
            }
        }
        _settings.value = sane(loaded)
    }

    suspend fun update(change: (Settings) -> Settings) = mutex.withLock { write(change(_settings.value)) }

    /**
     * A new vault is about to be created: turns the automatic copies off, as their rotation would delete the copies of
     * the previous vault (maybe the only good ones) without knowing which vault each belongs to. Returns the folder
     * that was chosen, so the caller gives its permission back; null if they were already off (nothing written).
     */
    suspend fun turnOffAutoBackupForNewVault(): String? = mutex.withLock {
        val s = _settings.value
        val folder = s.autoBackupUri ?: return@withLock null
        write(s.copy(autoBackupUri = null, lastAutoBackupError = null, lastAutoBackupAt = null, autoBackupOffForNewVault = true).forgettingAutoBackupCopies())
        folder
    }

    /**
     * «Desactivar»: the automatic copies go off ([withAutoBackupTurnedOff]). True when this very write dropped a
     * replacement of the old copies that was still waiting: decided under the same lock as the write (T11-c), so a
     * replacement that finishes just before it is never reported as dropped.
     */
    suspend fun turnOffAutoBackup(): Boolean = mutex.withLock {
        val before = _settings.value
        val after = before.withAutoBackupTurnedOff()
        if (after != before) write(after)
        before.autoBackupReplacePending && !after.autoBackupReplacePending
    }

    /**
     * An import is about to replace the vault: its automatic copies are no longer listed as ours, so none is ever rotated
     * away by the imported vault, and a request to replace them (A6) goes. Written to disk before the vault is saved:
     * throws if it cannot be (the import is then refused). The result says whether a request was dropped and can put
     * the three fields back ([ReplacePrepared.undo]) if the vault is not saved after all.
     */
    suspend fun forgetAutoBackupCopiesForImport(): ReplacePrepared = mutex.withLock {
        val before = _settings.value
        write(before.forgettingAutoBackupCopies())
        ReplacePrepared(before.autoBackupReplacePending) {
            update {
                it.copy(
                    autoBackupCopies = before.autoBackupCopies,
                    autoBackupCopiesFolder = before.autoBackupCopiesFolder,
                    autoBackupReplacePending = before.autoBackupReplacePending,
                )
            }
        }
    }

    private suspend fun write(change: Settings) {
        val next = sane(change)
        withContext(io) { file.writeTextAtomically(appJson.encodeToString(Settings.serializer(), next)) }
        _settings.value = next
    }

    private fun sane(settings: Settings): Settings {
        var s = settings
        if (s.kdf == KdfProfile.HIGH && !KdfProfile.HIGH.availableOn(heapKiB())) s = s.copy(kdf = KdfProfile.STANDARD)
        if (s.autoBackupKeep !in AUTO_BACKUP_KEEP_CHOICES) s = s.copy(autoBackupKeep = AUTO_BACKUP_KEEP_DEFAULT)
        return s
    }
}
