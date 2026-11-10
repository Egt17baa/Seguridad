package app.seguridad.ui.screens

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.seguridad.android.copySensitive
import app.seguridad.core.PickerPlace
import app.seguridad.crypto.hexLower
import app.seguridad.tools.TOOLS_AWAY_WIPE_NOTE
import app.seguridad.tools.cipher.FileHashSession
import app.seguridad.tools.hashlab.HASH_LAB_ALGO_CHANGED
import app.seguridad.tools.hashlab.HASH_LAB_CANCEL
import app.seguridad.tools.hashlab.HASH_LAB_CODEC_INPUT
import app.seguridad.tools.hashlab.HASH_LAB_CODEC_INVALID
import app.seguridad.tools.hashlab.HASH_LAB_COPY
import app.seguridad.tools.hashlab.HASH_LAB_DECODE
import app.seguridad.tools.hashlab.HASH_LAB_ENCODE
import app.seguridad.tools.hashlab.HASH_LAB_EXPECTED_INVALID
import app.seguridad.tools.hashlab.HASH_LAB_EXPECTED_LABEL
import app.seguridad.tools.hashlab.HASH_LAB_FILE_FAILED
import app.seguridad.tools.hashlab.HASH_LAB_HMAC_INTRO
import app.seguridad.tools.hashlab.HASH_LAB_INTRO
import app.seguridad.tools.hashlab.HASH_LAB_KEY_AS_HEX
import app.seguridad.tools.hashlab.HASH_LAB_KEY_AS_TEXT
import app.seguridad.tools.hashlab.HASH_LAB_KEY_EMPTY
import app.seguridad.tools.hashlab.HASH_LAB_KEY_INVALID
import app.seguridad.tools.hashlab.HASH_LAB_KEY_LABEL
import app.seguridad.tools.hashlab.HASH_LAB_MATCH
import app.seguridad.tools.hashlab.HASH_LAB_MESSAGE_LABEL
import app.seguridad.tools.hashlab.HASH_LAB_NOT_PROTECTED
import app.seguridad.tools.hashlab.HASH_LAB_NOT_PROTECTED_TITLE
import app.seguridad.tools.hashlab.HASH_LAB_NOT_TEXT
import app.seguridad.tools.hashlab.HASH_LAB_NO_MATCH
import app.seguridad.tools.hashlab.HASH_LAB_PICK_FILE
import app.seguridad.tools.hashlab.HASH_LAB_TAB_CODEC
import app.seguridad.tools.hashlab.HASH_LAB_TAB_FILE
import app.seguridad.tools.hashlab.HASH_LAB_TAB_HMAC
import app.seguridad.tools.hashlab.HASH_LAB_TAB_TEXT
import app.seguridad.tools.hashlab.HASH_LAB_TEXT_LABEL
import app.seguridad.tools.hashlab.HASH_LAB_TITLE
import app.seguridad.tools.hashlab.HASH_LAB_TOO_LONG
import app.seguridad.tools.hashlab.HashAlgo
import app.seguridad.tools.hashlab.HashLabSession
import app.seguridad.tools.hashlab.MAX_CODEC_UI_CHARS
import app.seguridad.tools.hashlab.MAX_KEY_CHARS
import app.seguridad.tools.hashlab.MAX_TEXT_CHARS
import app.seguridad.tools.hashlab.base64Decode
import app.seguridad.tools.hashlab.base64Encode
import app.seguridad.tools.hashlab.digest
import app.seguridad.tools.hashlab.hashLabBusy
import app.seguridad.tools.hashlab.hashLabRecalc
import app.seguridad.tools.hashlab.hashLabWrongSize
import app.seguridad.tools.hashlab.hexDecode
import app.seguridad.tools.hashlab.hmacSha256
import app.seguridad.tools.hashlab.parseExpected
import app.seguridad.tools.hashlab.sameDigest
import app.seguridad.tools.hashlab.textBytes
import app.seguridad.tools.hashlab.utf8OrNull
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.PositiveColor
import app.seguridad.ui.theme.SECRET_TEXT_KEYBOARD
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.VaultStore
import app.seguridad.vault.sanitizeReceivedName
import java.io.FilterInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LabTab(val label: String) {
    TEXT(HASH_LAB_TAB_TEXT),
    FILE(HASH_LAB_TAB_FILE),
    HMAC(HASH_LAB_TAB_HMAC),
    CODEC(HASH_LAB_TAB_CODEC),
}

