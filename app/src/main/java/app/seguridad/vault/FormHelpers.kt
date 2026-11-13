package app.seguridad.vault

import app.seguridad.crypto.Bip39
import app.seguridad.crypto.OtpAuthRead
import app.seguridad.crypto.Strength
import app.seguridad.crypto.readOtpAuth
import app.seguridad.crypto.strength
import app.seguridad.health.estimateBits
import app.seguridad.tools.qr.parseLink
import app.seguridad.tools.qr.schemeOf
import app.seguridad.tools.qr.asBrowserReadsIt
import java.text.Normalizer
import java.util.Locale

/*
 * 2.0, task 7 (design «Formulario»): what the entry form adds — the title from the URL, what a paste looks like, the
 * «+» chooser, and where the generator and the strength go. Plain functions, tested on the JVM; no network, and no
 * clipboard: a paste is only ever what the field itself received ([pastedBlock]).
 */

// The title from the URL.

/** The web schemes a title is taken from (an `otpauth://totp/…` or `ftp://` host is not the name of a site). */
private val WEB_SCHEMES = setOf("http", "https")

/**
 * A text with no scheme that reads as a bare domain («ejemplo.es», «www.ejemplo.es/cuenta»): labels of letters, digits
 * and hyphens joined by dots, then nothing or a path. No «@», «:» or space: «usuario:clave@host» is not one.
 */
private val BARE_DOMAIN = Regex("""[\p{L}\p{N}-]+(\.[\p{L}\p{N}-]+)+\.?([/?#].*)?""")

/**
 * The title an empty title takes from [url] (design «Formulario», R-11: from the domain, with no table of brand names):
 * the host as [parseLink] reads it the way a browser does (so `\` closes the authority and the user info, with its key,
 * is never part of it), in the ASCII form the browser connects to (a name in another script stays `xn--…`, never a
 * look-alike), without «www.» at the start and with its first letter in capitals. Only for `http`, `https` and a bare
 * domain; null when there is no host. No network.
 */
fun titleFromUrl(url: String): String? {
    val text = asBrowserReadsIt(url)
    if (text.isEmpty() || text.length > TITLE_URL_MAX_CHARS) return null
    val link = when {
        schemeOf(text) != null -> text
        BARE_DOMAIN.matches(text) -> "https://$text"
        else -> return null
    }
    val parsed = parseLink(link)
    if (parsed.scheme !in WEB_SCHEMES) return null
    val host = parsed.host?.removePrefix("www.")?.takeIf { it.isNotEmpty() } ?: return null
    return host.replaceFirstChar { it.titlecase(Locale.ROOT) }
}

/** A URL longer than this is not looked at for a title (decision (a)); the longest host is 253 characters. */
private const val TITLE_URL_MAX_CHARS = 4_096

// What a paste looks like.

/** What a block pasted into a field of the form looks like. */
enum class PastedKind { OTPAUTH, SEED }

/** The shortest block [pastedBlock] gives: the length of «otpauth://»; a phrase of 12 words is longer still. */
const val PASTE_MIN_CHARS = 10

/** A paste longer than this is not read as a phrase (24 words of the lists, with room for spaces, are far shorter). */
const val SEED_PASTE_MAX_CHARS = 1_000

private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

/** [s] without its accents: decomposed, and without the combining marks. */
private fun bare(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }

/**
 * The BIP-39 lists [detectPasted] reads a phrase with: the English one and the Spanish one, in NFC (the list of the app
 * is written decomposed) and without accents (as a phrase is often written; its 2048 words stay distinct). A list that
 * is not the 2048 words of BIP-39 is left out, so a list that could not be read recognises no phrase. Not secret.
 */
class SeedLists(english: List<String>, spanish: List<String>) {
    internal val readers: List<Pair<Bip39, (String) -> String>> = listOfNotNull(
        reader(english.map(::nfc), ::nfc),
        reader(spanish.map(::nfc), ::nfc),
        reader(spanish.map(::bare), ::bare),
    )

    private fun reader(words: List<String>, form: (String) -> String): Pair<Bip39, (String) -> String>? =
        if (words.size == 2048 && words.toSet().size == 2048) Bip39(words) to form else null
}

private val SEPARATORS = Regex("""[\s\p{Z}]+""")

private val SEED_COUNTS = setOf(12, 15, 18, 21, 24)

/**
 * What [text] (a block pasted into a field, [pastedBlock]) looks like: [PastedKind.OTPAUTH] if it is an `otpauth://`
 * link that [readOtpAuth] reads as a usable code (one whose settings are out of range is not one: CRY-B02, refused and
 * never changed); [PastedKind.SEED] if it is 12, 15, 18, 21 or 24 words of one of [seeds]' lists with a valid checksum.
 * Null otherwise, and for a phrase when [seeds] is null (the lists not loaded). Only the form calls it, with what the
 * field received; it never reads the clipboard. It keeps nothing, and the entropy it computes is wiped.
 */
