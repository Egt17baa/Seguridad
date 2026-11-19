package app.seguridad.vault

import app.seguridad.core.appJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.encodeToStream
import java.io.OutputStream
import java.util.Collections
import java.util.IdentityHashMap

/**
 * 2.0: the limits of format v4 (owner's decision (a) of 1.4.0, design «Formato v4»): the whole vault fits in memory and
 * in a copy with marker 3. Sizes are of the JSON the app writes (UTF-8, defaults left out); lengths of names and values
 * are in code points.
 */
object VaultLimits {
    /** One entry without its history. */
    const val ENTRY_MAX_JSON_BYTES = 64 * 1024
    const val FIELDS_MAX = 30
    const val FIELD_NAME_MAX = 100
    const val FIELD_VALUE_MAX = 10_000
    const val HISTORY_MAX_VERSIONS = 20

    /** The history of one entry. */
    const val HISTORY_MAX_BYTES = 64 * 1024

    /** The whole data blob, before it is encrypted. */
    const val VAULT_MAX_DATA_BYTES = 24 * 1024 * 1024

    /**
     * Re-review B i-2: what the bookkeeping writes may add to the data with no save that fits them ([vaultTag] when it is
     * missing, [VaultData.lastUnlockAt], [VaultData.trashPurgeSkipped], at an unlock with the master or the fingerprint).
     * Only a copy's check allows it over [VAULT_MAX_DATA_BYTES] ([checkCopySize]), so a copy of a vault at the limit is
     * still read; every save stays at [VAULT_MAX_DATA_BYTES].
     */
    const val COPY_DATA_MARGIN_BYTES = 4 * 1024
    const val FOLDERS_MAX = 500
    const val FOLDER_DEPTH_MAX = 5
    const val VIEWS_MAX = 50
    const val TAG_COLORS_MAX = 200
    const val VAULTS_MAX = 10

    /** The name of a vault ([VaultData.name]), in code points. */
    const val VAULT_NAME_MAX = 100
    const val BACKUP_TARGETS_MAX = 3
    const val TRASH_DAYS = 30

    /**
     * Deepest nesting of `{` and `[` [VaultCrypto.read] decodes: a real v4 blob reaches 8 (vault, entries, entry, history,
     * version, entry, fields, field). Checked before decoding, so a hostile copy (its maker knows its master password)
     * cannot nest versions inside versions until the decoder runs out of stack.
     */
    const val JSON_MAX_DEPTH = 16
}

/** A save refused by [checkLimits]: [message] is a fixed Spanish text that says what to do. Nothing is saved. */
class VaultLimitExceeded(message: String) : IllegalStateException(message)

const val VAULT_TOO_BIG = "La bóveda no cabe: pasa de 24 MB. Vacía la papelera, borra historial o quita entradas grandes."
const val ENTRY_TOO_BIG = "La entrada pasa de 64 KB. Acorta sus notas o sus campos."
const val TOO_MANY_FIELDS = "Una entrada puede tener como mucho 30 campos. Quita alguno."
const val FIELD_NAME_TOO_LONG = "El nombre de un campo puede tener como mucho 100 caracteres. Acórtalo."
const val FIELD_VALUE_TOO_LONG = "El valor de un campo puede tener como mucho 10 000 caracteres. Acórtalo."
const val HISTORY_TOO_BIG = "El historial de una entrada pasa de 20 versiones o de 64 KB. Borra su historial."
const val TOO_MANY_FOLDERS = "Puede haber como mucho 500 carpetas. Borra alguna."
const val FOLDERS_TOO_DEEP = "Las carpetas pueden anidarse como mucho 5 niveles. Mueve alguna más arriba."
const val FOLDER_CYCLE = "Una carpeta no puede estar dentro de sí misma. Muévela a otro sitio."
const val TOO_MANY_VIEWS = "Puede haber como mucho 50 vistas guardadas. Borra alguna."
const val TOO_MANY_TAG_COLORS = "Puede haber como mucho 200 etiquetas con color. Quita el color de alguna."
/** Two folders or two views with the same id: only a copy made elsewhere has them ([checkStructure]). */
const val ORGANIZE_DAMAGED = "Las carpetas o las vistas de la bóveda están dañadas: hay una repetida. Borra la que sale dos veces para poder guardar."
const val VAULT_NAME_TOO_LONG = "El nombre de la bóveda puede tener como mucho 100 caracteres. Acórtalo."

