package app.seguridad.core

import java.util.concurrent.ConcurrentHashMap

/**
 * 1.4.2, the owner's request: each file picker of the app, the system's, opens where that picker was used last, not
 * where any picker left it. [kept] says whether the place stays after the app closes. A place is kept only when its
 * document is a Clavero copy, an encrypted file or a cleaned output, whose name says nothing that is not already on
 * disk. The others are kept only in memory, and forgotten at every lock and unlock of the vault
 * ([PickerPlaces.forgetSession]): what a tool reads that may be plaintext (a file to encrypt, sign, hash or encrypt for a
 * contact, a text file to open, a photo to clean or redact, a QR image, an unencrypted export) and what Clavero writes out of the vault or of a decryption (an
 * attachment, a decrypted file). Their document URI holds the file name, and names that were only inside the vault
 * must not stay outside it in plain text (code review of 1.4.2). The folder of the automatic copies has no place here:
 * its picker opens on the folder chosen (Settings).
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
    FILE_ENCRYPTED_SAVE,
    FILE_DECRYPTED_SAVE(kept = false),
    ENCRYPT_FOR_OPEN(kept = false),
    ENCRYPT_FOR_SAVE,
    SIGN_OPEN(kept = false),
    SIGN_SAVE,
    RECEIVED_OPEN,
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
class PickerPlaces(private val kept: PlaceStore, private val packageName: String = "io.github.egt17baa.seguridad") {
    private val session = ConcurrentHashMap<PickerPlace, String>()

    fun initial(place: PickerPlace): String? {
        val value = if (place.kept) runCatching { kept.get(place.name) }.getOrNull() else session[place]
        return value?.takeIf(::acceptable)
    }

    fun remember(place: PickerPlace, uri: String) {
        if (!acceptable(uri)) return
        if (place.kept) runCatching { kept.put(place.name, uri) } else session[place] = uri
    }

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
