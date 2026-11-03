package app.seguridad.tools.slip39

import app.seguridad.crypto.secureRandom
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale

/** One group of a split: [threshold] of its [count] member parts give the group's share. */
data class GroupSpec(val threshold: Int, val count: Int)

/** Anything that keeps a split or a combination from going on, in Spanish for the screen. Never carries a secret. */
class Slip39Failure(message: String) : Exception(message)

/** What «Repartir» takes: 128 to 256 bits, a multiple of 16 (SLIP-0039 needs at least 128 bits, in 16-bit steps). */
const val SLIP39_MIN_SECRET_BYTES = 16
const val SLIP39_MAX_SECRET_BYTES = 32

/** The format's own limits: 4 bits per index and threshold. */
const val SLIP39_MAX_GROUPS = 16
const val SLIP39_MAX_MEMBERS = 16

/**
 * The most parts one combination can need (16 groups of 16), so more is refused before any work. The parts field of the
 * screen holds this many at their longest (SHARE_MAX_PARTS_CHARS).
 */
const val SLIP39_MAX_PARTS = SLIP39_MAX_GROUPS * SLIP39_MAX_MEMBERS

/** A part of 59 words of 8 letters is 530 characters; this leaves room for spaces of more, not for a flood. */
const val SLIP39_MAX_PART_CHARS = 1_000
const val SLIP39_MAX_PASSPHRASE_CHARS = 256

/** 20 words: a 128-bit secret. 59 words: 512 bits, the longest that «Recomponer» takes (a BIP-32 seed of other tools). */
const val SLIP39_MIN_WORDS = 20
const val SLIP39_MAX_WORDS = 59

/**
 * The iteration exponent e: PBKDF2 runs 10 000 · 2^e iterations in all. New splits use 1, as the spec's tools do. A part
 * may ask for up to 15 (327 million iterations, minutes on a phone): Clavero takes up to [SLIP39_MAX_EXPONENT] (2.56
 * million) and refuses more before any PBKDF2.
 */
const val SLIP39_DEFAULT_EXPONENT = 1
const val SLIP39_MAX_EXPONENT = 8

private const val RADIX = 1024
private const val RADIX_BITS = 10
private const val HEADER_WORDS = 4
private const val CHECKSUM_WORDS = 3
private const val ID_BITS = 15
private const val ROUNDS = 4
private const val ROUND_ITERATIONS = 2_500

/** One decoded part (SLIP-0039 «Format of the share mnemonic»): indices from 0, thresholds and counts as they are (1..16). */
internal data class Share(
    val identifier: Int,
    val extendable: Boolean,
    val exponent: Int,
    val groupIndex: Int,
    val groupThreshold: Int,
    val groupCount: Int,
    val memberIndex: Int,
    val memberThreshold: Int,
    val value: ByteArray,
) {
    /** The same part twice (a paste repeated): every field and the value. */
    fun sameAs(o: Share) = identifier == o.identifier && extendable == o.extendable && exponent == o.exponent &&
        groupIndex == o.groupIndex && groupThreshold == o.groupThreshold && groupCount == o.groupCount &&
        memberIndex == o.memberIndex && memberThreshold == o.memberThreshold && MessageDigest.isEqual(value, o.value)
}

/**
 * SLIP-0039, Shamir's secret sharing for mnemonic codes, written from the specification
 * (slip-0039.md of the SLIPs repository) with the English list of 1 024 [words]. Two levels: the encrypted master
 * secret is split among groups, and each group's share among its members. The passphrase goes through a 4-round Feistel
 * network with PBKDF2-HMAC-SHA256 (Bouncy Castle, on the passphrase's own bytes, so the phone and the tests run the same
 * code). Every input is bounded before any work; a refusal is a [Slip39Failure]; every intermediate array is zeroed.
 */
class Slip39(words: List<String>) {
    private val list: Array<String> = words.toTypedArray()
    private val index: Map<String, Int> = words.withIndex().associate { (i, w) -> w to i }

    init {
        require(list.size == RADIX && index.size == RADIX) { "SLIP-39 needs 1024 distinct words" }
    }

