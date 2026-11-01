package app.seguridad.vault

import app.seguridad.health.WEAK_BITS
import app.seguridad.health.estimateBits

/** Fewest characters of a master password (UTF-16 units, as the text field counts them). */
const val MIN_MASTER = 10

/** What the screens show while the new master has fewer than [MIN_MASTER] characters. */
const val MASTER_TOO_SHORT = "Mínimo $MIN_MASTER caracteres"

/** What the screens show when the new master is easy to guess. */
const val MASTER_TOO_WEAK =
    "Esta contraseña es fácil de adivinar. Usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una)."

/**
 * Why [candidate] does not do as a NEW master password, or null if it does (CRY-A01): first [MASTER_TOO_SHORT], then
 * [MASTER_TOO_WEAK]. The strength is the one of «Salud» ([estimateBits] against [WEAK_BITS]).
 *
 * Only for a password that is being chosen: creating the vault and changing the master. Opening the vault, the
 * fingerprint and importing a copy never come through here, so no vault or copy that exists stops opening, and neither
 * does the password of a copy made with a weaker master. The rule is not in [VaultStore] either: the tests and the test
 * data create vaults with throwaway masters.
 *
 * It is an estimate with no dictionary: it does not know words or personal data. «Barcelona1» gives about 59 bits and
 * passes, so the rule removes the obvious cases («contraseña», «1234567890», «aaaaaaaaaa», «qwertyuiop») but does not
 * guarantee that a master is strong. What the Generator proposes with its usual options (a phrase of 5 words, with any
 * separator and list; a password of 16 characters) always passes; the tests check it. A password of digits only needs
 * more than 16 characters to reach the threshold.
 */
fun newMasterProblem(candidate: String): String? = when {
    candidate.length < MIN_MASTER -> MASTER_TOO_SHORT
    estimateBits(candidate) < WEAK_BITS -> MASTER_TOO_WEAK
    else -> null
}
