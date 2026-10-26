package app.seguridad.core

/*
 * Base45 (RFC 9285): datos binarios con el alfabeto del modo alfanumérico de QR (ISO/IEC 18004). Cada 2 bytes dan
 * 3 caracteres y un byte suelto da 2. El QR gasta unos 8,25 bits por byte de datos, frente a unos 10,7 con Base64 en
 * modo binario, y por eso la tarjeta de contacto da un QR menos denso.
 */

private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
private const val BASE = 45
private const val BASE_SQUARED = BASE * BASE

/** Valor de cada carácter ASCII del alfabeto, y -1 para el resto. */
private val DIGIT_OF = IntArray(128) { -1 }.also { table -> ALPHABET.forEachIndexed { value, c -> table[c.code] = value } }

/** Codifica [b] en Base45: 3 caracteres por cada 2 bytes y 2 por el byte final si sobra uno. */
fun base45Encode(b: ByteArray): String {
    val out = StringBuilder(b.size / 2 * 3 + b.size % 2 * 2)
    var i = 0
    while (i + 1 < b.size) {
        val n = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        out.append(ALPHABET[n % BASE]).append(ALPHABET[n / BASE % BASE]).append(ALPHABET[n / BASE_SQUARED])
        i += 2
    }
    if (i < b.size) {
        val n = b[i].toInt() and 0xFF
        out.append(ALPHABET[n % BASE]).append(ALPHABET[n / BASE])
    }
    return out.toString()
}

/**
 * Descodifica [s]. Lanza [IllegalArgumentException] con un carácter que no es del alfabeto (las minúsculas y los saltos
 * de línea tampoco), un grupo final de 1 carácter, un grupo de 3 con un valor mayor que 65535 o uno de 2 con un valor
 * mayor que 255 (RFC 9285 §6.2). Así cada texto tiene un único descifrado posible.
 */
fun base45Decode(s: String): ByteArray {
    require(s.length % 3 != 1) { "Base45 no válido: longitud" }
    val out = ByteArray(s.length / 3 * 2 + if (s.length % 3 == 2) 1 else 0)
    var o = 0
    var i = 0
    while (i + 2 < s.length) {
        val n = digit(s[i]) + digit(s[i + 1]) * BASE + digit(s[i + 2]) * BASE_SQUARED
        require(n <= 0xFFFF) { "Base45 no válido: valor" }
        out[o++] = (n shr 8).toByte()
        out[o++] = n.toByte()
        i += 3
    }
    if (i < s.length) {
        val n = digit(s[i]) + digit(s[i + 1]) * BASE
        require(n <= 0xFF) { "Base45 no válido: valor" }
        out[o] = n.toByte()
    }
    return out
}

private fun digit(c: Char): Int {
    val value = if (c.code < DIGIT_OF.size) DIGIT_OF[c.code] else -1
    require(value >= 0) { "Base45 no válido: carácter" }
    return value
}