    /**
     * GenerateShares of SLIP-0039: one list of parts per group, in order. [master] is 16 to 32 bytes in an even number;
     * the extendable flag is set and the identifier is random. [check] runs between the PBKDF2 rounds and the groups
     * (the screen passes its coroutine's `ensureActive`, so a wipe stops the work).
     */
    fun splitSecret(
        master: ByteArray,
        passphrase: CharArray,
        groupThreshold: Int,
        groups: List<GroupSpec>,
        iterationExponent: Int = SLIP39_DEFAULT_EXPONENT,
        random: SecureRandom = secureRandom,
        check: () -> Unit = {},
    ): List<List<String>> {
        if (master.size !in SLIP39_MIN_SECRET_BYTES..SLIP39_MAX_SECRET_BYTES || master.size % 2 != 0) throw Slip39Failure(SLIP39_SECRET_LENGTH)
        if (groups.size !in 1..SLIP39_MAX_GROUPS || groupThreshold !in 1..groups.size) throw Slip39Failure(SLIP39_GROUP_LIMITS)
        for (g in groups) if (g.count !in 1..SLIP39_MAX_MEMBERS || g.threshold !in 1..g.count) throw Slip39Failure(SLIP39_MEMBER_LIMITS)
        for (g in groups) if (g.threshold == 1 && g.count > 1) throw Slip39Failure(SLIP39_ONE_OF_MANY)
        if (iterationExponent !in 0..SLIP39_MAX_EXPONENT) throw Slip39Failure(slip39Exponent(SLIP39_MAX_EXPONENT))
        val id = random.nextInt(1 shl ID_BITS)
        val pass = asciiPassphrase(passphrase)
        val ems = try {
            feistel(master, pass, iterationExponent, id, extendable = true, encrypt = true, check)
        } finally {
            pass.fill(0)
        }
        try {
            val groupShares = shamirSplit(groupThreshold, groups.size, ems, random)
            try {
                return groups.mapIndexed { gi, g ->
                    check()
                    val members = shamirSplit(g.threshold, g.count, groupShares[gi], random)
                    try {
                        members.mapIndexed { mi, value ->
                            encode(Share(id, true, iterationExponent, gi, groupThreshold, groups.size, mi, g.threshold, value))
                        }
                    } finally {
                        members.forEach { it.fill(0) }
                    }
                }
            } finally {
                groupShares.forEach { it.fill(0) }
            }
        } finally {
            ems.fill(0)
        }
    }

    /**
     * Combining the shares of SLIP-0039: the master secret of [mnemonics] (one part each; blank ones are skipped and an
     * exact repeat counts once) under [passphrase]. Every rule of the spec is checked and a broken one throws
     * [Slip39Failure], whose message names the part by its place in [mnemonics] (from 1) and never a word. A wrong
     * passphrase is not an error: it gives another secret (SLIP-0039 by design). The caller wipes the result.
     */
    fun combineShares(mnemonics: List<String>, passphrase: CharArray, check: () -> Unit = {}): ByteArray {
        if (mnemonics.size > SLIP39_MAX_PARTS) throw Slip39Failure(slip39TooManyParts(SLIP39_MAX_PARTS))
        val pass = asciiPassphrase(passphrase)
        val shares = ArrayList<Share>()
        try {
            // An exact repeat is found among the parts with the same group and member index only: linear, not n².
            val byIndex = HashMap<Int, MutableList<Share>>()
            mnemonics.forEachIndexed { i, m ->
                val s = decode(m, i + 1) ?: return@forEachIndexed
                val same = byIndex.getOrPut(s.groupIndex * SLIP39_MAX_MEMBERS + s.memberIndex) { ArrayList(1) }
                if (same.any { it.sameAs(s) }) {
                    s.value.fill(0)
                } else {
                    same += s
                    shares += s
                }
            }
            if (shares.isEmpty()) throw Slip39Failure(SLIP39_NO_PARTS)
            val first = shares[0]
            for (s in shares) {
                if (s.identifier != first.identifier || s.extendable != first.extendable || s.exponent != first.exponent ||
                    s.groupThreshold != first.groupThreshold || s.groupCount != first.groupCount || s.value.size != first.value.size
                ) {
                    throw Slip39Failure(SLIP39_NOT_SAME_SET)
                }
            }
            if (first.groupThreshold > first.groupCount) throw Slip39Failure(SLIP39_MORE_GROUPS_THAN_COUNT)
            if (first.exponent > SLIP39_MAX_EXPONENT) throw Slip39Failure(slip39Exponent(SLIP39_MAX_EXPONENT))
            val byGroup = shares.groupBy { it.groupIndex }
            if (byGroup.size < first.groupThreshold) throw Slip39Failure(slip39TooFewGroups(first.groupThreshold, byGroup.size))
            if (byGroup.size > first.groupThreshold) throw Slip39Failure(slip39TooManyGroups(first.groupThreshold, byGroup.size))
            for ((gi, members) in byGroup) {
                val t = members[0].memberThreshold
                if (members.any { it.memberThreshold != t }) throw Slip39Failure(slip39ThresholdMismatch(gi + 1))
                if (members.map { it.memberIndex }.toSet().size != members.size) throw Slip39Failure(slip39SameIndex(gi + 1))
                if (members.size != t) throw Slip39Failure(slip39WrongCount(gi + 1, t, members.size))
            }
            check()
            val groupValues = ArrayList<ByteArray>()
            try {
                for (members in byGroup.values) {
                    val xs = IntArray(members.size) { members[it].memberIndex }
                    groupValues += shamirRecover(members[0].memberThreshold, xs, members.map { it.value })
                }
                val groupXs = byGroup.keys.toIntArray()
                val ems = shamirRecover(first.groupThreshold, groupXs, groupValues)
                try {
                    return feistel(ems, pass, first.exponent, first.identifier, first.extendable, encrypt = false, check)
                } finally {
                    ems.fill(0)
                }
            } finally {
                groupValues.forEach { it.fill(0) }
            }
        } finally {
            shares.forEach { it.value.fill(0) }
            pass.fill(0)
        }
    }

