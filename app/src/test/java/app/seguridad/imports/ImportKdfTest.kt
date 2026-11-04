package app.seguridad.imports

import app.seguridad.crypto.KdfParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 1.4.0, Task 10: the bounds of the key derivations of encrypted exports (spec «Parámetros de las claves, con tope antes
 * de calcular»), checked before any work, and the helpers every encrypted reader shares.
 */
class ImportKdfTest {
    private fun refused(block: () -> Unit) {
        try {
            block()
            fail("accepted")
        } catch (e: ImportFailure) {
            assertEquals(IMPORT_KDF_TOO_BIG, e.message)
        }
    }

    @Test fun theMessageIsTheSpecs() {
        assertEquals(
            "Este archivo pide más memoria o más cálculo del que Clavero puede usar en este móvil. Vuelve a exportarlo con " +
                "los ajustes normales de la otra app o en un formato sin cifrar.",
            IMPORT_KDF_TOO_BIG,
        )
        assertEquals("La contraseña no es correcta o el archivo está dañado.", IMPORT_WRONG_PASSWORD)
    }

    @Test fun pbkdf2IterationsFrom5000To2Million() {
        checkPbkdf2Iterations(5_000)
        checkPbkdf2Iterations(600_000)
        checkPbkdf2Iterations(2_000_000)
        for (n in listOf(4_999, 2_000_001, 0, -1, Int.MAX_VALUE, Int.MIN_VALUE)) refused { checkPbkdf2Iterations(n) }
    }

    @Test fun scryptBounds() {
        val big = 4L * 1024 * 1024 // A heap of 4 GiB: only the hard bounds count.
        checkScrypt(1 shl 14, 1, 1, big)
        checkScrypt(1 shl 15, 8, 1, big) // Aegis's default: 32 MiB.
        checkScrypt(1 shl 20, 1, 4, big) // 128 MiB: the bound itself.
        checkScrypt(1 shl 16, 16, 4, big) // 128 MiB too.
        refused { checkScrypt(1 shl 13, 8, 1, big) }
        refused { checkScrypt(1 shl 21, 1, 1, big) }
        refused { checkScrypt((1 shl 15) + 1, 8, 1, big) }
        refused { checkScrypt(3 shl 14, 1, 1, big) } // Not a power of 2.
        refused { checkScrypt(1 shl 15, 0, 1, big) }
        refused { checkScrypt(1 shl 15, 17, 1, big) }
        refused { checkScrypt(1 shl 15, 8, 0, big) }
        refused { checkScrypt(1 shl 15, 8, 5, big) }
        refused { checkScrypt(1 shl 20, 2, 1, big) } // 256 MiB: over MAX_KDF_MEMORY_KIB.
        refused { checkScrypt(1 shl 20, 16, 1, big) } // 2 GiB: no overflow lets it through.
        refused { checkScrypt(0, 8, 1, big) }
        refused { checkScrypt(-(1 shl 15), 8, 1, big) }
        refused { checkScrypt(Int.MIN_VALUE, 8, 1, big) }
    }

    @Test fun scryptMemoryOverTheDefaultMustFitThisHeap() {
        // The rule of checkSane: up to 64 MiB always; above it, at most ¾ of what the app may use.
        checkScrypt(1 shl 16, 8, 1, heapKiB = 1) // 64 MiB.
        refused { checkScrypt(1 shl 17, 8, 1, heapKiB = 128 * 1024) } // 128 MiB on a 128 MiB heap.
        checkScrypt(1 shl 17, 8, 1, heapKiB = 171 * 1024)
    }

    @Test fun argon2idBoundsAreThoseOfCheckSane() {
        val big = 4L * 1024 * 1024
        checkArgon2id(KdfParams(64 * 1024, 3, 4), big)
        checkArgon2id(KdfParams(128 * 1024, 10, 4), big)
        refused { checkArgon2id(KdfParams(129 * 1024, 3, 4), big) }
        refused { checkArgon2id(KdfParams(64 * 1024, 11, 4), big) }
        refused { checkArgon2id(KdfParams(64 * 1024, 3, 5), big) }
        refused { checkArgon2id(KdfParams(64 * 1024, 0, 4), big) }
        refused { checkArgon2id(KdfParams(96 * 1024, 3, 4), heapKiB = 96 * 1024) }
    }

    @Test fun bitwardenArgon2idMemoryIsInMiB() {
        assertEquals(KdfParams(64 * 1024, 3, 4), bitwardenArgon2Params(memoryMiB = 64, iterations = 3, parallelism = 4))
        refused { bitwardenArgon2Params(memoryMiB = 129, iterations = 3, parallelism = 4) }
        refused { bitwardenArgon2Params(memoryMiB = Int.MAX_VALUE, iterations = 3, parallelism = 4) }
        refused { bitwardenArgon2Params(memoryMiB = 0, iterations = 3, parallelism = 4) }
    }

