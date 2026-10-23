package app.seguridad.android

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * The whole app is secret: no screenshots, screen recording or previews in Recents, and no taps while another app
 * draws over it (tapjacking; Android 12+ also blocks that itself). Set once in onCreate, before any content, and
 * never cleared, so no screen can show without it.
 */
fun Activity.secureWindow() {
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    window.decorView.filterTouchesWhenObscured = true
}

/**
 * Dialogs and menus are windows of their own: they inherit FLAG_SECURE from the activity, but not the touch filter.
 * Call this inside a dialog's content to filter taps there too.
 */
@Composable
fun FilterObscuredTouches() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.rootView.filterTouchesWhenObscured = true
        onDispose { }
    }
}
