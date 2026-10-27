package app.seguridad

import android.app.Application
import androidx.core.net.toUri
import app.seguridad.android.SafBackupTarget
import app.seguridad.ui.screens.DECRYPT_TMP_PREFIX
import app.seguridad.vault.wipePkTemp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SeguridadApp : Application() {
    val container: AppContainer by lazy {
        AppContainer(
            filesDir,
            appVersion = BuildConfig.VERSION_NAME,
            backupFolder = { uri -> SafBackupTarget(contentResolver, uri.toUri()) },
            releaseFolder = { uri -> SafBackupTarget.releaseFolder(contentResolver, uri) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        // A decrypted file left behind if the app died before it was saved.
        cacheDir.listFiles { f -> f.name.startsWith(DECRYPT_TMP_PREFIX) && f.name.endsWith(".tmp") }?.forEach { it.delete() }
        // Same for what «Abrir lo recibido» decrypted (one subfolder per operation): never survives a process death. A
        // recursive delete, so off the main thread. Nothing can be decrypted before it ends in practice (the vault opens
        // first); if it ever did, the copy would vanish and saving it fails closed («vuelve a descifrar»).
        container.appScope.launch(Dispatchers.IO) { wipePkTemp(cacheDir) }
        // A few hundred bytes, read before the first frame: the theme, the start notice and the lock time must be the
        // saved ones from the start (a damaged file just gives the defaults).
        runBlocking(Dispatchers.IO) { container.settings.load() }
    }
}
