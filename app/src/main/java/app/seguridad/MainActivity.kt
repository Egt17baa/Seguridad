package app.seguridad

import android.content.Intent
import android.graphics.Color
import android.os.Build
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
import app.seguridad.ui.ClipboardHistoryNotice
import app.seguridad.ui.NoLearningInput
import app.seguridad.ui.theme.SeguridadTheme
import app.seguridad.ui.theme.applyWindowLook
import app.seguridad.ui.theme.currentLook
import app.seguridad.ui.theme.isDark
import app.seguridad.ui.theme.multiWindowNow
import app.seguridad.ui.theme.themeWindowBackground
import app.seguridad.ui.theme.windowLookFor
import app.seguridad.vault.AWAY_BACKSTOP_MS
import app.seguridad.vault.VaultLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A FragmentActivity because the biometric prompt needs one. The app has no other entry point and takes no extras.
 * Navigation Compose reads the activity's intent for a deep link, extras included, every time a NavController is set up
 * (one per open vault, `key(vault)` in AppNavHost), so onCreate replaces the intent it was started with by a plain
 * launcher one (2.0, audit B-1), and the activity never takes a new one (no onNewIntent).
 */
class MainActivity : FragmentActivity() {
    private val container get() = (application as SeguridadApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // B-1: whatever another app put in the intent that started Clavero never reaches the navigation.
        intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(this, MainActivity::class.java)
        // Before anything is drawn, for every screen, dialog and menu of the app.
        secureWindow()
        // 2.1, Aspecto (task 3): the theme's window background, put back when «Transparente» stops showing the wallpaper
        // (code-review: the theme's drawable, not the decor view's, which Android may have wrapped).
        val opaqueBackground = themeWindowBackground(this)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val dark = settings.theme.isDark()
            // Split screen or a floating window: «Transparente» does not show the wallpaper then (another app is behind).
            val multiWindow = multiWindowNow(this)
            // System bar icons follow the app's theme, not the phone's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            SeguridadTheme(settings, multiWindow = multiWindow) {
                // «Transparente»: the window shows the wallpaper (sharp) and stops when the look is opaque, in multi-window
                // or another style; the next effect puts its own, so nothing to undo on dispose (ui/theme/WallpaperWindow.kt).
                val windowLook = windowLookFor(currentLook)
                DisposableEffect(windowLook) {
                    applyWindowLook(window, windowLook, opaqueBackground)
                    onDispose { }
                }
                // A7: no keyboard learns from any field, dialogs included. A14: the clipboard notice, for every screen.
                NoLearningInput {
                    AppNavHost(container)
                    ClipboardHistoryNotice(container)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Back from a picker or the QR scanner: if the vault was kept open longer than allowed (the idle ticker may
        // not have run while the app was in the background), it locks before anything is shown.
        container.vault.lockIfIdle()
        // After lockIfIdle: a lock for the time spent away is left to each screen's own rule for time away, as leaving is.
        container.foreground = true
        VaultLock.keepOpenOnce = false
        // 2.0, task 8: «Las copias con calendario se hacen al abrir la app».
        container.appOpened()
        // Back from sharing an image: the copy goes once 10 minutes have passed. Off the main thread, so a share being
        // prepared at the same time (a write of up to 50 MB, under the same lock) never holds up the activity.
        container.appScope.launch(Dispatchers.IO) { container.shareTemp.onReturn() }
    }

    /**
     * COV2-3: in split screen or a floating window, Seguridad stays visible (no onStop) while the user works in the
     * other app. Losing the top position to it starts the same backstop as a picker ([AWAY_BACKSTOP_MS], counted from
     * this moment with «Al salir»; the app-wide idle ticker checks it), and «PIN y códigos» counts the time away. The
     * app's own dialogs and menus are windows of this activity and never change it; a picker or the scanner already has
     * the backstop. Back on top, the time away is checked first, as in onStart. Android 10+; before, only one app is
     * resumed, so onPause and onResume tell it.
     */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        awayInMultiWindow(!isTopResumedActivity)
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) awayInMultiWindow(true)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) awayInMultiWindow(false)
    }

    private fun awayInMultiWindow(away: Boolean) {
        if (away) {
            // Only in multi-window: full screen, losing the top means leaving (onStop locks then), or a system dialog.
            if (isInMultiWindowMode && !isChangingConfigurations) container.leftForAnotherApp()
        } else {
            container.backFromAnotherApp()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) clearStaleCopy(this)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app locks the vault, except while a picker or the QR scanner we opened is on top.
        // Rotating, folding or resizing recreates the activity without leaving the app: no lock then.
        // Before the lock: a screen that sees it (as «PIN y códigos») must know it came from leaving the app. A rotation,
        // a fold or a resize re-creates the activity without leaving the app: it stays in the foreground (there is no
        // lock then either, and onStart sets it to true again right after).
        if (!isChangingConfigurations) container.foreground = false
        // A14: a copy waiting for the notice is dropped on leaving the app, whether or not the vault was open.
        if (!VaultLock.keepOpenOnce && !isChangingConfigurations) container.clipboardNotice.drop()
        // 2.0, task 6 (review m-1): the copy in two steps offers the password on the return from outside the app. Audit
        // B-4: here the lock below ends the offer anyway (leaving always locks); the offer lives only in split screen or a
        // floating window, where another app taking the focus marks it (container.leftForAnotherApp).
        if (!VaultLock.keepOpenOnce && !isChangingConfigurations) container.twoStepCopy.markLeft()
        // 2.0, task 9, ronda 1 (I-1): a QR text waiting at the vault's gate (a wifi password, a 2FA key) goes too.
        if (!isChangingConfigurations) container.leftApp(VaultLock.keepOpenOnce)
        // 2.1.2, C-1: through the container, which counts the leave, so a new vault being made or imported is locked too.
        if (!VaultLock.keepOpenOnce && !isChangingConfigurations) container.lockOnLeave()
    }
}
