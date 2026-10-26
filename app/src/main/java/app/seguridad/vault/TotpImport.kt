package app.seguridad.vault

import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.base32Decode

/** The key a code is compared by: its decoded bytes as hex, so spacing, dashes and case do not matter. */
private fun keyOf(secret: String): String? = base32Decode(secret)?.let { b -> b.joinToString("") { "%02x".format(it) }.also { b.fill(0) } }

/**
 * New TOTP entries for imported codes (Google Authenticator), leaving out any whose key is already in the vault or
 * earlier in the same list, so scanning a batch twice does not duplicate it. The second list names those left out.
 */
fun totpImportEntries(codes: List<TotpSecret>, existing: List<VaultEntry>, now: Long, newId: () -> String): Pair<List<VaultEntry>, List<String>> {
    val seen = existing.mapNotNullTo(HashSet()) { e -> e.totp?.let { keyOf(it.secret) } }
    val entries = ArrayList<VaultEntry>()
    val dupes = ArrayList<String>()
    for (c in codes) {
        val name = c.issuer.ifBlank { c.account }.ifBlank { EntryType.TOTP.label }
        val key = keyOf(c.secret)
        if (key != null && !seen.add(key)) {
            dupes += "«${listOf(c.issuer, c.account).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { name }}»: ya está en la bóveda"
            continue
        }
        entries += VaultEntry(id = newId(), type = EntryType.TOTP, title = name, totp = c, createdAt = now, updatedAt = now)
    }
    return entries to dupes
}
