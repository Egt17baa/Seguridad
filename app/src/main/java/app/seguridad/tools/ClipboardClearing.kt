package app.seguridad.tools

/** Label of everything the app copies (android/Clipboard.kt), so a copy of ours can be recognised later. */
const val CLIPBOARD_OWN_LABEL = "Seguridad"

/**
 * TECLADO-3 (audit 1.3.0): how long until a copy of ours, found when the app gets the focus again, has to go. The timer
 * of the copy lives only in the process that made it, so after Android closed the app nothing else clears it. [nowMs]
 * and [copiedAtMs] are wall-clock times (ClipDescription.getTimestamp is System.currentTimeMillis). 0 means at once:
 * the time has passed, or the clock was set back after copying (coverage-2, mejora 5), which never keeps the copy.
 */
fun ownCopyClearDelay(nowMs: Long, copiedAtMs: Long, clearAfterMs: Long): Long {
    val age = nowMs - copiedAtMs
    return if (age < 0 || age >= clearAfterMs) 0L else clearAfterMs - age
}

/**
 * At the time of a copy of ours, whether to clear the clipboard: one rule for the copy's own timer and for the one
 * armed on reopening (F4 review M3). [described] is whether Android gave the clip's description: without it the
 * clipboard is hidden (Android 10+, the app without the input focus) or empty, and it is cleared, never leaving a
 * secret there. With it, only that same copy of ours goes: another app's copy stays, also one with no label (an image,
 * a ClipData without a label), and a newer copy of ours has its own timer. [sameCopy] (its text, or its time for a copy
 * found on reopening) runs only when the label is ours, so another app's clip is never read: reading it shows Android
 * 12+'s clipboard access toast and takes its content for nothing (F4 re-review N1).
 */
fun wipeOwnCopy(described: Boolean, seenLabel: CharSequence?, sameCopy: () -> Boolean): Boolean =
    !described || seenLabel?.toString() == CLIPBOARD_OWN_LABEL && sameCopy()
