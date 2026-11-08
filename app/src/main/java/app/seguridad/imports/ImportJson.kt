package app.seguridad.imports

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * What the JSON readers share (decisions (a) and (c) of the owner). A JSON file is data, never code:
 * - it is scanned once before parsing ([checkJsonShape]), so a deep or huge one is refused before any tree is built;
 * - it is parsed only with [Json.parseToJsonElement] into a tree, and every value is taken by a fixed name with the
 *   accessors below. Nothing is decoded into classes (no `decodeFromString`, no polymorphic decoding, no reflection),
 *   so nothing in the file chooses which code runs;
 * - a value of the wrong type throws [WrongType], and the reader lists that element as [SkipReason.BROKEN];
 * - a key written twice takes its last value (what the parser keeps): the readers see only that one.
 */

/** Deepest nesting of objects and arrays read; the root object is level 1. The formats read need 6 at most. */
const val JSON_MAX_DEPTH = 64

/*
 * The tree that [Json.parseToJsonElement] builds, estimated before parsing (decision (a) of the owner, rulings R11a
 * and R11c): memory is bounded by what the tree costs, not by how many values it has. The model follows how the tree
 * is made in kotlinx-serialization-json 1.11.0 (the app's version):
 * - an array is a `JsonArray` over an `ArrayList`: [JSON_TREE_ARRAY_BYTES], plus a backing array of references made
 *   at its first element with room for 10 and grown by half each time it is full (10, 15, 22, 33, 49…), its unused
 *   room included ([refArrayBytes]);
 * - an object is a `JsonObject` over a `LinkedHashMap`: [JSON_TREE_OBJECT_BYTES], plus a table of 16 slots made at
 *   its first member and doubled when it is more than ¾ full (13, 25, 49… members), and [JSON_TREE_MEMBER_BYTES] for
 *   each member (its map entry and its name's string);
 * - a string, a number, `true`, `false` or `null`: [JSON_TREE_PRIMITIVE_BYTES];
 * - every character inside a string, a member name or a number: [JSON_TREE_CHAR_BYTES] (an escape such as `Ж`
 *   counts its 6, more than the one it gives).
 * The sizes are those of a 64-bit JVM with compressed references (12-byte headers, 4-byte references, 8-byte
 * alignment), rounded up; ART has 8-byte headers and 4-byte references, so they are an upper bound there too. The
 * estimate is checked against the live heap measured after GC (JDK 21, SerialGC) for the shapes in the table of the
 * task 7 report, fix round 2, and in [ImportJsonTest]: it is never below the measured tree.
 */

/** A `JsonObject` and its empty `LinkedHashMap` (measured: `{}` 82 B with its slot in the array that holds it). */
const val JSON_TREE_OBJECT_BYTES = 88L

/** One member: its `LinkedHashMap` entry (40 B) and its name's `String` and array (48 B for a short name). */
const val JSON_TREE_MEMBER_BYTES = 88L

/** A `JsonArray` and its empty `ArrayList` (measured: `[]` 45 B with its slot). */
const val JSON_TREE_ARRAY_BYTES = 48L

/** A `JsonLiteral` and its content's `String` and array, besides the characters (measured: `1` 75 B with its slot). */
const val JSON_TREE_PRIMITIVE_BYTES = 80L

/** Each character of a string, a member name or a number (UTF-16: two bytes; measured 2.0 per character). */
const val JSON_TREE_CHAR_BYTES = 2L

/** The room an `ArrayList` makes at its first element. */
private const val ARRAY_FIRST_CAPACITY = 10

/** The table a `LinkedHashMap` makes at its first member. */
private const val OBJECT_FIRST_TABLE = 16

/** An array of [slots] references: a 16-byte header and 4 bytes a slot, aligned to 8 bytes. */
internal fun refArrayBytes(slots: Int): Long = (16L + 4L * slots + 7) / 8 * 8

/**
 * Largest estimated tree read: 48 MiB. It leaves room on a phone with a heap of 192 MB for the file's bytes, its text
 * and the app, and keeps real exports of thousands of items (a Bitwarden login is about 6 KB of tree). A file whose
 * tree would pass it is refused before parsing ([IMPORT_JSON_TOO_MANY_VALUES]).
 */
const val JSON_MAX_TREE_BYTES = 48L * 1024 * 1024

/**
 * Refuses [text] with [IMPORT_JSON_TOO_DEEP] past [JSON_MAX_DEPTH] levels and with [IMPORT_JSON_TOO_MANY_VALUES] when
 * its tree would pass [budget] ([jsonTreeEstimate]; [JSON_MAX_TREE_BYTES] but in tests), before it is parsed. Both
 * stop at the first character that passes the bound.
 */
