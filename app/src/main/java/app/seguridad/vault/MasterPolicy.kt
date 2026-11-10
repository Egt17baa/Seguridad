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

/**
 * Longest password (UTF-16 units) the fields that open something with a pasted password take: the master of a copy in
 * «Comprobar una copia» and the password of an export in «Importar de otra app» (branch review B of 1.4.0, M-3). A
 * paste over it is refused and said, so a whole document pasted by mistake does not freeze the field; nothing is cut.
 * Creating the vault, changing the master, unlocking and «Importar copia» have no cap, so this one is far past any
 * password typed or pasted from a manager (64 Ki units, a 128 KiB string; Argon2id hashes the password, so its length
 * costs no memory there): «Comprobar una copia» then takes every master «Importar copia» takes in practice.
 */
const val PASSWORD_FIELD_MAX_CHARS = 65_536

/** Whether a password field of [PASSWORD_FIELD_MAX_CHARS] takes [value]. */
fun passwordFieldTakes(value: String): Boolean = value.length <= PASSWORD_FIELD_MAX_CHARS

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
 * What is left of [candidate] when only its letters count: in NFKC (so full-width or styled letters, «ｐａｓｓ» or «𝐩𝐚𝐬𝐬»,
 * are the plain ones, RT-3), lower case ([Locale.ROOT], so the phone's language does not matter), without accents (NFD
 * with the combining marks removed, so «ñ» is «n» and «Á» is «a») and with no digits, symbols or spaces.
 * «Contraseña2024!» is «contrasena»; «Ábaco-12345» is «abaco». It is the form in which [masterWords] holds the lists, and
 * the one compared against them (NFKC changes nothing in the lists, which are plain letters).
 *
 * It makes copies of the text (normalized, lower case, decomposed, filtered) that cannot be zeroed, like the one
 * [estimateBits] makes for the same typed password: the password is already an immutable `String` in the state of the
 * text field.
 */
