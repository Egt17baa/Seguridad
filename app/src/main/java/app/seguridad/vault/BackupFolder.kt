package app.seguridad.vault

/** A persisted URI permission the app holds (ContentResolver.persistedUriPermissions), as plain data. */
data class FolderGrant(val uri: String, val read: Boolean, val write: Boolean)

/**
 * Whether [folder], the automatic copies' folder from the settings, is one the user chose with the system picker: a
 * `content:` URI the app holds a persisted read and write permission for. The settings file is not authenticated, so
 * whatever wrote a folder into it (CADENAS-1) never gets the copies: without that permission none is made, and Ajustes
 * asks to choose the folder again.
 */
fun hasFolderGrant(folder: String, grants: List<FolderGrant>): Boolean =
    folder.startsWith("content://") && grants.any { it.uri == folder && it.read && it.write }
