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
fun hasFolderGrant(folder: String, grants: List<FolderGrant>): Boolean {
    val key = treeKey(folder) ?: return false
    return grants.any { it.read && it.write && treeKey(it.uri) == key }
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
