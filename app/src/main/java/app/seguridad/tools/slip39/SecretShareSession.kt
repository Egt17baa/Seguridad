package app.seguridad.tools.slip39

import app.seguridad.tools.cipher.GuardedSession
import app.seguridad.tools.codes.CellFactory
import app.seguridad.tools.codes.PlainCells
import app.seguridad.tools.codes.getValue
import app.seguridad.tools.codes.setValue

/** One group of the form, as typed: the number of parts it needs and the number it has. */
data class GroupForm(val threshold: String, val count: String)

/** The form's starting scheme: one group, 2 parts needed of 3. */
const val DEFAULT_GROUP_THRESHOLD = "1"
val DEFAULT_GROUPS = listOf(GroupForm("2", "3"))

/**
 * What «Repartir un secreto» holds that is a secret: the phrase or hex to split, the passphrase, the parts made, the
 * parts typed to combine and the secret recovered (its bytes are zeroed when it is replaced or wiped). On Android it
 * lives in the screen's ViewModel, never in saved state; [guard] applies the «PIN y códigos» rule as in «Cifrar texto»,
 * and [wipe] stops a run going on ([onWipe]) and empties all of it. The scheme (groups and thresholds), the mode and
 * the kind of input are not secrets and stay.
 */
class SecretShareSession(
    cells: CellFactory = PlainCells,
    createdAt: Long = 0L,
    private val onWipe: () -> Unit = {},
) : GuardedSession(createdAt) {
    private val secretInputCell = cells.cell("")
    var secretInput: String by secretInputCell
    private val passphraseCell = cells.cell("")
    var passphrase: String by passphraseCell
    private val typedPartsCell = cells.cell("")
    var typedParts: String by typedPartsCell
    private val partsCell = cells.cell<List<List<String>>?>(null)
    var parts: List<List<String>>? by partsCell
    private val errorCell = cells.cell<String?>(null)
    var error: String? by errorCell
    private val busyCell = cells.cell(false)
    var busy: Boolean by busyCell

    private val recoveredCell = cells.cell<ByteArray?>(null)

    /** The recovered secret; setting a new one (or null) zeroes the bytes of the one before. */
    var recovered: ByteArray?
        get() = recoveredCell.value
        set(v) {
            val old = recoveredCell.value
            recoveredCell.value = v
            if (old != null && old !== v) old.fill(0)
        }

    private val groupThresholdCell = cells.cell(DEFAULT_GROUP_THRESHOLD)
    var groupThreshold: String by groupThresholdCell
    private val groupsCell = cells.cell(DEFAULT_GROUPS)
    var groups: List<GroupForm> by groupsCell

    override fun forget() {
        onWipe() // the screen stops the run going on, so its result is not shown
        recovered = null
        secretInput = ""
        passphrase = ""
        typedParts = ""
        parts = null
        error = null
        busy = false
    }
}
