package app.seguridad.android

import android.app.Activity
import android.os.Build
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
 *
 * COV2-4 (audit 1.3.0): on Android 14+ the window is also marked accessibility data sensitive, so only accessibility
 * services that declare themselves accessibility tools (TalkBack, Switch Access, Voice Access) read its texts and act
 * on it; any other service gets nothing. Android 14 already infers it from the touch filter above; set explicitly so
 * it never depends on that rule (or on a manufacturer's change to it). Before Android 14 there is no such defence.
 */
fun Activity.secureWindow() {
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    window.decorView.filterTouchesWhenObscured = true
    window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    markAccessibilitySensitive(window.decorView)
}

/** COV2-4: see [secureWindow]. Descendants left on AUTO (Compose's view and its virtual nodes) inherit it. */
private fun markAccessibilitySensitive(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        view.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
    }
}

/**
 * Dialogs and menus are windows of their own: they inherit FLAG_SECURE from the activity, but not the touch filter,
 * the autofill exclusion nor the accessibility mark (see [secureWindow]). Call this inside a dialog's content to set
 * all three there too.
 */
@Composable
fun FilterObscuredTouches() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.rootView.filterTouchesWhenObscured = true
        view.rootView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.rootView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
        }
        onDispose { }
    }
}