/**
 * The «Laboratorio de hashes» for its navigation entry: what is typed ([HashLabSession]), the expected hash and the
 * file's result survive a rotation or another tab; none of it is saved state (never `rememberSaveable` nor a Bundle: the
 * HMAC key, the message and the decoded text can be secrets). The session's guard empties it by the «PIN y códigos» rule
 * (lock of the vault, another app, time away), and [onCleared] drops it when the screen is left. A file is read in
 * [viewModelScope] on IO: a new pick, «Cancelar» or leaving the screen stops the reading at its next 64 KiB, and the
 * result is shown only for the run started last ([FileHashSession], COV1-2 of the 1.3.0 audit). Changing the algorithm
 * never reads the file again by itself: the old result is hidden and the user asks for the new one.
 */
private class HashLabViewModel(
    private val appContext: Context,
    store: VaultStore,
    lockAfterMs: () -> Long,
    awayInOtherApp: StateFlow<Boolean>,
) : ViewModel() {
    val session = HashLabSession(ComposeCells, SystemClock.elapsedRealtime()) { job?.cancel(); job = null }
    var tab by mutableIntStateOf(0)
    val algo get() = session.algo
    var hmacKeyIsHex by mutableStateOf(false)
    var codecEncode by mutableStateOf(true)
    var codecHex by mutableStateOf(false)

    /** The field whose paste was refused for being too long (the field keeps what it had). */
    var refused by mutableStateOf<Field?>(null)
        private set

    val file get() = session.file

    /** The algorithm of the result [file] holds, how much of the file was read and its size (-1: unknown). */
    val fileAlgo get() = session.fileAlgo
    val readBytes get() = session.readBytes
    val totalBytes get() = session.totalBytes

    /** A file was chosen and not cancelled nor wiped: «Calcular otra vez» can read it again. */
    val hasFile get() = session.source != null

    private var job: Job? = null

    /** The run of the file picked last (main thread only); a wipe moves the session's on, so a cancel after it is refused. */
    private var run = 0L

    init {
        watchAway(session.guard, store, lockAfterMs, awayInOtherApp)
    }

    enum class Field { TEXT, EXPECTED, KEY, MESSAGE, CODEC }

    /** Sets a field from [value] unless it is longer than [max] characters; a refused paste is said, not cut. Typing is use of the screen. */
    fun limited(field: Field, value: String, max: Int, set: (String) -> Unit) {
        session.guard.typed()
        if (value.length <= max) {
            if (refused == field) refused = null
            set(value)
        } else {
            refused = field
        }
    }

    fun chooseTab(i: Int) {
        tab = i
        refused = null
    }

    /** Only the choice: a file already hashed is not read again until «Calcular otra vez». */
    fun chooseAlgo(a: HashAlgo) {
        session.algo = a
    }

    fun rehash() {
        (session.source as? Uri)?.let { pick(it) }
    }

    fun pick(uri: Uri) {
        job?.cancel()
        session.source = uri
        val chosen = algo
        val mine = session.startFile()
        run = mine
        job = viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { displayName(appContext, uri) }.getOrDefault("archivo") }
            val size = withContext(Dispatchers.IO) { runCatching { sizeOf(appContext, uri) }.getOrDefault(-1L) }
            file.named(mine, sanitizeReceivedName(name))
            session.sized(mine, size)
            val hash = try {
                withContext(Dispatchers.IO) {
                    val input = appContext.contentResolver.openInputStream(uri) ?: throw java.io.IOException("no stream")
                    input.use { raw ->
                        val counting = CountingInputStream(raw.buffered())
                        var shown = 0L
                        hexLower(
                            digest(chosen, counting) {
                                ensureActive()
                                // Progress: only the run started last writes it, and about once per MiB.
                                val n = counting.count
                                if (n - shown >= PROGRESS_STEP) {
                                    shown = n
                                    session.progress(mine, n)
                                }
                            },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            file.finish(mine, hash)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        session.source = null
        file.cancelled(run)
    }

    override fun onCleared() {
        job?.cancel()
        session.wipe()
    }
}

private const val PROGRESS_STEP = 1L shl 20

/** Counts the bytes read, for the progress; the one writer is the reading thread. */
private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    @Volatile var count = 0L
        private set

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) count += n
        return n
    }
}

