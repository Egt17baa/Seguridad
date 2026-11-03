package app.seguridad.ui.screens

import android.content.Context
import android.net.Uri
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
import androidx.compose.runtime.mutableLongStateOf
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
import app.seguridad.tools.hashlab.MAX_CODEC_UI_CHARS
import app.seguridad.tools.hashlab.MAX_KEY_CHARS
import app.seguridad.tools.hashlab.MAX_TEXT_CHARS
import app.seguridad.tools.hashlab.base64Decode
import app.seguridad.tools.hashlab.base64Encode
import app.seguridad.tools.hashlab.digest
import app.seguridad.tools.hashlab.hashLabBusy
import app.seguridad.tools.hashlab.hexDecode
import app.seguridad.tools.hashlab.hexEncode
import app.seguridad.tools.hashlab.hmacSha256
import app.seguridad.tools.hashlab.parseExpected
import app.seguridad.tools.hashlab.sameDigest
import app.seguridad.tools.hashlab.textBytes
import app.seguridad.tools.hashlab.utf8OrNull
import app.seguridad.tools.cipher.FileHashSession
import app.seguridad.ui.theme.NegativeColor
import app.seguridad.ui.theme.PasswordField
import app.seguridad.ui.theme.PositiveColor
import app.seguridad.ui.theme.SectionCard
import app.seguridad.vault.sanitizeReceivedName
import java.io.FilterInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LabTab(val label: String) {
    TEXT(HASH_LAB_TAB_TEXT),
    FILE(HASH_LAB_TAB_FILE),
    HMAC(HASH_LAB_TAB_HMAC),
    CODEC(HASH_LAB_TAB_CODEC),
}

/**
 * The «Laboratorio de hashes» for its navigation entry: what is typed, the expected hash and the file's result survive a
 * rotation or another tab; none of it is saved state (never `rememberSaveable` nor a Bundle: the HMAC key, the message
 * and the decoded text can be secrets), and [onCleared] drops it when the screen is left. A file is read in
 * [viewModelScope] on IO: a new pick, a new algorithm, «Cancelar» or leaving the screen stops the reading at its next
 * 64 KiB, and the result is shown only for the run started last ([FileHashSession], COV1-2 of the 1.3.0 audit).
 */
private class HashLabViewModel(private val appContext: Context) : ViewModel() {
    var tab by mutableIntStateOf(0)
    var algo by mutableStateOf(HashAlgo.SHA256)
        private set
    var text by mutableStateOf("")
    var expected by mutableStateOf("")
    var hmacKey by mutableStateOf("")
    var hmacKeyIsHex by mutableStateOf(false)
    var hmacMessage by mutableStateOf("")
    var codecInput by mutableStateOf("")
    var codecEncode by mutableStateOf(true)
    var codecHex by mutableStateOf(false)

    /** A paste over the limit of a field was refused (the field keeps what it had). */
    var tooLong by mutableStateOf(false)
        private set

    val file = FileHashSession(ComposeCells)
    var readBytes by mutableLongStateOf(0L)
        private set
    var totalBytes by mutableLongStateOf(-1L)
        private set

    private var job: Job? = null
    private var run = 0L
    private var lastUri: Uri? = null

    /** Sets a field from [value] unless it is longer than [max] characters; a refused paste is said, not cut. */
    fun limited(value: String, max: Int, set: (String) -> Unit) {
        if (value.length <= max) {
            tooLong = false
            set(value)
        } else {
            tooLong = true
        }
    }

    fun chooseTab(i: Int) {
        tab = i
        tooLong = false
    }

    /** A new algorithm hashes the chosen file again. */
    fun chooseAlgo(a: HashAlgo) {
        if (a == algo) return
        algo = a
        lastUri?.let { pick(it) }
    }

