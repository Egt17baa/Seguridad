package app.seguridad.vault

import app.seguridad.crypto.CryptoFailure
import app.seguridad.crypto.KdfParams
import app.seguridad.crypto.pk.cardBytes
import app.seguridad.crypto.pk.parseCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Bóveda versión 3 y copia versión 2 (identidad y contactos), con los archivos reales de la 1.1: `vault-1.1.json`
 * (bóveda versión 2) y `backup-1.1.sgbak` (copia versión 1). Contraseña maestra `maestra-de-prueba`,
 * `masterCheckedAt` = 1790000000000, KDF (1024, 1, 1); entradas «Banco» (con el adjunto hola.txt) y «Correo».
 */
class VaultV3Test {
    @get:Rule val tmp = TemporaryFolder()

    private val master = "maestra-de-prueba"
    private val checkedAt = 1_790_000_000_000L

    /** R3: cerca de la última maestra del archivo real; con otro reloj, la huella pediría la maestra a los 7 días. */
    private var clock = checkedAt + 3_600_000L

    private fun phone(name: String) = File(tmp.root, name).apply { mkdirs() }

    private fun store(dir: File) =
        VaultStore(File(dir, "vault.bin"), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = { KdfParams(1024, 1, 1) })

    private fun fixtureBytes(name: String) = javaClass.getResourceAsStream("/seguridad/$name")!!.use { it.readBytes() }

