package app.seguridad

import android.app.Application
import androidx.core.net.toUri
import app.seguridad.android.SafBackupTarget
import app.seguridad.ui.screens.DECRYPT_TMP_PREFIX
import app.seguridad.vault.VaultState
import app.seguridad.vault.wipePkTemp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SeguridadApp : Application() {
    val container: AppContainer by lazy {
        AppContainer(
            filesDir,
            cacheDir,
            appVersion = BuildConfig.VERSION_NAME,
            backupFolder = { uri -> SafBackupTarget(contentResolver, uri.toUri()) },
            releaseFolder = { uri -> SafBackupTarget.releaseFolder(contentResolver, uri) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        // A decrypted file left behind if the app died before it was saved.
        cacheDir.listFiles { f -> f.name.startsWith(DECRYPT_TMP_PREFIX) && f.name.endsWith(".tmp") }?.forEach { it.delete() }
        // Same for what «Abrir lo recibido» decrypted (one subfolder per operation): never survives a process death, nor
        // the vault being open. Wiped at start and each time the vault is not open, even with the app in the background
        // (the screen's own clean-up only runs while it is in view, and a save picker may be on top). A recursive delete,
        // so off the main thread. Nothing can be decrypted before the first one ends in practice (the vault opens first);
        // if it ever did, the copy would vanish and saving it fails closed («vuelve a descifrar»).
        // The first state collected is Loading, so it is also wiped at start.
        container.appScope.launch(Dispatchers.IO) {
            container.vault.state.collect { if (it !is VaultState.Unlocked) wipePkTemp(cacheDir) }
        }
        // The copy handed to another app by «Compartir»: its share time lived only in memory, so a new process deletes it.
        // Off the main thread; it cannot race a prepare in practice (the user must reach a tool screen and pick a photo
        // first), and if it ever did, ShareTemp is synchronized: the wipe runs before or after, never in the middle.
        container.appScope.launch(Dispatchers.IO) { container.shareTemp.wipeAll() }
        // A few hundred bytes, read before the first frame: the theme, the start notice and the lock time must be the
        // saved ones from the start (a damaged file just gives the defaults).
        runBlocking(Dispatchers.IO) { container.settings.load() }
    }
}
