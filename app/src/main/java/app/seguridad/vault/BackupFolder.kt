package app.seguridad.vault

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** A persisted URI permission the app holds (ContentResolver.persistedUriPermissions), as plain data. */
data class FolderGrant(val uri: String, val read: Boolean, val write: Boolean)

/**
 * Whether [folder], the automatic copies' folder from the settings, is one the user chose with the system picker: a
 * `content:` tree URI the app holds a persisted read and write permission for. The settings file is not authenticated,
 * so whatever wrote a folder into it (CADENAS-1) never gets the copies: without that permission none is made, and
 * Ajustes asks to choose the folder again.
 *
 * Compared as [treeKey] (authority and decoded tree document id), not as raw strings: the same folder may be written
 * `primary%3ACopias` or `primary%3aCopias`, or carry a `<user>@` prefix in its authority, and a folder chosen with 1.2.2
 * must keep working after the update without choosing it again.
 */
fun hasFolderGrant(folder: String, grants: List<FolderGrant>): Boolean = folderGrant(folder, grants) != null

/**
 * 2.1.2, R-19: the grant [hasFolderGrant] finds for [folder] (the first, in the order Android lists them); null with none.
 * The folder is then used through that grant's own URI, the one the permission was given for, whatever spelling of it the
 * settings hold.
 */
fun folderGrant(folder: String, grants: List<FolderGrant>): FolderGrant? {
    val key = treeKey(folder) ?: return null
    return grants.firstOrNull { it.read && it.write && treeKey(it.uri) == key }
}

/**
 * 2.1.2, R-19: whether [a] and [b] are the same folder: the same authority and tree document id ([treeKey]), however each
 * is written (`%3A` or `%3a`, a `<user>@` prefix), as [hasFolderGrant] compares them. Anything that is
 * not a tree URI only matches itself.
 */
fun sameFolder(a: String, b: String): Boolean {
    if (a == b) return true
    val key = treeKey(a) ?: return false
    return treeKey(b) == key
}

// Case-sensitive, like Android: ContentResolver only resolves the scheme `content`, and DocumentsContract.isTreeUri and
// getTreeDocumentId only take the path segment `tree`.
private val TREE = Regex("content://(?:\\d+@)?([^/?#@]+)/tree/([^/?#]+)(?:/[^?#]*)?")

/**
 * The authority (without a leading `<user>@`) and the decoded tree document id of a SAF tree URI: the id is what
 * DocumentsContract.getTreeDocumentId gives for the same URI. Null for anything Android would not take as a tree URI
 * (another scheme, `/TREE/`, no tree segment), and for an id that is not valid percent-encoded UTF-8 with ASCII hex
 * digits, as Android's own decoder requires.
 */
fun treeKey(uri: String): Pair<String, String>? {
    val m = TREE.matchEntire(uri) ?: return null
    val id = percentDecoded(m.groupValues[2]) ?: return null
    return m.groupValues[1] to id
}

// The authority as [TREE] reads it (an optional numeric `<user>@` prefix, then no `/`, `?`, `#` or `@`), then a path.
private val CONTENT_DOC = Regex("content://(?:\\d+@)?([^/?#@]+)/.*", RegexOption.DOT_MATCHES_ALL)

/**
 * 2.1.2, V-1: whether [doc], the URI a provider gave back from createDocument or renameDocument for the copies' folder
 * [tree], may be written, renamed or deleted: only with the exact scheme `content` and the tree's own authority,
 * compared as [treeKey] (a numeric `<user>@` prefix does not count). The provider is not trusted to return a document of
 * its own: a `file:` URI would reach Clavero's own files, and another authority another folder the app holds a grant
 * for. The document need not hang from the tree (some providers return `content://<authority>/document/<id>`), and
 * DocumentsContract.isChildDocument is not asked, because the provider itself answers it. Anything that is not such a
 * URI (and a [tree] that is not a tree) is false.
 */
fun documentOfTree(tree: String, doc: String): Boolean {
    val authority = treeKey(tree)?.first ?: return false
    val m = CONTENT_DOC.matchEntire(doc) ?: return false
    return m.groupValues[1] == authority
}

/** [s] with its `%XX` escapes decoded as UTF-8 (a `+` stays a `+`, as in a URI path); null if malformed. */
private fun percentDecoded(s: String): String? {
    val out = ByteArrayOutputStream()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%') {
            if (i + 2 >= s.length) return null
            val hi = asciiHex(s[i + 1])
            val lo = asciiHex(s[i + 2])
            if (hi < 0 || lo < 0) return null
            out.write(hi * 16 + lo)
            i += 3
        } else {
            // A whole run up to the next escape, so a character outside the BMP (a surrogate pair) stays one character.
            val end = s.indexOf('%', i).let { if (it < 0) s.length else it }
            out.write(s.substring(i, end).toByteArray(Charsets.UTF_8))
            i = end
        }
    }
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(out.toByteArray()))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }
}

/** The value of an ASCII hex digit; -1 for anything else (Character.digit would also take fullwidth or Arabic-Indic digits). */
private fun asciiHex(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}
