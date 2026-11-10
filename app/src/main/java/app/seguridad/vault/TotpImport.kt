package app.seguridad.vault

import app.seguridad.crypto.TotpSecret
import app.seguridad.crypto.base32Decode
import app.seguridad.crypto.hexLower

/**
 * The key a code is compared by: its decoded bytes as hex, so spacing, dashes and case do not matter. Null when it does
 * not decode. The one rule for duplicates of a code, here and in «Importar de otra app» (`imports/MergePlan.kt`).
 */
internal fun totpKeyOf(secret: String): String? = base32Decode(secret)?.let { b -> hexLower(b).also { b.fill(0) } }

/**
 * New TOTP entries for imported codes (Google Authenticator), leaving out any whose key is already in the vault or
 * earlier in the same list, so scanning a batch twice does not duplicate it. The second list names those left out.
 */
fun totpImportEntries(codes: List<TotpSecret>, existing: List<VaultEntry>, now: Long, newId: () -> String): Pair<List<VaultEntry>, List<String>> {
    val seen = existing.mapNotNullTo(HashSet()) { e -> e.totp?.let { totpKeyOf(it.secret) } }
    val entries = ArrayList<VaultEntry>()
    val dupes = ArrayList<String>()
    for (c in codes) {
        val name = c.issuer.ifBlank { c.account }.ifBlank { EntryType.TOTP.label }
        val key = totpKeyOf(c.secret)
        if (key != null && !seen.add(key)) {
            dupes += "«${listOf(c.issuer, c.account).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { name }}»: ya está en la bóveda"
            continue
        }
        entries += VaultEntry(id = newId(), type = EntryType.TOTP, title = name, totp = c, createdAt = now, updatedAt = now)
    }
    return entries to dupes
}