    /**
     * The fields of one part, [part] being its place for the messages; null if it has no word. Bounded first; the words
     * are read case-insensitively (NFKC, invisible format characters dropped, any spacing between them), the checksum
     * is checked before any field is trusted, and the padding must be at most 8 bits, all zero.
     */
    internal fun decode(mnemonic: String, part: Int): Share? {
        if (mnemonic.length > SLIP39_MAX_PART_CHARS) throw Slip39Failure(slip39PartTooLong(part))
        // null marks a token that cannot be a word; one that was only invisible characters is dropped.
        val tokens = tokens(mnemonic).map(::normalizedWord).filter { it != "" }
        if (tokens.isEmpty()) return null
        if (tokens.size !in SLIP39_MIN_WORDS..SLIP39_MAX_WORDS) {
            throw Slip39Failure(slip39WordCount(part, tokens.size, SLIP39_MIN_WORDS, SLIP39_MAX_WORDS))
        }
        val w = IntArray(tokens.size)
        try {
            tokens.forEachIndexed { i, t -> w[i] = t?.let(index::get) ?: throw Slip39Failure(slip39UnknownWord(i + 1, part)) }
            val extendable = (w[1] shr 4) and 1 == 1
            if (!Rs1024.verify(if (extendable) Rs1024.CUSTOMIZATION_EXTENDABLE else Rs1024.CUSTOMIZATION, w)) {
                throw Slip39Failure(slip39Checksum(part))
            }
            val bits = (w.size - HEADER_WORDS - CHECKSUM_WORDS) * RADIX_BITS
            val padding = bits % 16
            if (padding > 8) throw Slip39Failure(slip39BadLength(part))
            val value = ByteArray((bits - padding) / 8)
            var badPadding = 0
            for (k in 0 until bits) {
                val bit = (w[HEADER_WORDS + k / RADIX_BITS] shr (RADIX_BITS - 1 - k % RADIX_BITS)) and 1
                if (k < padding) {
                    badPadding = badPadding or bit
                } else {
                    val j = k - padding
                    value[j / 8] = (value[j / 8].toInt() or (bit shl (7 - j % 8))).toByte()
                }
            }
            if (badPadding != 0) {
                value.fill(0)
                throw Slip39Failure(slip39BadLength(part))
            }
            return Share(
                identifier = (w[0] shl 5) or (w[1] shr 5),
                extendable = extendable,
                exponent = w[1] and 15,
                groupIndex = w[2] shr 6,
                groupThreshold = ((w[2] shr 2) and 15) + 1,
                groupCount = (((w[2] and 3) shl 2) or (w[3] shr 8)) + 1,
                memberIndex = (w[3] shr 4) and 15,
                memberThreshold = (w[3] and 15) + 1,
                value = value,
            )
        } finally {
            w.fill(0)
        }
    }

    /** The mnemonic of [s]: header, the value left-padded with zero bits to a multiple of 10, and the RS1024 checksum. */
    internal fun encode(s: Share): String {
        require(s.identifier in 0 until (1 shl ID_BITS) && s.exponent in 0..15)
        require(s.groupIndex in 0..15 && s.groupThreshold in 1..16 && s.groupCount in 1..16)
        require(s.memberIndex in 0..15 && s.memberThreshold in 1..16)
        val valueBits = s.value.size * 8
        val valueWords = (valueBits + RADIX_BITS - 1) / RADIX_BITS
        val padding = valueWords * RADIX_BITS - valueBits
        val data = IntArray(HEADER_WORDS + valueWords)
        val all: IntArray
        try {
            data[0] = s.identifier shr 5
            data[1] = ((s.identifier and 31) shl 5) or ((if (s.extendable) 1 else 0) shl 4) or s.exponent
            data[2] = (s.groupIndex shl 6) or ((s.groupThreshold - 1) shl 2) or ((s.groupCount - 1) shr 2)
            data[3] = (((s.groupCount - 1) and 3) shl 8) or (s.memberIndex shl 4) or (s.memberThreshold - 1)
            for (k in 0 until valueBits) {
                val bit = (s.value[k / 8].toInt() shr (7 - k % 8)) and 1
                val p = padding + k
                data[HEADER_WORDS + p / RADIX_BITS] = data[HEADER_WORDS + p / RADIX_BITS] or (bit shl (RADIX_BITS - 1 - p % RADIX_BITS))
            }
            val cs = if (s.extendable) Rs1024.CUSTOMIZATION_EXTENDABLE else Rs1024.CUSTOMIZATION
            all = data + Rs1024.checksum(cs, data)
        } finally {
            data.fill(0)
        }
        try {
            return all.joinToString(" ") { list[it] }
        } finally {
            all.fill(0)
        }
    }
}

