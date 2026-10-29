package app.seguridad

import app.seguridad.net.PwnedPasswords
import app.seguridad.net.secureClient
import app.seguridad.settings.CLOCK_WRITE_STEP_MS
import app.seguridad.settings.SettingsStore
import app.seguridad.settings.clockMarkWorthWriting
import app.seguridad.settings.resettingClock
import app.seguridad.settings.seeingClock
import app.seguridad.settings.currentAutoBackupCopies
import app.seguridad.settings.withAutoBackupResult
import app.seguridad.tools.share.ShareTemp
import app.seguridad.vault.AutoBackup
import app.seguridad.vault.AutoBackupResult
import app.seguridad.vault.BackupTarget
import app.seguridad.vault.VaultStore
import kotlinx.coroutines.CancellationException
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
    /** The app's private cache; holds the copy handed to another app by «Compartir» (see [shareTemp]). */
    cacheDir: File,
    /** The installed version (BuildConfig.VERSION_NAME), for the User-Agent of the HIBP queries. */
    appVersion: String,
    backupFolder: (String) -> BackupTarget? = { null },
    releaseFolder: (String) -> Unit = {},
    /**
     * Lives as long as the process: the automatic copies, the record of how they went, and the clean-ups that must finish
     * even if the screen that started them goes away (the temporary files of «Abrir lo recibido»).
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
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
        // Same for an import: the copies stay on, but those made so far (the replaced vault's) are never deleted.
        beforeReplace = { settings.forgetAutoBackupCopies() },
        clockHighWater = { settings.settings.value.clockHighWaterMs },
        onClockSeen = ::rememberClock,
        onClockReset = ::resetClock,
    )

    /**
     * Keeps the highest time seen in the settings (it only rises). Called at every unlock and touch and once at start,
     * so it writes only when the time is [CLOCK_WRITE_STEP_MS] past the mark: the mark may lag by that much, well
     * under the 5 minutes of slack.
     */
    fun rememberClock(seen: Long) {
        if (!clockMarkWorthWriting(settings.settings.value.clockHighWaterMs, seen)) return
        appScope.launch { settings.update { it.seeingClock(seen) } }
    }

    /** After a master password check the mark restarts at [now], even lower: a clock set forward by mistake heals. */
    private fun resetClock(now: Long) {
        appScope.launch { settings.update { it.resettingClock(now) } }
    }

    /** The one shared image copy (cacheDir/compartir/): wiped at start and on return after 10 minutes, never on lock. */
    val shareTemp = ShareTemp(cacheDir, System::currentTimeMillis)

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
        copies = { settings.settings.value.currentAutoBackupCopies },
        onResult = ::recordAutoBackup,
    )

    /**
     * Keeps how the last automatic copy went, for Ajustes, and the copies it made (for the next rotation), if it went
     * to the folder still chosen. Waited for by [AutoBackup] before its next copy, so that one sees this list.
     */
    private suspend fun recordAutoBackup(result: AutoBackupResult) {
        try {
            settings.update { it.withAutoBackupResult(result) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A full disk: Ajustes just shows the previous state, and the copy just made is not rotated; never closes the app.
        }
    }
}