private fun sizeOf(context: Context, uri: Uri): Long =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
    } ?: -1L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HashLabScreen(store: VaultStore, lockAfterMs: () -> Long, awayInOtherApp: StateFlow<Boolean>, onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm = viewModel { HashLabViewModel(appContext, store, lockAfterMs, awayInOtherApp) }
    val s = vm.session
    GuardEffects(s.guard, lockAfterMs)
    var pickerError by remember { mutableStateOf<String?>(null) }
    val pick = rememberDocumentPicker(ActivityResultContracts.OpenDocument(), place = PickerPlace.HASH_FILE) { uri ->
        if (uri != null) vm.pick(uri)
    }
    ToolColumn(HASH_LAB_TITLE, onBack, s.guard) {
        Text(HASH_LAB_INTRO, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabTab.entries.forEachIndexed { i, t ->
                FilterChip(selected = vm.tab == i, onClick = { vm.chooseTab(i) }, label = { Text(t.label) })
            }
        }
        val tab = LabTab.entries[vm.tab]
        if (tab == LabTab.TEXT || tab == LabTab.FILE) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HashAlgo.entries.forEach { a ->
                    FilterChip(selected = vm.algo == a, onClick = { vm.chooseAlgo(a) }, label = { Text(a.label) })
                }
            }
        }
        val onExpected = { v: String -> vm.limited(HashLabViewModel.Field.EXPECTED, v, EXPECTED_FIELD_CHARS) { s.expected = it } }
        val expectedRefused = vm.refused == HashLabViewModel.Field.EXPECTED
        when (tab) {
            LabTab.TEXT -> {
                OutlinedTextField(
                    value = s.text,
                    onValueChange = { v -> vm.limited(HashLabViewModel.Field.TEXT, v, MAX_TEXT_CHARS) { s.text = it } },
                    label = { Text(HASH_LAB_TEXT_LABEL) },
                    minLines = 3,
                    keyboardOptions = SECRET_TEXT_KEYBOARD,
                    modifier = Modifier.fillMaxWidth(),
                )
                TooLongNote(vm.refused == HashLabViewModel.Field.TEXT)
                val bytes = remember(s.text, vm.algo) { digest(vm.algo, textBytes(s.text)) }
                ResultAndCheck(context, vm.algo.label, bytes, s.expected, false, expectedRefused, onExpected)
            }
            LabTab.FILE -> {
                val f = vm.file
                OutlinedButton(
                    enabled = !f.busy,
                    onClick = { pickerError = pick.launchOrError(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(f.name ?: HASH_LAB_PICK_FILE) }
                pickerError?.let { Text(it, color = NegativeColor) }
                if (f.busy) {
                    val done = Formatter.formatShortFileSize(context, vm.readBytes)
                    val total = vm.totalBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(hashLabBusy(done, total), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::cancel) { Text(HASH_LAB_CANCEL) }
                    }
                }
                if (f.failed) Text(HASH_LAB_FILE_FAILED, color = NegativeColor)
                val h = f.hash
                if (h != null && vm.fileAlgo == vm.algo) {
                    ResultAndCheck(context, vm.fileAlgo.label, hexDecode(h) ?: ByteArray(0), s.expected, false, expectedRefused, onExpected)
                } else if (h != null) {
                    // Another algorithm was chosen after the file was hashed: the old result is not shown under the new name.
                    Text(HASH_LAB_ALGO_CHANGED, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(enabled = vm.hasFile && !f.busy, onClick = vm::rehash) { Text(hashLabRecalc(vm.algo.label)) }
                }
            }
            LabTab.HMAC -> {
                Text(HASH_LAB_HMAC_INTRO, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !vm.hmacKeyIsHex, onClick = { vm.hmacKeyIsHex = false }, label = { Text(HASH_LAB_KEY_AS_TEXT) })
                    FilterChip(selected = vm.hmacKeyIsHex, onClick = { vm.hmacKeyIsHex = true }, label = { Text(HASH_LAB_KEY_AS_HEX) })
                }
                PasswordField(
                    s.hmacKey,
                    { v -> vm.limited(HashLabViewModel.Field.KEY, v, MAX_KEY_CHARS) { s.hmacKey = it } },
                    label = HASH_LAB_KEY_LABEL,
                )
                TooLongNote(vm.refused == HashLabViewModel.Field.KEY)
                OutlinedTextField(
                    value = s.hmacMessage,
                    onValueChange = { v -> vm.limited(HashLabViewModel.Field.MESSAGE, v, MAX_TEXT_CHARS) { s.hmacMessage = it } },
                    label = { Text(HASH_LAB_MESSAGE_LABEL) },
                    minLines = 3,
                    keyboardOptions = SECRET_TEXT_KEYBOARD,
                    modifier = Modifier.fillMaxWidth(),
                )
                TooLongNote(vm.refused == HashLabViewModel.Field.MESSAGE)
                val key = remember(s.hmacKey, vm.hmacKeyIsHex) {
                    if (vm.hmacKeyIsHex) hexDecode(s.hmacKey) else textBytes(s.hmacKey)
                }
                when {
                    s.hmacKey.isEmpty() -> Text(HASH_LAB_KEY_EMPTY, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    key == null -> Text(HASH_LAB_KEY_INVALID, color = NegativeColor)
                    else -> {
                        val mac = remember(key, s.hmacMessage) { hmacSha256(key, textBytes(s.hmacMessage)) }
                        ResultAndCheck(context, "HMAC-SHA256", mac, s.expected, true, expectedRefused, onExpected)
                    }
                }
            }
            LabTab.CODEC -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vm.codecEncode, onClick = { vm.codecEncode = true }, label = { Text(HASH_LAB_ENCODE) })
                    FilterChip(selected = !vm.codecEncode, onClick = { vm.codecEncode = false }, label = { Text(HASH_LAB_DECODE) })
                    FilterChip(selected = !vm.codecHex, onClick = { vm.codecHex = false }, label = { Text("Base64") })
                    FilterChip(selected = vm.codecHex, onClick = { vm.codecHex = true }, label = { Text("Hex") })
                }
                OutlinedTextField(
                    value = s.codecInput,
                    onValueChange = { v -> vm.limited(HashLabViewModel.Field.CODEC, v, MAX_CODEC_UI_CHARS) { s.codecInput = it } },
                    label = { Text(HASH_LAB_CODEC_INPUT) },
                    minLines = 3,
                    keyboardOptions = SECRET_TEXT_KEYBOARD,
                    modifier = Modifier.fillMaxWidth(),
                )
                TooLongNote(vm.refused == HashLabViewModel.Field.CODEC)
                if (s.codecInput.isNotEmpty()) {
                    val result = remember(s.codecInput, vm.codecEncode, vm.codecHex) { codec(s.codecInput, vm.codecEncode, vm.codecHex) }
                    if (result == null) {
                        Text(HASH_LAB_CODEC_INVALID, color = NegativeColor)
                    } else {
                        if (result.second) Text(HASH_LAB_NOT_TEXT, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutputCard(context, if (vm.codecEncode) (if (vm.codecHex) "Hex" else "Base64") else HASH_LAB_DECODE, result.first)
                    }
                }
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(HASH_LAB_NOT_PROTECTED_TITLE, style = MaterialTheme.typography.labelMedium)
                Text(HASH_LAB_NOT_PROTECTED, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(TOOLS_AWAY_WIPE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A hash pasted from a web page is far shorter than this; the cap keeps a huge paste out of the field. */
private const val EXPECTED_FIELD_CHARS = 1024

/**
 * The text and whether it is not UTF-8 (shown as hex), or null if [input] is not valid in that format. Encoding takes
 * the text as UTF-8; decoding takes Base64 or hex.
 */
private fun codec(input: String, encode: Boolean, hex: Boolean): Pair<String, Boolean>? {
    if (encode) {
        val bytes = textBytes(input)
        return (if (hex) hexLower(bytes) else base64Encode(bytes)) to false
    }
    val bytes = (if (hex) hexDecode(input) else base64Decode(input)) ?: return null
    val asText = utf8OrNull(bytes)
    return if (asText != null) asText to false else hexLower(bytes) to true
}

@Composable
private fun TooLongNote(show: Boolean) {
    if (show) Text(HASH_LAB_TOO_LONG, color = NegativeColor)
}

@Composable
private fun OutputCard(context: Context, label: String, value: String) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            SelectionContainer { Text(value, fontFamily = FontFamily.Monospace) }
            FilledTonalButton(onClick = { copySensitive(context, value) }) { Text(HASH_LAB_COPY) }
        }
    }
}

/**
 * The result in lowercase hex (how download pages publish it), the «huella esperada» field, [expectedRefused] under it
 * and its verdict. A hash of the other size says so instead of «No coincide» ([hashLabWrongSize]).
 */
@Composable
private fun ResultAndCheck(
    context: Context,
    label: String,
    result: ByteArray,
    expected: String,
    hmac: Boolean,
    expectedRefused: Boolean,
    onExpected: (String) -> Unit,
) {
    OutputCard(context, label, hexLower(result))
    OutlinedTextField(
        value = expected,
        onValueChange = onExpected,
        label = { Text(HASH_LAB_EXPECTED_LABEL) },
        keyboardOptions = SECRET_TEXT_KEYBOARD,
        modifier = Modifier.fillMaxWidth(),
    )
    TooLongNote(expectedRefused)
    if (expected.isNotBlank()) {
        val want = remember(expected) { parseExpected(expected) }
        when {
            want == null -> Text(HASH_LAB_EXPECTED_INVALID, color = NegativeColor)
            want.size != result.size -> Text(hashLabWrongSize(want.size, hmac), color = NegativeColor)
            sameDigest(want, result) -> Text(HASH_LAB_MATCH, color = PositiveColor)
            else -> Text(HASH_LAB_NO_MATCH, color = NegativeColor)
        }
    }
}
