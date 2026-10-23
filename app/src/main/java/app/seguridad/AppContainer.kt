package app.seguridad

import app.seguridad.net.PwnedPasswords
import app.seguridad.net.secureClient
import app.seguridad.settings.SettingsStore
import app.seguridad.vault.VaultStore
import okhttp3.OkHttpClient
import java.io.File

/** The settings file, in the app's private files directory. */
const val SETTINGS_FILE = "settings.json"

/** Manual dependency graph, one per process. Plain JVM: no Android types, so jvm-check compiles it too. */
class AppContainer(filesDir: File) {
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
}
