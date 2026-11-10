package app.seguridad.core

import java.util.concurrent.ConcurrentHashMap

/**
 * 1.4.2, the owner's request: each file picker of the app, the system's, opens where that picker was used last, not
 * where any picker left it. [kept] says whether the place stays after the app closes: «Importar de otra app» is kept
 * only while the app runs, because the file there is usually an unencrypted export and where it was is not written
 * down. The folder of the automatic copies has no place here: its picker opens on the folder chosen (Settings).
 */
enum class PickerPlace(val kept: Boolean = true) {
    EXPORT_COPY,
    IMPORT_COPY,
    IMPORT_OTHER(kept = false),
    IMPORT_COPY_BEFORE,
    BACKUP_CHECK,
    HASH_FILE,
    QR_IMAGE,
    PHOTO_OPEN,
    PHOTO_SAVE,
    REDACT_OPEN,
    REDACT_SAVE,
    TEXT_OPEN,
    TEXT_SAVE,
    FILE_OPEN,
    FILE_ENCRYPTED_SAVE,
    FILE_DECRYPTED_SAVE,
    ENCRYPT_FOR_OPEN,
    ENCRYPT_FOR_SAVE,
    SIGN_OPEN,
    SIGN_SAVE,
    RECEIVED_OPEN,
    RECEIVED_SAVE,
    ATTACHMENT_ADD,
    ATTACHMENT_SAVE,
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
