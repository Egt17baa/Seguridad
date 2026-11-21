package app.seguridad.vault

import app.seguridad.core.appJson
import app.seguridad.core.writeTextAtomically
import app.seguridad.crypto.randomBytes
import app.seguridad.imports.plainReportText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * 2.0: the local id of the vault there was before 2.0. Its folder stays `files/vault/` and its fingerprint key keeps the
 * alias of before (AppNameTest), so nothing of an earlier install moves.
 */
const val PRIMARY_VAULT_ID = "principal"

/** The registry of the vaults, in the app's private files (never in the system's backups: allowBackup is off). */
const val VAULTS_FILE = "vaults.json"

/** The folder of every vault but [PRIMARY_VAULT_ID], one subfolder per id. */
private const val VAULTS_DIR = "vaults"

/** An id made by [VaultRegistry.create]: 16 random bytes in lowercase hex. */
private val VAULT_ID = Regex("[0-9a-f]{32}")

/**
 * One vault of the phone, as the registry keeps it: its local id (names its folder, its settings and its fingerprint
 * key; never in a copy), its colour and its place in the list. No name (owner's decision, 12-11-2026, option A): the name
 * is inside the vault, encrypted ([VaultData.name]), and before unlocking the list shows «Bóveda N» ([displayName]).
 */
@Serializable
data class VaultInfo(val id: String, val color: TagColor = TagColor.entries.first(), val order: Int = 0)

@Serializable
private data class RegistryFile(val vaults: List<VaultInfo> = emptyList(), val lastOpened: String? = null)

/**
 * What the list shows for a vault: its name when it is open and has one ([data] is its open [VaultData]), as plain text
 * ([plainReportText]: no controls, direction marks or invisibles, as titles); otherwise «Bóveda N», N being its place in
 * the list ([order] from 0). A vault from before 2.0, or a copy of one, has no name until one is given.
 */
fun displayName(data: VaultData?, order: Int): String =
    data?.name?.let { plainReportText(it, VaultLimits.VAULT_NAME_MAX * 2).trim() }?.takeIf { it.isNotEmpty() } ?: "Bóveda ${order + 1}"

/**
 * The per-vault name of something the first vault already had: [base] as it was for [PRIMARY_VAULT_ID] (the Keystore
 * alias and the preferences of its fingerprint, which AppNameTest fixes), and `<base>_<id>` for any other.
 */
fun perVaultName(base: String, id: String): String = if (id == PRIMARY_VAULT_ID) base else "${base}_$id"

/**
 * The vaults of this phone (design «Varias bóvedas»): at most [VaultLimits.VAULTS_MAX], kept in [VAULTS_FILE] with
 * [writeTextAtomically]: ids, colours, order and the last one opened; never a name nor a secret. A file that is missing or
 * cannot be read is rebuilt from the folders ([PRIMARY_VAULT_ID] first, if its folder is there or there is no other), and
 * written at the next change. Read once, when built (a few hundred bytes, as the settings at the start of the app);
 * every change writes the file under a lock, off the main thread.
 */