internal fun checkJsonShape(text: String, budget: Long = JSON_MAX_TREE_BYTES) {
    if (scanJson(text, budget) > budget) throw ImportFailure(IMPORT_JSON_TOO_MANY_VALUES)
}

/**
 * The estimated size in bytes of the tree [text] would give (the model above), with no bound but the depth
 * ([IMPORT_JSON_TOO_DEEP]). For tests and for [checkJsonShape].
 */
internal fun jsonTreeEstimate(text: String): Long = scanJson(text, Long.MAX_VALUE)

/**
 * One linear pass that knows about strings: a bracket inside a string does not count, nor one after an escaped quote
 * (`\"`), and an escaped backslash (`\\`) does not escape the quote that follows it. It keeps, for each open level
 * (at most [JSON_MAX_DEPTH], in three small arrays made once), whether it is an object, how many elements or members
 * it has and the room it has made, and adds the cost of each new backing array or table when it is made (an array's
 * element is any value that starts in it; an object's member is a `:` in it). A string followed by `:` is a member's
 * name: the `:` turns its cost from [JSON_TREE_PRIMITIVE_BYTES] into [JSON_TREE_MEMBER_BYTES]. It stops as soon as the
 * estimate passes [limit] and returns it; it does not check that the JSON is valid (the parser does that afterwards).
 */
private fun scanJson(text: String, limit: Long): Long {
    val isObject = BooleanArray(JSON_MAX_DEPTH + 1)
    val count = IntArray(JSON_MAX_DEPTH + 1)
    val room = IntArray(JSON_MAX_DEPTH + 1)
    var depth = 0
    var bytes = 0L
    var inLiteral = false

    /** A value starts at the current level: an element, if that level is an array. */
    fun element() {
        if (depth == 0 || isObject[depth]) return
        if (++count[depth] <= room[depth]) return
        val old = room[depth]
        val grown = if (old == 0) ARRAY_FIRST_CAPACITY else old + (old shr 1)
        bytes += refArrayBytes(grown) - (if (old == 0) 0 else refArrayBytes(old))
        room[depth] = grown
    }

    /** A `:` at the current level: a member, if that level is an object. */
    fun member() {
        if (depth == 0 || !isObject[depth]) return
        bytes += JSON_TREE_MEMBER_BYTES - JSON_TREE_PRIMITIVE_BYTES
        val members = ++count[depth]
        val table = room[depth]
        if (table == 0) {
            bytes += refArrayBytes(OBJECT_FIRST_TABLE)
            room[depth] = OBJECT_FIRST_TABLE
        } else if (members > table / 4 * 3) {
            bytes += refArrayBytes(table * 2) - refArrayBytes(table)
            room[depth] = table * 2
        }
    }

    fun open(obj: Boolean) {
        element()
        bytes += if (obj) JSON_TREE_OBJECT_BYTES else JSON_TREE_ARRAY_BYTES
        if (++depth > JSON_MAX_DEPTH) throw ImportFailure(IMPORT_JSON_TOO_DEEP)
        isObject[depth] = obj
        count[depth] = 0
        room[depth] = 0
    }

    var i = 0
    val n = text.length
    while (i < n) {
        when (text[i]) {
            '"' -> {
                inLiteral = false
                element()
                val from = ++i
                while (i < n) {
                    val c = text[i]
                    if (c == '\\') i += 2 else if (c == '"') break else i++
                }
                bytes += JSON_TREE_PRIMITIVE_BYTES + JSON_TREE_CHAR_BYTES * (minOf(i, n) - from)
            }
            '{', '[' -> {
                inLiteral = false
                open(text[i] == '{')
            }
            '}', ']' -> {
                inLiteral = false
                if (depth > 0) depth--
            }
            ':' -> {
                inLiteral = false
                member()
            }
            ',', ' ', '\t', '\n', '\r' -> inLiteral = false
            else -> {
                if (!inLiteral) {
                    element()
                    bytes += JSON_TREE_PRIMITIVE_BYTES
                }
                inLiteral = true
                bytes += JSON_TREE_CHAR_BYTES
            }
        }
        if (bytes > limit) return bytes
        i++
    }
    return bytes
}

