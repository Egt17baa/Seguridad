package app.seguridad.vault

/**
 * How many old passwords 1.4.3 and before kept in [VaultEntry.passwordHistory]. 2.0 (task 3) no longer writes that list:
 * a change goes to [VaultEntry.history] ([withVersion]), and the list is only read ([oldPasswords]) until «Borrar
 * historial» empties it ([withoutHistory]).
 */
const val MAX_PASSWORD_HISTORY = 5
