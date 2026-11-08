package app.seguridad.imports

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 1.4.0, Task 10: Aegis's encrypted vault export (its published vault.md): scrypt over the password slot opens the
 * master key with AES-256-GCM, and the master key opens `db`. The fixture was written by
 * scripts/import-fixtures/gen_encrypted.py (independent Python) with [FIXTURE_PASSWORD] and Aegis's default
 * N = 2^15, r = 8, p = 1.
 */
class AegisCryptoTest {
    private companion object {
        /** Password bytes, scrypt key, master key and the vault's plaintext. */
        const val OPEN_KEPT = 4

        /** A wrong password: password bytes, scrypt key and the unwrap's output whose tag failed (blockGcm wiped it). */
        const val WRONG_KEPT = 3
    }

    private val plain = resource("aegis/aegis-cifrado-claro.json")
    private val encrypted = resource("aegis/aegis-cifrado.json")

    private fun JsonObject.header() = this["header"]!!.jsonObject
    private fun JsonObject.slot() = header()["slots"]!!.jsonArray[0].jsonObject

    /** [root] with the password slot's [key] set to [value]. */
    private fun JsonObject.withSlot(key: String, value: Any?): JsonObject {
        val slots = JsonArray(listOf(slot().with(key, value)))
        return with("header", header().with("slots", slots))
    }

    @Test fun withoutAPasswordItAsksForOne() {
        assertEquals(ReadResult.NeedsPassword(ImportFormat.AEGIS), readImport(encrypted))
    }

    @Test fun opensAsItsPlainTwin() {
        val twin = okRead(plain)
        assertSameRead(twin, okRead(encrypted, FIXTURE_PASSWORD))
        // Three TOTPs enter; HOTP and Steam are listed.
        assertEquals(3, twin.items.size)
        assertEquals(listOf(SkipReason.NOT_TOTP, SkipReason.NOT_TOTP), twin.skipped.map { it.reason })
    }

    @Test fun aWrongPasswordIsSaidWithoutDecryptingTheVault() {
        var decrypts = 0
        try {
            openAegis(jsonRoot(encrypted), "prueba-clavero-1.5".toCharArray(), JSON_MAX_TREE_BYTES, onDecrypt = { decrypts++ })
            fail("opened")
        } catch (e: ImportFailure) {
            assertEquals(IMPORT_WRONG_PASSWORD, e.message)
        }
        assertEquals(0, decrypts)
    }

    @Test fun everyByteFlippedFailsAGcmTag() {
        val e = aegisEncrypted(jsonRoot(encrypted))
        val s = Secrets()
        val slot = e.slots.single()
        val derived = aegisSlotKey(slot, FIXTURE_PASSWORD.toCharArray(), s)
        val master = aegisUnwrap(slot, derived, s)
        assertNotNull(master)
        master!!
        assertTrue(aegisDb(e, master, s) {}.isNotEmpty())
        var flips = 0
        // The wrapped master key with its tag, and its nonce.
        assertEquals(32 + 16, slot.sealedKey.size)
        for (part in 0..1) {
            val bytes = listOf(slot.sealedKey, slot.nonce)[part]
            for (i in bytes.indices) {
                val f = bytes.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
                val changed = if (part == 0) slot.copy(sealedKey = f) else slot.copy(nonce = f)
                assertNull("slot $part/$i", aegisUnwrap(changed, derived, s))
                flips++
            }
        }
        // The vault with its tag, and its nonce.
        var decrypts = 0
        for (part in 0..1) {
            val bytes = listOf(e.sealed, e.nonce)[part]
            for (i in bytes.indices) {
                val f = bytes.copyOf().also { it[i] = (it[i].toInt() xor 0x80).toByte() }
                val changed = if (part == 0) e.copy(sealed = f) else e.copy(nonce = f)
                try {
                    aegisDb(changed, master, s) { decrypts++ }
                    fail("db $part/$i accepted")
                } catch (x: ImportFailure) {
                    assertEquals(IMPORT_WRONG_PASSWORD, x.message)
                }
                flips++
            }
        }
        assertTrue(flips > 32 + 12 + 16 + 12 + 16 + 100)
        s.wipe()
    }

    @Test fun kdfOutOfRangeIsRefusedBeforeWork() {
        val root = jsonRoot(encrypted)
        val cases = listOf(
            root.withSlot("n", 1 shl 21), root.withSlot("n", 1 shl 13), root.withSlot("n", (1 shl 15) + 1),
            root.withSlot("r", 17), root.withSlot("r", 0), root.withSlot("p", 5), root.withSlot("p", 0),
            root.withSlot("n", 1 shl 20).withSlot("r", 2), // 256 MiB.
            root.withSlot("n", 1 shl 16).withSlot("r", 1), // RFC 7914: with r = 1, N < 2^16.
        ).map { it.bytes() } +
            // Not whole numbers, as a fraction and with an exponent: the bounds refuse them (NOT_A_WHOLE_NUMBER).
            listOf("32768.5", "3.2768e4", "32768.0").map { root.withSlot("n", LITERAL_MARK).bytesWithLiteral(it) }
        failedRead(cases[0])
        for (c in cases) {
            var message = ""
            val ms = millis { message = failedRead(c) }
            assertEquals(String(c), IMPORT_KDF_TOO_BIG, message)
            assertTrue("$ms ms", ms < 50)
        }
    }

