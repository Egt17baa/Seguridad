package app.seguridad.tools.codes

import app.seguridad.crypto.secureRandom
import app.seguridad.tools.answersNoteTitle
import app.seguridad.vault.EntryType
import app.seguridad.vault.VaultEntry
import java.security.SecureRandom
import java.text.Normalizer

private const val ANSWER_WORDS = 4

/** [word] without accents or diaeresis, and «ñ» as «n»: canonical decomposition, then the combining marks (Mn) dropped. */
fun withoutAccents(word: String): String =
    Normalizer.normalize(word, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

/**
 * An answer of 4 words drawn uniformly (`nextInt(bound)`, no modulo bias) and independently from [words] (the 2.048
 * of the Spanish BIP-39 list: 44 bits), separated by one space; [plain] drops accents and «ñ» from each word. Otherwise
 * each word is composed (NFC), the form a keyboard types: the list file keeps its accents as separate marks (NFD).
 */
fun inventedAnswer(words: List<String>, plain: Boolean = true, random: SecureRandom = secureRandom): String {
    require(words.isNotEmpty())
    return List(ANSWER_WORDS) { words[random.nextInt(words.size)] }.joinToString(" ") {
        if (plain) withoutAccents(it) else Normalizer.normalize(it, Normalizer.Form.NFC)
    }
}

data class InventedQuestion(val question: String, val answer: String)

/** The secure note «Respuestas de seguridad · [site]» with one line `pregunta → respuesta` per question. */
fun inventedAnswersNote(site: String, questions: List<InventedQuestion>, now: Long, id: String): VaultEntry =
    VaultEntry(
        id = id,
        type = EntryType.NOTE,
        title = answersNoteTitle(site),
        notes = questions.joinToString("\n") { "${it.question} → ${it.answer}" },
        createdAt = now,
        updatedAt = now,
    )