    /** Un teléfono con la bóveda real de la 1.1 en su sitio, todavía cerrada. */
    private suspend fun phone11(name: String = "p11"): Pair<File, VaultStore> {
        val dir = phone(name)
        File(dir, "vault.bin").writeBytes(fixtureBytes("vault-1.1.json"))
        // El adjunto cifrado de «Banco» (att/<id>.bin) es el de la copia real: el vault.bin no lo lleva.
        ZipInputStream(ByteArrayInputStream(fixtureBytes("backup-1.1.sgbak"))).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name.startsWith("att/")) File(dir, e.name).apply { parentFile!!.mkdirs() }.writeBytes(zip.readBytes())
            }
        }
        return dir to store(dir).apply { load() }
    }

    private fun diskFile(dir: File) = VaultCrypto.decodeFile(File(dir, "vault.bin").readText())

    private fun VaultStore.entries() = (state.value as VaultState.Unlocked).entries

    private fun identityEntry() = newIdentityEntry(clock)

    /** La clave de datos de la bóveda real, como la daría la huella. */
    private fun fixtureDek(): ByteArray =
        VaultCrypto.unlock(VaultCrypto.decodeFile(fixtureBytes("vault-1.1.json").decodeToString()), master.toCharArray())

    private fun fixtureEntries(): List<VaultEntry> =
        VaultCrypto.read(VaultCrypto.decodeFile(fixtureBytes("vault-1.1.json").decodeToString()), fixtureDek()).sortedBy { it.title.lowercase() }

    // ---- Los archivos reales de la 1.1 ----

    @Test fun laBovedaRealDeLa11SeAbreConLaMaestra() = runTest {
        val (_, s) = phone11()
        assertEquals(2, VaultCrypto.decodeFile(fixtureBytes("vault-1.1.json").decodeToString()).version) // El punto de partida es la 2.
        s.unlock(master.toCharArray())
        val entries = s.entries()
        assertEquals(listOf("Banco", "Correo"), entries.map { it.title })
        assertEquals("clave-1.1", entries[0].password)
        assertEquals("hola.txt", entries[0].attachments.single().name)
        assertEquals("JBSWY3DPEHPK3PXP", entries[1].totp!!.secret)
        assertEquals("hola", s.openAttachment(entries[0].id, entries[0].attachments.single().id)!!.decodeToString())
    }

    @Test fun trasLaMaestraElArchivoEsLaVersion3ConLasMismasEntradas() = runTest {
        val before = fixtureEntries()
        val (dir, s) = phone11()
        s.unlock(master.toCharArray())
        val onDisk = diskFile(dir)
        assertEquals("seguridad-vault", onDisk.format)
        assertEquals(3, onDisk.version)
        assertEquals(VaultFile.VERSION, onDisk.version)
        assertEquals(clock, onDisk.masterCheckedAt)
        assertFalse(s.needsMasterForContacts)
        assertEquals(before, s.entries()) // Banco (con su adjunto) y Correo, idénticos.
        // La misma clave de datos: una huella dactilar configurada antes sigue valiendo.
        assertArrayEquals(fixtureDek(), s.dataKeyCopy())
        // Y desde cero, como al reiniciar la app.
        s.lock()
        val again = store(dir).apply { load() }
        again.unlock(master.toCharArray())
        assertEquals(before, again.entries())
        assertEquals("hola", again.openAttachment(before[0].id, before[0].attachments.single().id)!!.decodeToString())
    }

    @Test fun lasEntradasAntiguasNoGananCamposNuevosAlEscribirse() = runTest {
        val (dir, s) = phone11()
        s.unlock(master.toCharArray())
        val json = ByteArrayOutputStream().also { assertTrue(s.exportBackup(it)) }.toByteArray()
        assertTrue(json.isNotEmpty())
        // El JSON de una entrada de la 1.1 no lleva ninguno de los campos nuevos (valores por defecto fuera).
        val encoded = app.seguridad.core.appJson.encodeToString(s.entries())
        listOf("identitySeed", "\"card\"", "verified").forEach { assertFalse(it, encoded.contains(it)) }
        assertEquals(3, diskFile(dir).version)
    }

    // ---- Abierta con la huella: sigue en la versión 2 ----

    @Test fun conLaHuellaSigueEnLa2YNoSeAdmiteLaIdentidad() = runTest {
        val (dir, s) = phone11()
        val diskBefore = File(dir, "vault.bin").readText()
        s.unlockWithKey(fixtureDek())
        assertEquals(VaultState.Unlocked::class, s.state.value::class)
        assertEquals(2, diskFile(dir).version)
        assertTrue(s.needsMasterForContacts)
        failsWith<IllegalStateException> { s.upsert(identityEntry()) }
        assertEquals(diskBefore, File(dir, "vault.bin").readText()) // Nada se escribió.
        assertEquals(listOf("Banco", "Correo"), s.entries().map { it.title })
        assertTrue(s.needsMasterForContacts)
    }

    @Test fun conLaHuellaUnaEntradaNormalSeGuardaEnLa2() = runTest {
        val (dir, s) = phone11()
        s.unlockWithKey(fixtureDek())
        s.upsert(VaultEntry("n1", EntryType.NOTE, "Nota", notes = "x", createdAt = 1, updatedAt = 1))
        assertEquals(2, diskFile(dir).version) // La 1.1 todavía puede abrirla: no se ha pedido nada nuevo.
        assertEquals(3, s.entries().size)
        assertTrue(s.needsMasterForContacts)
    }

    @Test fun unContactoTampocoSeAdmiteEnLa2() = runTest {
        val (dir, s) = phone11()
        s.unlockWithKey(fixtureDek())
        val contact = contactEntry("Ana")
        failsWith<IllegalStateException> { s.upsert(contact) }
        failsWith<IllegalStateException> { s.addAll(listOf(contact)) }
        failsWith<IllegalStateException> { s.addAll(listOf(identityEntry())) }
        assertEquals(2, diskFile(dir).version)
        assertEquals(2, s.entries().size)
    }

    @Test fun confirmMaestraMigraALa3YDejaGuardarLaIdentidad() = runTest {
        val (dir, s) = phone11()
        s.unlockWithKey(fixtureDek())
        assertTrue(s.needsMasterForContacts)
        s.confirmMaster(master.toCharArray())
        assertFalse(s.needsMasterForContacts)
        val migrated = diskFile(dir)
        assertEquals(3, migrated.version)
        assertEquals(clock, migrated.masterCheckedAt)
        assertEquals(listOf("Banco", "Correo"), s.entries().map { it.title })
        val identity = identityEntry()
        s.upsert(identity)
        assertEquals(3, diskFile(dir).version)
        assertEquals(identity.id, s.entries().identity()!!.id)
        // La clave de datos no cambió: la huella sigue valiendo.
        assertArrayEquals(fixtureDek(), s.dataKeyCopy())
        s.lock()
        val again = store(dir).apply { load() }
        again.unlock(master.toCharArray())
        assertEquals(setOf("Banco", "Correo", "Mi identidad"), again.entries().map { it.title }.toSet())
        assertEquals("hola", again.openAttachment(again.entries().first { it.title == "Banco" }.id, again.entries().first { it.title == "Banco" }.attachments.single().id)!!.decodeToString())
    }

    @Test fun confirmMaestraConUnaMaestraMalaLanzaLoMismoQueUnlockYNoTocaElArchivo() = runTest {
        val (dir, s) = phone11()
        s.unlockWithKey(fixtureDek())
        val before = File(dir, "vault.bin").readText()
        val unlockError = try {
            store(dir).apply { load() }.unlock("mala".toCharArray())
            null
        } catch (e: CryptoFailure) {
            e
        }
        val confirmError = failsWith<CryptoFailure> { s.confirmMaster("mala".toCharArray()) }
        assertEquals(unlockError!!.message, confirmError.message)
        assertEquals(before, File(dir, "vault.bin").readText())
        assertTrue(s.needsMasterForContacts)
        assertTrue(s.state.value is VaultState.Unlocked) // Sigue abierta, como estaba.
    }

    @Test fun confirmMaestraEnLaVersion3SoloComprueba() = runTest {
        val (dir, s) = phone11()
        s.unlock(master.toCharArray())
        val before = File(dir, "vault.bin").readText()
        s.confirmMaster(master.toCharArray())
        assertEquals(before, File(dir, "vault.bin").readText())
        failsWith<CryptoFailure> { s.confirmMaster("mala".toCharArray()) }
        assertEquals(before, File(dir, "vault.bin").readText())
        assertFalse(s.needsMasterForContacts)
    }

    @Test fun unaBovedaNuevaEsLaVersion3YNoPideLaMaestraParaLosContactos() = runTest {
        val dir = phone("nueva")
        val s = store(dir).apply { load(); create("otra".toCharArray()) }
        assertEquals(3, diskFile(dir).version)
        assertFalse(s.needsMasterForContacts)
        s.upsert(identityEntry())
        s.upsert(contactEntry("Ana"))
        assertEquals(setOf(EntryType.IDENTITY, EntryType.CONTACT), s.entries().map { it.type }.toSet())
    }

    @Test fun elCambioDeMaestraDeUnaBovedaDeLaHuellaLaDejaEnLa3() = runTest {
        val (dir, s) = phone11()
        s.unlockWithKey(fixtureDek())
        assertTrue(s.changeMaster("nueva-maestra".toCharArray()))
        assertEquals(3, diskFile(dir).version)
        val again = store(dir).apply { load() }
        again.unlock("nueva-maestra".toCharArray())
        assertEquals(listOf("Banco", "Correo"), again.entries().map { it.title })
        assertFalse(again.needsMasterForContacts)
    }

    // ---- Solo una identidad ----

    @Test fun unaSegundaIdentidadSeRechaza() = runTest {
        val dir = phone("una")
        val s = store(dir).apply { load(); create("otra".toCharArray()) }
        val first = identityEntry()
        s.upsert(first)
        failsWith<IllegalStateException> { s.upsert(identityEntry()) }
        failsWith<IllegalStateException> { s.addAll(listOf(identityEntry())) }
        assertEquals(1, s.entries().count { it.type == EntryType.IDENTITY })
        // Editar la que hay (mismo id) sí se puede.
        s.upsert(first.copy(updatedAt = clock + 1))
        assertEquals(clock + 1, s.entries().identity()!!.updatedAt)
        // Y borrada la identidad, se puede crear otra.
        s.delete(first.id)
        s.upsert(identityEntry())
        assertEquals(1, s.entries().count { it.type == EntryType.IDENTITY })
    }

    @Test fun unaIdentidadNoLlevaAdjuntos() = runTest {
        val dir = phone("adjuntos")
        val s = store(dir).apply { load(); create("otra".toCharArray()) }
        val identity = identityEntry()
        val contact = contactEntry("Ana")
        s.upsert(identity)
        s.upsert(contact)
        assertFalse(s.addAttachment(identity.id, ByteArrayInputStream(byteArrayOf(1)), "a.txt"))
        assertFalse(s.addAttachment(contact.id, ByteArrayInputStream(byteArrayOf(1)), "a.txt"))
        assertTrue(s.entries().all { it.attachments.isEmpty() })
    }

    // ---- Copia versión 2 ----

    @Test fun laCopiaRealDeLa11SeImportaConSuAdjunto() = runTest {
        val dir = phone("destino")
        val s = store(dir).apply { load(); create("otra".toCharArray()) }
        s.importReplace(ByteArrayInputStream(fixtureBytes("backup-1.1.sgbak")), master.toCharArray())
        val entries = s.entries()
        assertEquals(listOf("Banco", "Correo"), entries.map { it.title })
        val banco = entries[0]
        assertEquals("hola", s.openAttachment(banco.id, banco.attachments.single().id)!!.decodeToString())
        assertEquals(3, diskFile(dir).version) // La importación pide la maestra, así que queda en la 3.
        assertEquals(fixtureEntries(), entries)
    }

    private fun markerOf(zip: ByteArray): String =
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            assertEquals("seguridad-backup", z.nextEntry!!.name)
            z.readBytes().decodeToString()
        }

    private fun withMarker(zip: ByteArray, marker: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { out ->
            ZipInputStream(ByteArrayInputStream(zip)).use { input ->
                var first = true
                while (true) {
                    val e = input.nextEntry ?: break
                    val data = input.readBytes()
                    out.putNextEntry(ZipEntry(e.name))
                    out.write(if (first) marker.toByteArray(Charsets.US_ASCII) else data)
                    out.closeEntry()
                    first = false
                }
            }
        }
        return bos.toByteArray()
    }

    private suspend fun backupOf(s: VaultStore): ByteArray = ByteArrayOutputStream().also { assertTrue(s.exportBackup(it)) }.toByteArray()

    @Test fun exportBackupEscribeLaVersion2YSeVuelveAImportar() = runTest {
        val (_, s) = phone11()
        s.unlock(master.toCharArray())
        val zip = backupOf(s)
        assertEquals("2\n", markerOf(zip))
        val dest = store(phone("otro")).apply { load(); create("otra".toCharArray()) }
        dest.importReplace(ByteArrayInputStream(zip), master.toCharArray())
        assertEquals(fixtureEntries(), dest.entries())
        val banco = dest.entries()[0]
        assertEquals("hola", dest.openAttachment(banco.id, banco.attachments.single().id)!!.decodeToString())
    }

    @Test fun unaCopiaConUnNumeroMayorDaCopiaMasNueva() = runTest {
        val (_, s) = phone11()
        s.unlock(master.toCharArray())
        val zip = backupOf(s)
        val dest = store(phone("otro")).apply { load(); create("otra".toCharArray()) }
        listOf("3\n", "4\n", "10\n").forEach { marker ->
            val error = failsWith<CryptoFailure> { dest.importReplace(ByteArrayInputStream(withMarker(zip, marker)), master.toCharArray()) }
            assertEquals(BACKUP_NEWER, error.message)
        }
        // Lo que no es un número, o un 0, es una copia dañada, no una «más nueva».
        listOf("0\n", "x\n", "2", "2\r\n", "").forEach { marker ->
            val error = failsWith<CryptoFailure> { dest.importReplace(ByteArrayInputStream(withMarker(zip, marker)), master.toCharArray()) }
            assertEquals(marker, BACKUP_DAMAGED, error.message)
        }
        assertEquals(0, dest.entries().size) // Nada cambió: sigue su bóveda vacía.
    }

    @Test fun unaCopiaVersion2ConIdentidadImportadaSobreLaHuellaQuedaEnLa3() = runTest {
        // El teléfono A tiene una identidad y hace una copia.
        val a = store(phone("a")).apply { load(); create("maestra-a".toCharArray()) }
        val identity = identityEntry()
        val contact = contactEntry("Ana")
        a.upsert(identity)
        a.upsert(contact)
        val zip = backupOf(a)
        assertEquals("2\n", markerOf(zip))
        // El teléfono B se abrió con la huella: su bóveda es la versión 2.
        val (dirB, b) = phone11("b")
        b.unlockWithKey(fixtureDek())
        assertTrue(b.needsMasterForContacts)
        b.importReplace(ByteArrayInputStream(zip), "maestra-a".toCharArray())
        assertEquals(3, diskFile(dirB).version)
        assertFalse(b.needsMasterForContacts)
        assertEquals(identity.id, b.entries().identity()!!.id)
        assertTrue(b.entries().any { it.id == contact.id })
        assertEquals(contact.contactCard(), b.entries().first { it.id == contact.id }.contactCard())
    }

    // ---- La versión va en los datos asociados ----

    @Test fun cambiarLaVersionDeUnArchivoRompeLaApertura() = runTest {
        val (dir, s) = phone11()
        s.unlock(master.toCharArray()) // Ahora es la 3.
        val v3 = diskFile(dir)
        assertEquals(3, v3.version)
        assertThrows(CryptoFailure::class.java) { VaultCrypto.unlock(v3.copy(version = 2), master.toCharArray()) }
        val v2 = VaultCrypto.decodeFile(fixtureBytes("vault-1.1.json").decodeToString())
        assertThrows(CryptoFailure::class.java) { VaultCrypto.unlock(v2.copy(version = 3), master.toCharArray()) }
        assertThrows(CryptoFailure::class.java) { VaultCrypto.unlock(v3.copy(version = 4), master.toCharArray()) }
        // Ni con la clave de datos se leen las entradas con otra versión.
        val dek = fixtureDek()
        assertThrows(CryptoFailure::class.java) { VaultCrypto.read(v2.copy(version = 3), dek) }
    }

    @Test fun elLectorDeLaVersion2YDeLaVersion3() = runTest {
        val v2 = VaultCrypto.decodeFile(fixtureBytes("vault-1.1.json").decodeToString())
        val dek = fixtureDek()
        // write() conserva la versión del archivo.
        assertEquals(2, VaultCrypto.write(v2, dek, fixtureEntries()).version)
        val v3 = VaultCrypto.afterMasterUnlock(v2, dek.copyOf(), master.toCharArray(), clock, KdfParams(1024, 1, 1))
        assertEquals(3, v3.version)
        assertEquals(v2.revision + 1, v3.revision)
        assertEquals(3, VaultCrypto.write(v3, dek, fixtureEntries()).version)
        assertArrayEquals(dek, VaultCrypto.unlock(v3, master.toCharArray()))
    }

    @Test fun writeEnLaVersion2RechazaIdentidadYContactos() {
        val v2 = VaultCrypto.decodeFile(fixtureBytes("vault-1.1.json").decodeToString())
        val dek = fixtureDek()
        assertThrows(IllegalStateException::class.java) { VaultCrypto.write(v2, dek, fixtureEntries() + identityEntry()) }
        assertThrows(IllegalStateException::class.java) { VaultCrypto.write(v2, dek, fixtureEntries() + contactEntry("Ana")) }
        val v3 = VaultCrypto.afterMasterUnlock(v2, dek.copyOf(), master.toCharArray(), clock, KdfParams(1024, 1, 1))
        val written = VaultCrypto.write(v3, dek, fixtureEntries() + identityEntry() + contactEntry("Ana"))
        assertEquals(4, VaultCrypto.read(written, dek).size)
    }

    private fun contactEntry(name: String): VaultEntry {
        val bytes = identityEntry().openIdentity().use { it.cardBytes(name) }
        return newContactEntry(parseCard(bytes), bytes, clock, viaQr = false)
    }

    /** La excepción que lanza [block] (que tiene que lanzar la del tipo [T]). */
    private suspend inline fun <reified T : Throwable> failsWith(block: () -> Unit): T =
        try {
            block()
            throw AssertionError("tenía que lanzar ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e is T) e else throw e
        }
}
