package app.seguridad.ui.theme

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.function.Consumer
import kotlin.math.roundToInt

/*
 * 2.1, Aspecto (task 3): the window of «Transparente». The app asks Android to draw the wallpaper behind the activity's
 * own window (FLAG_SHOW_WALLPAPER, set and cleared at run time, so the manifest does not change, B-2) and, from Android
 * 12 and when the phone allows it, to blur it there (the window's background blur). Android composes the wallpaper: the
 * app never gets its image (O-A3), and no permission is needed. The activity is still an opaque one: what shows behind
 * it is the wallpaper only, never the app before it or another app (O-A4). FLAG_SECURE is set once in onCreate
 * (secureWindow) and nothing here touches it.
 *
 * Ruling R-A4: Android's setting to remove animations does not take the wallpaper away. The app adds no movement of its
 * own; a live wallpaper is the system's, and the system usually stops it with that setting. «Reducir transparencias» and
 * «Alto contraste» do take it away (the look is then opaque, [Look.wallpaperBehind] is false).
 */

/** What the activity's window does: show the wallpaper behind it ([showWallpaper]) and blur it by [blurRadiusDp] (0: no blur). */
data class WindowLook(val showWallpaper: Boolean, val blurRadiusDp: Int)

/** How far the window blurs the wallpaper, in dp: the radius of the panels' blur (Glass.kt). */
private const val WALLPAPER_BLUR_DP = 24

/**
 * The window for [showWallpaper] ([showsWallpaper] of the look) on Android [sdk]: blurred only from Android 12 and while
 * the phone allows blurs between windows ([crossWindowBlur]; a battery saver or the developer options can turn them off).
 */
fun windowLookFor(showWallpaper: Boolean, sdk: Int, crossWindowBlur: Boolean): WindowLook =
    WindowLook(showWallpaper, if (showWallpaper && sdk >= Build.VERSION_CODES.S && crossWindowBlur) WALLPAPER_BLUR_DP else 0)

/**
 * Puts [look] on [window]. With the wallpaper, the flag and a transparent background (its opacity is what makes Android
 * give the window's surface an alpha channel, so the wallpaper shows where the app's layer lets it); without it, the
 * flag cleared and [opaqueBackground], the background the window had when the activity was created (the surface is
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        window.setBackgroundBlurRadius((look.blurRadiusDp * window.context.resources.displayMetrics.density).roundToInt())
    }
}

/** Whether the phone blurs between windows right now, followed while it changes; always false before Android 12. */
@Composable
fun crossWindowBlurEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    return crossWindowBlurNow()
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun crossWindowBlurNow(): Boolean {
    val windowManager = LocalContext.current.getSystemService(WindowManager::class.java) ?: return false
    var enabled by remember(windowManager) { mutableStateOf(windowManager.isCrossWindowBlurEnabled) }
    DisposableEffect(windowManager) {
        // Called on the main thread (the overload without an executor), when the phone turns the blurs on or off.
        val listener = Consumer<Boolean> { enabled = it }
        windowManager.addCrossWindowBlurEnabledListener(listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}
