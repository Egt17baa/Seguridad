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
        // The wrapped master key, its nonce and its tag.
        for (part in 0..2) {
            val bytes = listOf(slot.key, slot.nonce, slot.tag)[part]
            for (i in bytes.indices) {
                val f = bytes.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
                val changed = when (part) {
                    0 -> slot.copy(key = f)
                    1 -> slot.copy(nonce = f)
                    else -> slot.copy(tag = f)
                }
                assertNull("slot $part/$i", aegisUnwrap(changed, derived, s))
                flips++
            }
        }
        // The vault, its nonce and its tag.
        var decrypts = 0
        for (part in 0..2) {
            val bytes = listOf(e.db, e.nonce, e.tag)[part]
            for (i in bytes.indices) {
                val f = bytes.copyOf().also { it[i] = (it[i].toInt() xor 0x80).toByte() }
                val changed = when (part) {
                    0 -> e.copy(db = f)
                    1 -> e.copy(nonce = f)
                    else -> e.copy(tag = f)
                }
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
        )
        failedRead(cases[0].bytes())
        for (c in cases) {
            var message = ""
            val ms = millis { message = failedRead(c.bytes()) }
            assertEquals(IMPORT_KDF_TOO_BIG, message)
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

    @Test fun brokenHeadersAreRefused() {
        val root = jsonRoot(encrypted)
        val noPasswordSlot = root.with("header", root.header().with("slots", JsonArray(listOf(root.slot().with("type", 2)))))
        val broken = listOf(
            root.withSlot("salt", "zz"), root.withSlot("key", "00"), root.withSlot("salt", 5),
            root.with("header", root.header().with("params", null)), root.with("header", root.header().with("slots", null)),
            root.with("db", "%%%"), noPasswordSlot,
            root.with("header", root.header().with("slots", JsonArray(List(5) { root.slot() }))),
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
        val seen = ArrayList<ByteArray>()
        openAegis(jsonRoot(encrypted), FIXTURE_PASSWORD.toCharArray(), JSON_MAX_TREE_BYTES, Secrets(seen::add))
        // The password bytes, the scrypt key, the master key and the vault's plaintext.
        assertAllZero(seen, min = 4)
        val wrong = ArrayList<ByteArray>()
        try {
            openAegis(jsonRoot(encrypted), "otra".toCharArray(), JSON_MAX_TREE_BYTES, Secrets(wrong::add))
        } catch (e: ImportFailure) {
            assertEquals(IMPORT_WRONG_PASSWORD, e.message)
        }
        assertAllZero(wrong, min = 2)
    }
}
