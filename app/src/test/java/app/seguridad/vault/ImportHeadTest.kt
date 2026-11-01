package app.seguridad.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Prueba del dueño (1-11-2026): «Importar copia» pedía la contraseña maestra para cualquier archivo (un .txt) y solo
 * después decía que no era una bóveda. [mayBeVaultImport] mira la cabecera antes de pedir la contraseña; nunca rechaza
 * lo que la importación acepta (las copias .sgbak y los JSON de Seguridad). Los formatos actuales
 * (seguridad-vault v3 y seguridad-backup v2) se comprueban en BackupTest.formatPinned.
 */
class ImportHeadTest {
    private fun head(bytes: ByteArray) = bytes.copyOf(minOf(bytes.size, BACKUP_HEAD_BYTES))
    private fun resource(path: String) = File(javaClass.classLoader!!.getResource(path)!!.toURI()).readBytes()

    @Test fun acceptsEverythingTheImportReads() {
        for (path in listOf(
            "seguridad/backup-1.1.sgbak",
            "seguridad/export-1.0.2.json",
            "seguridad/vault-1.1.json",
        )) assertTrue(path, mayBeVaultImport(head(resource(path))))
        // A JSON saved by another editor with leading blank lines or spaces: the parser skips any amount of them, so a
        // head of blanks only (the `{` beyond the 64 bytes) is let through for the import to decide.
        assertTrue(mayBeVaultImport(head(" \r\n\t{\"format\":1}".toByteArray())))
        assertTrue(mayBeVaultImport(head(" ".repeat(63).toByteArray() + "{}".toByteArray())))
        assertTrue(mayBeVaultImport(head(" ".repeat(64).toByteArray() + "{}".toByteArray())))
        assertTrue(mayBeVaultImport(head(" ".repeat(200).toByteArray() + "{}".toByteArray())))
    }

    @Test fun refusesWhatCannotBeAVault() {
        assertFalse(mayBeVaultImport(ByteArray(0)))
        assertFalse(mayBeVaultImport("   \n".toByteArray()))
        // A byte-order mark: the strict JSON parser refuses it, so the import would only fail after the password.
        assertFalse(mayBeVaultImport(head(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "{}".toByteArray())))
        assertFalse(mayBeVaultImport(head("Hola, esto es una nota de texto.".toByteArray())))
        assertFalse(mayBeVaultImport(head(resource("seguridad/README.txt"))))
        assertFalse(mayBeVaultImport(head("[1,2,3]".toByteArray())))
        // A PNG and another ZIP.
        assertFalse(mayBeVaultImport(head(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(40))))
        val other = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("documento.xml")); it.write("<a/>".toByteArray()); it.closeEntry() }
        }.toByteArray()
        assertFalse(mayBeVaultImport(head(other)))
    }
}
