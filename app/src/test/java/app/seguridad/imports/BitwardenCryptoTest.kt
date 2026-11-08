package app.seguridad.imports

import app.seguridad.vault.EntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 1.4.0, Task 10: Bitwarden's JSON export «Protegida con contraseña». The fixtures were written by
 * scripts/import-fixtures/gen_encrypted.py (independent Python, from the public documents named there) with the
 * password [FIXTURE_PASSWORD], one per KDF: PBKDF2-SHA256 (100 000 iterations) and Argon2id (16 MiB, 3 passes, 4 lanes;
 * the salt hashed with SHA-256, from documentation, not yet confirmed against a real export).
 */
class BitwardenCryptoTest {
    private companion object {
        /** Password bytes, salt bytes, PBKDF2 key, enc, mac, the two MACs, the CBC buffer and the plaintext. */
        const val PBKDF2_KEPT = 9

        /** The same as [PBKDF2_KEPT] plus the salt's SHA-256, which Argon2id takes as its salt. */
        const val ARGON2_KEPT = 10

        /** A wrong password with PBKDF2: password bytes, salt bytes, PBKDF2 key, enc, mac and the first MAC (it fails). */
        const val PBKDF2_WRONG_KEPT = 6

        /** The same as [PBKDF2_WRONG_KEPT] plus the salt's SHA-256. */
        const val ARGON2_WRONG_KEPT = 7
    }

    private val plain = resource("bitwarden/bitwarden-protegido-claro.json")
    private val pbkdf2 = resource("bitwarden/bitwarden-protegido-pbkdf2.json")
    private val argon2 = resource("bitwarden/bitwarden-protegido-argon2id.json")

    @Test fun withoutAPasswordItAsksForOne() {
        assertEquals(ReadResult.NeedsPassword(ImportFormat.BITWARDEN), readImport(pbkdf2))
        assertEquals(ReadResult.NeedsPassword(ImportFormat.BITWARDEN), readImport(argon2))
    }

    @Test fun pbkdf2FileOpensAsItsPlainTwin() {
        val twin = okRead(plain)
        assertSameRead(twin, okRead(pbkdf2, FIXTURE_PASSWORD))
        // The twin itself: two logins (with their TOTPs), a note and a card that became a note.
        assertEquals(listOf(EntryType.LOGIN, EntryType.TOTP, EntryType.LOGIN, EntryType.TOTP, EntryType.NOTE, EntryType.NOTE), twin.items.map { it.entry.type })
        assertEquals(1, twin.becameNotes)
        assertEquals(listOf("Trabajo"), twin.items[0].folders)
    }

    @Test fun argon2idFileOpensAsItsPlainTwin() {
        assertSameRead(okRead(plain), okRead(argon2, FIXTURE_PASSWORD))
    }

    @Test fun aWrongPasswordIsSaidWithoutDecryptingAnything() {
        for (file in listOf(pbkdf2, argon2)) {
            var decrypts = 0
            try {
                openBitwarden(jsonRoot(file), "prueba-clavero-1.5".toCharArray(), JSON_MAX_TREE_BYTES, onDecrypt = { decrypts++ })
                fail("opened")
            } catch (e: ImportFailure) {
                assertEquals(IMPORT_WRONG_PASSWORD, e.message)
            }
            assertEquals(0, decrypts)
            assertEquals(IMPORT_WRONG_PASSWORD, failedRead(file, "x"))
        }
    }

    @Test fun everyByteFlippedFailsTheHmacBeforeAnyCbcDecryption() {
        val p = bitwardenProtected(jsonRoot(pbkdf2))
        val s = Secrets()
        val keys = bitwardenKeys(p, FIXTURE_PASSWORD.toCharArray(), s)
        var decrypts = 0
        val good = bitwardenData(p, keys, s) { decrypts++ }
        assertEquals(1, decrypts)
        assertEquals(plain.toString(Charsets.UTF_8), good.toString(Charsets.UTF_8))
        decrypts = 0
        var flips = 0
        for (which in 0..1) {
            val target = if (which == 0) p.validation else p.data
            for (part in 0..2) {
                val bytes = listOf(target.iv, target.ct, target.mac)[part]
                for (i in bytes.indices) for (bit in listOf(0x01, 0x80)) {
                    val flipped = bytes.copyOf().also { it[i] = (it[i].toInt() xor bit).toByte() }
                    val parts = listOf(target.iv, target.ct, target.mac).toMutableList().also { it[part] = flipped }
                    val changed = EncString(parts[0], parts[1], parts[2])
                    val q = if (which == 0) p.copy(validation = changed) else p.copy(data = changed)
                    try {
                        bitwardenData(q, keys, s) { decrypts++ }
                        fail("flip $which/$part/$i accepted")
                    } catch (e: ImportFailure) {
                        assertEquals(IMPORT_WRONG_PASSWORD, e.message)
                    }
                    flips++
                }
            }
        }
        assertEquals(0, decrypts)
        assertTrue(flips > 2 * 2 * (16 + 32 + 32))
        s.wipe()
    }