/** The pieces of [text] between runs of spacing (any whitespace and any space separator, the no-break space too). */
private fun tokens(text: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    for (c in text) {
        if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
            if (sb.isNotEmpty()) out += sb.toString()
            sb.setLength(0)
        } else {
            sb.append(c)
        }
    }
    if (sb.isNotEmpty()) out += sb.toString()
    return out
}

/**
 * [token] as a list word may be: NFKC (full-width letters and ligatures become plain ones), without the invisible format
 * characters a paste can bring (zero-width space, BOM), in lower case. Null when it holds an unpaired surrogate: no
 * word of the list has one, and the normaliser does not see it. A word with an accent or another script stays as it
 * is, and so is not in the list.
 */
private fun normalizedWord(token: String): String? {
    for (i in token.indices) {
        val c = token[i]
        if (c.isHighSurrogate() && (i + 1 >= token.length || !token[i + 1].isLowSurrogate())) return null
        if (c.isLowSurrogate() && (i == 0 || !token[i - 1].isHighSurrogate())) return null
    }
    val nfkc = Normalizer.normalize(token, Normalizer.Form.NFKC)
    val sb = StringBuilder(nfkc.length)
    for (c in nfkc) if (Character.getType(c) != Character.FORMAT.toInt()) sb.append(c)
    return sb.toString().lowercase(Locale.ROOT)
}

/** The passphrase's bytes: SLIP-0039 wants printable ASCII (32 to 126) only, so anything else is refused, never encoded. */
private fun asciiPassphrase(p: CharArray): ByteArray {
    if (p.size > SLIP39_MAX_PASSPHRASE_CHARS) throw Slip39Failure(slip39PassphraseTooLong(SLIP39_MAX_PASSPHRASE_CHARS))
    for (c in p) if (c.code !in 32..126) throw Slip39Failure(SLIP39_PASSPHRASE_NOT_ASCII)
    return ByteArray(p.size) { p[it].code.toByte() }
}

/**
 * Encryption (or, with [encrypt] false, decryption) of the master secret (SLIP-0039): a 4-round Feistel network whose
 * round function is F(i, R) = PBKDF2-HMAC-SHA256(i ‖ passphrase, salt_prefix ‖ R, 2500 · 2^e, n/2 bytes). The salt prefix
 * is empty with the extendable flag, else «shamir» and the identifier in two bytes. [check] runs before each round.
 */
private fun feistel(input: ByteArray, pass: ByteArray, e: Int, id: Int, extendable: Boolean, encrypt: Boolean, check: () -> Unit): ByteArray {
    val half = input.size / 2
    var l = input.copyOfRange(0, half)
    var r = input.copyOfRange(half, input.size)
    val prefix = if (extendable) ByteArray(0) else "shamir".toByteArray(Charsets.US_ASCII) + byteArrayOf((id shr 8).toByte(), id.toByte())
    val iterations = ROUND_ITERATIONS shl e
    try {
        for (step in 0 until ROUNDS) {
            check()
            val f = roundFunction(if (encrypt) step else ROUNDS - 1 - step, pass, prefix, r, iterations, half)
            for (k in 0 until half) f[k] = (f[k].toInt() xor l[k].toInt()).toByte()
            l.fill(0)
            l = r
            r = f
        }
        return r + l
    } finally {
        l.fill(0)
        r.fill(0)
    }
}

private fun roundFunction(i: Int, pass: ByteArray, prefix: ByteArray, r: ByteArray, iterations: Int, length: Int): ByteArray {
    val password = ByteArray(1 + pass.size)
    password[0] = i.toByte()
    pass.copyInto(password, 1)
    val salt = prefix + r
    try {
        val gen = PKCS5S2ParametersGenerator(SHA256Digest())
        gen.init(password, salt, iterations)
        val key = (gen.generateDerivedParameters(length * 8) as KeyParameter).key
        return key.copyOf().also { key.fill(0) }
    } finally {
        password.fill(0)
        salt.fill(0)
    }
}
