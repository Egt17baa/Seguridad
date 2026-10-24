package app.seguridad.vault

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