class VaultRegistry(private val filesDir: File, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val file = File(filesDir, VAULTS_FILE)
    private val mutex = Mutex()

    private val _vaults: MutableStateFlow<List<VaultInfo>>
    private var _lastOpened: String

    init {
        val read = read() ?: rebuilt()
        val list = read.vaults.ifEmpty { listOf(VaultInfo(PRIMARY_VAULT_ID)) }.sortedBy { it.order }.mapIndexed { i, v -> v.copy(order = i) }
        _vaults = MutableStateFlow(list)
        _lastOpened = read.lastOpened?.takeIf { id -> list.any { it.id == id } } ?: list.first().id
    }

    /** The vaults, in the order the user gave them; never empty. */
    val vaults: StateFlow<List<VaultInfo>> = _vaults.asStateFlow()

    /** The vault opened last ([setLastOpened]), the one the app starts on; the first one if that one is gone. */
    val lastOpened: String get() = _lastOpened

    /**
     * Where the vault [id] keeps its file and its attachments: `files/vault/` for [PRIMARY_VAULT_ID], as before 2.0, and
     * `files/vaults/<id>/` for the others. [IllegalArgumentException] for anything that is not such an id, so no id can
     * name a path outside those folders.
     */
    fun dirOf(id: String): File = when {
        id == PRIMARY_VAULT_ID -> File(filesDir, "vault")
        VAULT_ID.matches(id) -> File(File(filesDir, VAULTS_DIR), id)
        else -> throw IllegalArgumentException("Not a vault id")
    }

    /** A new vault, last in the list, with a new random id; [VaultLimitExceeded] when there are [VaultLimits.VAULTS_MAX]. */
    suspend fun create(color: TagColor): VaultInfo = mutex.withLock {
        val list = _vaults.value
        if (list.size >= VaultLimits.VAULTS_MAX) throw VaultLimitExceeded(TOO_MANY_VAULTS)
        var id: String
        do {
            id = randomBytes(16).joinToString("") { "%02x".format(it) }
        } while (list.any { it.id == id } || dirOf(id).exists())
        val info = VaultInfo(id, color, list.size)
        write(list + info, _lastOpened)
        info
    }

    suspend fun setColor(id: String, color: TagColor) = mutex.withLock {
        val list = _vaults.value
        require(list.any { it.id == id }) { "Unknown vault" }
        write(list.map { if (it.id == id) it.copy(color = color) else it }, _lastOpened)
    }

    /** [ids] are the vaults in their new order: every one, once. */
    suspend fun reorder(ids: List<String>) = mutex.withLock {
        val list = _vaults.value
        require(ids.size == list.size && ids.toSet() == list.map { it.id }.toSet()) { "Not a reordering" }
        write(ids.mapIndexed { i, id -> list.first { it.id == id }.copy(order = i) }, _lastOpened)
    }

    /**
     * Takes [id] off the list and deletes its folder (its file and its attachments; copies made elsewhere are not
     * touched). Never the only vault ([IllegalStateException]): there is always one to show. The list is written first,
     * so a failure while deleting leaves at worst a folder no vault points at, which nothing reads again until a rebuild.
     */
    suspend fun remove(id: String) = mutex.withLock {
        val list = _vaults.value
        require(list.any { it.id == id }) { "Unknown vault" }
        check(list.size > 1) { "The last vault is never removed" }
        val rest = list.filterNot { it.id == id }.mapIndexed { i, v -> v.copy(order = i) }
        write(rest, if (_lastOpened == id) rest.first().id else _lastOpened)
        withContext(io) { dirOf(id).deleteRecursively() }
    }

    suspend fun setLastOpened(id: String) = mutex.withLock {
        require(_vaults.value.any { it.id == id }) { "Unknown vault" }
        if (id != _lastOpened || !file.exists()) write(_vaults.value, id)
    }

    /** Writes [list] and [last] to the file, then shows them; throws (nothing shown) if the write fails. */
    private suspend fun write(list: List<VaultInfo>, last: String) {
        val text = appJson.encodeToString(RegistryFile.serializer(), RegistryFile(list, last))
        withContext(io) { file.writeTextAtomically(text) }
        _vaults.value = list
        _lastOpened = last
    }

    /** The file as it was written; null if it is missing, damaged or names no valid vault. Duplicated ids keep the first. */
    private fun read(): RegistryFile? = try {
        if (!file.exists()) {
            null
        } else {
            val read = appJson.decodeFromString(RegistryFile.serializer(), file.readText())
            val seen = HashSet<String>()
            val valid = read.vaults.filter { (it.id == PRIMARY_VAULT_ID || VAULT_ID.matches(it.id)) && seen.add(it.id) }.take(VaultLimits.VAULTS_MAX)
            if (valid.isEmpty()) null else read.copy(vaults = valid)
        }
    } catch (e: Exception) {
        null
    }

    /** The vaults the folders show: [PRIMARY_VAULT_ID] first (if its folder is there, or there is no other), then the others by id. */
    private fun rebuilt(): RegistryFile {
        val others = try {
            File(filesDir, VAULTS_DIR).listFiles()?.filter { it.isDirectory && VAULT_ID.matches(it.name) }?.map { it.name }?.sorted().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        val primary = File(filesDir, "vault").exists() || others.isEmpty()
        val ids = (if (primary) listOf(PRIMARY_VAULT_ID) else emptyList()) + others
        return RegistryFile(ids.take(VaultLimits.VAULTS_MAX).mapIndexed { i, id -> VaultInfo(id, order = i) })
    }
}

const val TOO_MANY_VAULTS = "Puede haber como mucho 10 bóvedas. Borra alguna para crear otra."
