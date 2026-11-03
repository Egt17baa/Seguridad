package app.seguridad.tools.slip39

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** SLIP-0039 keeps the shared secret at x = 255 and its digest at x = 254 («Shamir's secret-sharing»). */
private const val SECRET_INDEX = 255
private const val DIGEST_INDEX = 254
private const val DIGEST_BYTES = 4

/**
 * Lagrange interpolation at [x] of the points ([xs], [ys]), byte by byte (SLIP-0039 «Polynomial interpolation»). The
 * indices are public: their basis coefficient uses the log tables. Every product with a value byte is [Gf256.mul].
 * The [xs] must be pairwise distinct (the caller checks); the result is a new array the caller wipes.
 */
internal fun interpolate(xs: IntArray, ys: List<ByteArray>, x: Int): ByteArray {
    val n = ys[0].size
    xs.indexOf(x).let { if (it >= 0) return ys[it].copyOf() }
    // log of ∏ (x − x_j) over every j; subtraction in GF(256) is xor.
    var logAll = 0
    for (xj in xs) logAll += Gf256.log(x xor xj)
    val out = ByteArray(n)
    for (i in xs.indices) {
        var logBasis = logAll - Gf256.log(x xor xs[i])
        for (j in xs.indices) if (j != i) logBasis -= Gf256.log(xs[i] xor xs[j])
        val basis = Gf256.exp(logBasis)
        val y = ys[i]
        for (k in 0 until n) out[k] = (out[k].toInt() xor Gf256.mul(basis, y[k].toInt())).toByte()
    }
    return out
}

private fun digestOf(random: ByteArray, secret: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(random, "HmacSHA256"))
    val full = mac.doFinal(secret)
    return full.copyOf(DIGEST_BYTES).also { full.fill(0) }
}

/**
 * SplitSecret(T, N, S) of SLIP-0039: [count] shares with indices 0..count−1, any [threshold] of which give [secret].
 * The caller has checked 1 ≤ threshold ≤ count ≤ 16 and the length. Every intermediate array is wiped here.
 */
internal fun shamirSplit(threshold: Int, count: Int, secret: ByteArray, random: SecureRandom): List<ByteArray> {
    if (threshold == 1) return List(count) { secret.copyOf() }
    val n = secret.size
    val r = ByteArray(n - DIGEST_BYTES).also(random::nextBytes)
    val tag = digestOf(r, secret)
    val digest = tag + r
    tag.fill(0)
    r.fill(0)
    val randomShares = List(threshold - 2) { ByteArray(n).also(random::nextBytes) }
    val xs = IntArray(threshold) { if (it < threshold - 2) it else if (it == threshold - 2) DIGEST_INDEX else SECRET_INDEX }
    val ys = randomShares + listOf(digest, secret)
    try {
        return List(count) { i -> if (i < threshold - 2) randomShares[i].copyOf() else interpolate(xs, ys, i) }
    } finally {
        randomShares.forEach { it.fill(0) }
        digest.fill(0)
    }
}

/**
 * RecoverSecret(T, …) of SLIP-0039 from exactly [threshold] shares with distinct [xs]. With T ≥ 2 the digest at index
 * 254 is checked in constant time; a mismatch (a changed or foreign share) throws [Slip39Failure].
 */
internal fun shamirRecover(threshold: Int, xs: IntArray, ys: List<ByteArray>): ByteArray {
    if (threshold == 1) return ys[0].copyOf()
    val secret = interpolate(xs, ys, SECRET_INDEX)
    val digest = interpolate(xs, ys, DIGEST_INDEX)
    val r = digest.copyOfRange(DIGEST_BYTES, digest.size)
    val tag = digestOf(r, secret)
    val head = digest.copyOf(DIGEST_BYTES)
    val ok = MessageDigest.isEqual(tag, head)
    head.fill(0)
    tag.fill(0)
    r.fill(0)
    digest.fill(0)
    if (!ok) {
        secret.fill(0)
        throw Slip39Failure(SLIP39_DIGEST_MISMATCH)
    }
    return secret
}