/**
 * The JSON formats (spec «Detección»): null when [text] is not a JSON object, or is one of no format Clavero reads
 * (then the next reader is asked, and in the end [IMPORT_UNKNOWN]). A text that starts with `{` is scanned first
 * ([checkJsonShape]); one the parser refuses is unknown too, and the parser's message (which quotes the file) is
 * never used. The format is told by its top-level names, in this order:
 * - `encrypted` or `items`: Bitwarden ([readBitwarden]);
 * - `db`: Aegis (an object in clear, a Base64 string when encrypted: [openAegis]);
 * - `services` or `servicesEncrypted`: 2FAS (`servicesEncrypted` a string when encrypted: [openTwoFas]).
 * An encrypted file without [password] has its header checked first ([bitwardenProtected], [aegisEncrypted],
 * [twoFasEncrypted]: the KDF's bounds and every part's length, nothing derived), so a file no password can open says
 * so at once ([IMPORT_KDF_TOO_BIG], [IMPORT_BROKEN], [IMPORT_ENCRYPTED_TOO_BIG]) instead of asking for one (branch
 * review A, M-3); then it is [ReadResult.NeedsPassword]. With [password], it is opened (Task 10), and what it holds goes
 * through the same [checkJsonShape] with [treeBudget].
 */
internal fun readJsonImport(text: String, treeBudget: Long = JSON_MAX_TREE_BYTES, password: CharArray? = null): ReadResult? {
    var start = 0
    while (start < text.length && text[start].isJsonSpace()) start++
    if (start >= text.length || text[start] != '{') return null
    checkJsonShape(text, treeBudget)
    val root = try {
        Json.parseToJsonElement(text)
    } catch (e: SerializationException) {
        return null
    } catch (e: IllegalArgumentException) {
        return null
    }
    if (root !is JsonObject) return null
    return when {
        "encrypted" in root || "items" in root -> readBitwarden(root, password, treeBudget)
        "db" in root -> when (val db = root["db"]) {
            is JsonObject -> ReadResult.Ok(readAegisPlain(db))
            is JsonPrimitive -> when {
                !db.isString -> throw ImportFailure(IMPORT_BROKEN)
                password == null -> aegisEncrypted(root).let { ReadResult.NeedsPassword(ImportFormat.AEGIS) }
                else -> ReadResult.Ok(openAegis(root, password, treeBudget))
            }
            else -> throw ImportFailure(IMPORT_BROKEN)
        }
        "services" in root || "servicesEncrypted" in root -> when (val enc = root["servicesEncrypted"]) {
            null, JsonNull -> ReadResult.Ok(readTwoFasPlain(root))
            is JsonPrimitive -> when {
                !enc.isString -> throw ImportFailure(IMPORT_BROKEN)
                password == null -> twoFasEncrypted(root).let { ReadResult.NeedsPassword(ImportFormat.TWOFAS) }
                else -> ReadResult.Ok(openTwoFas(root, password, treeBudget))
            }
            else -> throw ImportFailure(IMPORT_BROKEN)
        }
        else -> null
    }
}

private fun Char.isJsonSpace() = this == ' ' || this == '\t' || this == '\n' || this == '\r'

/** A value of the wrong JSON type in one element: the reader lists that element as [SkipReason.BROKEN]. */
internal class WrongType : Exception() {
    // Control flow only, and never a value from the file: no message and no stack trace.
    override fun fillInStackTrace(): Throwable = this
}

/** The string at [key]; null if absent or `null`. [WrongType] for any other type (a number, an unquoted word…). */
internal fun JsonObject.textOrNull(key: String): String? = when (val v = this[key]) {
    null, JsonNull -> null
    is JsonPrimitive -> if (v.isString) v.content else throw WrongType()
    else -> throw WrongType()
}

/** The string at [key], or "" if absent or `null`; [WrongType] for any other type. */
internal fun JsonObject.text(key: String): String = textOrNull(key).orEmpty()

/** The `true` or `false` at [key]; null if absent or `null`; [WrongType] for anything else (also the string "true"). */
internal fun JsonObject.flag(key: String): Boolean? = when (val v = this[key]) {
    null, JsonNull -> null
    is JsonPrimitive -> when {
        v.isString -> throw WrongType()
        v.content == "true" -> true
        v.content == "false" -> false
        else -> throw WrongType()
    }
    else -> throw WrongType()
}

/** The object at [key]; null if absent or `null`; [WrongType] for any other type. */
internal fun JsonObject.obj(key: String): JsonObject? = when (val v = this[key]) {
    null, JsonNull -> null
    is JsonObject -> v
    else -> throw WrongType()
}

/** The array at [key]; null if absent or `null`; [WrongType] for any other type. */
internal fun JsonObject.list(key: String): JsonArray? = when (val v = this[key]) {
    null, JsonNull -> null
    is JsonArray -> v
    else -> throw WrongType()
}

/** What [number] gives for a JSON number that is not a whole number in Int range: no type or setting read has it. */
internal const val NOT_A_WHOLE_NUMBER = -1

