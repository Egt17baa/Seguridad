package app.seguridad.imports

import app.seguridad.vault.CopyResult
import kotlinx.coroutines.CancellationException

/** How «Confirmar» ended. */
sealed interface ImportOutcome {
    /** The copy was made ([where], as [CopyResult.Saved] said) and then the import saved ([done]). */
    data class Imported(val done: ApplyResult.Done, val where: String) : ImportOutcome

    /** No copy, so nothing was imported; [message] is a fixed Spanish text. */
    data class CopyFailed(val message: String) : ImportOutcome

    /** The copy was made, but the import was not saved ([result]: [ApplyResult.Stale], [ApplyResult.TooBig] or [ApplyResult.EntryTooBig]). */
    data class NotApplied(val result: ApplyResult) : ImportOutcome

    /**
     * The copy was made ([where]), but saving the import threw. That is almost always a full disk, with nothing saved;
     * see [ImportFlow]. [message] is the fixed [IMPORT_SAVE_FAILED], never what the exception said.
     */
    data class SaveFailed(val where: String, val message: String = IMPORT_SAVE_FAILED) : ImportOutcome
}

/**
 * «Confirmar» of «Importar de otra app» (spec «Confirmar: la copia y el guardado»): first [copy], the copy of the vault as
 * it is (to the automatic copies' folder, or where the user chooses); only if it is [CopyResult.Saved], [apply], the one
 * save of the import ([app.seguridad.vault.VaultStore.applyImport]). Without a copy there is no import: a copy that
 * fails, throws or is cancelled never reaches [apply]. If the process dies between the two, the vault is as it was and
 * the copy is left over; [apply]'s own write is atomic. It keeps nothing: the plan, the file and its secrets live in
 * the two lambdas the caller gives (a ViewModel's), not here. Any failure of [apply] itself is
 * [ImportOutcome.SaveFailed]; a cancellation is thrown. That failure is almost always a full disk before
 * [app.seguridad.vault.VaultStore.applyImport]'s atomic rename, with nothing saved. A failure after the rename (the
 * entries saved could not be read back) cannot be ruled out, so [IMPORT_SAVE_FAILED] never says the vault is
 * unchanged.
 */
class ImportFlow(val copy: suspend () -> CopyResult, val apply: suspend () -> ApplyResult) {
    suspend fun confirm(): ImportOutcome {
        val copied = try {
            copy()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportOutcome.CopyFailed(IMPORT_COPY_FAILED) // Never what the exception said.
        }
        val where = when (copied) {
            is CopyResult.Failed -> return ImportOutcome.CopyFailed(copied.message)
            is CopyResult.Saved -> copied.where
        }
        val applied = try {
            apply()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportOutcome.SaveFailed(where) // Never what the exception said.
        }
        return when (val r = applied) {
            is ApplyResult.Done -> ImportOutcome.Imported(r, where)
            else -> ImportOutcome.NotApplied(r)
        }
    }
}
