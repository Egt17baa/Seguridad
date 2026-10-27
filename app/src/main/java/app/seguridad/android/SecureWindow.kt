package app.seguridad.android

import android.app.Activity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * The whole app is secret: no screenshots, screen recording or previews in Recents, and no taps while another app
 * draws over it (tapjacking; Android 12+ also blocks that itself). Nothing typed here reaches an autofill service
 * either (another app, maybe synced to the cloud): Compose marks its own view important for autofill, but a parent
 * that excludes its descendants keeps every field out of what the service gets, to fill or to save. Set once in
 * onCreate, before any content, and never cleared, so no screen can show without it.
 */
fun Activity.secureWindow() {
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    window.decorView.filterTouchesWhenObscured = true
    window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
}

/**
 * Dialogs and menus are windows of their own: they inherit FLAG_SECURE from the activity, but not the touch filter
 * nor the autofill exclusion (see [secureWindow]). Call this inside a dialog's content to set both there too.
 */
@Composable
fun FilterObscuredTouches() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.rootView.filterTouchesWhenObscured = true
        view.rootView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose { }
    }
}
