package app.seguridad.core

import java.util.concurrent.ConcurrentHashMap

/**
 * 1.4.2, the owner's request: each file picker of the app, the system's, opens where that picker was used last, not
 * where any picker left it. [kept] says whether the place is written down and stays after the app closes. Only the
 * vault's copies (export, import, the one before importing, «Comprobar una copia»), the outputs of Limpiar foto and
 * Tachar (fixed names) and a saved encrypted text are kept. Every other place names a file that may be plaintext or
 * says what it held (a file to encrypt, sign or hash, an encrypted or signed output named after its original, what is
 * opened in «Abrir lo recibido», a decrypted file, a text file, a photo, a QR image, an unencrypted export, an
 * attachment): it lives only in memory, only while the vault is open, and goes at every lock and unlock
 * ([PickerPlaces.forgetSession]). A document URI holds the file name, and none of those names stays outside the vault
 * in plain text, nor for whoever takes the phone after a lock (code review and final audit of 1.4.2). The folder of the
 * automatic copies has no place here: its picker opens on the folder chosen (Settings).
 */
enum class PickerPlace(val kept: Boolean = true) {
    EXPORT_COPY,
    IMPORT_COPY,
    IMPORT_OTHER(kept = false),
    IMPORT_COPY_BEFORE,
    BACKUP_CHECK,
    HASH_FILE(kept = false),
    QR_IMAGE(kept = false),
    PHOTO_OPEN(kept = false),
    PHOTO_SAVE,
    REDACT_OPEN(kept = false),
    REDACT_SAVE,
    TEXT_OPEN(kept = false),
    TEXT_SAVE,
    FILE_OPEN(kept = false),
    FILE_ENCRYPTED_SAVE(kept = false),
    FILE_DECRYPTED_SAVE(kept = false),
    ENCRYPT_FOR_OPEN(kept = false),
    ENCRYPT_FOR_SAVE(kept = false),
    SIGN_OPEN(kept = false),
    SIGN_SAVE(kept = false),
    RECEIVED_OPEN(kept = false),
    RECEIVED_SAVE(kept = false),
    ATTACHMENT_ADD(kept = false),
    ATTACHMENT_SAVE(kept = false),
}

/** Where the kept places are written (the app's private preferences, never in a backup: backup_rules.xml). */
interface PlaceStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun clear()
}

/** A [PlaceStore] in memory: the JVM tests, and nothing kept after the process. */
class MemoryPlaceStore : PlaceStore {
    private val map = ConcurrentHashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) {
        map[key] = value
    }
    override fun clear() = map.clear()
}

/** The longest place kept: a document URI is a few hundred characters. */
const val PLACE_MAX_CHARS = 2048

/**
 * The last document each [PickerPlace] gave back, handed to its picker as where to start (`EXTRA_INITIAL_URI`: the
 * provider decides whether it follows it). Only another app's `content://` document is kept ([isAcceptableDocumentUri]),
 * checked again when read; a failing [kept] store is no place. Never throws.
 */
class PickerPlaces(
    private val kept: PlaceStore,
    private val packageName: String = "io.github.egt17baa.seguridad",
    /** Whether the vault is open: the places not [PickerPlace.kept] are neither remembered nor offered otherwise. */
    private val sessionOpen: () -> Boolean = { true },
) {
    private val session = ConcurrentHashMap<PickerPlace, String>()

    fun initial(place: PickerPlace): String? {
        val value = if (place.kept) runCatching { kept.get(place.name) }.getOrNull() else session[place]?.takeIf { isOpen() }
        return value?.takeIf(::acceptable)
    }

    /** A result that comes back after a lock (the picker took the app away) is not remembered by a private place. */
    fun remember(place: PickerPlace, uri: String) {
        if (!acceptable(uri)) return
        if (place.kept) runCatching { kept.put(place.name, uri) } else if (isOpen()) session[place] = uri
    }

    private fun isOpen() = runCatching(sessionOpen).getOrDefault(false)

    /**
     * At every lock and unlock of the vault (AppContainer): the places kept only in memory. A document picked while the
     * vault was locked, which the screen ignores, is forgotten when it opens.
     */
    fun forgetSession() = session.clear()

    /** With the vault: every place, kept or not. */
    fun forgetAll() {
        session.clear()
        runCatching { kept.clear() }
    }

    private fun acceptable(uri: String): Boolean {
        if (uri.length > PLACE_MAX_CHARS) return false
        val scheme = uri.substringBefore(':', "")
        val authority = uri.removePrefix("$scheme://").takeIf { uri.startsWith("$scheme://") }?.substringBefore('/')
        return isAcceptableDocumentUri(scheme, authority, packageName)
    }
}