    @Test fun knownVectors() {
        val s = Secrets()
        // RFC 7914 §12, the third vector (N = 16384 is the smallest N the import accepts).
        val scrypt = scryptKey("pleaseletmein".toByteArray(), "SodiumChloride".toByteArray(), 16384, 8, 1, 64, s)
        assertEquals(
            "7023bdcb3afd7348461c06cd81fd38ebfda8fbba904f8e3ea9b543f6545da1f2d5432955613f0fcf62d49705242a9af9e61e85dc0d651e40dfcf017b45575887",
            scrypt.toHex(),
        )
        // PBKDF2-HMAC-SHA256: the published vector with «password», «salt» and 4096 iterations (32 bytes), with
        // bounds off only for this call.
        val pbkdf2 = pbkdf2Sha256("password".toByteArray(), "salt".toByteArray(), 4096, 32, s, checked = false)
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a", pbkdf2.toHex())
        // RFC 5869, test case 1, the Expand step alone (PRK as given there).
        val prk = "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5".hexBytes()
        val okm = hkdfExpandSha256(prk, "f0f1f2f3f4f5f6f7f8f9".hexBytes(), 42, s)
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.toHex())
        s.wipe()
        for (a in listOf(scrypt, pbkdf2, okm)) assertArrayEquals(ByteArray(a.size), a)
    }

    @Test fun secretsWipesEverythingItHolds() {
        val seen = ArrayList<ByteArray>()
        val s = Secrets(seen::add)
        val a = s.bytes(8).also { it.fill(7) }
        val b = s.keep(byteArrayOf(1, 2, 3))
        s.wipe()
        assertArrayEquals(ByteArray(8), a)
        assertArrayEquals(ByteArray(3), b)
        assertEquals(2, seen.size)
    }

    @Test fun thePasswordIsUtf8AndALoneSurrogateIsAWrongPassword() {
        val s = Secrets()
        assertArrayEquals("ñ€😀".toByteArray(), importPasswordBytes("ñ€😀".toCharArray(), s))
        try {
            importPasswordBytes(charArrayOf('a', '\uD800'), s)
            fail("accepted")
        } catch (e: ImportFailure) {
            assertEquals(IMPORT_WRONG_PASSWORD, e.message)
        }
    }

    @Test fun base64AndHexAreBoundedBeforeDecoding() {
        assertArrayEquals(byteArrayOf(1, 2, 3), importBase64("AQID", maxChars = 4))
        assertArrayEquals(byteArrayOf(1, 2, 3), importBase64("AQ\nID", maxChars = 8)) // Line breaks, as some apps wrap.
        for (bad in listOf("AQI", "AQ!D", "AQIDBA===", "")) {
            try {
                importBase64(bad, maxChars = 16)
                fail(bad)
            } catch (e: ImportFailure) {
                assertEquals(IMPORT_BROKEN, e.message)
            }
        }
        try {
            importBase64("AQIDBAUG", maxChars = 4)
            fail("over the bound")
        } catch (e: ImportFailure) {
            assertEquals(IMPORT_ENCRYPTED_TOO_BIG, e.message)
        }
        assertArrayEquals(byteArrayOf(0x0a, -1), importHex("0aFF", bytes = 2))
        for (bad in listOf("0a", "0aF", "0aFG", "0aFF00")) {
            try {
                importHex(bad, bytes = 2)
                fail(bad)
            } catch (e: ImportFailure) {
                assertEquals(IMPORT_BROKEN, e.message)
            }
        }
    }

    @Test fun theDecryptedJsonGoesThroughTheSameTreeBound() {
        val s = Secrets()
        val json = """{"a":[1,2,3]}""".toByteArray()
        assertTrue(decryptedJson(json.copyOf(), JSON_MAX_TREE_BYTES) is kotlinx.serialization.json.JsonObject)
        try {
            decryptedJson(json.copyOf(), budget = 10)
            fail("over the budget")
        } catch (e: ImportFailure) {
            assertEquals(IMPORT_JSON_TOO_MANY_VALUES, e.message)
        }
        for (bad in listOf(byteArrayOf(-1, -2), "{\"a\":".toByteArray(), "[1]x".toByteArray())) {
            try {
                decryptedJson(bad, JSON_MAX_TREE_BYTES)
                fail(String(bad))
            } catch (e: ImportFailure) {
                assertEquals(IMPORT_BROKEN, e.message)
            }
        }
        s.wipe()
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
