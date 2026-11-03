package app.seguridad.vault

import app.seguridad.health.WEAK_BITS
import app.seguridad.health.commonPasswords
import app.seguridad.health.estimateBits
import app.seguridad.health.strengthForm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale

/**
 * Fewest characters of a master password. A NEW master ([newMasterProblem]) needs this many both in UTF-16 units, as
 * the text field counts them, and in code points of its NFC form without format characters, so five emojis or five
 * letters typed decomposed are not ten (F3). The import dialog only counts UTF-16 units, as before.
 */
const val MIN_MASTER = 10

/** What the screens show while the new master has fewer than [MIN_MASTER] characters. */
const val MASTER_TOO_SHORT = "Mínimo $MIN_MASTER caracteres"

/** What the screens show when the new master is easy to guess. */
const val MASTER_TOO_WEAK =
    "Esta contraseña es fácil de adivinar. Usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una)."

/** The EFF list of `assets/`: the Generator's phrases, and one of [MASTER_WORD_LISTS]. */
const val EFF_WORD_LIST = "eff_large_wordlist.txt"

/** The BIP-39 English list of `assets/`: one of [MASTER_WORD_LISTS], and the phrases of «Repartir un secreto». */
const val BIP39_ENGLISH_LIST = "bip39_english.txt"

/** The word lists of `assets/` that the rule for a new master knows, the ones the app already reads (EFF, BIP-39 English and Spanish). */
val MASTER_WORD_LISTS = listOf(EFF_WORD_LIST, BIP39_ENGLISH_LIST, "bip39_spanish.txt")

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * What is left of [candidate] when only its letters count: lower case ([Locale.ROOT], so the phone's language does not
 * matter), without accents (NFD with the combining marks removed, so «ñ» is «n» and «Á» is «a») and with no digits, symbols
 * or spaces. «Contraseña2024!» is «contrasena»; «Ábaco-12345» is «abaco». It is the form in which [masterWords] holds the
 * lists, and the one compared against them.
 *
 * It makes copies of the text (lower case, decomposed, filtered) that cannot be zeroed, like the one [estimateBits] makes
 * for the same typed password: the password is already an immutable `String` in the state of the text field.
 */
