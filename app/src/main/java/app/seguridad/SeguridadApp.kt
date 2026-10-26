package app.seguridad

import android.app.Application
import androidx.core.net.toUri
import app.seguridad.android.SafBackupTarget
import app.seguridad.ui.screens.DECRYPT_TMP_PREFIX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class SeguridadApp : Application() {
    val container: AppContainer by lazy {
        AppContainer(filesDir, backupFolder = { uri -> SafBackupTarget(contentResolver, uri.toUri()) })
    }

    override fun onCreate() {
        super.onCreate()
        // A decrypted file left behind if the app died before it was saved.
        cacheDir.listFiles { f -> f.name.startsWith(DECRYPT_TMP_PREFIX) && f.name.endsWith(".tmp") }?.forEach { it.delete() }
        // A few hundred bytes, read before the first frame: the theme, the start notice and the lock time must be the
        // saved ones from the start (a damaged file just gives the defaults).
        runBlocking(Dispatchers.IO) { container.settings.load() }
    }
}
