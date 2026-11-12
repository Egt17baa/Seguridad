package app.seguridad.vault

/*
 * 2.0, task 3: the history of each entry (design «Historial por entrada»). Every save that changes something keeps the
 * entry as it was, as an [EntryVersion] at the front of [VaultEntry.history], within the limits of [VaultLimits] (20
 * versions and 64 KiB; the oldest go first). A version holds every field but the attachments, which are not versioned,
 * and has no history of its own.
 *
 * Old passwords (review M-4 of task 1, ruling T3 I-2): a change of password goes to the history and also, as in 1.4.3,
 * to [VaultEntry.passwordHistory] (the 5 newest, [withNewPassword]), which the limits of the history never trim.
 * [oldPasswords] reads both, so no old password disappears from the card or from «Salud» because the history was
 * trimmed; «Borrar historial» empties both.
 */

/** The entry as a version: without history, attachments or the legacy list of old passwords. */
private fun VaultEntry.asVersion(): VaultEntry =
    if (history.isEmpty() && attachments.isEmpty() && passwordHistory.isEmpty()) this
    else copy(history = emptyList(), attachments = emptyList(), passwordHistory = emptyList())

/** What a change is compared on: everything but the dates of use and update, the history, the attachments and the legacy list. */
private fun VaultEntry.comparable(): VaultEntry =
    copy(updatedAt = 0, lastUsedAt = null, history = emptyList(), attachments = emptyList(), passwordHistory = emptyList())

/** Whether [other] differs from this entry in anything the history keeps (not [VaultEntry.lastUsedAt] nor [VaultEntry.updatedAt]). */
fun VaultEntry.changedFrom(other: VaultEntry): Boolean = comparable() != other.comparable()

/**
 * This entry (as it is about to be saved, with its history) with [previous] (the entry as it was stored) added at the
 * front of its history as an [EntryVersion] saved at [now], without history or attachments, when any field changed
 * ([changedFrom]: [VaultEntry.lastUsedAt], [VaultEntry.updatedAt], the history and the attachments do not count). Then
 * within [VaultLimits.HISTORY_MAX_VERSIONS] and [VaultLimits.HISTORY_MAX_BYTES], the oldest versions first. The same
 * object when nothing changed.
 */
fun VaultEntry.withVersion(previous: VaultEntry, now: Long): VaultEntry {
    if (!changedFrom(previous)) return this
    return copy(history = listOf(EntryVersion(now, previous.asVersion())) + history).fittedHistory()
}

/**
 * The old passwords of this entry, newest first, each once (its newest date): from [VaultEntry.history], each version
 * of type [EntryType.LOGIN] whose non-empty password differs from the one that came after it (the next newer version,
 * or this entry), with the date it was replaced ([EntryVersion.savedAt]); and the legacy [VaultEntry.passwordHistory]
 * of 1.4.3 and before that the history no longer holds. The entry card and «Salud» read this.
 */
fun VaultEntry.oldPasswords(): List<PasswordChange> {
    val found = LinkedHashMap<String, PasswordChange>()
    var after = password
    for (v in history) {
        val p = v.entry.password
        if (v.entry.type == EntryType.LOGIN && p.isNotEmpty() && p != after && p !in found) found[p] = PasswordChange(p, v.savedAt)
        after = p
    }
    for (old in passwordHistory) if (old.password.isNotEmpty() && old.password !in found) found[old.password] = old
    return found.values.sortedByDescending { it.changedAt }
}

/** This entry without history nor the list of old passwords («Borrar historial»); the same object when it had none. */
fun VaultEntry.withoutHistory(): VaultEntry =
    if (history.isEmpty() && passwordHistory.isEmpty()) this else copy(history = emptyList(), passwordHistory = emptyList())
