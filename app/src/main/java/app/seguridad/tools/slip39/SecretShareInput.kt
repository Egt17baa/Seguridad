package app.seguridad.tools.slip39

import app.seguridad.crypto.Bip39
import app.seguridad.tools.hashlab.hexDecode
import java.text.Normalizer

/** The phrase or hex field: 24 words of up to 8 letters, or 64 hex digits with separators, fit with room to spare. */
const val SHARE_MAX_SECRET_CHARS = 1_000

/** The longest part on one line: [SLIP39_MAX_WORDS] words of up to 8 letters, a space or the line break after each. */
const val SHARE_MAX_PART_LINE_CHARS = SLIP39_MAX_WORDS * 9

/**
 * The parts field of «Recomponer»: the [SLIP39_MAX_PARTS] parts a combination may need, at their longest (59 words),
 * each on its line; 135 936 characters. Bounded, so a bigger paste is refused before any work (review M-1).
 */
const val SHARE_MAX_PARTS_CHARS = SLIP39_MAX_PARTS * SHARE_MAX_PART_LINE_CHARS

/** A number field of the scheme: 1 to 16. */
const val SHARE_MAX_NUMBER_CHARS = 2

private val BIP39_WORD_COUNTS = setOf(12, 15, 18, 21, 24)

/** The secret of the «Repartir» form ([secret], which the caller wipes) or the reason it is not one ([error]). */
class SplitInput(val secret: ByteArray?, val error: String?)

/**
 * The secret to split from the form: the entropy of a BIP-39 phrase when [isPhrase] (any case, any spacing, invisible
 * format characters dropped), else the bytes of the hex (spaces and colons allowed), 16 to 32 in an even number.
 */
fun splitInput(text: String, isPhrase: Boolean, bip39: Bip39): SplitInput {
    if (text.length > SHARE_MAX_SECRET_CHARS) return SplitInput(null, SHARE_SECRET_TOO_LONG)
    if (!isPhrase) {
        val bytes = hexDecode(text)
        if (bytes == null || bytes.size !in SLIP39_MIN_SECRET_BYTES..SLIP39_MAX_SECRET_BYTES || bytes.size % 2 != 0) {
            bytes?.fill(0)
            return SplitInput(null, SHARE_HEX_INVALID)
        }
        return SplitInput(bytes, null)
    }
    // Only the place of the first unknown word is shown, so no suggestion is worked out (review M-3).
    val words = bip39.normalize(cleanPhrase(text))
    val unknown = words.indexOfFirst { !bip39.isWord(it) }
    if (unknown >= 0) return SplitInput(null, sharePhraseUnknown(unknown + 1))
    if (words.size !in BIP39_WORD_COUNTS) return SplitInput(null, SHARE_PHRASE_COUNT)
    val entropy = bip39.entropyOf(words) ?: return SplitInput(null, SHARE_PHRASE_CHECKSUM)
    return SplitInput(entropy, null)
}

/** [text] with every kind of spacing as a plain space, NFKC and without invisible format characters, as for a part. */
private fun cleanPhrase(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text) {
        when {
            Character.isWhitespace(c) || Character.isSpaceChar(c) -> sb.append(' ')
            Character.getType(c) == Character.FORMAT.toInt() -> Unit
            // An unpaired surrogate stays a character that is no list word, never something the normaliser sees.
            Character.isSurrogate(c) -> sb.append('?')
            else -> sb.append(c)
        }
    }
    return Normalizer.normalize(sb, Normalizer.Form.NFKC)
}

/**
 * The scheme of the form: the group threshold and one [GroupSpec] per group, each number 1 to 16; null if a box is not
 * such a number. The rules between them (needed ≤ parts, 1 of 1) are [Slip39.splitSecret]'s, with its messages.
 */
