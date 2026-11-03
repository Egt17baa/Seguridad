package app.seguridad.tools.slip39

/**
 * GF(256) as SLIP-0039 uses it: the field of AES (FIPS-197 §4.2), bytes as polynomials modulo the Rijndael polynomial
 * x^8 + x^4 + x^3 + x + 1 ([POLYNOMIAL]). The exp/log tables (generator 3) are used only on share indices, which are not
 * secret; a product that touches a secret byte goes through [mul], which has no table lookup and no branch on its inputs.
 */
internal object Gf256 {
    const val POLYNOMIAL = 0x11B

    private val EXP = IntArray(255)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            LOG[x] = i
            // x · 3 = x · 2 + x
            x = x xor (x shl 1)
            if (x and 0x100 != 0) x = x xor POLYNOMIAL
        }
    }

    /** 3^[i], for i in 0..254 (taken modulo 255). */
    fun exp(i: Int): Int = EXP[Math.floorMod(i, 255)]

    /** The logarithm in base 3 of [a], 1..255. */
    fun log(a: Int): Int {
        require(a in 1..255)
        return LOG[a]
    }

    /** [a] · [b] in constant time: eight shift-and-add steps, the reduction by a mask, never a lookup. */
    fun mul(a: Int, b: Int): Int {
        var x = a and 0xFF
        var y = b and 0xFF
        var r = 0
        repeat(8) {
            r = r xor (x and -(y and 1))
            val high = -((x shr 7) and 1)
            x = ((x shl 1) xor (POLYNOMIAL and high)) and 0xFF
            y = y shr 1
        }
        return r
    }
}
