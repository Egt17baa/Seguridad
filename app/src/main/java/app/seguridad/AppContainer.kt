package app.seguridad

import app.seguridad.net.PwnedPasswords
import app.seguridad.net.secureClient
import app.seguridad.settings.SettingsStore
import app.seguridad.vault.AutoBackup
import app.seguridad.vault.BackupTarget
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

/** The settings file, in the app's private files directory. */
const val SETTINGS_FILE = "settings.json"

/**
 * Manual dependency graph, one per process. Plain JVM: no Android types, so jvm-check compiles it too.
 * [backupFolder] opens the folder saved for the automatic copies (Android passes the SAF one).
 */
class AppContainer(
    filesDir: File,
    backupFolder: (String) -> BackupTarget? = { null },
    /** Lives as long as the process: the automatic copies and the record of how they went. */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val settings = SettingsStore(File(filesDir, SETTINGS_FILE))

    /** The only connection of the app (Have I Been Pwned), with the eMudhra keys distrusted; built on first use. */
    val client: OkHttpClient by lazy { secureClient() }

    /** Password vault (files/vault/vault.bin); never in backups (allowBackup is off). Lock time and Argon2id cost come from the settings. */
    val vault = VaultStore(
        File(File(filesDir, "vault"), "vault.bin"),
        kdf = { settings.settings.value.kdf.params },
        idleLockMs = { settings.settings.value.lockAfter.ms },
    )

    val pwned: PwnedPasswords by lazy { PwnedPasswords(client) }

    /** Encrypted copies to the folder chosen in Ajustes, a few seconds after each change to the vault. */
    val autoBackup = AutoBackup(
        vault,
        target = { settings.settings.value.autoBackupUri?.let(backupFolder) },
        appScope,
        now = System::currentTimeMillis,
        keep = { settings.settings.value.autoBackupKeep },
        onError = ::recordAutoBackup,
    )

    /** Keeps how the last automatic copy went, for Ajustes: [error] null means it was written now. */
    private fun recordAutoBackup(error: String?) {
        val at = System.currentTimeMillis()
        appScope.launch {
            try {
                settings.update { s ->
                    when {
                        s.autoBackupUri == null -> s // Turned off meanwhile: nothing to show.
                        error == null -> s.copy(lastAutoBackupError = null, lastAutoBackupAt = at)
                        else -> s.copy(lastAutoBackupError = error)
                    }
                }
            } catch (e: Exception) {
                // A full disk: Ajustes just shows the previous state; never closes the app.
            }
        }
    }
}
