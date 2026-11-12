package app.seguridad.settings

import app.seguridad.core.appJson
import app.seguridad.core.writeTextAtomically
import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.ReplacePrepared
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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

    /** Whether the file was there when [load] read it (also a damaged one): data from an earlier version. */
    @Volatile private var fileExistedAtLoad = false

    /** Reads the saved settings; a missing, damaged or unreadable file just leaves the defaults. */
    suspend fun load() = mutex.withLock {
        val loaded = withContext(io) {
            fileExistedAtLoad = file.exists()
            try {
                if (file.exists()) decodeSettings(file.readText()) else Settings()
            } catch (e: Exception) {
                Settings()
            }
        }
        _settings.value = sane(loaded)
    }

    suspend fun update(change: (Settings) -> Settings) = mutex.withLock { write(change(_settings.value)) }

    /** [update] of the settings of the vault [id] only ([Settings.withVault]). */
    suspend fun updateVault(id: String, change: (VaultSettings) -> VaultSettings) = update { it.withVault(id, change) }

    /**
     * Right after [load], at the start of the app: decides once whether the notice of the new name is due
     * ([Settings.renameNoticePending]): only when data from an earlier version was there, this settings file (read by
     * [load], even if damaged) or the vault ([vaultExists]). Once decided it never changes here. If it cannot be saved,
     * it holds for this run only and the next start decides again.
     */
    suspend fun decideRenameNotice(vaultExists: Boolean) = mutex.withLock {
        val s = _settings.value
        if (s.renameNoticePending != null) return@withLock
        val next = s.copy(renameNoticePending = fileExistedAtLoad || vaultExists)
        try {
            write(next)
        } catch (e: Exception) {
            _settings.value = next
        }
    }

    /**
     * A new vault is about to be created: turns the automatic copies off, as their rotation would delete the copies of
     * the previous vault (maybe the only good ones) without knowing which vault each belongs to. Whether they were on or
     * off, the previous vault's list of copies and any request to replace them are forgotten (I1 of the CR re-review):
     * with the copies off the list is kept on purpose, so choosing the same folder again would otherwise have the new
     * vault rotate the old vault's copies away. Returns the folder that was chosen, so the caller gives its permission
     * back; null if they were already off. Nothing is written when there is nothing to change.
     */
    suspend fun turnOffAutoBackupForNewVault(id: String): String? = mutex.withLock {
        val all = _settings.value
        val s = all.vault(id)
        val folder = s.autoBackupUri
        val off = if (folder != null) {
            s.copy(autoBackupUri = null, lastAutoBackupError = null, lastAutoBackupAt = null, autoBackupOffForNewVault = true)
        } else {
            s
        }
        val next = off.forgettingAutoBackupCopies()
        if (next != s) write(all.withVault(id) { next })
        folder
    }

    /**
     * «Desactivar»: the automatic copies go off ([withAutoBackupTurnedOff]). True when this very write dropped a
     * replacement of the old copies that was still waiting: decided under the same lock as the write (T11-c), so a
     * replacement that finishes just before it is never reported as dropped.
     */
    suspend fun turnOffAutoBackup(id: String): Boolean = mutex.withLock {
        val before = _settings.value.vault(id)
        val after = before.withAutoBackupTurnedOff()
        if (after != before) write(_settings.value.withVault(id) { after })
        before.autoBackupReplacePending && !after.autoBackupReplacePending
    }

    /**
     * An import is about to replace the vault: its automatic copies are no longer listed as ours, so none is ever rotated
     * away by the imported vault, and a request to replace them (A6) goes. Written to disk before the vault is saved:
     * throws if it cannot be (the import is then refused). The result says whether a request was dropped and can put
     * the request fields back ([ReplacePrepared.undo]) if the vault is not saved after all.
     */
    suspend fun forgetAutoBackupCopiesForImport(id: String): ReplacePrepared = mutex.withLock {
        val before = _settings.value.vault(id)
        write(_settings.value.withVault(id) { before.forgettingAutoBackupCopies() })
        ReplacePrepared(before.autoBackupReplacePending) {
            updateVault(id) {
                it.copy(
                    autoBackupCopies = before.autoBackupCopies,
                    autoBackupCopiesFolder = before.autoBackupCopiesFolder,
                    autoBackupReplacePending = before.autoBackupReplacePending,
                    autoBackupReplaceTargets = before.autoBackupReplaceTargets,
                    autoBackupReplaceTold = before.autoBackupReplaceTold,
                    importCopies = before.importCopies,
                    importCopiesFolder = before.importCopiesFolder,
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
        if (settings.perVault.values.all { saneVault(it) == it }) return settings
        return settings.copy(perVault = settings.perVault.mapValues { saneVault(it.value) })
    }

    private fun saneVault(settings: VaultSettings): VaultSettings {
        var s = settings
        if (s.kdf == KdfProfile.HIGH && !KdfProfile.HIGH.availableOn(heapKiB())) s = s.copy(kdf = KdfProfile.STANDARD)
        if (s.autoBackupKeep !in AUTO_BACKUP_KEEP_CHOICES) s = s.copy(autoBackupKeep = AUTO_BACKUP_KEEP_DEFAULT)
        return s
    }
}

/** The fields a [VaultSettings] has: at the top of a settings.json, they are 1.4.3's (or before), of the first vault. */
private val VAULT_FIELDS: Set<String> = VaultSettings.serializer().descriptor.let { d -> (0 until d.elementsCount).map(d::getElementName).toSet() }

/**
 * Reads a settings.json. One of 1.4.3 or before has the copies, A6, the import copies and the cost at the top (2.0, task
 * 2): they are read, by their names, as the settings of [PRIMARY_VAULT_ID], unless the file already has those. A file
 * written by 2.0 has none at the top, so a vault deleted later never comes back from them.
 */
internal fun decodeSettings(text: String): Settings {
    val tree = appJson.parseToJsonElement(text).jsonObject
    val read = appJson.decodeFromJsonElement(Settings.serializer(), tree)
    if (PRIMARY_VAULT_ID in read.perVault || tree.keys.none { it in VAULT_FIELDS }) return read
    val old = appJson.decodeFromJsonElement(VaultSettings.serializer(), JsonObject(tree.filterKeys { it in VAULT_FIELDS }))
    return read.copy(perVault = read.perVault + (PRIMARY_VAULT_ID to old))
}
