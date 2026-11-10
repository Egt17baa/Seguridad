package app.seguridad.tools.slip39

/**
 * The RS1024 checksum of SLIP-0039 («Checksum»): a Reed-Solomon code over GF(1024) whose last three 10-bit words detect
 * any change of up to three words. The customization string goes in first, one US-ASCII value per character:
 * [CUSTOMIZATION] for shares with ext = 0 and [CUSTOMIZATION_EXTENDABLE] for ext = 1.
 */
internal object Rs1024 {
    const val CUSTOMIZATION = "shamir"
    const val CUSTOMIZATION_EXTENDABLE = "shamir_extendable"

    private val GEN = intArrayOf(
        0xE0E040, 0x1C1C080, 0x3838100, 0x7070200, 0xE0E0009, 0x1C0C2412, 0x38086C24, 0x3090FC48, 0x21B1F890, 0x3F3F120,
    )

    private fun polymod(cs: String, values: IntArray, zeros: Int): Int {
        var chk = 1
        fun step(v: Int) {
            val b = chk shr 20
            chk = ((chk and 0xFFFFF) shl 10) xor v
            for (i in 0 until 10) if ((b shr i) and 1 != 0) chk = chk xor GEN[i]
        }
        for (c in cs) step(c.code)
        for (v in values) step(v)
        repeat(zeros) { step(0) }
        return chk
    }

    /** The three checksum words of [data] (10-bit values). */
    fun checksum(cs: String, data: IntArray): IntArray {
        val p = polymod(cs, data, 3) xor 1
        return IntArray(3) { i -> (p shr (10 * (2 - i))) and 1023 }
    }

    /** True if [words] (data and checksum, 10-bit values) is a valid RS1024 codeword for [cs]. */
    fun verify(cs: String, words: IntArray): Boolean = polymod(cs, words, 0) == 1
}
