package app.seguridad.redteam

import app.seguridad.crypto.CHUNK_BYTES
import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.MAX_SAVED_NAME_BYTES
import app.seguridad.crypto.decryptStream
import app.seguridad.crypto.encryptStream
import app.seguridad.crypto.safeFileName
import app.seguridad.tools.qr.parseLink
import app.seguridad.tools.share.ShareTemp
import app.seguridad.tools.slip39.GroupSpec
import app.seguridad.tools.slip39.Slip39
import app.seguridad.tools.slip39.Slip39Failure
import app.seguridad.vault.BiometricRefusal
import app.seguridad.vault.MASTER_RECHECK_MS
import app.seguridad.vault.MASTER_TOO_WEAK
import app.seguridad.vault.MASTER_WORD_LISTS
import app.seguridad.vault.VaultCrypto
import app.seguridad.vault.VaultFile
import app.seguridad.vault.VaultKdf
import app.seguridad.vault.masterWords
import app.seguridad.vault.newMasterProblem
import app.seguridad.vault.sanitizeReceivedName
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom

private fun appAsset(name: String): List<String> =
    listOf("src/main/assets", "../app/src/main/assets").map { File(it, name) }.first { it.exists() }.readLines().filter { it.isNotBlank() }

/*
 * Red team of Clavero 1.4.0: crypto formats, file names, SLIP-0039, the fingerprint's clock rule and the rule of a new
 * master password. Same naming as RedTeamImportTest: rtN_… failed while RT-N was open and passes since its fix,
 * resists… passes.
 */
class RedTeamCryptoTest {
    // ---------------------------------------------------------------------------------------------------------------
    // RT-3: the rule of a new master lets through a known word written in «leet» (P4ssw0rd, C0ntr4s3ñ4).
    // ---------------------------------------------------------------------------------------------------------------

    private val known by lazy { masterWords(MASTER_WORD_LISTS.map(::appAsset)) }

    /**
     * RT-3. Before the fix, `masterCore` kept only the letters, so a digit or a symbol written for a letter («4» for
     * «a», «0» for «o», «3» for «e», «@» for «a», «$» for «s», «1» for «i» or «l») took the letter out: «P4ssw0rd2024!»
     * became «psswrd», which is in no list, and the estimate gives it about 60 bits. These are the first variants every
     * cracking tool tries (the «leet» rules of hashcat and John the Ripper), so the «una sola palabra conocida» rule of
     * CRY-A01 missed them. The same with the word typed in full-width letters («ｐａｓｓｗｏｒｄ12345»): `masterCore` did
     * not apply NFKC, so «ｐａｓｓｗｏｒｄ» was in no list, and each full-width letter counts as a non-ASCII symbol in the
     * estimate.
     */
    @Test fun rt3_aKnownWordInLeetIsRefusedAsANewMaster() {
        for (candidate in listOf("P4ssw0rd2024!", "C0ntr4s3ñ4!!", "P@\$\$w0rd1234", "Dr4g0n!2024", "Pr1nc3ss2024", "ｐａｓｓｗｏｒｄ12345", "ｃｏｎｔｒａｓｅñａ2024")) {
            assertEquals("«$candidate» is one known word", MASTER_TOO_WEAK, newMasterProblem(candidate, known))
        }
    }