    @Test fun theMacsAreComparedInConstantTime() {
        // Ruling (b): one comparison, MessageDigest.isEqual, and it comes before the CBC decryption in the source.
        val src = sourceOf("BitwardenCrypto.kt")
        assertTrue("MessageDigest.isEqual(" in src)
        assertTrue(src.indexOf("MessageDigest.isEqual(") < src.indexOf("PaddedBufferedBlockCipher("))
        assertTrue("contentEquals" !in src)
    }

    @Test fun kdfOutOfRangeIsRefusedBeforeWork() {
        val pb = jsonRoot(pbkdf2)
        val ar = jsonRoot(argon2)
        val cases = listOf(
            pb.with("kdfIterations", 2_000_001), pb.with("kdfIterations", 4_999), pb.with("kdfIterations", 0),
            ar.with("kdfMemory", 129), ar.with("kdfIterations", 11), ar.with("kdfParallelism", 5), ar.with("kdfIterations", 0),
            ar.with("kdfMemory", 4_000_000_000L), ar.with("kdfMemory", 0), ar.with("kdfMemory", -1),
            ar.with("kdfParallelism", 0), ar.with("kdfParallelism", -1),
        ).map { it.bytes() } +
            // Not whole numbers, as a fraction and with an exponent: the bounds refuse them (NOT_A_WHOLE_NUMBER).
            listOf("600000.5", "6e5", "6E5", "600000.0").map { pb.with("kdfIterations", LITERAL_MARK).bytesWithLiteral(it) } +
            listOf("16.5", "1.6e1").map { ar.with("kdfMemory", LITERAL_MARK).bytesWithLiteral(it) }
        failedRead(cases[0]) // Warms the JSON parser and the classes up.
        for (c in cases) {
            var message = ""
            val ms = millis { message = failedRead(c) }
            assertEquals(String(c), IMPORT_KDF_TOO_BIG, message)
            assertTrue("$ms ms", ms < 50)
            // Branch review A, M-3: said before the password is asked for, never «NeedsPassword» for a file no
            // password opens.
            assertEquals(String(c), IMPORT_KDF_TOO_BIG, failedRead(c, password = null))
        }
    }

    @Test fun brokenHeadersAreRefusedBeforeWork() {
        val pb = jsonRoot(pbkdf2)
        val broken = listOf(
            pb.with("kdfType", 2), pb.with("kdfType", "0"), pb.with("kdfIterations", null), pb.with("salt", ""),
            pb.with("salt", 5), pb.with("salt", "a".repeat(1025)), pb.with("data", "0.AAAA|BBBB"), pb.with("data", "2.AAAA|BBBB"),
            pb.with("data", "2.AAAA|BBBB|CCCC|DDDD"), pb.with("encKeyValidation_DO_NOT_EDIT", null), pb.with("data", 7),
            pb.with("data", "2.%%%%|BBBB|CCCC"), pb.with("encKeyValidation_DO_NOT_EDIT", "2." + "A".repeat(5000)),
        )
        for (c in broken) {
            val ms = millis { assertEquals(c.toString(), IMPORT_BROKEN, failedRead(c.bytes())) }
            assertTrue("$ms ms", ms < 50)
            assertEquals(c.toString(), IMPORT_BROKEN, failedRead(c.bytes(), password = null)) // M-3: before the password.
        }
    }

    @Test fun theDecryptedDataMustBeAPlainExport() {
        // Ruling (c): what comes out is parsed as a plain export only; an export inside saying it is encrypted again is
        // broken, never read a second time.
        val p = bitwardenProtected(jsonRoot(pbkdf2))
        val s = Secrets()
        val keys = bitwardenKeys(p, FIXTURE_PASSWORD.toCharArray(), s)
        for (inner in listOf("""{"encrypted":true,"passwordProtected":true}""", "[1,2]", "no es json", "{\"items\":5}")) {
            val q = p.copy(data = encStringForTest(keys, inner.toByteArray()))
            try {
                bitwardenRead(q, keys, JSON_MAX_TREE_BYTES, s)
                fail(inner)
            } catch (e: ImportFailure) {
                assertEquals(inner, IMPORT_BROKEN, e.message)
            }
        }
        // And it goes through the same tree estimate as a plain file.
        val big = """{"encrypted":false,"folders":[],"items":[${(1..200).joinToString(",") { """{"type":2,"name":"n$it","notes":"x"}""" }}]}"""
        val q = p.copy(data = encStringForTest(keys, big.toByteArray()))
        assertEquals(200, bitwardenRead(q, keys, JSON_MAX_TREE_BYTES, s).items.size)
        try {
            bitwardenRead(q, keys, treeBudget = 10_000, s)
            fail("over the tree budget")
        } catch (e: ImportFailure) {
            assertEquals(IMPORT_JSON_TOO_MANY_VALUES, e.message)
        }
        s.wipe()
    }

