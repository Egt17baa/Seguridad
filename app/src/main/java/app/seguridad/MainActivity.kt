package app.seguridad

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seguridad.android.clearStaleCopy
import app.seguridad.android.secureWindow
import app.seguridad.ui.AppNavHost
import app.seguridad.ui.theme.SeguridadTheme
import app.seguridad.ui.theme.isDark
import app.seguridad.vault.VaultLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A FragmentActivity because the biometric prompt needs one. The app has no other entry point and takes no extras. */
class MainActivity : FragmentActivity() {
    private val container get() = (application as SeguridadApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before anything is drawn, for every screen, dialog and menu of the app.
        secureWindow()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val dark = settings.theme.isDark()
            // System bar icons follow the app's theme, not the phone's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            SeguridadTheme(settings.theme) { AppNavHost(container) }
        }
    }

    override fun onStart() {
        super.onStart()
        // Back from a picker or the QR scanner: if the vault was kept open longer than allowed (the idle ticker may
        // not have run while the app was in the background), it locks before anything is shown.
        container.vault.lockIfIdle()
        VaultLock.keepOpenOnce = false
        // Back from sharing an image: the copy goes once 10 minutes have passed. Off the main thread, so a share being
        // prepared at the same time (a write of up to 50 MB, under the same lock) never holds up the activity.
        container.appScope.launch(Dispatchers.IO) { container.shareTemp.onReturn() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) clearStaleCopy(this)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app locks the vault, except while a picker or the QR scanner we opened is on top.
        // Rotating, folding or resizing recreates the activity without leaving the app: no lock then.
        if (!VaultLock.keepOpenOnce && !isChangingConfigurations) container.vault.lock()
    }
}
