package app.seguridad.android

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import app.seguridad.SeguridadApp
import app.seguridad.settings.ClipboardClear
import app.seguridad.tools.CLIPBOARD_OWN_LABEL
import app.seguridad.tools.copyOrAskNotice
import app.seguridad.tools.ownCopyClearDelay
import app.seguridad.tools.wipeOwnCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Outlives any screen, so leaving the screen right after copying still clears the clipboard. */
private val clipboardScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Label of everything this app copies, so a leftover copy can be recognised later. */
const val CLIP_LABEL = CLIPBOARD_OWN_LABEL

/** How long a copy stays, from Ajustes. */
private fun clearAfterMs(context: Context): Long {
    val seconds = (context.applicationContext as? SeguridadApp)?.container?.settings?.settings?.value?.clipboard?.seconds
        ?: ClipboardClear.S45.seconds
    return seconds * 1_000L
}

private fun ClipboardManager.wipe() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clearPrimaryClip() else setPrimaryClip(ClipData.newPlainText("", ""))
}

/**
 * Copies [text] marked as sensitive (hidden in the keyboard's clipboard preview) and clears it after the chosen time if
 * unchanged. On Android 8 to 12 (no EXTRA_IS_SENSITIVE) the first copy waits for the A14 notice at the root of the app
 * (ClipboardHistoryNotice) and is made only if the user accepts it ([copyOrAskNotice]). [onCopied] runs once the text is
 * in the clipboard (at once, or on accept), never if it is not. Returns whether it was copied now.
 */
fun copySensitive(context: Context, text: String, onCopied: (() -> Unit)? = null): Boolean {
    val container = (context.applicationContext as? SeguridadApp)?.container
        ?: return true.also { copySensitiveNow(context, text); onCopied?.invoke() }
    val app = context.applicationContext
    return copyOrAskNotice(
        text,
        container.clipboardNotice,
        accepted = container.settings.settings.value.clipboardHistoryNoticeAccepted,
        sdk = Build.VERSION.SDK_INT,
        write = { copySensitiveNow(app, it) },
        onCopied = onCopied,
    )
}

/** [copySensitive] without the A14 notice: what it writes, now or on «Entendido, copiar». */
fun copySensitiveNow(context: Context, text: String) {
    // The application's: the timer below outlives any screen (F4 review M2).
    val cm = context.applicationContext.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(CLIP_LABEL, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    cm.setPrimaryClip(clip)
    val wait = clearAfterMs(context)
    clipboardScope.launch {
        delay(wait)
        // Hidden from us (Android 10+ hides the clipboard from an app without the input focus) or empty: clear it, never
        // leave a secret there. Readable: someone else's copy since then (an image, another app's text, labelled or not)
        // stays, and so does a newer copy of ours (another text). Text unreadable under our label: ours, cleared. The
        // text is read only under our label (F4 re-review N1): reading another app's clip shows Android 12+'s toast.
        val desc = runCatching { cm.primaryClipDescription }.getOrNull()
        val clear = wipeOwnCopy(described = desc != null, seenLabel = desc?.label) {
            runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull().let { it == null || it == text }
        }
        if (clear) runCatching { cm.wipe() }
    }
}

/**
 * The text in the clipboard, or `null` if it is empty, holds no text, or Android does not let the app read it (in the
 * background, or any exception from the system service). Never throws: pasting must not close the app.
 */
fun readClipboardText(context: Context): String? = runCatching {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
    if (clip == null || clip.itemCount == 0) null else clip.getItemAt(0)?.text?.toString()
}.getOrNull()

/** The timer of [clearStaleCopy]; only one, replaced on each call (the main thread only). */
private var reopenedCopyTimer: Job? = null

/**
 * TECLADO-3: a copy of ours left behind because Android closed the app before its timer ran (no timer outlives the
 * process: there is no reliable one without new permissions or components). Call when the app has focus, which is
 * needed to read it. Older than the chosen time, or with the clock set back since: cleared now. Younger (the app was
 * reopened before the time): cleared when its time is up, while the app is alive, with the same rule as the copy's own
 * timer ([wipeOwnCopy]). A copy of this run also has its own timer; clearing it twice is harmless.
 */
fun clearStaleCopy(context: Context) {
    // The application's ClipboardManager: the timer below outlives the Activity and must not keep it (F4 review M2).
    val cm = context.applicationContext.getSystemService(ClipboardManager::class.java) ?: return
    val desc = runCatching { cm.primaryClipDescription }.getOrNull() ?: return
    if (desc.label?.toString() != CLIP_LABEL) return
    val copiedAt = desc.timestamp
    val wait = ownCopyClearDelay(System.currentTimeMillis(), copiedAt, clearAfterMs(context))
    reopenedCopyTimer?.cancel()
    reopenedCopyTimer = null
    if (wait == 0L) {
        runCatching { cm.wipe() }
        return
    }
    reopenedCopyTimer = clipboardScope.launch {
        delay(wait)
        val now = runCatching { cm.primaryClipDescription }.getOrNull()
        if (wipeOwnCopy(described = now != null, seenLabel = now?.label) { now?.timestamp == copiedAt }) runCatching { cm.wipe() }
    }
}
