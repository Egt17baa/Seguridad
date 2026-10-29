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
import org.junit.Assert.assertNull
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
        assertFalse(s.needsMasterForContacts.value)
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

    /** El JSON de las entradas tal como está escrito en [dir]/vault.bin: se lee el archivo y se descifra con la clave de datos. */
    private fun jsonOnDisk(dir: File, dek: ByteArray): String =
        VaultCrypto.readJson(diskFile(dir), dek).also { assertTrue(it.startsWith("[")) }

    @Test fun lasEntradasAntiguasNoGananCamposNuevosAlEscribirse() = runTest {
        val dek = fixtureDek()
        val original = VaultCrypto.readJson(VaultCrypto.decodeFile(fixtureBytes("vault-1.1.json").decodeToString()), dek)
        listOf("identitySeed", "\"card\"", "verified").forEach { assertFalse(it, original.contains(it)) }
        val (dir, s) = phone11()
        s.unlock(master.toCharArray())
        // Lo escrito en el disco por la migración es, byte a byte, el JSON de la 1.1: ningún campo nuevo.
        assertEquals(original, jsonOnDisk(dir, dek))
        // Y al guardar otra entrada, las antiguas siguen sin campos nuevos; la nueva (una nota) tampoco los lleva.
        s.upsert(VaultEntry("n1", EntryType.NOTE, "Nota", notes = "x", createdAt = 1, updatedAt = 1))
        val afterSave = jsonOnDisk(dir, dek)
        listOf("identitySeed", "\"card\"", "verified").forEach { assertFalse(it, afterSave.contains(it)) }
        assertTrue(afterSave.startsWith(original.removeSuffix("]")))
        // La copia lleva el mismo archivo.
        val zip = backupOf(s)
        val inZip = ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            generateSequence { z.nextEntry }.first { it.name == "vault.json" }.let { z.readBytes().decodeToString() }
        }
        assertEquals(File(dir, "vault.bin").readText(), inZip)
        // Con una identidad sí aparecen sus campos, y solo en su entrada.
        s.upsert(identityEntry())
        val withIdentity = jsonOnDisk(dir, dek)
        assertEquals(1, Regex("identitySeed").findAll(withIdentity).count())
        assertFalse(withIdentity.contains("\"verified\""))
    }

    // ---- Abierta con la huella: sigue en la versión 2 ----

    @Test fun conLaHuellaSigueEnLa2YNoSeAdmiteLaIdentidad() = runTest {
        val (dir, s) = phone11()
        val diskBefore = File(dir, "vault.bin").readText()
        s.unlockWithKey(fixtureDek())
        assertEquals(VaultState.Unlocked::class, s.state.value::class)
        assertEquals(2, diskFile(dir).version)
        assertTrue(s.needsMasterForContacts.value)
        failsWith<IllegalStateException> { s.upsert(identityEntry()) }
        assertEquals(diskBefore, File(dir, "vault.bin").readText()) // Nada se escribió.
        assertEquals(listOf("Banco", "Correo"), s.entries().map { it.title })
        assertTrue(s.needsMasterForContacts.value)
    }

    @Test fun conLaHuellaUnaEntradaNormalSeGuardaEnLa2() = runTest {
        val (dir, s) = phone11()
        s.unlockWithKey(fixtureDek())
        s.upsert(VaultEntry("n1", EntryType.NOTE, "Nota", notes = "x", createdAt = 1, updatedAt = 1))
        assertEquals(2, diskFile(dir).version) // La 1.1 todavía puede abrirla: no se ha pedido nada nuevo.
        assertEquals(3, s.entries().size)
        assertTrue(s.needsMasterForContacts.value)
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
        assertTrue(s.needsMasterForContacts.value)
        s.confirmMaster(master.toCharArray())
        assertFalse(s.needsMasterForContacts.value)
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
        assertTrue(s.needsMasterForContacts.value)
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
        assertFalse(s.needsMasterForContacts.value)
    }

    @Test fun confirmMaestraDiceSiHizoAlgo() = runTest {
        val (_, s) = phone11()
        assertFalse(s.confirmMaster(master.toCharArray())) // Cerrada: no hace nada.
        s.unlockWithKey(fixtureDek())
        assertTrue(s.confirmMaster(master.toCharArray())) // Migra.
        assertTrue(s.confirmMaster(master.toCharArray())) // Ya es la 3: solo comprueba.
        failsWith<CryptoFailure> { s.confirmMaster("mala".toCharArray()) }
        s.lock()
        assertFalse(s.confirmMaster(master.toCharArray())) // Bloqueada otra vez.
        val empty = store(phone("vacia")).apply { load() }
        assertFalse(empty.confirmMaster(master.toCharArray())) // Sin bóveda.
    }

    @Test fun needsMasterForContactsSigueLasTransiciones() = runTest {
        // Sin bóveda: no hace falta nada.
        val none = store(phone("ninguna")).apply { load() }
        assertFalse(none.needsMasterForContacts.value)
        // La real de la 1.1: pendiente desde que se carga hasta que se escribe la maestra.
        val (dir, s) = phone11()
        assertTrue(s.needsMasterForContacts.value)
        s.unlockWithKey(fixtureDek())
        assertTrue(s.needsMasterForContacts.value)
        s.lock()
        assertTrue(s.needsMasterForContacts.value) // Bloquear no migra.
        s.unlockWithKey(fixtureDek())
        s.confirmMaster(master.toCharArray())
        assertFalse(s.needsMasterForContacts.value)
        s.lock()
        assertFalse(s.needsMasterForContacts.value)
        // unlock con la maestra migra.
        val (_, viaUnlock) = phone11("p11b")
        assertTrue(viaUnlock.needsMasterForContacts.value)
        viaUnlock.unlock(master.toCharArray())
        assertFalse(viaUnlock.needsMasterForContacts.value)
        // Importar (siempre con la maestra) la deja en la 3.
        val (_, viaImport) = phone11("p11c")
        viaImport.unlockWithKey(fixtureDek())
        assertTrue(viaImport.needsMasterForContacts.value)
        viaImport.importReplace(ByteArrayInputStream(fixtureBytes("backup-1.1.sgbak")), master.toCharArray())
        assertFalse(viaImport.needsMasterForContacts.value)
        // Cambiar la maestra desde la huella también.
        val (_, viaChange) = phone11("p11d")
        viaChange.unlockWithKey(fixtureDek())
        assertTrue(viaChange.needsMasterForContacts.value)
        assertTrue(viaChange.changeMaster("nueva".toCharArray()))
        assertFalse(viaChange.needsMasterForContacts.value)
        // Borrar la bóveda.
        viaChange.destroy()
        assertFalse(viaChange.needsMasterForContacts.value)
        assertEquals(3, diskFile(dir).version)
    }

    @Test fun unaBovedaNuevaEsLaVersion3YNoPideLaMaestraParaLosContactos() = runTest {
        val dir = phone("nueva")
        val s = store(dir).apply { load(); create("otra".toCharArray()) }
        assertEquals(3, diskFile(dir).version)
        assertFalse(s.needsMasterForContacts.value)
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
        assertFalse(again.needsMasterForContacts.value)
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

    @Test fun renovarLaIdentidadLaSustituyeYNoCuentaComoUnaSegunda() = runTest {
        val dir = phone("renovar")
        val s = store(dir).apply { load(); create("otra".toCharArray()) }
        val first = identityEntry().copy(username = "Yo")
        s.upsert(first)
        val before = s.entries().identity()!!.openIdentity().use { it.edPublic to it.kemPublic }
        clock += 1_000
        // No lo rechaza ONLY_ONE_IDENTITY: es el mismo id.
        s.upsert(renewedIdentityEntry(s.entries().identity()!!, clock))
        assertEquals(1, s.entries().count { it.type == EntryType.IDENTITY })
        val renewed = s.entries().identity()!!
        assertEquals(first.id, renewed.id)
        assertEquals(first.createdAt, renewed.createdAt)
        assertEquals(clock, renewed.updatedAt)
        assertEquals("Yo", renewed.username)
        val after = renewed.openIdentity().use { it.edPublic to it.kemPublic }
        assertFalse(before.first.contentEquals(after.first))
        assertFalse(before.second.contentEquals(after.second))
        // Y queda guardada en disco: al cerrar y abrir sigue siendo la nueva, y sigue siendo una sola.
        s.lock()
        s.unlock("otra".toCharArray())
        assertEquals(1, s.entries().count { it.type == EntryType.IDENTITY })
        assertArrayEquals(after.first, s.entries().identity()!!.openIdentity().use { it.edPublic })
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
        assertTrue(b.needsMasterForContacts.value)
        b.importReplace(ByteArrayInputStream(zip), "maestra-a".toCharArray())
        assertEquals(3, diskFile(dirB).version)
        assertFalse(b.needsMasterForContacts.value)
        assertEquals(identity.id, b.entries().identity()!!.id)
        assertTrue(b.entries().any { it.id == contact.id })
        assertEquals(contact.contactCard(), b.entries().first { it.id == contact.id }.contactCard())
    }

    // ---- Una copia con dos identidades no se importa ----

    /** Un archivo de bóveda de la versión 3 con [entries] dentro, como lo escribiría un teléfono con dos identidades. */
    private fun vaultJsonWith(entries: List<VaultEntry>, masterOfCopy: String): String {
        val (file, dek) = VaultCrypto.create(masterOfCopy.toCharArray(), clock, KdfParams(1024, 1, 1))
        return VaultCrypto.encodeFile(VaultCrypto.write(file, dek, entries))
    }

    private fun snapshot(dir: File): Map<String, String> =
        dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path to java.util.Base64.getEncoder().encodeToString(it.readBytes()) }

    @Test fun unaCopiaConDosIdentidadesSeRechazaSinTocarNada() = runTest {
        val twoIdentities = vaultJsonWith(listOf(identityEntry(), identityEntry(), contactEntry("Ana")), "maestra-copia")
        val zip = ByteArrayOutputStream().also { writeBackup(it, twoIdentities, emptyMap()) }.toByteArray()
        val (dir, s) = phone11()
        s.unlock(master.toCharArray())
        val before = snapshot(dir)
        val entriesBefore = s.entries()
        // Como copia .sgbak, como exportación JSON y con la maestra buena de la copia: se rechaza antes de tocar nada.
        val fromZip = failsWith<CryptoFailure> { s.importReplace(ByteArrayInputStream(zip), "maestra-copia".toCharArray()) }
        assertEquals("La copia tiene más de una identidad: no se puede importar", fromZip.message)
        val fromJson = failsWith<CryptoFailure> { s.importReplace(twoIdentities, "maestra-copia".toCharArray()) }
        assertEquals(fromZip.message, fromJson.message)
        assertEquals(before, snapshot(dir)) // vault.bin y adjuntos, byte a byte.
        assertEquals(entriesBefore, s.entries())
        assertEquals("hola", s.openAttachment(entriesBefore[0].id, entriesBefore[0].attachments.single().id)!!.decodeToString())
        // Con una sola identidad sí se importa.
        val one = vaultJsonWith(listOf(identityEntry(), contactEntry("Ana")), "maestra-copia")
        s.importReplace(one, "maestra-copia".toCharArray())
        assertEquals(2, s.entries().size)
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

    // ---- Manipulaciones de la versión (los tests de VaultTamperTest no se tocan) ----

    private fun v2Fixture() = VaultCrypto.decodeFile(fixtureBytes("vault-1.1.json").decodeToString())

    /** La bóveda real ya migrada a la 3 con la maestra, y su clave de datos. */
    private fun v3Fixture(): VaultFile =
        VaultCrypto.afterMasterUnlock(v2Fixture(), fixtureDek(), master.toCharArray(), clock, KdfParams(1024, 1, 1))

    @Test fun unArchivoDeLaVersion2SinElCampoVersionSigueAbriendose() = runTest {
        val stripped = VaultCrypto.decodeFile(fixtureBytes("vault-1.1.json").decodeToString().replace("\"version\":2,", ""))
        assertEquals(VaultFile.VERSION_UNKNOWN, stripped.version)
        // Se prueba la 3 y se cae a la 2; la versión real es la 2.
        val dek = VaultCrypto.unlock(stripped, master.toCharArray())
        assertArrayEquals(fixtureDek(), dek)
        assertEquals(2, VaultCrypto.resolve(stripped, dek).version)
        assertEquals(fixtureEntries().map { it.title }.toSet(), VaultCrypto.read(stripped, dek).map { it.title }.toSet())
        // Con la maestra se migra a la 3.
        assertEquals(3, VaultCrypto.afterMasterUnlock(stripped, dek, master.toCharArray(), clock, KdfParams(1024, 1, 1)).version)
        // Y con la huella (en la tienda) queda en la 2: nada de identidad hasta la maestra.
        val dir = phone("sinversion")
        File(dir, "vault.bin").writeText(VaultCrypto.encodeFile(stripped))
        val before = File(dir, "vault.bin").readText()
        val s = store(dir).apply { load() }
        s.unlockWithKey(fixtureDek())
        assertTrue(s.state.value is VaultState.Unlocked)
        assertTrue(s.needsMasterForContacts.value)
        // Abrir con la huella no reescribe el archivo: sigue sin el campo version.
        assertEquals(before, File(dir, "vault.bin").readText())
        assertFalse(File(dir, "vault.bin").readText().contains("\"version\""))
    }

    @Test fun deLaVersion3NoSePasaALa2PorLaHuella() = runTest {
        val v3 = v3Fixture()
        val dek = fixtureDek()
        // Una v3 sin el campo version se resuelve como 3, nunca como 2.
        assertEquals(3, VaultCrypto.resolve(v3.copy(version = VaultFile.VERSION_UNKNOWN), dek).version)
        // Diciendo que es la 2, ni la clave de datos abre las entradas.
        failsWith<CryptoFailure> { VaultCrypto.read(v3.copy(version = 2), dek) }
        // Por el camino de la tienda: el archivo en disco con la versión cambiada no se abre y no se toca.
        val dir = phone("degradada")
        File(dir, "vault.bin").writeText(VaultCrypto.encodeFile(v3.copy(version = 2)))
        val before = File(dir, "vault.bin").readText()
        val s = store(dir).apply { load() }
        failsWith<CryptoFailure> { s.unlockWithKey(dek) }
        assertFalse(s.state.value is VaultState.Unlocked)
        assertEquals(before, File(dir, "vault.bin").readText())
        assertNull(s.dataKeyCopy())
    }

    @Test fun unaClaveEnvueltaDeLaVersion2JuntoAUnosDatosDeLaVersion3SeRechaza() = runTest {
        val v2 = v2Fixture()
        val v3 = v3Fixture()
        val dek = fixtureDek()
        // Sal, coste y clave envuelta de la 2, con los datos de la 3, diciendo 3, 2 o nada.
        val hybrids = listOf(3, 2, VaultFile.VERSION_UNKNOWN).map { v ->
            v3.copy(version = v, kdf = v2.kdf, salt = v2.salt, wrappedKey = v2.wrappedKey)
        }
        // Y la clave envuelta de la 2 sola, con la sal y el coste de la 3: no abre con la maestra (la clave de datos
        // no va ligada a esa clave envuelta, así que con ella las entradas de la 3 siguen siendo las suyas).
        val onlyKey = listOf(3, 2).map { v -> v3.copy(version = v, wrappedKey = v2.wrappedKey) }
        // Lo que dice la nota: con la clave de datos, la 3 con la clave envuelta de la 2 sigue dando sus propias entradas.
        onlyKey.filter { it.version == 3 }.forEach { h ->
            assertEquals(VaultCrypto.read(v3, dek).map { it.title }, VaultCrypto.read(h, dek).map { it.title })
        }
        (hybrids + onlyKey).forEach { h ->
            // Con la maestra, o no abre la clave (AAD con otra versión o sal) o no abre las entradas.
            failsWith<CryptoFailure> {
                val key = VaultCrypto.unlock(h, master.toCharArray())
                VaultCrypto.afterMasterUnlock(h, key, master.toCharArray(), clock, KdfParams(1024, 1, 1))
            }
            val dir = phone("hibrido-${h.version}-${h.salt.take(6).filter { it.isLetterOrDigit() }}-${h.hashCode()}")
            File(dir, "vault.bin").writeText(VaultCrypto.encodeFile(h))
            val before = File(dir, "vault.bin").readText()
            val s = store(dir).apply { load() }
            failsWith<CryptoFailure> { s.unlock(master.toCharArray()) }
            assertFalse(s.state.value is VaultState.Unlocked)
            assertEquals(before, File(dir, "vault.bin").readText())
        }
        hybrids.forEach { h ->
            // Con la clave de datos (la huella), tampoco se leen las entradas de la 3 con la sal y el coste de la 2.
            failsWith<CryptoFailure> { VaultCrypto.read(h, dek) }
            val dir = phone("huella-${h.version}")
            File(dir, "vault.bin").writeText(VaultCrypto.encodeFile(h))
            val before = File(dir, "vault.bin").readText()
            val s = store(dir).apply { load() }
            failsWith<CryptoFailure> { s.unlockWithKey(dek.copyOf()) }
            assertFalse(s.state.value is VaultState.Unlocked)
            assertEquals(before, File(dir, "vault.bin").readText())
        }
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

    // A11 (1.3.0): the pure gate. A clock set back more than 5 minutes behind the highest time seen asks for the master.
    @Test fun clockSetBackBlocksBiometrics() {
        val now = 1_000_000_000L
        val (file, dek) = VaultCrypto.create("m".toCharArray(), now, KdfParams(1024, 1, 1))
        dek.fill(0)
        assertFalse(VaultCrypto.biometricAllowed(file, now, highWater = now + 6 * 60_000))
        assertTrue(VaultCrypto.biometricAllowed(file, now, highWater = now + 4 * 60_000))
        assertTrue(VaultCrypto.biometricAllowed(file, now, highWater = now + CLOCK_SLACK_MS))
        assertTrue(VaultCrypto.biometricAllowed(file, now)) // No mark yet.
    }

    // A8 (1.3.0): afterMasterUnlock re-wraps a v3 file whose Argon2id is cheaper than asked, keeping the data key.
    @Test fun afterMasterUnlockRewrapsOnlyAWeakerKdf() {
        val master = "m".toCharArray()
        val (weak, dek) = VaultCrypto.create(master, 1, KdfParams(1024, 1, 1))
        val asked = KdfParams(2048, 2, 1)
        val up = VaultCrypto.afterMasterUnlock(weak, dek.copyOf(), master, 2, asked)
        assertEquals(VaultKdf(2048, 2, 1), up.kdf)
        assertEquals(weak.revision + 1, up.revision)
        assertEquals(weak.version, up.version)
        assertArrayEquals(dek, VaultCrypto.unlock(up, master)) // Same data key.
        val same = VaultCrypto.afterMasterUnlock(up, dek.copyOf(), master, 3, KdfParams(1024, 1, 1))
        assertEquals(up.kdf, same.kdf) // Cheaper ask: never lowered.
        assertEquals(up.salt, same.salt)
        // One axis weaker is enough, and the stronger axis is not lowered.
        val mixed = VaultCrypto.afterMasterUnlock(up, dek.copyOf(), master, 4, KdfParams(1024, 3, 1))
        assertEquals(VaultKdf(2048, 3, 1), mixed.kdf)
    }

    @Test fun biometricRefusalSaysWhyTheClockOrTheSevenDays() {
        val now = 1_000_000_000L
        val (file, dek) = VaultCrypto.create("m".toCharArray(), now, KdfParams(1024, 1, 1))
        dek.fill(0)
        assertNull(VaultCrypto.biometricRefusal(file, now, now))
        assertEquals(BiometricRefusal.CLOCK, VaultCrypto.biometricRefusal(file, now, now + 6 * 60_000))
        assertEquals(BiometricRefusal.CLOCK, VaultCrypto.biometricRefusal(file, now - 1)) // Before the last check.
        assertEquals(BiometricRefusal.MASTER_DUE, VaultCrypto.biometricRefusal(file, now + MASTER_RECHECK_MS))
        assertEquals(BiometricRefusal.MASTER_DUE, VaultCrypto.biometricRefusal(file.copy(version = 1), now))
    }

    // Fix round 1: converting an old file never lowers its cost either (real 1.1 file: 1024 KiB, 1 pass).
    @Test fun conversionOfAStrongerOldFileKeepsItsCost() {
        val v2 = v2Fixture()
        val up = VaultCrypto.afterMasterUnlock(v2, fixtureDek(), master.toCharArray(), clock, KdfParams(512, 3, 1))
        assertEquals(VaultKdf(1024, 3, 1), up.kdf)
        assertEquals(3, up.version)
        val keep = VaultCrypto.afterMasterUnlock(v2, fixtureDek(), master.toCharArray(), clock, KdfParams(256, 1, 1))
        assertEquals(v2.kdf, keep.kdf)
        assertArrayEquals(fixtureDek(), VaultCrypto.unlock(up, master.toCharArray()))
    }
}
