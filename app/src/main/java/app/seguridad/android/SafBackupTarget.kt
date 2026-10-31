package app.seguridad.android

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import app.seguridad.vault.BackupEntry
import app.seguridad.vault.BackupFile
import app.seguridad.vault.BackupTarget
import app.seguridad.vault.FolderGrant
import app.seguridad.vault.hasFolderGrant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * The folder the user chose with the system picker (OpenDocumentTree), through [DocumentsContract] with the
 * persistable permission taken by [takeFolder]. A revoked permission throws SecurityException; a folder that was
 * deleted, FileNotFoundException (some providers only say IllegalArgumentException, also turned into it).
 */
class SafBackupTarget(private val resolver: ContentResolver, private val tree: Uri) : BackupTarget {
    private fun folderId(): String = DocumentsContract.getTreeDocumentId(tree)

    override suspend fun create(name: String): BackupFile {
        val doc = withContext(Dispatchers.IO) {
            saf {
                val folder = DocumentsContract.buildDocumentUriUsingTree(tree, folderId())
                // Plain bytes: no app treats the copy as a ZIP to open, and no provider adds an extension.
                DocumentsContract.createDocument(resolver, folder, "application/octet-stream", name)
            } ?: throw FileNotFoundException()
        }
        return Created(doc)
    }

    /** The document [create] made, by the URI the provider gave back: never another one with the same name. */
    private inner class Created(private var doc: Uri) : BackupFile {
        override val id: String get() = DocumentsContract.getDocumentId(doc)

        /** What the provider says of it (FLAG_SUPPORTS_RENAME); if it cannot be read, renaming is tried. */
        override val canRename: Boolean
            get() = try {
                resolver.query(doc, arrayOf(Document.COLUMN_FLAGS), null, null, null)?.use { c ->
                    !c.moveToFirst() || c.isNull(0) || (c.getInt(0) and Document.FLAG_SUPPORTS_RENAME) != 0
                } ?: true
            } catch (e: Exception) {
                true
            }

        override suspend fun write(writer: suspend (OutputStream) -> Unit) {
            val out = withContext(Dispatchers.IO) { saf { resolver.openOutputStream(doc, "wt") } } ?: throw FileNotFoundException()
            out.use { writer(it) }
        }

        /** Throws if the provider cannot (UnsupportedOperationException, or null): [AutoBackup] then writes the copy under its name. */
        override fun rename(to: String) {
            doc = saf { DocumentsContract.renameDocument(resolver, doc, to) } ?: throw IOException()
        }

        override fun delete() {
            saf { if (!DocumentsContract.deleteDocument(resolver, doc)) throw IOException() }
        }
    }

    override fun list(prefix: String): List<BackupEntry> = children().filter { it.name.startsWith(prefix) }

    /** Only a document in the folder ([children]): an id from the settings never reaches anything else. */
    override fun delete(id: String) {
        if (children().none { it.id == id }) return
        saf {
            if (!DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))) throw IOException()
        }
    }

    /** Each file in the folder, with its document id. */
    private fun children(): List<BackupEntry> = saf {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId())
        resolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    add(BackupEntry(id, name))
                }
            }
        } ?: throw FileNotFoundException() // The provider no longer knows the folder.
    }

    private inline fun <T> saf(block: () -> T): T = try {
        block()
    } catch (e: IllegalArgumentException) {
        throw FileNotFoundException()
    }

    companion object {
        private const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** Keeps access to [tree] across restarts; false if the provider refused it. */
        fun takeFolder(resolver: ContentResolver, tree: Uri): Boolean = try {
            resolver.takePersistableUriPermission(tree, FLAGS)
            true
        } catch (e: SecurityException) {
            false
        }

        /** Gives the access back (when the copies are turned off or another folder is chosen); never fails. */
        fun releaseFolder(resolver: ContentResolver, tree: String) {
            try {
                resolver.releasePersistableUriPermission(tree.toUri(), FLAGS)
            } catch (e: Exception) {
                // Already gone (revoked or the app's data cleared).
            }
        }

        /**
         * The folder of the settings to write the copies to, only if the app holds a persisted read and write permission
         * for it, which only the system picker gives ([takeFolder]). The settings file is not authenticated: a folder
         * put there by anything else (CADENAS-1) throws SecurityException, which AutoBackup reports as
         * AUTO_BACKUP_NO_PERMISSION («Vuelve a elegirla»), and no copy is made. Compared by authority and tree document
         * id ([hasFolderGrant]), so a folder saved by 1.2.2 with another spelling of the same URI still matches.
         */
        fun open(resolver: ContentResolver, tree: String): SafBackupTarget {
            val grants = resolver.persistedUriPermissions.map { FolderGrant(it.uri.toString(), it.isReadPermission, it.isWritePermission) }
            if (!hasFolderGrant(tree, grants)) throw SecurityException("No persisted permission for the backup folder")
            return SafBackupTarget(resolver, tree.toUri())
        }

        /** What to show for the folder: the provider's id of it (such as «primary:Copias»), never more. */
        fun label(tree: String): String = try {
            DocumentsContract.getTreeDocumentId(tree.toUri())
        } catch (e: Exception) {
            "carpeta elegida"
        }
    }
}
