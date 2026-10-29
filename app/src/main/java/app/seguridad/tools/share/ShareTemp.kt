package app.seguridad.tools.share

import java.io.File
import java.io.IOException

/** The only folder the FileProvider serves (res/xml/share_paths.xml), inside the app's private cache. */
const val SHARE_DIR = "compartir"

/** Back in Seguridad this long after sharing, the copy is deleted (the receiving app has had time to read it). */
const val SHARE_KEEP_MS = 10 * 60_000L

/** Base name of the shared photo copy («copia-de-foto.jpg|png|webp»): it says nothing about the person nor the date. */
const val PHOTO_FILE_BASE = "copia-de-foto"

/** The fixed, neutral names the shared copies get: nothing in them tells the date or who is in the photo. */
private val SHARE_NAMES = Regex("${PHOTO_FILE_BASE}\\.(jpg|png|webp)|imagen-tachada\\.(jpg|png)")

/**
 * The copy handed to another app through «Compartir». Never wiped on lock: leaving for WhatsApp or Gmail locks the
 * vault, and the receiver may read the content:// later (Gmail, on sending). Instead:
 * - there is never more than one file: [prepare] deletes everything else first;
 * - [onReturn] deletes it once [SHARE_KEEP_MS] or more have passed since it was prepared;
 * - [wipeAll] runs at each process start (the time of the last [prepare] lives only in memory).
 *
 * Synchronized: the screen prepares off the main thread and the return check may run on another one.
 */
class ShareTemp(cacheDir: File, private val now: () -> Long) {
    private val dir = File(cacheDir, SHARE_DIR)
    private var preparedAt: Long? = null

    /** The File object the last [prepare] returned; [discard] acts only on that same object. */
    private var preparedFile: File? = null

    /**
     * Writes [bytes] as `compartir/<name>`, the only file left there, and returns it. [name] must be one of the fixed
     * names (`copia-de-foto.jpg|png|webp`, `imagen-tachada.jpg|png`); anything else throws IllegalArgumentException.
     * On a write error nothing is left behind and the IOException goes up.
     */
    @Synchronized
    fun prepare(name: String, bytes: ByteArray): File {
        require(SHARE_NAMES.matches(name)) { "Nombre de archivo no admitido" }
        clear()
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("No se ha podido preparar la carpeta")
        val file = File(dir, name)
        try {
            file.writeBytes(bytes)
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        preparedAt = now()
        preparedFile = file
        return file
    }

    /**
     * Back in the app: deletes the copy if [SHARE_KEEP_MS] or more have passed, or if the clock went backwards. With
     * nothing prepared in this process, anything still there is stale and goes too (cheap: one directory check).
     */
    @Synchronized
    fun onReturn() {
        val at = preparedAt
        if (at != null) {
            val elapsed = now() - at
            if (elapsed in 0 until SHARE_KEEP_MS) return
        }
        wipeQuietly()
    }

    /**
     * Deletes [file] if it is the very object the last [prepare] returned (the copy of a failed or cancelled share);
     * anything else is left alone. Compared by identity, not by path: the names are fixed, so a later prepare writes
     * the same path, and a late discard of the earlier attempt must not delete the newer copy nor forget its time.
     * Never throws. An earlier copy is not touched here: [prepare] already removed it.
     */
    @Synchronized
    fun discard(file: File) {
        if (file !== preparedFile) return
        file.delete()
        preparedFile = null
        preparedAt = null
    }

    /** Empties `compartir/`. Never throws: it runs at start, where a full or read-only cache must not close the app. */
    @Synchronized
    fun wipeAll() = wipeQuietly()

    private fun wipeQuietly() {
        try {
            clear()
        } catch (e: IOException) {
            // Whatever could not be deleted is retried at the next return or start.
        }
    }

    /** Deletes every entry of `compartir/`; throws if any is left, so [prepare] never leaves two files. */
    private fun clear() {
        preparedAt = null
        preparedFile = null
        val entries = dir.listFiles() ?: return
        for (entry in entries) entry.deleteRecursively()
        if (!dir.listFiles().isNullOrEmpty()) throw IOException("No se ha podido borrar la copia anterior")
    }
}
