package app.seguridad.android

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import app.seguridad.SeguridadApp
import app.seguridad.settings.ClipboardClear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Outlives any screen, so leaving the screen right after copying still clears the clipboard. */
private val clipboardScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Label of everything this app copies, so a leftover copy can be recognised later. */
const val CLIP_LABEL = "Seguridad"

/** How long a copy stays, from Ajustes. */
private fun clearAfterMs(context: Context): Long {
    val seconds = (context.applicationContext as? SeguridadApp)?.container?.settings?.settings?.value?.clipboard?.seconds
        ?: ClipboardClear.S45.seconds
    return seconds * 1_000L
}

private fun ClipboardManager.wipe() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clearPrimaryClip() else setPrimaryClip(ClipData.newPlainText("", ""))
}

/** Copies [text] marked as sensitive (hidden in the keyboard's clipboard preview) and clears it after the chosen time if unchanged. */
fun copySensitive(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(CLIP_LABEL, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    cm.setPrimaryClip(clip)
    val wait = clearAfterMs(context)
    clipboardScope.launch {
        delay(wait)
        // Someone else's copy since then (an image, another app's text): leave it alone.
        val label = runCatching { cm.primaryClipDescription?.label }.getOrNull()
        if (label != null && label != CLIP_LABEL) return@launch
        // Ours, or hidden from us (Android 10+ hides the clipboard from apps in the background): clear it, never
        // leave a secret there. If it is readable and no longer our text, it was replaced: leave it.
        val current = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        if (current == null || current == text) cm.wipe()
    }
}

/**
 * Clears a copy of ours older than the chosen time, left behind because the app was closed before its timer ran
 * (Android 8–12 never clear the clipboard themselves). Call when the app has focus (needed to read it).
 */
fun clearStaleCopy(context: Context) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val desc = runCatching { cm.primaryClipDescription }.getOrNull() ?: return
    if (desc.label == CLIP_LABEL && System.currentTimeMillis() - desc.timestamp >= clearAfterMs(context)) cm.wipe()
}
