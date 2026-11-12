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
private fun VaultEntry.fittedHistory(): VaultEntry {
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
 * within them. So an old vault always opens, passes to v4 and keeps working. The structure and the whole vault are
 * always checked.
 */
internal fun VaultData.fittedForSave(previous: VaultData?): VaultData {
    val old: MutableSet<VaultEntry> = Collections.newSetFromMap(IdentityHashMap())
    previous?.entries?.let { old.addAll(it) }
    // Decided before fitting, which may make a new object of an old entry by trimming its history.
    val kept = entries.filter { it in old }.mapTo(HashSet()) { it.id }
    val (data, size) = fittedWithSize()
    data.checkStructure()
    data.entries.forEach { if (it.id !in kept) it.checkLimits() }
    if (size > VaultLimits.VAULT_MAX_DATA_BYTES) throw VaultLimitExceeded(VAULT_TOO_BIG)
    return data
}

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
 * The limits on what organises the vault: folders (count, depth and no folder inside itself), views and tag colours.
 * A folder whose parent is not in the vault counts as a top folder. Also checked on a copy before it is imported.
 */
internal fun VaultData.checkStructure() {
    if (folders.size > VaultLimits.FOLDERS_MAX) throw VaultLimitExceeded(TOO_MANY_FOLDERS)
    if (views.size > VaultLimits.VIEWS_MAX) throw VaultLimitExceeded(TOO_MANY_VIEWS)
    if (tagColors.size > VaultLimits.TAG_COLORS_MAX) throw VaultLimitExceeded(TOO_MANY_TAG_COLORS)
    if (name.codePointLength() > VaultLimits.VAULT_NAME_MAX) throw VaultLimitExceeded(VAULT_NAME_TOO_LONG)
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