fun masterCore(candidate: String): String {
    val compatible = Normalizer.normalize(candidate, Normalizer.Form.NFKC)
    val folded = COMBINING_MARKS.replace(Normalizer.normalize(compatible.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
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
 * Masters longer than this many code points (in NFKC) skip [isKnownWordInLeet]: that is far beyond one word with a few
 * symbols around it, and it keeps the check, which runs on every keystroke, bounded.
 */
const val LEET_MAX_CODE_POINTS = 128

/**
 * The letter a usual «leet» substitution stands for (RT-3): 0 o, 3 e, 4 and @ a, 5 and $ s, 7 and + t, 8 b, 6 and 9 g,
 * and 1, ! and | as [oneAs] (tried once as «i» and once as «l»). Since RT-5 (ruling R42) also what a Spanish keyboard
 * or a phone's long press puts at hand: ¡ i (a key of its own), € e (AltGr+E) and ø and Ø o. The candidate is in NFKC,
 * which keeps the case, so «Ø» is listed apart. «º» and «ª» need nothing here: NFKC already turns them into «o» and «a».
 * Not 2 for z: it would turn every year («2024») into letters. Null for any other character.
 */
private fun leetLetter(cp: Int, oneAs: Char): Char? = when (cp) {
    '0'.code, 'ø'.code, 'Ø'.code -> 'o'
    '3'.code, '€'.code -> 'e'
    '4'.code, '@'.code -> 'a'
    '5'.code, '$'.code -> 's'
    '7'.code, '+'.code -> 't'
    '8'.code -> 'b'
    '6'.code, '9'.code -> 'g'
    '1'.code, '!'.code, '|'.code -> oneAs
    '¡'.code -> 'i'
    else -> null
}

/**
 * Whether [candidate] is ONE known word of [words] written in leet (RT-3, owner's decision (h), 8-11-2026: «P4ssw0rd2024!»,
 * «C0ntr4s3ñ4!!», «Dr4g0n!2024»). In NFKC, it looks for a split `lead + middle + tail` where `lead` and `tail` have no
 * letters at all (digits, symbols, spaces, or nothing) and `middle`, with the substitutions of [leetLetter] turned back
 * into letters and the rest as [masterCore] leaves it, has its core in [words]. Since `lead` and `tail` have no letters,
 * `middle` always holds every letter of the password: only where it starts before the first letter and ends after the
 * last one is tried, so a year or a few symbols at the ends («2024!») stay out of the word and a random password, whose
 * letters are spread over it, very rarely gives a short core. A password without letters is never a word here (digits
 * only are left to the estimate).
 *
 * Bounded: nothing past [LEET_MAX_CODE_POINTS], and at most (first letter + 1) × (n − last letter) cores per reading of
 * «1», each built by appending to the previous one, a few thousand set lookups at worst; no regular expression.
 */
internal fun isKnownWordInLeet(candidate: String, words: Set<String>): Boolean {
    if (words.isEmpty()) return false
    val cps = Normalizer.normalize(candidate, Normalizer.Form.NFKC).codePoints().toArray()
    if (cps.size > LEET_MAX_CODE_POINTS) return false
    val first = cps.indexOfFirst { Character.isLetter(it) }
    if (first < 0) return false
    val last = cps.indexOfLast { Character.isLetter(it) }
    val core = StringBuilder()
    for (oneAs in charArrayOf('i', 'l')) {
        val pieces = Array(cps.size) { k -> leetLetter(cps[k], oneAs)?.toString() ?: masterCore(String(Character.toChars(cps[k]))) }
        for (start in 0..first) {
            core.setLength(0)
            for (k in start..last) core.append(pieces[k])
            if (core.toString() in words) return true
            for (end in last + 1 until cps.size) {
                core.append(pieces[end])
                if (core.toString() in words) return true
            }
        }
    }
    return false
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
 * Since RT-3 (owner's decision (h), 8-11-2026) also a known word in full-width letters («ｐａｓｓｗｏｒｄ12345», through
 * the NFKC of [masterCore]) or in leet, with digits and symbols for some letters and more of them around it
 * («P4ssw0rd2024!», «C0ntr4s3ñ4!!», «Dr4g0n!2024»: [isKnownWordInLeet]), and since RT-5 (ruling R42) with the symbols
 * of a Spanish keyboard for letters («Pr¡nc3ss2024», «C0ntra$€na2024!», «Dr4gøn!2024»). Without [words] (the default, or
 * an empty set because the lists could not be read) the rule is the length and the estimate, as it was.
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
 * «una sola palabra» and pass if the estimate reaches, and so does any word that is in none of the lists. The leet check
 * only knows the usual substitutions of [isKnownWordInLeet], with «1» read all as «i» or all as «l»; any other spelling
 * of a word passes if the estimate reaches. For example (RT-11 of the 1.4.0 red team, kept as a documented limit in
 * 1.4.2, option B): «(», «<», «©» or «¢» for c («Pr1n(3ss2024!»), «§» for s, «°» for o, «#» for h, «ı», «ł» and the
 * small capitals («ᴘʀɪɴᴄᴇss2024!»), letters of other scripts («Pаssword2024!» with a Cyrillic «а», accepted by R9),
 * enclosed letters such as «🅿🅰🆂🆂» (symbols, so the core has no letters) and «1» read as «i» and «l» in the same word
 * («M1|l1on2024!»). «(», «<» and «#» are among the Generator's symbols, which is one reason not to read them as letters.
 * It is an estimate: the rule does not guarantee that a master is strong.
 *
 * What the Generator proposes with its usual options passes in the tests: a phrase of 5 words (at least 15 letters, and no
 * known word has more than 10: «contrasena», from «Salud»; the lists have 9 at most; the leet reading can only add letters
 * to a core, never remove them), with any separator and list, and a password of 16 characters. A password of digits only
 * is sometimes refused up to about 24 characters (a sequence such as «123» counts as one character); the leet check never
 * looks at a password without letters. With [words], a password of the Generator with the four classes of characters is
 * refused when its few letters (2 to 5), read as they are or in leet, are a word of the lists. Measured on 8-11-2026
 * (a million draws for each count of letters, weighted as the Generator draws them): about 1 time in 3,100 at 12
 * characters, 1 in 54,000 at 16 and 1 in 1.1 million at 20 (the default length), against 1 in 3,800, 1 in 60,000 and 1 in
 * 1.1 million with the rule before RT-3. Measured again on 9-11-2026 after RT-5 (ruling R42), the same way and with the
 * same draws before and after: the same figures (1 in 3,200, 55,000 and 1.1 million in that run), because the Generator
 * only draws ASCII and «¡», «€» and «ø» are not among its characters. The estimate alone, besides, very rarely refuses
 * a random one whose letters hold a keyboard run or repeats (fewer than 1 in 500,000 with 16 letters only). Asking the
 * Generator for another one solves it.
 */
fun newMasterProblem(candidate: String, words: Set<String> = emptySet()): String? = when {
    candidate.length < MIN_MASTER || strengthForm(candidate).let { it.codePointCount(0, it.length) } < MIN_MASTER ->
        MASTER_TOO_SHORT
    estimateBits(candidate) < WEAK_BITS -> MASTER_TOO_WEAK
    words.isNotEmpty() && (masterCore(candidate) in words || isKnownWordInLeet(candidate, words)) -> MASTER_TOO_WEAK
    else -> null
}
