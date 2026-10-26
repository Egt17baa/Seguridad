package app.seguridad.android

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import app.seguridad.vault.BackupTarget
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

    override suspend fun write(name: String, writer: suspend (OutputStream) -> Unit) {
        val doc = withContext(Dispatchers.IO) {
            saf {
                val folder = DocumentsContract.buildDocumentUriUsingTree(tree, folderId())
                // Plain bytes: no app treats the copy as a ZIP to open, and no provider adds an extension.
                DocumentsContract.createDocument(resolver, folder, "application/octet-stream", name)
            } ?: throw FileNotFoundException()
        }
        val out = withContext(Dispatchers.IO) { saf { resolver.openOutputStream(doc, "wt") } } ?: throw FileNotFoundException()
        out.use { writer(it) }
    }

    override fun list(prefix: String): List<String> = children().map { it.second }.filter { it.startsWith(prefix) }

    override fun delete(name: String) {
        val id = idOf(name) ?: return
        saf {
            if (!DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))) throw IOException()
        }
    }

    override fun rename(from: String, to: String) {
        val id = idOf(from) ?: throw FileNotFoundException()
        saf { DocumentsContract.renameDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id), to) } ?: throw IOException()
    }

    private fun idOf(name: String): String? = children().firstOrNull { it.second == name }?.first

    /** (document id, display name) of each file in the folder. */
    private fun children(): List<Pair<String, String>> = saf {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId())
        resolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    add(id to name)
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

        /** What to show for the folder: the provider's id of it (such as «primary:Copias»), never more. */
        fun label(tree: String): String = try {
            DocumentsContract.getTreeDocumentId(tree.toUri())
        } catch (e: Exception) {
            "carpeta elegida"
        }
    }
}