/** Counts what is written to it, and keeps nothing. */
private class CountingStream : OutputStream() {
    var count = 0L
    override fun write(b: Int) {
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        count += len
    }
}

/** The UTF-8 size of [value] as the app writes it, counted while it is encoded: no copy of the JSON is kept. */
@OptIn(ExperimentalSerializationApi::class)
internal fun <T> jsonBytes(serializer: KSerializer<T>, value: T): Long =
    CountingStream().also { appJson.encodeToStream(serializer, value, it) }.count

internal fun jsonBytes(data: VaultData): Long = jsonBytes(VaultData.serializer(), data)

private val historySerializer = ListSerializer(EntryVersion.serializer())

private fun VaultEntry.historyBytes(): Long = jsonBytes(historySerializer, history)

private fun VaultEntry.bytesWithoutHistory(): Long = jsonBytes(VaultEntry.serializer(), if (history.isEmpty()) this else copy(history = emptyList()))

private fun String.codePointLength(): Int = codePointCount(0, length)

/**
 * The history of this entry within [VaultLimits.HISTORY_MAX_VERSIONS] and [VaultLimits.HISTORY_MAX_BYTES]: the oldest
 * versions (by [EntryVersion.savedAt]) go first; the order of the rest does not change. The same object when it fits.
 */
internal fun VaultEntry.fittedHistory(): VaultEntry {
    if (history.isEmpty()) return this
    var kept = history
    if (kept.size > VaultLimits.HISTORY_MAX_VERSIONS) {
        val doomed = kept.withIndex().sortedBy { it.value.savedAt }.take(kept.size - VaultLimits.HISTORY_MAX_VERSIONS).mapTo(HashSet()) { it.index }
        kept = kept.filterIndexed { i, _ -> i !in doomed }
    }
    while (kept.isNotEmpty() && jsonBytes(historySerializer, kept) > VaultLimits.HISTORY_MAX_BYTES) {
        val oldest = kept.indices.minBy { kept[it].savedAt }
        kept = kept.filterIndexed { i, _ -> i != oldest }
    }
    return if (kept.size == history.size) this else copy(history = kept)
}

/** [fitted], with the size of what it returns. */
private fun VaultData.fittedWithSize(): Pair<VaultData, Long> {
    var changed = false
    val each = entries.map { e -> e.fittedHistory().also { if (it !== e) changed = true } }
    var data = if (changed) copy(entries = each) else this
    var size = jsonBytes(data)
    while (size > VaultLimits.VAULT_MAX_DATA_BYTES) {
        val versions = data.entries.flatMapIndexed { i, e -> e.history.mapIndexed { j, v -> Triple(i, j, v) } }
        if (versions.isEmpty()) break
        // The oldest of the whole vault first, until about what is over is freed; measured again after.
        val over = size - VaultLimits.VAULT_MAX_DATA_BYTES
        val doomed = HashSet<Pair<Int, Int>>()
        var freed = 0L
        for ((i, j, v) in versions.sortedBy { it.third.savedAt }) {
            doomed += i to j
            freed += jsonBytes(EntryVersion.serializer(), v) + 1
            if (freed >= over) break
        }
        data = data.copy(
            entries = data.entries.mapIndexed { i, e ->
                if (e.history.indices.none { (i to it) in doomed }) e else e.copy(history = e.history.filterIndexed { j, _ -> (i to j) !in doomed })
            },
        )
        size = jsonBytes(data)
    }
    return data to size
}

/**
 * This vault with its history trimmed to fit: each entry's within its limits, and then, while the whole vault passes
 * [VaultLimits.VAULT_MAX_DATA_BYTES], the oldest versions of the whole vault first, whichever entry holds them. Nothing
 * but history is ever dropped: a vault that does not fit without it still does not ([checkLimits] refuses it). The same
 * object when nothing had to go.
 */
fun VaultData.fitted(): VaultData = fittedWithSize().first

/** Throws [VaultLimitExceeded] if this vault passes any limit of [VaultLimits]; checks what [fitted] would trim too. */
fun VaultData.checkLimits() {
    checkStructure()
    entries.forEach { it.checkLimits() }
    if (jsonBytes(this) > VaultLimits.VAULT_MAX_DATA_BYTES) throw VaultLimitExceeded(VAULT_TOO_BIG)
}