fun schemeOf(groupThreshold: String, groups: List<GroupForm>): Pair<Int, List<GroupSpec>>? {
    if (groups.size !in 1..SLIP39_MAX_GROUPS) return null
    fun number(s: String) = s.trim().takeIf { it.length <= SHARE_MAX_NUMBER_CHARS }?.toIntOrNull()?.takeIf { it in 1..SLIP39_MAX_MEMBERS }
    val gt = number(groupThreshold) ?: return null
    val specs = groups.map { GroupSpec(number(it.threshold) ?: return null, number(it.count) ?: return null) }
    return gt to specs
}

/** The parts typed in «Recomponer», one per line, without the blank lines: «la parte N» is the N-th line with text. */
fun partsOf(text: String): List<String> = text.lines().filter { it.isNotBlank() }

/** The recovered secret as a BIP-39 phrase, when its length is one a phrase can have (16 to 32 bytes in steps of 4). */
fun phraseOrNull(bip39: Bip39, secret: ByteArray): String? =
    if (secret.size in 16..32 && secret.size % 4 == 0) bip39.phraseOf(secret).joinToString(" ") else null

/**
 * A split, as the form held it when «Repartir» was tapped: the secret, the passphrase and the scheme, copied once so
 * nothing the user types afterwards reaches the run. [error] when one of them is not valid; [master] and [passphrase]
 * are then null. The caller zeroes both when the run ends.
 */
class SplitRequest(
    val master: ByteArray?,
    val passphrase: CharArray?,
    val groupThreshold: Int,
    val groups: List<GroupSpec>,
    val error: String?,
)

/**
 * The [SplitRequest] of the form. Every raw text is measured before it is parsed, split or copied: an oversized one is
 * refused with no further work (the field limits already keep them short; this holds whoever calls it).
 */
fun splitRequest(
    secretInput: String,
    isPhrase: Boolean,
    passphrase: String,
    groupThreshold: String,
    groups: List<GroupForm>,
    bip39: Bip39,
): SplitRequest {
    fun refused(message: String) = SplitRequest(null, null, 0, emptyList(), message)
    if (passphrase.length > SLIP39_MAX_PASSPHRASE_CHARS) return refused(slip39PassphraseTooLong(SLIP39_MAX_PASSPHRASE_CHARS))
    if (secretInput.length > SHARE_MAX_SECRET_CHARS) return refused(SHARE_SECRET_TOO_LONG)
    if (groupThreshold.length > SHARE_MAX_NUMBER_CHARS) return refused(SHARE_NUMBER_INVALID)
    val scheme = schemeOf(if (groups.size == 1) "1" else groupThreshold, groups) ?: return refused(SHARE_NUMBER_INVALID)
    val input = splitInput(secretInput, isPhrase, bip39)
    val master = input.secret ?: return refused(input.error ?: SHARE_HEX_INVALID)
    return SplitRequest(master, passphrase.toCharArray(), scheme.first, scheme.second, null)
}

/** A combination, as the form held it when «Recomponer» was tapped: the parts and the passphrase, copied once. */
class CombineRequest(val parts: List<String>, val passphrase: CharArray?, val error: String?)

/** The [CombineRequest] of the form: the raw texts are measured first, and the number of parts before any is read. */
fun combineRequest(typedParts: String, passphrase: String): CombineRequest {
    if (passphrase.length > SLIP39_MAX_PASSPHRASE_CHARS) {
        return CombineRequest(emptyList(), null, slip39PassphraseTooLong(SLIP39_MAX_PASSPHRASE_CHARS))
    }
    if (typedParts.length > SHARE_MAX_PARTS_CHARS) return CombineRequest(emptyList(), null, SHARE_PARTS_TOO_LONG)
    val parts = partsOf(typedParts)
    if (parts.size > SLIP39_MAX_PARTS) return CombineRequest(emptyList(), null, slip39TooManyParts(SLIP39_MAX_PARTS))
    return CombineRequest(parts, passphrase.toCharArray(), null)
}