    @Test fun everySlotIsCheckedBeforeAnyWork() {
        // A good first slot does not run while a later one asks too much.
        val root = jsonRoot(encrypted)
        val good = root.slot()
        val bad = good.with("type", 1).with("n", 1 shl 22)
        val two = root.with("header", root.header().with("slots", JsonArray(listOf(good, bad))))
        failedRead(two.bytes()) // Warms the classes up.
        val ms = millis { assertEquals(IMPORT_KDF_TOO_BIG, failedRead(two.bytes())) }
        assertTrue("$ms ms", ms < 50)
    }

    @Test fun onlyTheFirstPasswordSlotIsTried() {
        // Ruling R24 (M-2): Aegis keeps one password per vault, so one scrypt per attempt, whatever the file says.
        val root = jsonRoot(encrypted)
        val good = root.slot()
        fun withSlots(vararg slots: JsonObject) = root.with("header", root.header().with("slots", JsonArray(slots.toList())))
        // Two slots at the bounds (128 MiB, p = 4 each): only the first is kept, so at most one derivation.
        val maximal = good.with("n", 1 shl 17).with("r", 8).with("p", 4)
        assertEquals(1, aegisEncrypted(withSlots(maximal, maximal)).slots.size)
        // Two good slots and a wrong password: one derivation, then «no es correcta».
        var derivations = 0
        try {
            openAegis(withSlots(good, good), "otra".toCharArray(), JSON_MAX_TREE_BYTES, onDerive = { derivations++ })
            fail("opened")
        } catch (e: ImportFailure) {
            assertEquals(IMPORT_WRONG_PASSWORD, e.message)
        }
        assertEquals(1, derivations)
        // The right password through the first: it opens. A first slot that does not open: the second is never tried.
        assertSameRead(okRead(plain), okRead(withSlots(good, good).bytes(), FIXTURE_PASSWORD))
        val otherKey = good.with("key", "00".repeat(32))
        derivations = 0
        try {
            openAegis(withSlots(otherKey, good), FIXTURE_PASSWORD.toCharArray(), JSON_MAX_TREE_BYTES, onDerive = { derivations++ })
            fail("opened through the second slot")
        } catch (e: ImportFailure) {
            assertEquals(IMPORT_WRONG_PASSWORD, e.message)
        }
        assertEquals(1, derivations)
    }

    @Test fun brokenHeadersAreRefused() {
        val root = jsonRoot(encrypted)
        val noPasswordSlot = root.with("header", root.header().with("slots", JsonArray(listOf(root.slot().with("type", 2)))))
        val broken = listOf(
            root.withSlot("salt", "zz"), root.withSlot("key", "00"), root.withSlot("salt", 5),
            root.with("header", root.header().with("params", null)), root.with("header", root.header().with("slots", null)),
            root.with("db", "%%%"), noPasswordSlot,
            root.with("header", root.header().with("slots", JsonArray(List(17) { root.slot() }))),
        )
        for (c in broken) assertEquals(c.toString(), IMPORT_BROKEN, failedRead(c.bytes()))
    }

    @Test fun truncatedFilesFail() {
        assertTruncationsFail(encrypted)
        val root = jsonRoot(encrypted)
        val db = (root["db"] as JsonPrimitive).content
        for (k in 1..5) {
            val message = failedRead(root.with("db", db.substring(0, db.length * k / 6)).bytes())
            assertTrue(message, message == IMPORT_BROKEN || message == IMPORT_WRONG_PASSWORD)
        }
    }

    @Test fun anEncryptedPartOverTheBoundIsRefusedBeforeDecoding() {
        val root = jsonRoot(encrypted)
        assertEquals(IMPORT_ENCRYPTED_TOO_BIG, failedRead(root.with("db", "A".repeat(IMPORT_MAX_ENCRYPTED_CHARS + 4)).bytes()))
    }

    @Test fun passwordAndKeysAreZeroed() {
        // M-4: the exact number of arrays on each path, and the keys and the plaintext themselves among them.
        val seen = ArrayList<ByteArray>()
        openAegis(jsonRoot(encrypted), FIXTURE_PASSWORD.toCharArray(), JSON_MAX_TREE_BYTES, Secrets(seen::add))
        assertAllZero(seen, exactly = OPEN_KEPT)
        val steps = ArrayList<ByteArray>()
        val s = Secrets(steps::add)
        val e = aegisEncrypted(jsonRoot(encrypted))
        val derived = aegisSlotKey(e.slots.single(), FIXTURE_PASSWORD.toCharArray(), s)
        val master = aegisUnwrap(e.slots.single(), derived, s)!!
        val plaintext = aegisDb(e, master, s)
        assertKept(steps, derived, master, plaintext)
        s.wipe()
        assertAllZero(steps, exactly = OPEN_KEPT)
        val wrong = ArrayList<ByteArray>()
        try {
            openAegis(jsonRoot(encrypted), "otra".toCharArray(), JSON_MAX_TREE_BYTES, Secrets(wrong::add))
            fail("opened")
        } catch (x: ImportFailure) {
            assertEquals(IMPORT_WRONG_PASSWORD, x.message)
        }
        assertAllZero(wrong, exactly = WRONG_KEPT)
    }
}
