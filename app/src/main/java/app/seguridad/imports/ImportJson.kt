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

/**
 * Most values and names (every object, array, string, number, `true`, `false` and `null`, keys included) in one JSON:
 * [IMPORT_MAX_ITEMS] elements of 50. It bounds the tree the parser builds to a few tens of MB, whatever the file:
 * 16 MB of `[1,1,1…]` would otherwise be 8 million objects.
 */
const val JSON_MAX_TOKENS = 1_000_000

/**
 * Refuses [text] with [IMPORT_JSON_TOO_DEEP] past [JSON_MAX_DEPTH] levels and with [IMPORT_JSON_TOO_MANY_VALUES] past
 * [JSON_MAX_TOKENS], before it is parsed. One linear pass with no allocation that knows about strings: a bracket
 * inside a string does not count, nor one after an escaped quote (`\"`), and an escaped backslash (`\\`) does not
 * escape the quote that follows it. It does not check that the JSON is valid: the parser does that afterwards.
 */
internal fun checkJsonShape(text: String) {
    var depth = 0
    var tokens = 0
    var inLiteral = false
    var i = 0
    val n = text.length
    while (i < n) {
        when (text[i]) {
            '"' -> {
                inLiteral = false
                tokens++
                i++
                while (i < n) {
                    val c = text[i]
                    if (c == '\\') i += 2 else if (c == '"') break else i++
                }
            }
            '{', '[' -> {
                inLiteral = false
                tokens++
                if (++depth > JSON_MAX_DEPTH) throw ImportFailure(IMPORT_JSON_TOO_DEEP)
            }
            '}', ']' -> {
                inLiteral = false
                depth--
            }
            ',', ':', ' ', '\t', '\n', '\r' -> inLiteral = false
            else -> if (!inLiteral) {
                inLiteral = true
                tokens++
            }
        }
        if (tokens > JSON_MAX_TOKENS) throw ImportFailure(IMPORT_JSON_TOO_MANY_VALUES)
        i++
    }
}

/**
 * The JSON formats (spec «Detección»): null when [text] is not a JSON object, or is one of no format Clavero reads
 * (then the next reader is asked, and in the end [IMPORT_UNKNOWN]). A text that starts with `{` is scanned first
 * ([checkJsonShape]); one the parser refuses is unknown too, and the parser's message (which quotes the file) is
 * never used. The format is told by its top-level names, in this order:
 * - `encrypted` or `items`: Bitwarden ([readBitwarden]);
 * - `db`: Aegis (an object in clear, a Base64 string when encrypted);
 * - `services` or `servicesEncrypted`: 2FAS.
 */
internal fun readJsonImport(text: String): ReadResult? {
    var start = 0
    while (start < text.length && text[start].isJsonSpace()) start++
    if (start >= text.length || text[start] != '{') return null
    checkJsonShape(text)
    val root = try {
        Json.parseToJsonElement(text)
    } catch (e: SerializationException) {
        return null
    } catch (e: IllegalArgumentException) {
        return null
    }
    if (root !is JsonObject) return null
    return when {
        "encrypted" in root || "items" in root -> readBitwarden(root)
        "db" in root -> when (val db = root["db"]) {
            is JsonObject -> ReadResult.Ok(readAegisPlain(db))
            is JsonPrimitive -> if (db.isString) ReadResult.NeedsPassword(ImportFormat.AEGIS) else throw ImportFailure(IMPORT_BROKEN)
            else -> throw ImportFailure(IMPORT_BROKEN)
        }
        "services" in root || "servicesEncrypted" in root -> when (val enc = root["servicesEncrypted"]) {
            null, JsonNull -> ReadResult.Ok(readTwoFasPlain(root))
            is JsonPrimitive -> if (enc.isString) ReadResult.NeedsPassword(ImportFormat.TWOFAS) else throw ImportFailure(IMPORT_BROKEN)
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

/** If [s] is a number by the grammar of RFC 8259 §6: `-`, then `0` or digits without a leading zero, a fraction and an exponent. */
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
 * The folder or group names of a file, by their id ([idKey]) as [nameKey]. A list that is not an array, and a record
 * without a string id and name, give no folder: folders only become tags, so the elements still enter. With an id
 * written twice, the first one counts.
 */
internal fun folderNames(list: JsonElement?, idKey: String, nameKey: String = "name"): Map<String, String> {
    val out = HashMap<String, String>()
    if (list !is JsonArray) return out
    for (f in list) {
        val o = f as? JsonObject ?: continue
        val id = (o[idKey] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
        val name = (o[nameKey] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
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
