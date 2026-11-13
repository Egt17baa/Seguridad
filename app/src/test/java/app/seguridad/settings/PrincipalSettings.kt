package app.seguridad.settings

import app.seguridad.vault.BackupSchedule
import app.seguridad.vault.PRIMARY_VAULT_ID

// 2.0, task 2: the tests of the copies, A6 and the cost written for 1.4.3's Settings now read and write those of the first
// vault ([Settings.perVault]), where a settings.json of 1.4.3 puts them.

/** The settings of the first vault. */
val Settings.principal: VaultSettings get() = vault(PRIMARY_VAULT_ID)

/** [SettingsStore.updateVault] of the first vault. */
suspend fun SettingsStore.updatePrincipal(change: (VaultSettings) -> VaultSettings) = updateVault(PRIMARY_VAULT_ID, change)

// 2.0, task 8: the tests of 1.x's one folder (the copies, A6, the import copies) keep their words: a vault with one
// target reads and writes as 1.4.3's settings did, through the same conversion a settings.json of 1.4.3 goes through
// ([LegacyVaultSettings.converted]). The tests of several targets (AutoBackupTargetsTest) use the targets themselves.

/** These settings as 1.4.3 would have kept them: the first target is the folder; a list of another folder is a past one. */
internal fun VaultSettings.legacyView(): LegacyVaultSettings {
    val t = targets.firstOrNull()
    val pastCopies = pastTargets.lastOrNull { it.copies.isNotEmpty() }
    val pastImports = pastTargets.lastOrNull { it.importCopies.isNotEmpty() }
    val copiesHere = t?.copies.orEmpty().isNotEmpty() || pastCopies == null
    val importsHere = t?.importCopies.orEmpty().isNotEmpty() || pastImports == null
    return LegacyVaultSettings(
        kdf = kdf,
        clockHighWaterMs = clockHighWaterMs,
        autoBackupUri = t?.uri,
        autoBackupKeep = t?.keep ?: pastTargets.lastOrNull()?.keep ?: AUTO_BACKUP_KEEP_DEFAULT,
        lastAutoBackupError = t?.lastError,
        lastAutoBackupAt = t?.lastAt,
        autoBackupOffForNewVault = autoBackupOffForNewVault,
        autoBackupCopies = if (copiesHere) t?.copies.orEmpty() else pastCopies!!.copies,
        autoBackupCopiesFolder = if (copiesHere) t?.uri?.takeIf { t.copies.isNotEmpty() } else pastCopies!!.uri,
        autoBackupReplacePending = t?.replacePending == true,
        autoBackupReplaceTargets = t?.replaceTargets,
        autoBackupReplaceTold = t?.replaceTold.orEmpty(),
        importCopies = if (importsHere) t?.importCopies.orEmpty() else pastImports!!.importCopies,
        importCopiesFolder = if (importsHere) t?.uri?.takeIf { t.importCopies.isNotEmpty() } else pastImports!!.uri,
    )
}

/** A vault's settings as 1.4.3 would have had them, with one folder ([LegacyVaultSettings.converted]). */
internal fun oldVaultSettings(
    autoBackupUri: String? = null,
    autoBackupKeep: Int = AUTO_BACKUP_KEEP_DEFAULT,
    lastAutoBackupError: String? = null,
    lastAutoBackupAt: Long? = null,
    autoBackupOffForNewVault: Boolean = false,
    autoBackupCopies: List<String> = emptyList(),
    autoBackupCopiesFolder: String? = null,
    autoBackupReplacePending: Boolean = false,
    autoBackupReplaceTargets: List<String>? = null,
    autoBackupReplaceTold: List<String> = emptyList(),
    importCopies: List<String> = emptyList(),
    importCopiesFolder: String? = null,
): VaultSettings = LegacyVaultSettings(
    autoBackupUri = autoBackupUri, autoBackupKeep = autoBackupKeep, lastAutoBackupError = lastAutoBackupError,
    lastAutoBackupAt = lastAutoBackupAt, autoBackupOffForNewVault = autoBackupOffForNewVault, autoBackupCopies = autoBackupCopies,
    autoBackupCopiesFolder = autoBackupCopiesFolder, autoBackupReplacePending = autoBackupReplacePending,
    autoBackupReplaceTargets = autoBackupReplaceTargets, autoBackupReplaceTold = autoBackupReplaceTold, importCopies = importCopies,
    importCopiesFolder = importCopiesFolder,
).converted()

