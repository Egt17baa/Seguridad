package app.seguridad

import app.seguridad.net.PwnedPasswords
import app.seguridad.net.secureClient
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.withAutoBackupResult
import app.seguridad.vault.AutoBackup
import app.seguridad.vault.AutoBackupResult
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
 * [backupFolder] opens the folder saved for the automatic copies (Android passes the SAF one), and [releaseFolder]
 * gives its access back when creating a new vault turns them off.
 */
class AppContainer(
    filesDir: File,
    /** The installed version (BuildConfig.VERSION_NAME), for the User-Agent of the HIBP queries. */
    appVersion: String = "0",
    backupFolder: (String) -> BackupTarget? = { null },
    releaseFolder: (String) -> Unit = {},
    /** Lives as long as the process: the automatic copies and the record of how they went. */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val settings = SettingsStore(File(filesDir, SETTINGS_FILE))

    /** The only connection of the app (Have I Been Pwned), with the eMudhra keys distrusted; built on first use. */
    val client: OkHttpClient by lazy { secureClient(appVersion) }

    /** Password vault (files/vault/vault.bin); never in backups (allowBackup is off). Lock time and Argon2id cost come from the settings. */
    val vault = VaultStore(
        File(File(filesDir, "vault"), "vault.bin"),
        kdf = { settings.settings.value.kdf.params },
        idleLockMs = { settings.settings.value.lockAfter.ms },
        // The copies' rotation does not know which vault each copy is of: a new vault (the old one damaged and set
        // aside, or deleted) would have it delete the old vault's copies. Turned off; Ajustes says so.
        beforeCreate = { settings.turnOffAutoBackupForNewVault()?.let(releaseFolder) },
    )

    val pwned: PwnedPasswords by lazy { PwnedPasswords(client) }

    /** Encrypted copies to the folder chosen in Ajustes, a few seconds after each change to the vault. */
    val autoBackup = AutoBackup(
        vault,
        folder = { settings.settings.value.autoBackupUri },
        open = backupFolder,
        appScope,
        now = System::currentTimeMillis,
        keep = { settings.settings.value.autoBackupKeep },
        lastCopyAt = { settings.settings.value.lastAutoBackupAt },
        onResult = ::recordAutoBackup,
    )

    /** Keeps how the last automatic copy went, for Ajustes, if it went to the folder still chosen. */
    private fun recordAutoBackup(result: AutoBackupResult) {
        appScope.launch {
            try {
                settings.update { it.withAutoBackupResult(result) }
            } catch (e: Exception) {
                // A full disk: Ajustes just shows the previous state; never closes the app.
            }
        }
    }
}
