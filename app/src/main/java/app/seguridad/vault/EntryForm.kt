package app.seguridad.vault

// The entry editor keeps a separate field for the LOGIN password and for the SEED's BIP-39 passphrase. With one shared
// field (1.0.0), a password generated under «Acceso» became the passphrase after switching to «Frase semilla», and a
// passphrase creates a different wallet: saved by mistake, it would point the user at an empty one.

/** What the field for [type] starts with: the saved password only when [existing] is of that same type. */
internal fun initialPassword(existing: VaultEntry?, type: EntryType): String =
    if (existing != null && existing.type == type) existing.password else ""

/** The `password` saved for an entry of [type]: the field of that type only, and nothing for NOTE and TOTP. */
internal fun passwordToSave(type: EntryType, loginPassword: String, seedPassphrase: String): String = when (type) {
    EntryType.LOGIN -> loginPassword
    EntryType.SEED -> seedPassphrase
    EntryType.NOTE, EntryType.TOTP, EntryType.IDENTITY, EntryType.CONTACT -> ""
}