/**
 * The number at [key]; null if absent or `null`. A whole number in Int range is its value; any other JSON number (a
 * fraction, an exponent, one too big such as `1e999`) is [NOT_A_WHOLE_NUMBER], which every caller refuses. Anything
 * that is not a JSON number by the grammar of RFC 8259 (a string, `true`, an unquoted word such as `6&period=10`) is
 * [WrongType]: the parser keeps such words as they are, and they never reach a link.
 */
internal fun JsonObject.number(key: String): Int? = when (val v = this[key]) {
    null, JsonNull -> null
    is JsonPrimitive -> {
        val s = v.content
        if (v.isString || !isJsonNumber(s)) throw WrongType()
        if (s.any { it == '.' || it == 'e' || it == 'E' }) NOT_A_WHOLE_NUMBER else s.toIntOrNull() ?: NOT_A_WHOLE_NUMBER
    }
    else -> throw WrongType()
}

/**
 * If [s] is a number by the grammar of RFC 8259 §6: `-`, then `0` or digits without a leading zero, a fraction and an
 * exponent.
 */
private fun isJsonNumber(s: String): Boolean {
    var i = 0
    val n = s.length
    fun digits(): Boolean {
        val from = i
        while (i < n && s[i] in '0'..'9') i++
        return i > from
    }
    if (i < n && s[i] == '-') i++
    if (i >= n) return false
    if (s[i] == '0') i++ else if (!digits()) return false
    if (i < n && s[i] == '.') {
        i++
        if (!digits()) return false
    }
    if (i < n && (s[i] == 'e' || s[i] == 'E')) {
        i++
        if (i < n && (s[i] == '+' || s[i] == '-')) i++
        if (!digits()) return false
    }
    return i == n
}

/** The string [e] is, or "": only for the title of an element listed in «no entran», which may be broken. */
internal fun shownText(e: JsonElement?): String = (e as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

/**
 * If [s] can be stored as read: no NUL, and no surrogate without its pair. A JSON escape can write either (`\u0000`,
 * `\ud800`), which the strict decoding of the file ([decodeText]) cannot; the element goes to [SkipReason.BROKEN].
 */
internal fun wellFormed(s: String): Boolean {
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\u0000' -> return false
            Character.isHighSurrogate(c) -> {
                if (i + 1 >= s.length || !Character.isLowSurrogate(s[i + 1])) return false
                i += 2
                continue
            }
            Character.isLowSurrogate(c) -> return false
        }
        i++
    }
    return true
}

/**
 * The folder or group names of a file, by their id ([idKey]) as [nameKey]. A list that is not an array, a record
 * without a string id and name, and a name with a NUL or a lone surrogate ([wellFormed]) give no folder: folders
 * only become tags, so the elements still enter. With an id written twice, the first one counts.
 */
internal fun folderNames(list: JsonElement?, idKey: String, nameKey: String = "name"): Map<String, String> {
    val out = HashMap<String, String>()
    if (list !is JsonArray) return out
    for (f in list) {
        val o = f as? JsonObject ?: continue
        val id = (o[idKey] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
        val name = (o[nameKey] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(::wellFormed) ?: continue
        out.putIfAbsent(id, name)
    }
    return out
}

/**
 * The notes of one element, built line by line with the bound of [MAX_NOTES]: a part that would pass it is not
 * added, so a file with millions of fields never makes a huge string; [tooLong] says the element must go to
 * [SkipReason.FIELD_TOO_LONG] (it is never imported cut). [broken] says a part had a NUL or a lone surrogate.
 */
internal class NotesBuilder {
    private val sb = StringBuilder()
    var tooLong = false
        private set
    var broken = false
        private set

    val isEmpty: Boolean get() = sb.isEmpty() && !tooLong

    /** A block of text as written (the element's own notes); nothing if empty. */
    fun text(t: String) {
        if (t.isNotEmpty()) put(t, null)
    }

    /** A «Name: value» line; nothing if the value is empty. */
    fun line(name: String, value: String) {
        if (value.isNotEmpty()) put(name, value)
    }

    private fun put(a: String, b: String?) {
        if (!wellFormed(a) || (b != null && !wellFormed(b))) broken = true
        if (tooLong) return
        val need = (if (sb.isEmpty()) 0L else 1L) + a.length + (if (b == null) 0 else 2 + b.length)
        if (sb.length + need > MAX_NOTES) {
            tooLong = true
            return
        }
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(a)
        if (b != null) sb.append(": ").append(b)
    }

    /** The notes, and the builder's array overwritten (it may hold hidden fields). */
    fun build(): String {
        val s = sb.toString()
        sb.setLength(0)
        sb.setLength(sb.capacity())
        sb.setLength(0)
        return s
    }
}