    @Test fun truncatedFilesFail() {
        assertTruncationsFail(pbkdf2)
        // The data string itself cut, so the JSON stays valid.
        val root = jsonRoot(pbkdf2)
        val data = (root["data"] as kotlinx.serialization.json.JsonPrimitive).content
        for (k in 1..5) {
            val cut = root.with("data", data.substring(0, data.length * k / 6)).bytes()
            val message = failedRead(cut)
            assertTrue(message, message == IMPORT_BROKEN || message == IMPORT_WRONG_PASSWORD)
        }
    }

    @Test fun anEncryptedPartOverTheBoundIsRefusedBeforeDecoding() {
        val root = jsonRoot(pbkdf2)
        val huge = "2." + "A".repeat(24) + "|" + "A".repeat(IMPORT_MAX_ENCRYPTED_CHARS) + "|" + "A".repeat(44)
        assertEquals(IMPORT_ENCRYPTED_TOO_BIG, failedRead(root.with("data", huge).bytes()))
        assertEquals(IMPORT_ENCRYPTED_TOO_BIG, failedRead(root.with("data", huge).bytes(), password = null)) // M-3.
    }

    @Test fun passwordAndKeysAreZeroed() {
        // M-4: the exact number of arrays on each path and KDF, and the keys and the plaintext themselves among them.
        for ((file, right, wrongCount) in listOf(Triple(pbkdf2, PBKDF2_KEPT, PBKDF2_WRONG_KEPT), Triple(argon2, ARGON2_KEPT, ARGON2_WRONG_KEPT))) {
            val seen = ArrayList<ByteArray>()
            val pw = FIXTURE_PASSWORD.toCharArray()
            openBitwarden(jsonRoot(file), pw, JSON_MAX_TREE_BYTES, Secrets(seen::add))
            assertAllZero(seen, exactly = right)
            // The CharArray is the caller's (readImport's contract): it is left for the caller to zero.
            assertEquals(FIXTURE_PASSWORD, String(pw))
            val steps = ArrayList<ByteArray>()
            val s = Secrets(steps::add)
            val p = bitwardenProtected(jsonRoot(file))
            val keys = bitwardenKeys(p, pw, s)
            val data = bitwardenData(p, keys, s)
            assertEquals(plain.toString(Charsets.UTF_8), data.toString(Charsets.UTF_8))
            assertKept(steps, keys.enc, keys.mac, data)
            s.wipe()
            assertAllZero(steps, exactly = right)
            val wrong = ArrayList<ByteArray>()
            try {
                openBitwarden(jsonRoot(file), "otra".toCharArray(), JSON_MAX_TREE_BYTES, Secrets(wrong::add))
                fail("opened")
            } catch (e: ImportFailure) {
                assertEquals(IMPORT_WRONG_PASSWORD, e.message)
            }
            assertAllZero(wrong, exactly = wrongCount)
        }
    }
}

/** A type 2 cipher string of [plain] under [keys], for tests only (a random IV). */
internal fun encStringForTest(keys: BitwardenKeys, plain: ByteArray): EncString {
    val iv = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
    val c = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
    c.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(keys.enc, "AES"), javax.crypto.spec.IvParameterSpec(iv))
    val ct = c.doFinal(plain)
    val mac = javax.crypto.Mac.getInstance("HmacSHA256").apply { init(javax.crypto.spec.SecretKeySpec(keys.mac, "HmacSHA256")) }.doFinal(iv + ct)
    return EncString(iv, ct, mac)
}

/** The text of a main source file of the import package. */
internal fun sourceOf(name: String): String {
    var dir: File? = File(".").absoluteFile
    while (dir != null) {
        for (base in listOf("app/src/main/java", "src/main/java")) {
            val f = File(dir, "$base/app/seguridad/imports/$name")
            if (f.isFile) return f.readText()
        }
        dir = dir.parentFile
    }
    error("$name not found")
}
