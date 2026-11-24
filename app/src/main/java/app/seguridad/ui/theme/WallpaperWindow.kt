package app.seguridad.ui.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.MultiWindowModeChangedInfo
import androidx.core.util.Consumer

/*
 * 2.1, Aspecto (task 3): the window of «Transparente». The app asks Android to draw the wallpaper behind the activity's
 * own window (FLAG_SHOW_WALLPAPER, set and cleared at run time, so the manifest does not change, B-2). Android composes
 * the wallpaper: the app never gets its image (O-A3), and no permission is needed. The activity is still an opaque one:
 * what shows behind it is the wallpaper only, never the app before it or another app (O-A4). FLAG_SECURE is set once in
 * onCreate (secureWindow) and nothing here touches it.
 *
 * Owner decision (24-11-2026): the window does not blur the wallpaper. Android blurs what is behind a window (its
 * background blur) only when the window is translucent, which O-A4 forbids, so the wallpaper is always sharp
 * and the layer over it is the one for a sharp image (Look.layerAlpha).
 *
 * Branch review I-1: in split screen or in a floating or desktop window, what is under the activity's window is another
 * app, so the wallpaper is not shown then ([multiWindowNow], Look.wallpaperBehind).
 *
 * F-1 of the final audit (owner decision (b), 24-11-2026): only from Android 14 (Look.wallpaperBehind, TRANSPARENT_MIN_SDK),
 * where secureWindow() asks Android, for every look, not to pass the taps on the window to the wallpaper's app. This
 * switch never touches that.
 *
 * Ruling R-A4: Android's setting to remove animations does not take the wallpaper away. The app adds no movement of its
 * own; a live wallpaper is the system's, and the system usually stops it with that setting. «Reducir transparencias» and
 * «Alto contraste» do take it away (the look is then opaque, [Look.wallpaperBehind] is false).
 */

/** What the activity's window does: show the wallpaper behind it or not. */
data class WindowLook(val showWallpaper: Boolean)

/** The window for [look]: the wallpaper exactly when the theme draws its layer over it ([Look.wallpaperBehind]). */
fun windowLookFor(look: Look): WindowLook = WindowLook(showWallpaper = look.wallpaperBehind)

/**
 * Puts [look] on [window]. With the wallpaper, the flag and a transparent background (its opacity is what makes Android
 * give the window's surface an alpha channel, so the wallpaper shows where the app's layer lets it); without it, the
 * flag cleared and [opaqueBackground], the theme's window background ([themeWindowBackground]) (the surface is
 * opaque again). Each call sets everything, so the last look wins whatever came before.
 */
fun applyWindowLook(window: Window, look: WindowLook, opaqueBackground: Drawable?) {
    if (look.showWallpaper) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(opaqueBackground)
    }
}

/**
 * The window background [context]'s theme sets (`android:windowBackground`), as the theme gives it: the decor view's own
 * background may be that drawable wrapped by Android (insets), and passing it back would wrap it again.
 */
fun themeWindowBackground(context: Context): Drawable? {
    val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
    try {
        return attrs.getDrawable(0)
    } finally {
        attrs.recycle()
    }
}

/**
 * Whether [activity] is in split screen or in a floating or desktop window now, followed while it changes (the
 * activity handles its size changes itself, so it is not created again then).
 */
@Composable
fun multiWindowNow(activity: ComponentActivity): Boolean {
    var multiWindow by remember(activity) { mutableStateOf(activity.isInMultiWindowMode) }
    DisposableEffect(activity) {
        // Called on the main thread when the activity enters or leaves multi-window mode.
        val listener = Consumer<MultiWindowModeChangedInfo> { multiWindow = it.isInMultiWindowMode }
        activity.addOnMultiWindowModeChangedListener(listener)
        multiWindow = activity.isInMultiWindowMode
        onDispose { activity.removeOnMultiWindowModeChangedListener(listener) }
    }
    return multiWindow
}