/**
 * What [VaultStore] saves instead of [this]: [fitted], and then [checkLimits], except that the limits of one entry are
 * only checked on the entries that are not, as the same objects, in [previous] (the vault as it was): an entry that
 * 1.4.3 or before saved over 64 KiB (its notes had no limit) is kept as it is, and only an entry added or changed must be
 * within them (a move to a folder or to the trash, or a use, is no change of what it holds). So an old vault always opens, passes to v4 and keeps working. The structure and the whole vault are
 * always checked.
 */
internal fun VaultData.fittedForSave(previous: VaultData?): VaultData {
    val old: MutableSet<VaultEntry> = Collections.newSetFromMap(IdentityHashMap())
    previous?.entries?.let { old.addAll(it) }
    val before = previous?.entries?.associateBy { it.id }.orEmpty()
    // Decided before fitting, which may make a new object of an old entry by trimming its history. 2.0, task 4: an entry
    // that only changed its folder, its last use or its place in the trash ([sameButPlace]) is kept too, so an old entry
    // over 64 KiB can still be moved, used and sent to the trash.
    val kept = entries.filter { it in old || before[it.id]?.let(it::sameButPlace) == true }.mapTo(HashSet()) { it.id }
    val (data, size) = fittedWithSize()
    data.checkStructure()
    data.entries.forEach { if (it.id !in kept) it.checkLimits() }
    if (size > VaultLimits.VAULT_MAX_DATA_BYTES) throw VaultLimitExceeded(VAULT_TOO_BIG)
    return data
}

/** Whether this entry is [other] but for [VaultEntry.folderId], [VaultEntry.lastUsedAt] and [VaultEntry.deletedAt]. */
private fun VaultEntry.sameButPlace(other: VaultEntry): Boolean =
    copy(folderId = other.folderId, lastUsedAt = other.lastUsedAt, deletedAt = other.deletedAt) == other

private fun VaultEntry.checkLimits() {
    entryLimitProblem()?.let { throw VaultLimitExceeded(it) }
}

/** Which limit of one entry this entry passes (its fixed text), or null when it is within all of them. */
internal fun VaultEntry.entryLimitProblem(): String? = when {
    fields.size > VaultLimits.FIELDS_MAX -> TOO_MANY_FIELDS
    fields.any { it.name.codePointLength() > VaultLimits.FIELD_NAME_MAX } -> FIELD_NAME_TOO_LONG
    fields.any { it.value.codePointLength() > VaultLimits.FIELD_VALUE_MAX } -> FIELD_VALUE_TOO_LONG
    history.size > VaultLimits.HISTORY_MAX_VERSIONS || history.isNotEmpty() && historyBytes() > VaultLimits.HISTORY_MAX_BYTES -> HISTORY_TOO_BIG
    bytesWithoutHistory() > VaultLimits.ENTRY_MAX_JSON_BYTES -> ENTRY_TOO_BIG
    else -> null
}

/**
 * 2.0, task 5 (review I-1 and I-2): whether the fields of this entry pass what the app ever writes: at most
 * [VaultLimits.FIELDS_MAX], each name within [VaultLimits.FIELD_NAME_MAX] and each value within
 * [VaultLimits.FIELD_VALUE_MAX] code points, and no id twice. Only a copy made elsewhere fails it (fields are new in v4: a
 * copy of 1.4.3 or before has none), and [checkCopyEntries] refuses it as damaged, for the entry and each version of its
 * history.
 */
internal fun VaultEntry.fieldsDamaged(): Boolean {
    if (fields.isEmpty()) return false
    if (fields.size > VaultLimits.FIELDS_MAX) return true
    val ids = HashSet<String>()
    return fields.any { f ->
        !ids.add(f.id) || f.name.codePointLength() > VaultLimits.FIELD_NAME_MAX || f.value.codePointLength() > VaultLimits.FIELD_VALUE_MAX
    }
}

/**
 * The limits on what organises the vault: folders (count, depth, no folder inside itself and no id twice), views (count
 * and no id twice) and tag colours.
 * A folder whose parent is not in the vault counts as a top folder. Also checked on a copy before it is imported.
 */
