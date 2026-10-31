package app.seguridad.vault

import app.seguridad.crypto.MigrationResult
import app.seguridad.crypto.TotpSecret
import app.seguridad.tools.codes.CellFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// The entry editor keeps a separate field for the LOGIN password and for the SEED's BIP-39 passphrase. With one shared
// field (1.0.0), a password generated under «Acceso» became the passphrase after switching to «Frase semilla», and a
// passphrase creates a different wallet: saved by mistake, it would point the user at an empty one.

/** What the field for [type] starts with: the saved password only when [existing] is of that same type. */
internal fun initialPassword(existing: VaultEntry?, type: EntryType): String =
    if (existing != null && existing.type == type) existing.password else ""

/** The `password` saved for an entry of [type]: the field of that type only, and nothing for NOTE and TOTP. */
internal fun passwordToSave(type: EntryType, loginPassword: String, seedPassphrase: String): String = when (type) {
    EntryType.LOGIN -> loginPassword
    EntryType.SEED -> seedPassphrase
    EntryType.NOTE, EntryType.TOTP, EntryType.IDENTITY, EntryType.CONTACT -> ""
}

/** A Google Authenticator export waiting for the user's confirmation: the [entries] it would add and what it leaves out. */
data class PendingMigration(val result: MigrationResult, val entries: List<VaultEntry>, val leftOut: List<String>)

/**
 * The entry editor's state (M6 and concern 3 of the CR review), held by the screen's ViewModel for its navigation entry:
 * a rotation keeps every field (both passwords, the seed phrase and the 2FA secret included), the pending Google
 * Authenticator import, the texts on screen and one id for a new entry ([entryId], so a save cut short and tapped again
 * never makes a second entry). Never in saved state: the death of the process loses it, as ruled.
 *
 * It goes ([clear]) when the vault locks after the form was loaded ([vaultStateIs], from a collector not tied to the
 * lifecycle, so also in the background), when the screen is left without a configuration change and when the ViewModel
 * is cleared; [finish] on save and back, for good. Once cleared, the form starts again from the vault's entry
 * ([load]), never from what was typed. The QR inspector's text is applied once per session ([takeHandOver]).
 */
class EntryEditSession(cells: CellFactory, val id: String?, private val initialType: EntryType, private val newId: String) {
    val existing = cells.cell<VaultEntry?>(null)
    val type = cells.cell(initialType)
    val title = cells.cell("")
    val username = cells.cell("")
    val loginPassword = cells.cell("")
    val seedPassphrase = cells.cell("")
    val url = cells.cell("")
    val notes = cells.cell("")
    val seed = cells.cell("")
    val totp = cells.cell(TotpSecret(""))
    val favorite = cells.cell(false)
    val tagsText = cells.cell("")
    val error = cells.cell<String?>(null)
    val info = cells.cell<String?>(null)
    val saving = cells.cell(false)
    val migration = cells.cell<PendingMigration?>(null)

    /** Saved, or left with «Atrás»: the screen goes back, and nothing is loaded again. */
    val finished = cells.cell(false)

    /** The fields hold the vault's entry (or a new one's defaults) and what was typed since. */
    @Volatile var loaded = false
        private set

    @Volatile private var handedOver = false

    /** The id the entry is saved with: the existing one's, or one fixed for the whole session. */
    val entryId: String get() = existing.value?.id ?: id ?: newId

    /** Fills the fields from [entries] (the open vault) the first time, and again only after [clear]. */
    fun load(entries: List<VaultEntry>) {
        if (loaded || finished.value) return
        val e = id?.let { i -> entries.firstOrNull { it.id == i } }
        existing.value = e
        type.value = e?.type ?: initialType
        title.value = e?.title.orEmpty()
        username.value = e?.username.orEmpty()
        loginPassword.value = initialPassword(e, EntryType.LOGIN)
        seedPassphrase.value = initialPassword(e, EntryType.SEED)
        url.value = e?.url.orEmpty()
        notes.value = e?.notes.orEmpty()
        seed.value = e?.seedWords.orEmpty()
        totp.value = e?.totp ?: TotpSecret("")
        favorite.value = e?.favorite ?: false
        tagsText.value = e?.tags.orEmpty().joinToString(", ")
        loaded = true
    }

    /** The vault's state: open loads the form (if it was cleared), any other state after it was loaded clears it. */
    fun vaultStateIs(unlocked: Boolean, entries: List<VaultEntry>) {
        if (unlocked) load(entries) else if (loaded) clear()
    }

    /** The QR inspector's [text] the first time it is offered in this session; null afterwards (a rotation, a new unlock). */
    fun takeHandOver(text: String?): String? {
        if (text == null || handedOver) return null
        handedOver = true
        return text
    }

    /** Everything typed, the pending import and the texts go; [load] fills the form again from the vault. */
    fun clear() {
        existing.value = null
        type.value = initialType
        title.value = ""
        username.value = ""
        loginPassword.value = ""
        seedPassphrase.value = ""
        url.value = ""
        notes.value = ""
        seed.value = ""
        totp.value = TotpSecret("")
        favorite.value = false
        tagsText.value = ""
        error.value = null
        info.value = null
        saving.value = false
        migration.value = null
        loaded = false
    }

    /** Saved or left with «Atrás»: cleared for good, and the screen goes back ([finished]). */
    fun finish() {
        clear()
        finished.value = true
    }
}

/**
 * The ViewModel's wiring of this session (m6 of the CR re-review), here so it is tested: follows the vault's [state] in
 * [scope] (the ViewModel's, not tied to the lifecycle), so a lock clears the form at once even with no composition (the
 * app in the background), and the next unlock loads it again from the vault.
 */
fun EntryEditSession.followVault(state: Flow<VaultState>, scope: CoroutineScope): Job = scope.launch {
    state.collect { vaultStateIs(it is VaultState.Unlocked, (it as? VaultState.Unlocked)?.entries.orEmpty()) }
}

/** The screen left the composition: a rotation ([changingConfigurations]) keeps what was typed, any other way out clears it. */
fun EntryEditSession.left(changingConfigurations: Boolean) {
    if (!changingConfigurations) clear()
}

/** The ViewModel's onCleared: the navigation entry is gone for good. */
fun EntryEditSession.cleared() = clear()
