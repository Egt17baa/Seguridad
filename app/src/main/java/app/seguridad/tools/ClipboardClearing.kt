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
 * At the time of a copy of ours found on reopening, whether to clear the clipboard. [seenLabel] and [seenTimestamp] are
 * what the clipboard says now, both `null` if Android hides it (Android 10+, app not in the foreground) or it is empty:
 * then it is cleared, as copySensitive does, never leaving a secret there. Otherwise only that same copy goes; another
 * app's copy stays, and a newer copy of ours has its own timer.
 */
fun wipeReopenedCopy(seenLabel: CharSequence?, seenTimestamp: Long?, expectedTimestamp: Long): Boolean =
    seenLabel == null && seenTimestamp == null ||
        seenLabel?.toString() == CLIPBOARD_OWN_LABEL && seenTimestamp == expectedTimestamp