fun masterCore(candidate: String): String {
    val folded = COMBINING_MARKS.replace(Normalizer.normalize(candidate.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
    val core = StringBuilder(folded.length)
    var i = 0
    while (i < folded.length) {
        val cp = folded.codePointAt(i)
        if (Character.isLetter(cp)) core.appendCodePoint(cp)
        i += Character.charCount(cp)
    }
    return core.toString()
}

/**
 * The known words against which a new master is compared: every word of [lists] and the most used passwords of «Salud»,
 * each passed through [masterCore], with the empty ones left out (a list line or a common password of digits only has no
 * letters, and it would refuse every password that has none). A word with a hyphen («drop-down») is held without it.
 */
fun masterWords(lists: List<List<String>>): Set<String> {
    val words = HashSet<String>()
    for (list in lists) for (word in list) masterCore(word).takeIf { it.isNotEmpty() }?.let(words::add)
    for (common in commonPasswords()) masterCore(common).takeIf { it.isNotEmpty() }?.let(words::add)
    return words
}

/**
 * Reads [MASTER_WORD_LISTS] with [open] (on Android, `assets.open`) and returns [masterWords] of them. Blocking: call it off
 * the main thread. If any list cannot be read, for whatever reason, it returns an empty set, not half of the lists: the rule
 * is then the length and the estimate, because leaving someone unable to create the vault (or a crash in that screen) would
 * be worse than missing the word check. It is plain blocking code, so catching `Exception` swallows no cancellation.
 */
fun loadMasterWords(open: (String) -> InputStream): Set<String> = try {
    masterWords(MASTER_WORD_LISTS.map { name -> open(name).bufferedReader(Charsets.UTF_8).use { it.readLines() } })
} catch (e: Exception) {
    emptySet()
}

/**
 * The word lists of `assets/` that the screens need, read with [open] on [io] (never the caller's thread) and kept for
 * the life of the process (one instance lives in AppContainer, F6): the known words of the rule of a new master
 * ([master]) and the EFF list of the Generator's phrases ([eff]). They are not secret. A read that fails is not kept,
 * so the next screen that asks reads again; a caller cancelled while they load (its screen closed) keeps nothing
 * either. The EFF list is read twice per process at most: once among the master's lists and once for the Generator.
 */
class WordLists(private val open: (String) -> InputStream, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val mutex = Mutex()
    private var known: Set<String>? = null
    private var effList: List<String>? = null
    private var bip39List: List<String>? = null

    /** [loadMasterWords]: an empty set if a list cannot be read, which is not kept. */
    suspend fun master(): Set<String> = mutex.withLock {
        known ?: withContext(io) { loadMasterWords(open) }.also { if (it.isNotEmpty()) known = it }
    }

    /** The EFF list without blank lines, or null if it cannot be read (not kept: the next screen tries again). */
    suspend fun eff(): List<String>? = mutex.withLock {
        effList ?: withContext(io) { readList(EFF_WORD_LIST) }?.also { effList = it }
    }

    /** The BIP-39 English list without blank lines (for «Repartir un secreto»), or null if it cannot be read (not kept). */
    suspend fun bip39English(): List<String>? = mutex.withLock {
        bip39List ?: withContext(io) { readList(BIP39_ENGLISH_LIST) }?.also { bip39List = it }
    }

    /** Blocking, like [loadMasterWords]: catching `Exception` here swallows no cancellation. */
    private fun readList(name: String): List<String>? = try {
        open(name).bufferedReader(Charsets.UTF_8).use { it.readLines() }
            .filter { it.isNotBlank() }
            .ifEmpty { null }
    } catch (e: Exception) {
        null
    }
}

/**
 * Why [candidate] does not do as a NEW master password, or null if it does (CRY-A01): first [MASTER_TOO_SHORT] (fewer
 * than [MIN_MASTER] UTF-16 units, or fewer than [MIN_MASTER] code points once in NFC and without format characters,
 * F3), then [MASTER_TOO_WEAK], which is the answer to two different things, in this order: the strength is the one of
 * «Salud» ([estimateBits] against [WEAK_BITS]) and, if [words] is given, the password is ONE known word.
 *
 * [words] is what [loadMasterWords] returns: the cores ([masterCore]) of the common list of «Salud» and of the EFF,
 * BIP-39 English and BIP-39 Spanish lists. A candidate whose core is in it is a single known word with capitals, accents,
 * digits or symbols around it («contraseña123», «Password1!», «Ábaco12345»), which the estimate alone lets through.
 * Without [words] (the default, or an empty set because the lists could not be read) the rule is the length and the
 * estimate, as it was.
 *
 * Only for a password that is being chosen: creating the vault and changing the master. Opening the vault, the
 * fingerprint and importing a copy never come through here, so no vault or copy that exists stops opening, and neither
 * does the password of a copy made with a weaker master. The rule is not in [VaultStore] either: the tests and the test
 * data create vaults with throwaway masters.
 *
 * It is an estimate and a list of words, not a dictionary: it does not know other words, names, places or personal data,
 * so it removes the obvious cases and nothing more. The estimate refuses a short password, a run of equal characters, a
 * sequence («1234567890», «abcdefghij», «qwertyuiop») and the exact words of the common list of «Salud»; since F3 also
 * when they are emojis, letters typed decomposed or digits with invisible characters between them (it counts the UTF-16
 * units and the code points of the NFC form without format characters, and keeps the weaker result). «Barcelona1»
 * gives about 59 bits and passes, because «barcelona» is in no list. A word with an enye or an accent gets a large bonus
 * in the estimate (one character outside ASCII adds 100 symbols to the pool of the whole password), which is why the
 * word check exists. Its limit: it is about the WHOLE core being one word. Two words together («abandon-ability») are not
 * «una sola palabra» and pass if the estimate reaches, and so does any word that is in none of the lists. The rule does
 * not guarantee that a master is strong.
 *
 * What the Generator proposes with its usual options passes in the tests: a phrase of 5 words (at least 15 letters, and no
 * known word has more than 10: «contrasena», from «Salud»; the lists have 9 at most), with any separator and list, and a
 * password of 16 characters. A password of digits only is sometimes refused up to about 24 characters (a sequence such as
 * «123» counts as one character). With [words], a password of the Generator with the four classes of characters is refused
 * when its few letters (3 to 5) are a word of the lists: about 1 time in 3,900 at 12 characters, 1 in 61,000 at 16 and 1 in
 * 1.1 million at 20 (the default length); asking the Generator for another one solves it.
 */
fun newMasterProblem(candidate: String, words: Set<String> = emptySet()): String? = when {
    candidate.length < MIN_MASTER || strengthForm(candidate).let { it.codePointCount(0, it.length) } < MIN_MASTER ->
        MASTER_TOO_SHORT
    estimateBits(candidate) < WEAK_BITS -> MASTER_TOO_WEAK
    words.isNotEmpty() && masterCore(candidate) in words -> MASTER_TOO_WEAK
    else -> null
}