/** These settings with 1.4.3's fields changed as given (the others as they are), through [legacyView]. */
internal fun VaultSettings.oneTarget(
    autoBackupUri: String? = legacyView().autoBackupUri,
    autoBackupKeep: Int = legacyView().autoBackupKeep,
    lastAutoBackupError: String? = legacyView().lastAutoBackupError,
    lastAutoBackupAt: Long? = legacyView().lastAutoBackupAt,
    autoBackupOffForNewVault: Boolean = this.autoBackupOffForNewVault,
    autoBackupCopies: List<String> = legacyView().autoBackupCopies,
    autoBackupCopiesFolder: String? = legacyView().autoBackupCopiesFolder,
    autoBackupReplacePending: Boolean = legacyView().autoBackupReplacePending,
    autoBackupReplaceTargets: List<String>? = legacyView().autoBackupReplaceTargets,
    autoBackupReplaceTold: List<String> = legacyView().autoBackupReplaceTold,
    importCopies: List<String> = legacyView().importCopies,
    importCopiesFolder: String? = legacyView().importCopiesFolder,
): VaultSettings {
    val schedule = targets.firstOrNull()?.schedule
    val next = legacyView().copy(
        autoBackupUri = autoBackupUri, autoBackupKeep = autoBackupKeep, lastAutoBackupError = lastAutoBackupError,
        lastAutoBackupAt = lastAutoBackupAt, autoBackupOffForNewVault = autoBackupOffForNewVault, autoBackupCopies = autoBackupCopies,
        autoBackupCopiesFolder = autoBackupCopiesFolder, autoBackupReplacePending = autoBackupReplacePending,
        autoBackupReplaceTargets = autoBackupReplaceTargets, autoBackupReplaceTold = autoBackupReplaceTold, importCopies = importCopies,
        importCopiesFolder = importCopiesFolder,
    ).converted()
    return next.copy(targets = next.targets.map { if (schedule != null) it.copy(schedule = schedule) else it })
}

val VaultSettings.autoBackupUri: String? get() = targets.firstOrNull()?.uri
val VaultSettings.autoBackupKeep: Int get() = legacyView().autoBackupKeep
val VaultSettings.lastAutoBackupError: String? get() = targets.firstOrNull()?.lastError
val VaultSettings.lastAutoBackupAt: Long? get() = targets.firstOrNull()?.lastAt
val VaultSettings.autoBackupCopies: List<String> get() = legacyView().autoBackupCopies
val VaultSettings.autoBackupCopiesFolder: String? get() = legacyView().autoBackupCopiesFolder
val VaultSettings.autoBackupReplacePending: Boolean get() = targets.firstOrNull()?.replacePending == true
val VaultSettings.autoBackupReplaceTargets: List<String>? get() = targets.firstOrNull()?.replaceTargets
val VaultSettings.autoBackupReplaceTold: List<String> get() = targets.firstOrNull()?.replaceTold.orEmpty()
val VaultSettings.importCopies: List<String> get() = legacyView().importCopies
val VaultSettings.importCopiesFolder: String? get() = legacyView().importCopiesFolder

/** 1.4.3's: the copies listed in the folder chosen now (the first target's). */
val VaultSettings.currentAutoBackupCopies: List<String> get() = targets.firstOrNull()?.copies.orEmpty()

/** 1.4.3's: the import copies listed in the folder chosen now (the first target's). */
val VaultSettings.currentImportCopies: List<String> get() = targets.firstOrNull()?.importCopies.orEmpty()

/** 1.4.3's «Elegir carpeta» or «Cambiar de carpeta» for its one folder. */
fun VaultSettings.withFolderChosen(chosen: String): VaultSettings {
    val t = targets.firstOrNull() ?: return withTargetAdded(chosen, BackupSchedule.ON_CHANGE, legacyView().autoBackupKeep)
    return withTargetFolderChanged(t.uri, chosen)
}

/** 1.4.3's «Desactivar» of its one folder. */
fun VaultSettings.withAutoBackupTurnedOff(): VaultSettings = targets.firstOrNull()?.let { withTargetRemoved(it.uri) } ?: this

/** 1.4.3's «Desactivar»: [SettingsStore.removeTarget] of the one folder; false with none. */
suspend fun SettingsStore.turnOffAutoBackup(id: String): Boolean {
    val uri = settings.value.vault(id).targets.firstOrNull()?.uri ?: return false
    return removeTarget(id, uri)
}
