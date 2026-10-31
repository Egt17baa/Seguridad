package app.seguridad

import android.app.Application
import android.os.SystemClock
import app.seguridad.android.SafBackupTarget
import app.seguridad.android.revokeSharedCopy
import app.seguridad.tools.qr.QrHandoff
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
            // Only a folder the app holds a persisted permission for (CADENAS-1): any other throws, and no copy goes there.
            backupFolder = { uri -> SafBackupTarget.open(contentResolver, uri) },
            releaseFolder = { uri -> SafBackupTarget.releaseFolder(contentResolver, uri) },
            // Counts deep sleep too (System.nanoTime does not): the idle lock must see the time the screen was off.
            elapsedRealtime = SystemClock::elapsedRealtime,
            // ANDROID-1: no app keeps reading a shared copy's URI once the copy is gone.
            revokeShare = { file -> revokeSharedCopy(this, file) },
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
        // The first state collected is Loading, so it is also wiped at start. A QR text the inspector handed to a vault
        // screen (a 2FA key among them) goes at the same moments.
        container.appScope.launch(Dispatchers.IO) {
            container.vault.state.collect {
                if (it !is VaultState.Unlocked) {
                    QrHandoff.clear()
                    wipePkTemp(cacheDir)
                }
            }
        }
        // The copy handed to another app by «Compartir»: its share time lived only in memory, so a new process deletes it.
        // Off the main thread; it cannot race a prepare in practice (the user must reach a tool screen and pick a photo
        // first), and if it ever did, ShareTemp is synchronized: the wipe runs before or after, never in the middle.
        container.appScope.launch(Dispatchers.IO) { container.shareTemp.wipeAll() }
        // A few hundred bytes, read before the first frame: the theme, the start notice and the lock time must be the
        // saved ones from the start (a damaged file just gives the defaults).
        runBlocking(Dispatchers.IO) { container.settings.load() }
        container.rememberClock(System.currentTimeMillis()) // The clock mark also rises when the app starts, not only on use.
    }
}