internal fun VaultData.checkStructure() {
    if (folders.size > VaultLimits.FOLDERS_MAX) throw VaultLimitExceeded(TOO_MANY_FOLDERS)
    if (views.size > VaultLimits.VIEWS_MAX) throw VaultLimitExceeded(TOO_MANY_VIEWS)
    if (tagColors.size > VaultLimits.TAG_COLORS_MAX) throw VaultLimitExceeded(TOO_MANY_TAG_COLORS)
    if (name.codePointLength() > VaultLimits.VAULT_NAME_MAX) throw VaultLimitExceeded(VAULT_NAME_TOO_LONG)
    // 2.0, review I-1 of task 4: the app never repeats an id (a new folder or view gets a random one), so a repeated one
    // only comes from a copy made elsewhere, which [checkCopyStructure] then refuses as damaged.
    val folderIds = HashSet<String>()
    if (!folders.all { folderIds.add(it.id) }) throw VaultLimitExceeded(ORGANIZE_DAMAGED)
    val viewIds = HashSet<String>()
    if (!views.all { viewIds.add(it.id) }) throw VaultLimitExceeded(ORGANIZE_DAMAGED)
    val parent = HashMap<String, String?>()
    folders.forEach { parent.putIfAbsent(it.id, it.parentId) }
    var deepest = 0
    for (f in folders) {
        val seen = HashSet<String>()
        var at: String? = f.id
        while (at != null && at in parent) {
            if (!seen.add(at)) throw VaultLimitExceeded(FOLDER_CYCLE)
            at = parent[at]
        }
        deepest = maxOf(deepest, seen.size)
    }
    if (deepest > VaultLimits.FOLDER_DEPTH_MAX) throw VaultLimitExceeded(FOLDERS_TOO_DEEP)
}

/*
 * RT-21, part b (ruling of the director on the final attack): what a copy's data cost once decoded, estimated from its
 * bytes before they are decoded, and the longest of its lists. Only for copies («Importar copia», «Importar como bóveda
 * nueva», the JSON export of 1.0 and «Comprobar una copia»); never for the vault's own file.
 *
 * The model follows the typed decoding (kotlinx-serialization into [VaultData]), not a JSON tree; member names cost
 * nothing (they are the classes' fields, and one with an escape is refused, RT-26, so each is read as the decoder reads
 * it), save in [VaultData.tagColors], a map:
 * - an object: [COPY_TREE_OBJECT_BYTES] (the largest class, [VaultEntry]); in `tagColors`, a `LinkedHashMap`
 *   ([COPY_TREE_COLLECTION_BYTES] and its table) and per member [COPY_TREE_ELEMENT_BYTES] and its key's string;
 * - an array: [COPY_TREE_COLLECTION_BYTES] and a table of references made at its first element with 16 slots and
 *   doubled when more than ¾ full, plus [COPY_TREE_ELEMENT_BYTES] per element: the cost of a `LinkedHashSet` element,
 *   the dearest of the lists the classes hold (a `Set` in a view's filter), for every array;
 * - a string: [COPY_TREE_STRING_BYTES] plus [COPY_TREE_CHAR_BYTES] per byte inside its quotes (an escape counts what it
 *   writes, more than what it gives);
 * - a number, `true`, `false` or `null`: [COPY_TREE_LITERAL_BYTES] (a boxed `Long?` at most).
 * Each constant is measured against the live heap after GC for samples of each shape (list, set, map and object) and is
 * never below it (RedTeam20CopyShapeTest); the table is in redteam-fixes-report.md.
 */

/** Longest list (or object with members) of a copy, the list of entries aside. */
const val COPY_LIST_MAX = 10_000

/** An object: a [VaultEntry] (the largest class: about 30 fields, 4-byte references, 8-byte longs, a 12-byte header). */
const val COPY_TREE_OBJECT_BYTES = 160L

/** An `ArrayList`, `LinkedHashSet` or `LinkedHashMap` with nothing in it (its table aside). */
const val COPY_TREE_COLLECTION_BYTES = 64L

/** One element of a list, a set or a map: a `LinkedHashMap.Entry` (40 B), the dearest of the three. */
const val COPY_TREE_ELEMENT_BYTES = 40L

/** A `String` and its byte array, besides the characters (a one-character string is 48 B). */
const val COPY_TREE_STRING_BYTES = 48L

/** Each byte inside a string's quotes: at most two bytes of `String` (UTF-16) per byte of UTF-8. */
const val COPY_TREE_CHAR_BYTES = 2L

/** A number, `true`, `false` or `null`: a boxed `Long?` at most. */
const val COPY_TREE_LITERAL_BYTES = 16L