    fun pick(uri: Uri) {
        job?.cancel()
        lastUri = uri
        val chosen = algo
        val mine = file.start()
        run = mine
        readBytes = 0
        totalBytes = -1
        job = viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { displayName(appContext, uri) }.getOrDefault("archivo") }
            val size = withContext(Dispatchers.IO) { runCatching { sizeOf(appContext, uri) }.getOrDefault(-1L) }
            file.named(mine, sanitizeReceivedName(name))
            totalBytes = size
            val hash = try {
                withContext(Dispatchers.IO) {
                    val input = appContext.contentResolver.openInputStream(uri) ?: throw java.io.IOException("no stream")
                    input.use { raw ->
                        val counting = CountingInputStream(raw.buffered())
                        hexEncode(digest(chosen, counting) { ensureActive(); readBytes = counting.count })
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
        lastUri = null
        file.cancelled(run)
    }

    override fun onCleared() {
        job?.cancel()
        lastUri = null
        text = ""
        expected = ""
        hmacKey = ""
        hmacMessage = ""
        codecInput = ""
    }
}

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
fun HashLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val vm = viewModel { HashLabViewModel(appContext) }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val pick = rememberDocumentPicker(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }
    ToolColumn(HASH_LAB_TITLE, onBack) {
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
        when (tab) {
            LabTab.TEXT -> {
                OutlinedTextField(
                    value = vm.text,
                    onValueChange = { v -> vm.limited(v, MAX_TEXT_CHARS) { vm.text = it } },
                    label = { Text(HASH_LAB_TEXT_LABEL) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                TooLongNote(vm.tooLong)
                val bytes = remember(vm.text, vm.algo) { digest(vm.algo, textBytes(vm.text)) }
                ResultAndCheck(context, vm.algo.label, bytes, vm.expected) { v -> vm.limited(v, EXPECTED_FIELD_CHARS) { vm.expected = it } }
            }
            LabTab.FILE -> {
                val s = vm.file
                OutlinedButton(
                    enabled = !s.busy,
                    onClick = { pickerError = pick.launchOrError(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.name ?: HASH_LAB_PICK_FILE) }
                pickerError?.let { Text(it, color = NegativeColor) }
                if (s.busy) {
                    val done = Formatter.formatShortFileSize(context, vm.readBytes)
                    val total = vm.totalBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(hashLabBusy(done, total), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::cancel) { Text(HASH_LAB_CANCEL) }
                    }
                }
                if (s.failed) Text(HASH_LAB_FILE_FAILED, color = NegativeColor)
                s.hash?.let { h ->
                    ResultAndCheck(context, vm.algo.label, hexDecode(h) ?: ByteArray(0), vm.expected) { v ->
                        vm.limited(v, EXPECTED_FIELD_CHARS) { vm.expected = it }
                    }
                }
            }
            LabTab.HMAC -> {
                Text(HASH_LAB_HMAC_INTRO, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !vm.hmacKeyIsHex, onClick = { vm.hmacKeyIsHex = false }, label = { Text(HASH_LAB_KEY_AS_TEXT) })
                    FilterChip(selected = vm.hmacKeyIsHex, onClick = { vm.hmacKeyIsHex = true }, label = { Text(HASH_LAB_KEY_AS_HEX) })
                }
                PasswordField(
                    vm.hmacKey,
                    { v -> vm.limited(v, MAX_KEY_CHARS) { vm.hmacKey = it } },
                    label = HASH_LAB_KEY_LABEL,
                )
                OutlinedTextField(
                    value = vm.hmacMessage,
                    onValueChange = { v -> vm.limited(v, MAX_TEXT_CHARS) { vm.hmacMessage = it } },
                    label = { Text(HASH_LAB_MESSAGE_LABEL) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                TooLongNote(vm.tooLong)
                val key = remember(vm.hmacKey, vm.hmacKeyIsHex) {
                    if (vm.hmacKeyIsHex) hexDecode(vm.hmacKey) else textBytes(vm.hmacKey)
                }
                when {
                    vm.hmacKey.isEmpty() -> Text(HASH_LAB_KEY_EMPTY, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    key == null -> Text(HASH_LAB_KEY_INVALID, color = NegativeColor)
                    else -> {
                        val mac = remember(key, vm.hmacMessage) { hmacSha256(key, textBytes(vm.hmacMessage)) }
                        ResultAndCheck(context, "HMAC-SHA256", mac, vm.expected) { v -> vm.limited(v, EXPECTED_FIELD_CHARS) { vm.expected = it } }
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
                    value = vm.codecInput,
                    onValueChange = { v -> vm.limited(v, MAX_CODEC_UI_CHARS) { vm.codecInput = it } },
                    label = { Text(HASH_LAB_CODEC_INPUT) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                TooLongNote(vm.tooLong)
                if (vm.codecInput.isNotEmpty()) {
                    val result = remember(vm.codecInput, vm.codecEncode, vm.codecHex) { codec(vm.codecInput, vm.codecEncode, vm.codecHex) }
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
        return (if (hex) hexEncode(bytes) else base64Encode(bytes)) to false
    }
    val bytes = (if (hex) hexDecode(input) else base64Decode(input)) ?: return null
    val asText = utf8OrNull(bytes)
    return if (asText != null) asText to false else hexEncode(bytes) to true
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

/** The result in lowercase hex (how download pages publish it), the «huella esperada» field and its verdict. */
@Composable
private fun ResultAndCheck(context: Context, label: String, result: ByteArray, expected: String, onExpected: (String) -> Unit) {
    OutputCard(context, label, hexEncode(result))
    OutlinedTextField(
        value = expected,
        onValueChange = onExpected,
        label = { Text(HASH_LAB_EXPECTED_LABEL) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (expected.isNotBlank()) {
        val want = remember(expected) { parseExpected(expected) }
        when {
            want == null -> Text(HASH_LAB_EXPECTED_INVALID, color = NegativeColor)
            sameDigest(want, result) -> Text(HASH_LAB_MATCH, color = PositiveColor)
            else -> Text(HASH_LAB_NO_MATCH, color = NegativeColor)
        }
    }
}
