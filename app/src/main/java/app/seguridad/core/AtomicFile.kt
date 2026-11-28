package app.seguridad.core

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replaces this file with [text] all at once: written to a temporary file, flushed to the storage itself (fsync),
 * then renamed over the old one. A crash or power cut leaves either the old file or the new one, never an empty one.
 */
fun File.writeTextAtomically(text: String) = writeBytesAtomically(text.toByteArray(Charsets.UTF_8))

/**
 * [writeTextAtomically] for bytes; the temporary file is this one's path plus ".tmp". 2.1.2, R-18: if the write or the
 * move fails, the temporary file (the new data, maybe a secret's) is deleted and the error is thrown again; a folder in its
 * place, which this write never made, is left alone.
 */
fun File.writeBytesAtomically(bytes: ByteArray) {
    parentFile?.mkdirs()
    val tmp = File("$path.tmp")
    try {
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        Files.move(tmp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: Throwable) {
        try {
            if (tmp.isFile) tmp.delete()
        } catch (ignored: Exception) {
            // The first error is the one that counts.
        }
        throw e
    }
}