/**
 * Largest estimated decoded tree of a copy's data ([copyTreeEstimate]): 262 MiB, 1.25 times the dearest legitimate
 * shape at [VaultLimits.VAULT_MAX_DATA_BYTES] + [VaultLimits.COPY_DATA_MARGIN_BYTES] (logins as the app makes them, each
 * with 10 tags of one code point: 219 574 908 bytes estimated), rounded up to the MiB. The calibration, shape by shape,
 * is in redteam-fixes-report.md and RedTeam20CopyShapeTest. A copy over it is refused before it is decoded; the vault's
 * own file never is.
 */
const val COPY_TREE_MAX_BYTES = 262L * 1024 * 1024

/**
 * What [scanCopy] found: the [estimate] (stopped at [treeLimit] if passed), the [longest] list but the entries, and
 * whether a member of an object of a class (any but the map `tagColors`) has an [escapedName] (RT-26).
 */
internal data class CopyShape(val estimate: Long, val longest: Int, val escapedName: Boolean = false)

/**
 * RT-21: refuses with [CryptoFailure] ([COPY_DAMAGED]) the data of a copy ([json], of [version]) with a list or an
 * object of more than [COPY_LIST_MAX] elements or members (the list of entries aside: the root of a version 2, 3 or
 * unknown, the member `entries` of the root in a 4), or whose decoded tree would pass [treeMax]. Before any `String` is
 * made, in one linear pass.
 *
 * RT-26 (round 2 of the fixes of the attack): also refuses a `\` in the name of a member of an object of a class, at
 * any depth (every object but the map `tagColors`, whose keys are tags and may carry `\"`, `\\` or `\uXXXX` as
 * kotlinx writes them). Clavero never writes an escape in a field's name, and the decoder reads names unescaped: so the
 * pass only ever knows names the decoder reads the same (CWE-436), and the estimate is never below the tree.
 */
internal fun checkCopyShape(json: ByteArray, version: Int, treeMax: Long = COPY_TREE_MAX_BYTES) {
    val shape = scanCopy(json, version, treeMax, COPY_LIST_MAX)
    if (shape.escapedName || shape.longest > COPY_LIST_MAX || shape.estimate > treeMax) {
        throw app.seguridad.crypto.CryptoFailure(COPY_DAMAGED)
    }
}

/** The estimate of [checkCopyShape], with no bound: for the tests and the calibration. */
internal fun copyTreeEstimate(json: ByteArray, version: Int): Long = scanCopy(json, version, Long.MAX_VALUE, Int.MAX_VALUE).estimate

/** The longest list or object but the entries, with no bound: for the tests. */
internal fun copyLongestList(json: ByteArray, version: Int): Int = scanCopy(json, version, Long.MAX_VALUE, Int.MAX_VALUE).longest

private fun tableBytes(slots: Int): Long = (16L + 4L * slots + 7) / 8 * 8

private fun bytesEqual(json: ByteArray, from: Int, to: Int, word: String): Boolean {
    if (to - from != word.length) return false
    for (k in word.indices) if (json[from + k].toInt() != word[k].code) return false
    return true
}

/**
 * The pass of [checkCopyShape]: knows about strings and their escapes (a bracket or a quote inside a string does not
 * count), keeps per open level whether it is an object, a map or the entries, its count and its table, and stops as
 * soon as the estimate passes [treeLimit] or a list passes [listLimit]. It does not check that the JSON is valid; the
 * decoder does that afterwards. Nesting past [VaultLimits.JSON_MAX_DEPTH] is [jsonNestingWithin]'s, checked before.
 */