fun detectPasted(text: String, seeds: SeedLists?): PastedKind? {
    val t = text.trim()
    if (t.regionMatches(0, "otpauth://", 0, 10, ignoreCase = true)) {
        return if (readOtpAuth(t) is OtpAuthRead.Usable) PastedKind.OTPAUTH else null
    }
    if (seeds == null || t.length > SEED_PASTE_MAX_CHARS) return null
    val words = t.lowercase(Locale.ROOT).split(SEPARATORS).filter { it.isNotEmpty() }
    if (words.size !in SEED_COUNTS) return null
    for ((bip, form) in seeds.readers) {
        val entropy = bip.entropyOf(words.map(form)) ?: continue
        entropy.fill(0)
        return PastedKind.SEED
    }
    return null
}

/**
 * The block of text a change of a field from [before] to [after] inserted at once, if it has [PASTE_MIN_CHARS] or more:
 * what the field's own «Pegar», Ctrl+V or the keyboard's clipboard put in (also over a selection). That is «the field's
 * paste» of the form: it is read from the field's value, never from the clipboard. Typing, one character or one word of
 * the keyboard at a time, and deleting, give null, so they never reach [detectPasted].
 */
fun pastedBlock(before: String, after: String): String? {
    if (after.length < PASTE_MIN_CHARS) return null
    var p = 0
    val max = minOf(before.length, after.length)
    while (p < max && before[p] == after[p]) p++
    var s = 0
    while (s < max - p && before[before.length - 1 - s] == after[after.length - 1 - s]) s++
    val inserted = after.length - p - s
    return if (inserted >= PASTE_MIN_CHARS) after.substring(p, p + inserted) else null
}

// The «+».

/**
 * What the «+» of the vault offers, in this order (design «Entradas»). The labels are those of the types (R-4: «Tarjeta
 * bancaria», «Red wifi»), as in the chips of the form; [OTHER] is an empty «Acceso», where the user adds the fields.
 */
enum class NewEntryChoice(val type: EntryType, val label: String) {
    LOGIN(EntryType.LOGIN, EntryType.LOGIN.label),
    TOTP(EntryType.TOTP, EntryType.TOTP.label),
    NOTE(EntryType.NOTE, EntryType.NOTE.label),
    SEED(EntryType.SEED, EntryType.SEED.label),
    CARD(EntryType.CARD, EntryType.CARD.label),
    WIFI(EntryType.WIFI, EntryType.WIFI.label),
    LICENSE(EntryType.LICENSE, EntryType.LICENSE.label),
    PASSPORT(EntryType.PASSPORT, EntryType.PASSPORT.label),
    OTHER(EntryType.LOGIN, NEW_ENTRY_OTHER),
}

val NEW_ENTRY_CHOICES: List<NewEntryChoice> = NewEntryChoice.entries

// The generator and the strength.

/** A password estimated longer than this is read up to it only (the estimate is linear; 10 000 is a field's top). */
private const val STRENGTH_MAX_CHARS = 10_000

/**
 * The line under a password while it is typed: the estimate of «Salud» ([estimateBits]) in words ([Strength.label]); null
 * for an empty one. The words carry the meaning, never the colour alone (R-1). It is an estimate.
 */
fun strengthText(password: String): String? {
    if (password.isEmpty()) return null
    return STRENGTH_LINE + strength(estimateBits(password.take(STRENGTH_MAX_CHARS))).label
}

/** The strength of [password] for the colour of its line ([strengthText]); null for an empty one. */
fun strengthOf(password: String): Strength? = if (password.isEmpty()) null else strength(estimateBits(password.take(STRENGTH_MAX_CHARS)))

/**
 * Whether the secret [key] of [type]'s template is a password the user chooses, with the generator and the strength
 * next to it: the wifi's. A card's number, CVV and PIN, a licence's key and a passport's number are given by someone
 * else, so neither is offered. The user's hidden fields always have both (they follow the rules of a password).
 */
fun templateSecretIsPassword(type: EntryType, key: String): Boolean = type == EntryType.WIFI && key == WIFI_PASSWORD_KEY

// Texts of the form (also in «Textos para el Abogado Digital» of the task's report).

/** The last choice of the «+». */
const val NEW_ENTRY_OTHER = "Otro"

/** The title of the «+» chooser. */
const val NEW_ENTRY_CHOOSE = "¿Qué quieres guardar?"

/** What «Otro» is, under it in the chooser. */
const val NEW_ENTRY_OTHER_LINE = "Un acceso vacío con los campos que quieras"

/** The start of the line of [strengthText]. */
const val STRENGTH_LINE = "Fuerza estimada: "

/** The generator's button next to a password. */
const val GENERATE_PASSWORD = "Generar una al azar"

/** The question after a paste that looks like a 2FA link. */
const val PASTE_OTPAUTH_QUESTION = "Parece un código 2FA. ¿Guardarlo como 2FA?"

/** The question after a paste that looks like a seed phrase (its words are never shown). */
const val PASTE_SEED_QUESTION = "Parece una frase semilla. ¿Guardarla como frase semilla?"

/** What «Sí» does, under the question. */
const val PASTE_MOVES = "Se quita del campo donde lo has pegado."

const val PASTE_YES = "Sí"
const val PASTE_NO = "No, dejarlo así"

/** The dialog of «Atrás» with changes. */
const val UNSAVED_TITLE = "¿Guardar los cambios?"
const val UNSAVED_SAVE = "Guardar"
const val UNSAVED_DISCARD = "Descartar"
const val UNSAVED_KEEP = "Seguir editando"
