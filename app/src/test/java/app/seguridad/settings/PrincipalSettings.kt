package app.seguridad.settings

import app.seguridad.vault.PRIMARY_VAULT_ID

// 2.0, task 2: the tests of the copies, A6 and the cost written for 1.4.3's Settings now read and write those of the first
// vault ([Settings.perVault]), where a settings.json of 1.4.3 puts them.

/** The settings of the first vault. */
val Settings.principal: VaultSettings get() = vault(PRIMARY_VAULT_ID)

/** [SettingsStore.updateVault] of the first vault. */
suspend fun SettingsStore.updatePrincipal(change: (VaultSettings) -> VaultSettings) = updateVault(PRIMARY_VAULT_ID, change)