private fun scanCopy(json: ByteArray, version: Int, treeLimit: Long, listLimit: Int): CopyShape {
    val max = VaultLimits.JSON_MAX_DEPTH + 1
    val isObject = BooleanArray(max + 1)
    val isMap = BooleanArray(max + 1)
    val isEntries = BooleanArray(max + 1)
    val count = IntArray(max + 1)
    val room = IntArray(max + 1)
    var depth = 0
    var bytes = 0L
    var longest = 0
    var inLiteral = false
    var lastStringFrom = -1
    var lastStringTo = -1
    var lastStringCost = 0L
    var lastStringEscaped = false
    var escapedName = false
    // What the next value of the root's member is: 1 entries, 2 tagColors, 0 anything else.
    var pending = 0

    fun grow() {
        val table = room[depth]
        if (table == 0) {
            bytes += tableBytes(16)
            room[depth] = 16
        } else if (count[depth] > table / 4 * 3) {
            bytes += tableBytes(table * 2) - tableBytes(table)
            room[depth] = table * 2
        }
    }

    fun counted() {
        if (!isEntries[depth] && count[depth] > longest) longest = count[depth]
    }

    /** A value starts at the current level: an element, if that level is an array. */
    fun element() {
        if (depth == 0 || isObject[depth]) return
        count[depth]++
        bytes += COPY_TREE_ELEMENT_BYTES
        grow()
        counted()
    }

    fun open(obj: Boolean) {
        element()
        val kind = if (depth == 1) pending else 0
        pending = 0
        if (++depth > max) return
        isObject[depth] = obj
        isMap[depth] = obj && kind == 2
        isEntries[depth] = (!obj && kind == 1) || (!obj && depth == 1 && version != VaultFile.VERSION)
        count[depth] = 0
        room[depth] = 0
        bytes += if (!obj || isMap[depth]) COPY_TREE_COLLECTION_BYTES else COPY_TREE_OBJECT_BYTES
    }

    var i = 0
    val n = json.size
    while (i < n) {
        when (json[i].toInt()) {
            '"'.code -> {
                inLiteral = false
                element()
                val from = ++i
                lastStringEscaped = false
                while (i < n) {
                    val c = json[i].toInt()
                    if (c == '\\'.code) {
                        lastStringEscaped = true
                        i += 2
                    } else if (c == '"'.code) break else i++
                }
                lastStringFrom = from
                lastStringTo = minOf(i, n)
                lastStringCost = COPY_TREE_STRING_BYTES + COPY_TREE_CHAR_BYTES * (lastStringTo - from)
                bytes += lastStringCost
            }
            '{'.code, '['.code -> {
                inLiteral = false
                open(json[i].toInt() == '{'.code)
            }
            '}'.code, ']'.code -> {
                inLiteral = false
                if (depth > 0) depth--
            }
            ':'.code -> {
                inLiteral = false
                if (depth > 0 && isObject[depth]) {
                    count[depth]++
                    counted()
                    if (isMap[depth]) {
                        // A key of tagColors: its string stays, with its map entry and the table.
                        bytes += COPY_TREE_ELEMENT_BYTES
                        grow()
                    } else {
                        bytes -= lastStringCost // A member's name is a field: it costs nothing.
                        // RT-26: a field's name with an escape is one the pass would not read as the decoder does.
                        if (lastStringEscaped) escapedName = true
                        if (depth == 1) {
                            pending = when {
                                bytesEqual(json, lastStringFrom, lastStringTo, "entries") -> 1
                                bytesEqual(json, lastStringFrom, lastStringTo, "tagColors") -> 2
                                else -> 0
                            }
                        }
                    }
                }
            }
            ','.code, ' '.code, '\t'.code, '\n'.code, '\r'.code -> inLiteral = false
            else -> {
                if (!inLiteral) {
                    element()
                    bytes += COPY_TREE_LITERAL_BYTES
                }
                inLiteral = true
            }
        }
        if (bytes > treeLimit || longest > listLimit) break
        i++
    }
    return CopyShape(bytes, longest, escapedName)
}

/**
 * Whether the JSON in [json] never nests `{` and `[` deeper than [max] (outside strings, with their escapes). A linear
 * pass over the bytes, before decoding: what does not pass is not decoded.
 */
internal fun jsonNestingWithin(json: ByteArray, max: Int = VaultLimits.JSON_MAX_DEPTH): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (b in json) {
        val c = b.toInt()
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\'.code -> escaped = true
                c == '"'.code -> inString = false
            }
            continue
        }
        when (c) {
            '"'.code -> inString = true
            '{'.code, '['.code -> if (++depth > max) return false
            '}'.code, ']'.code -> depth--
        }
    }
    return true
}

/** [data] with every version of [VaultEntry.history] without history or attachments of its own, as the app keeps them. */
internal fun VaultData.withPlainVersions(): VaultData {
    if (entries.none { e -> e.history.any { it.entry.history.isNotEmpty() || it.entry.attachments.isNotEmpty() } }) return this
    return copy(
        entries = entries.map { e ->
            if (e.history.none { it.entry.history.isNotEmpty() || it.entry.attachments.isNotEmpty() }) e
            else e.copy(history = e.history.map { it.copy(entry = it.entry.copy(history = emptyList(), attachments = emptyList())) })
        },
    )
}
