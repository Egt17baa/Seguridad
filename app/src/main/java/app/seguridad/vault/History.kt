package app.seguridad.vault

/**
 * How many old passwords [VaultEntry.passwordHistory] keeps. 2.0, task 3 (ruling T3, I-2): it is still written, as in
 * 1.4.3, besides [VaultEntry.history] ([withVersion]): the limits of the history may trim the version that held an old
 * password (a large entry, or many edits of other fields), and this list keeps the 5 newest whatever happens to the
 * rest. [oldPasswords] reads both; «Borrar historial» empties both ([withoutHistory]).
 */
const val MAX_PASSWORD_HISTORY = 5

/**
 * This entry with [newPassword]. For a LOGIN whose non-empty password changes, the old one goes first in
 * [VaultEntry.passwordHistory] (at most [MAX_PASSWORD_HISTORY]); otherwise only `password` changes.
 */
fun VaultEntry.withNewPassword(newPassword: String, now: Long): VaultEntry {
    if (type != EntryType.LOGIN || password.isEmpty() || password == newPassword) return copy(password = newPassword)
    return copy(
        password = newPassword,
        passwordHistory = (listOf(PasswordChange(password, now)) + passwordHistory).take(MAX_PASSWORD_HISTORY),
    )
}