    /** What the rule does catch today: the same words without the substitutions (CRY-A01). */
    @Test fun resistsAKnownWordWithDigitsAndSymbolsAround() {
        for (candidate in listOf("Password2024!", "Contraseña2024!", "dragon\u200B\u200B\u200B\u200B\u200B", "PASSWORD2024!", "Contrasen\u0303a2024!")) {
            assertTrue("«$candidate»", newMasterProblem(candidate, known) != null)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // .sgenc (STREAM): truncation, reordering, splicing records of another file.
    // ---------------------------------------------------------------------------------------------------------------

    private val cheap = KdfParams(memoryKiB = 8, iterations = 1, parallelism = 1)
    private val overhead = 16

    private fun sealed(plain: ByteArray, password: String = "clave-de-prueba-1", name: String = "a.txt"): ByteArray =
        ByteArrayOutputStream().also { encryptStream(ByteArrayInputStream(plain), it, name, password.toCharArray(), kdf = cheap) }.toByteArray()

    private fun opens(file: ByteArray, password: String = "clave-de-prueba-1"): Boolean = try {
        decryptStream(ByteArrayInputStream(file), ByteArrayOutputStream(), password.toCharArray())
        true
    } catch (e: CryptoFailure) {
        false
    }

    @Test fun resistsStreamTruncationReorderAndSplice() {
        val plain = ByteArray(3 * CHUNK_BYTES).also { SecureRandom().nextBytes(it) }
        val file = sealed(plain)
        val record = CHUNK_BYTES + overhead
        val start = file.size - 3 * record
        assertTrue(opens(file))
        fun rec(f: ByteArray, i: Int) = f.copyOfRange(start + i * record, start + (i + 1) * record)
        val head = file.copyOfRange(0, start)
        // The last record dropped (a cut exactly at a record boundary).
        assertFalse(opens(head + rec(file, 0) + rec(file, 1)))
        // Two records swapped, and one repeated.
        assertFalse(opens(head + rec(file, 1) + rec(file, 0) + rec(file, 2)))
        assertFalse(opens(head + rec(file, 0) + rec(file, 0) + rec(file, 1) + rec(file, 2)))
        // A record appended after the last one.
        assertFalse(opens(file + rec(file, 1)))
        // The records of another file with the same password spliced in: other salt and nonce prefix.
        val other = sealed(plain)
        assertFalse(opens(head + rec(other, 0) + rec(other, 1) + rec(other, 2)))
        // The name record of another file (the one an attacker chose) in front of this file's data.
        val evilName = sealed(ByteArray(0), name = "../../shared_prefs/x.xml")
        assertFalse(opens(evilName.copyOfRange(0, evilName.size - overhead) + rec(file, 0) + rec(file, 1) + rec(file, 2)))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Names of restored and received files: traversal, reversal, NUL, reserved and huge names.
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun resistsHostileFileNames() {
        val hostile = listOf(
            "../../../data/data/io.github.egt17baa.seguridad/files/vault/vault.bin",
            "..\\..\\shared_prefs\\x.xml",
            "factura\u202Efdp.exe",
            "a\u0000b.txt",
            "..",
            ".",
            "\u200B\u200D\uFEFF",
            "x".repeat(10_000) + ".pdf",
            "€".repeat(200),
            "\uD83D".repeat(300),
            "con.txt\u0085\u2028",
        )
        for (n in hostile) {
            for (out in listOf(safeFileName(n), sanitizeReceivedName(n))) {
                assertFalse("«$out»", '/' in out || '\\' in out || '\u0000' in out || out == "." || out == "..")
                assertFalse("«$out»", out.any { it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it.code < 0x20 || it == '\u2028' })
                assertTrue("«$out»", out.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_NAME_BYTES && out.isNotEmpty())
            }
        }
        assertEquals("vault.bin", safeFileName(hostile[0]))
    }

    @Test fun resistsShareCopyNameInjection() {
        val cache = Files.createTempDirectory("rt-share").toFile()
        try {
            val share = ShareTemp(cache, System::currentTimeMillis)
            for (n in listOf("../copia-de-foto.jpg", "copia-de-foto.jpg/../../x", "copia-de-foto.apk", "copia-de-foto.jpg\u0000.png", "COPIA-DE-FOTO.JPG")) {
                try {
                    share.prepare(n, byteArrayOf(1))
                    fail("accepted «$n»")
                } catch (e: IllegalArgumentException) {
                    // Refused before anything is written.
                }
            }
            val f = share.prepare("copia-de-foto.jpg", byteArrayOf(1))
            assertEquals(File(cache, "compartir").canonicalPath, f.parentFile.parentFile.canonicalPath)
            assertEquals(32, f.parentFile.name.length)
        } finally {
            cache.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // SLIP-0039: foreign, tampered and costly parts.
    // ---------------------------------------------------------------------------------------------------------------

    private val slip by lazy { Slip39(appAsset("slip39_english.txt")) }

    private fun failure(block: () -> Unit): String? = try {
        block()
        null
    } catch (e: Slip39Failure) {
        e.message
    }

    @Test fun resistsForeignTamperedAndCostlySlip39Parts() {
        val secret = ByteArray(16) { it.toByte() }
        val set = slip.splitSecret(secret, CharArray(0), 1, listOf(GroupSpec(2, 3))).single()
        assertArrayEquals(secret, slip.combineShares(listOf(set[0], set[2]), CharArray(0)))
        // A part of another set (another random identifier).
        val other = slip.splitSecret(secret, CharArray(0), 1, listOf(GroupSpec(2, 3))).single()
        assertTrue(failure { slip.combineShares(listOf(set[0], other[1]), CharArray(0)) } != null)
        // A part with one bit of its value changed and a VALID checksum (re-encoded): the digest at x = 254 catches it.
        val s1 = slip.decode(set[1], 1)!!
        val flipped = s1.value.copyOf().also { it[3] = (it[3].toInt() xor 1).toByte() }
        val forged = slip.encode(s1.copy(value = flipped))
        assertTrue(failure { slip.combineShares(listOf(set[0], forged), CharArray(0)) } != null)
        // Two parts of the same index (a part pasted twice with a changed value).
        assertTrue(failure { slip.combineShares(listOf(set[1], forged), CharArray(0)) } != null)
        // Parts that ask for 2^15 · 10 000 PBKDF2 iterations: refused before any derivation.
        val costly = set.map { slip.encode(slip.decode(it, 1)!!.let { s -> s.copy(exponent = 15) }) }
        val t = System.nanoTime()
        assertTrue(failure { slip.combineShares(costly.take(2), CharArray(0)) } != null)
        assertTrue((System.nanoTime() - t) / 1_000_000 < 2_000)
        // A flood of parts, a part of 10 000 characters and a passphrase that is not printable ASCII.
        assertTrue(failure { slip.combineShares(List(300) { set[0] }, CharArray(0)) } != null)
        assertTrue(failure { slip.combineShares(listOf("a".repeat(10_000)), CharArray(0)) } != null)
        assertTrue(failure { slip.combineShares(listOf(set[0], set[1]), "contraseña".toCharArray()) } != null)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The fingerprint's clock rule (A11): setting the clock back to make an old master check look recent.
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun resistsClockSetBackToStretchTheSevenDays() {
        val t0 = 1_790_000_000_000L
        val day = 24 * 60 * 60_000L
        val file = VaultFile(VaultFile.FORMAT, VaultFile.VERSION, VaultKdf(8, 1, 1), "", "", "", masterCheckedAt = t0, revision = 1)
        // Day 8: the master is due.
        assertEquals(BiometricRefusal.MASTER_DUE, VaultCrypto.biometricRefusal(file, t0 + 8 * day, highWater = t0 + 8 * day))
        // The thief sets the clock back to day 2: the highest time seen (day 8) says the clock went back.
        assertEquals(BiometricRefusal.CLOCK, VaultCrypto.biometricRefusal(file, t0 + 2 * day, highWater = t0 + 8 * day))
        // Before the master check itself.
        assertEquals(BiometricRefusal.CLOCK, VaultCrypto.biometricRefusal(file, t0 - 1, highWater = 0))
        // Within the 5 minutes of slack, and the boundary of the 7 days.
        assertNull(VaultCrypto.biometricRefusal(file, t0 + day, highWater = t0 + day + 4 * 60_000))
        assertEquals(BiometricRefusal.MASTER_DUE, VaultCrypto.biometricRefusal(file, t0 + MASTER_RECHECK_MS, highWater = 0))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // QR: a link that hides the user info it carries (the «PIN y códigos» rule wipes such links on lock).
    // ---------------------------------------------------------------------------------------------------------------

    @Test fun resistsUserInfoHiddenFromTheSecretRule() {
        val withCredentials = listOf(
            "https://ana:clave@banco.es/",
            "HTTPS://ana@banco.es",
            "https:\\\\ana:clave@banco.es",
            "https:ana:clave@banco.es",
            "https://ana:cl%40ve@banco.es",
            "https://an\ta:clave@banco.es",
            "ftp://ana:clave@ftp.example",
            "intent://ana:clave@banco.es#Intent;scheme=https;end",
        )
        for (l in withCredentials) assertTrue(l, parseLink(l).hasUserInfo)
    }
}
