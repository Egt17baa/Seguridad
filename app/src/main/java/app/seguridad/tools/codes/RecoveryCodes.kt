package app.seguridad.tools.codes

import app.seguridad.crypto.secureRandom
import app.seguridad.tools.recoveryNoteTitle
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import java.security.SecureRandom

/** 32 symbols: the digits 2 to 9 and the letters but I and O, so that none can be read as another (0 O, 1 I l). */
const val RECOVERY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

const val RECOVERY_CODE_COUNT = 10
private const val RECOVERY_CODE_SYMBOLS = 10

private fun oneCode(random: SecureRandom): String {
    // 32 symbols over 10 places: 50 bits each; nextInt(bound) draws each symbol uniformly.
    val symbols = CharArray(RECOVERY_CODE_SYMBOLS) { RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)] }
    return String(symbols, 0, 4) + "-" + String(symbols, 4, 4) + "-" + String(symbols, 8, 2)
}

/** Ten random recovery codes of the form XXXX-XXXX-XX. A set with any repeated code is drawn again whole. */
fun recoveryCodes(random: SecureRandom = secureRandom): List<String> {
    while (true) {
        val codes = List(RECOVERY_CODE_COUNT) { oneCode(random) }
        if (codes.toSet().size == codes.size) return codes
    }
}

/** The secure note «Códigos de recuperación · [site]» with one code per line. */
fun recoveryCodesNote(site: String, codes: List<String>, now: Long, id: String): VaultEntry =
    VaultEntry(id = id, type = EntryType.NOTE, title = recoveryNoteTitle(oneLine(site)), notes = codes.joinToString("\n"), createdAt = now, updatedAt = now)
