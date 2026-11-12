package app.seguridad.vault

import kotlinx.serialization.Serializable

/**
 * 2.0: what the data blob of a version-4 vault holds (format v4, design «Formato v4»), encrypted and authenticated with
 * the data key as the list of entries was in versions 2 and 3. Every field has a default, so a version-2 or version-3
 * vault reads as `VaultData(entries = list)` ([VaultCrypto.read]). The entries in the trash are in [entries] too, with
 * [VaultEntry.deletedAt]. Nothing here is ever outside the encrypted blob.
 */
@Serializable
data class VaultData(
    val entries: List<VaultEntry> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val views: List<SavedView> = emptyList(),
    /** Tag (as stored, see [normalizeTags]) to its colour. */
    val tagColors: Map<String, TagColor> = emptyMap(),
    /** When «Comprobar una copia» last checked a copy of this vault ([vaultTag]); 0 for never. */
    val lastCheckedBackupAt: Long = 0,
    /** The last unlock with the master password or the fingerprint (the trash's clock, task 3); 0 for unknown. */
    val lastUnlockAt: Long = 0,
    /**
     * 16 random bytes in lowercase hex, made with the vault or when it passes to v4: only tells whether a checked copy is
     * of this vault. Not a secret and not a key; empty in a vault read from version 2 or 3.
     */
    val vaultTag: String = "",
    val templatesVersion: Int = 1,
    /**
     * 2.0, task 2 (owner's decision, 12-11-2026, option A): the vault's name, inside the encrypted blob, so the registry
     * of the vaults never holds it ([VaultInfo]); shown only once the vault is open ([displayName]). Empty in a vault
     * from before 2.0 until one is given. At most [VaultLimits.VAULT_NAME_MAX] code points.
     */
    val name: String = "",
) {
    /** The entries not in the trash, in the order stored. */
    val live: List<VaultEntry> get() = entries.filter { it.deletedAt == null }

    /** The entries in the trash, in the order stored. */
    val trash: List<VaultEntry> get() = entries.filter { it.deletedAt != null }
}

/** One field the user adds to an entry (2.0). [HIDDEN] is shown masked, like a password. */
@Serializable
data class CustomField(
    val id: String,
    val name: String,
    val kind: FieldKind,
    val value: String,
    /** Whether [value] is a date the expiry notices look at. */
    val expires: Boolean = false,
)

@Serializable
enum class FieldKind { TEXT, HIDDEN, NUMBER, DATE }

/** How the notes are shown: as they are, or with the app's own limited format (task 5; never links, HTML or images). */
@Serializable
enum class NotesFormat { PLAIN, LIMITED }

/** An earlier version of an entry, kept in [VaultEntry.history]: [entry] always without history or attachments. */
@Serializable
data class EntryVersion(val savedAt: Long, val entry: VaultEntry)

@Serializable
data class Folder(val id: String, val name: String, val parentId: String? = null, val order: Int = 0)

@Serializable
data class SavedView(val id: String, val name: String, val filter: EntryFilter)

@Serializable
data class EntryFilter(
    val text: String = "",
    val types: Set<EntryType> = emptySet(),
    val tags: Set<String> = emptySet(),
    val folderId: String? = null,
    val includeSubfolders: Boolean = true,
    val favorites: Boolean = false,
    val weak: Boolean = false,
    val noTotp: Boolean = false,
    val expiringSoon: Boolean = false,
    val sort: SortOrder = SortOrder.NAME,
)

@Serializable
enum class SortOrder { NAME, UPDATED, CREATED, USED }

/** The fixed colours a tag may have; the colour is never the only sign of anything (R-1). */
@Serializable
enum class TagColor { RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, PURPLE, GRAY }
