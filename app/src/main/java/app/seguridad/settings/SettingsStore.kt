package app.seguridad.settings

import app.seguridad.core.appJson
import app.seguridad.core.writeTextAtomically
import app.seguridad.vault.PRIMARY_VAULT_ID
import app.seguridad.vault.BackupSchedule
import app.seguridad.vault.BackupTargetSettings
import app.seguridad.vault.ReplacePrepared
import app.seguridad.vault.VaultLimits
import kotlinx.serialization.Serializable
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
        val earlier = fileExistedAtLoad || vaultExists
        // 2.0, task 5 bis (R-C8): the line about the new look is decided the same way, on its own: those who updated from
        // 1.3.x or 1.4.x already have the notice of the name decided, and they are the ones who see the new look.
        val next = s.copy(
            renameNoticePending = s.renameNoticePending ?: earlier,
            appearanceNoticePending = s.appearanceNoticePending ?: earlier,
        )
        if (next == s) return@withLock
        try {
            write(next)
        } catch (e: Exception) {
            _settings.value = next
        }
    }

    /**
     * A new vault is about to be created: turns the automatic copies off, as their rotation would delete the copies of
     * the previous vault (maybe the only good ones) without knowing which vault each belongs to. Whether they were on or
     * off, the previous vault's lists of copies (also those of folders no longer chosen) and any request to replace them
     * are forgotten (I1 of the CR re-review): otherwise choosing the same folder again would have the new vault rotate
     * the old vault's copies away. Returns the folders that were chosen, so the caller gives their permission back; none
     * if they were already off. Nothing is written when there is nothing to change.
     */
    suspend fun turnOffAutoBackupForNewVault(id: String): List<String> = mutex.withLock {
        val all = _settings.value
        val s = all.vault(id)
        val folders = s.backupFolders
        val next = s.copy(targets = emptyList(), pastTargets = emptyList(), autoBackupOffForNewVault = s.autoBackupOffForNewVault || folders.isNotEmpty())
        if (next != s) write(all.withVault(id) { next })
        folders
    }

    /**
     * «Quitar» a target ([withTargetRemoved]). True when this very write dropped a replacement of the old copies that was
     * still waiting in it: decided under the same lock as the write (T11-c), so a replacement that finishes just before
     * it is never reported as dropped.
     */
    suspend fun removeTarget(id: String, uri: String): Boolean = mutex.withLock {
        val before = _settings.value.vault(id)
        val after = before.withTargetRemoved(uri)
        if (after != before) write(_settings.value.withVault(id) { after })
        before.target(uri)?.replacePending == true
    }

    /**
     * An import is about to replace the vault: its automatic copies are no longer listed as ours, so none is ever rotated
     * away by the imported vault, and a request to replace them (A6) goes. Written to disk before the vault is saved:
     * throws if it cannot be (the import is then refused). The result says whether a request was dropped and can put
     * the lists and the requests back ([ReplacePrepared.undo]) if the vault is not saved after all, in the targets that
     * are still there.
     */
    suspend fun forgetAutoBackupCopiesForImport(id: String): ReplacePrepared = mutex.withLock {
        val before = _settings.value.vault(id)
        write(_settings.value.withVault(id) { before.forgettingAutoBackupCopies() })
        ReplacePrepared(before.replacePending) {
            updateVault(id) { now ->
                now.copy(
                    targets = now.targets.map { t ->
                        val was = before.target(t.uri) ?: return@map t
                        t.copy(
                            copies = was.copies, importCopies = was.importCopies, replacePending = was.replacePending,
                            replaceTargets = was.replaceTargets, replaceTold = was.replaceTold,
                        )
                    },
                    pastTargets = before.pastTargets,
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
        val custom = settings.custom?.let(::saneCustom)
        var s = if (custom == settings.custom) settings else settings.copy(custom = custom)
        // Review M-5 of task 5 bis: «Personalizado» without its tones is drawn as «Noche», so it is «Noche».
        if (s.palette == PaletteId.CUSTOM && s.custom == null) s = s.copy(palette = PaletteId.NIGHT)
        if (s.perVault.values.all { saneVault(it) == it }) return s
        return s.copy(perVault = s.perVault.mapValues { saneVault(it.value) })
    }

    private fun saneVault(settings: VaultSettings): VaultSettings {
        var s = settings
        if (s.kdf == KdfProfile.HIGH && !KdfProfile.HIGH.availableOn(heapKiB())) s = s.copy(kdf = KdfProfile.STANDARD)
        // 2.0, task 8: at most [VaultLimits.BACKUP_TARGETS_MAX] targets, never one folder twice, each keeping 3, 5 or 10.
        val targets = s.targets.distinctBy { it.uri }.take(VaultLimits.BACKUP_TARGETS_MAX)
            .map { if (it.keep in AUTO_BACKUP_KEEP_CHOICES) it else it.copy(keep = AUTO_BACKUP_KEEP_DEFAULT) }
        if (targets != s.targets) s = s.copy(targets = targets)
        if (s.pastTargets.size > PAST_TARGETS_MAX) s = s.copy(pastTargets = s.pastTargets.takeLast(PAST_TARGETS_MAX))
        return s
    }
}

/**
 * The settings of a vault as 1.4.3 (at the top of its settings.json) and the builds of 2.0 before task 8 (in
 * [Settings.perVault]) wrote them: one folder for the copies. Frozen: its field names are those files' names, read by
 * [decodeSettings] and turned into [VaultSettings] ([converted]); never written.
 */
@Serializable
internal data class LegacyVaultSettings(
    val kdf: KdfProfile = KdfProfile.STANDARD,
    val clockHighWaterMs: Long = 0,
    val autoBackupUri: String? = null,
    val autoBackupKeep: Int = AUTO_BACKUP_KEEP_DEFAULT,
    val lastAutoBackupError: String? = null,
    val lastAutoBackupAt: Long? = null,
    val autoBackupOffForNewVault: Boolean = false,
    val autoBackupCopies: List<String> = emptyList(),
    val autoBackupCopiesFolder: String? = null,
    val autoBackupReplacePending: Boolean = false,
    val autoBackupReplaceTargets: List<String>? = null,
    val autoBackupReplaceTold: List<String> = emptyList(),
    val importCopies: List<String> = emptyList(),
    val importCopiesFolder: String? = null,
) {
    /**
     * 2.0, task 8: the folder chosen becomes one [BackupSchedule.ON_CHANGE] target (what 1.4.3 did) with its number of
     * copies, its date and error, the lists that were of that folder and its A6 request, as they were. Lists of another
     * folder (the copies turned off, or another folder chosen since) go to [VaultSettings.pastTargets], as 1.4.3 kept them
     * for that folder chosen again. Nothing is lost.
     */
    fun converted(): VaultSettings {
        val uri = autoBackupUri
        val copiesHere = if (uri != null && autoBackupCopiesFolder == uri) autoBackupCopies else emptyList()
        val importsHere = if (uri != null && importCopiesFolder == uri) importCopies else emptyList()
        val target = uri?.let {
            BackupTargetSettings(
                it, autoBackupKeep, BackupSchedule.ON_CHANGE, lastAutoBackupAt, lastAutoBackupError, copiesHere,
                autoBackupReplacePending, autoBackupReplaceTargets, autoBackupReplaceTold, importsHere,
            )
        }
        // The lists of a folder that is not the chosen one, by folder (the copies' and the import copies' may be two).
        val past = LinkedHashMap<String, BackupTargetSettings>()
        fun keep(folder: String?, copies: List<String>, imports: List<String>) {
            if (folder == null || folder == uri || (copies.isEmpty() && imports.isEmpty())) return
            val was = past[folder] ?: BackupTargetSettings(folder, autoBackupKeep, BackupSchedule.ON_CHANGE)
            past[folder] = was.copy(copies = was.copies + copies, importCopies = was.importCopies + imports)
        }
        keep(autoBackupCopiesFolder, autoBackupCopies, emptyList())
        keep(importCopiesFolder, emptyList(), importCopies)
        return VaultSettings(
            kdf = kdf,
            clockHighWaterMs = clockHighWaterMs,
            targets = listOfNotNull(target),
            autoBackupOffForNewVault = autoBackupOffForNewVault,
            pastTargets = past.values.toList(),
        )
    }
}

/** The fields a [LegacyVaultSettings] has: at the top of a settings.json, they are 1.4.3's (or before), of the first vault. */
private val LEGACY_FIELDS: Set<String> = LegacyVaultSettings.serializer().descriptor.let { d -> (0 until d.elementsCount).map(d::getElementName).toSet() }

/** The fields only [LegacyVaultSettings] has: an object of [Settings.perVault] with any of them was written before task 8. */
private val LEGACY_ONLY_FIELDS: Set<String> =
    LEGACY_FIELDS - VaultSettings.serializer().descriptor.let { d -> (0 until d.elementsCount).map(d::getElementName).toSet() }

/**
 * Reads a settings.json. One of 1.4.3 or before has the copies, A6, the import copies and the cost at the top (2.0, task
 * 2): they are read, by their names, as the settings of [PRIMARY_VAULT_ID], unless the file already has those. A file
 * written by 2.0 has none at the top, so a vault deleted later never comes back from them. 2.0, task 8: the settings of a
 * vault with the one folder of before ([LegacyVaultSettings]), at the top or in [Settings.perVault], are converted to
 * [VaultSettings.targets].
 */
internal fun decodeSettings(text: String): Settings {
    val parsed = appJson.parseToJsonElement(text).jsonObject
    val tree = withConvertedVaults(parsed)
    // 2.0, task 5 bis: a «Personalizado» that cannot be read goes alone, so the lock time and the vaults' settings stay.
    val read = try {
        appJson.decodeFromJsonElement(Settings.serializer(), tree)
    } catch (e: IllegalArgumentException) { // SerializationException is one
        if ("custom" !in tree) throw e
        appJson.decodeFromJsonElement(Settings.serializer(), JsonObject(tree - "custom"))
    }
    if (PRIMARY_VAULT_ID in read.perVault || tree.keys.none { it in LEGACY_FIELDS }) return read
    val old = appJson.decodeFromJsonElement(LegacyVaultSettings.serializer(), JsonObject(tree.filterKeys { it in LEGACY_FIELDS }))
    return read.copy(perVault = read.perVault + (PRIMARY_VAULT_ID to old.converted()))
}

/** [tree] with each object of `perVault` written before task 8 (one folder) turned into one with targets. */
private fun withConvertedVaults(tree: JsonObject): JsonObject {
    val perVault = tree["perVault"] as? JsonObject ?: return tree
    if (perVault.values.none { v -> v is JsonObject && "targets" !in v && v.keys.any { it in LEGACY_ONLY_FIELDS } }) return tree
    val converted = perVault.mapValues { (_, v) ->
        if (v !is JsonObject || "targets" in v || v.keys.none { it in LEGACY_ONLY_FIELDS }) {
            v
        } else {
            try {
                appJson.encodeToJsonElement(VaultSettings.serializer(), appJson.decodeFromJsonElement(LegacyVaultSettings.serializer(), v).converted())
            } catch (e: IllegalArgumentException) {
                v
            }
        }
    }
    return JsonObject(tree + ("perVault" to JsonObject(converted)))
}
