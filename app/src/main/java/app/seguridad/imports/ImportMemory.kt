package app.seguridad.imports

import app.seguridad.vault.VaultEntry

/*
 * Round 3 of the fixes of the attack, RT-27: «Importar de otra app» works over the whole vault in two places, the plan
 * ([planMerge]: a matcher of every live entry and its tags) and the preview's estimate ([previewEstimate]: the merged
 * vault). Running out of memory there never closes the app: each runs in one of these two wrappers, inside the
 * caller's `withContext(Dispatchers.Default)`, and gives null instead; the screen then says IMPORT_NO_MEMORY (ImportSession
 * planNoMemory and estimateNoMemory), and «Importar» stays off. R11b and R11c are widened to these two calls only.
 */

/** Runs right before each wrapper's work; only tests change it (an [OutOfMemoryError] made by hand). */
@Volatile internal var beforeImportWork: () -> Unit = {}

/** [planMerge], or null when the phone ran out of memory working it out. */
internal fun planMergeOrNoMemory(read: ImportRead, existing: List<VaultEntry>, revision: Long): MergePlan? = try {
    beforeImportWork()
    planMerge(read, existing, revision)
} catch (e: OutOfMemoryError) {
    null
}

/** [previewEstimate], or null when the phone ran out of memory working it out. */
internal fun previewEstimateOrNoMemory(
    plan: MergePlan,
    default: DuplicateChoice,
    overrides: Map<Int, DuplicateChoice>,
    existing: List<VaultEntry>,
    now: Long,
): PreviewEstimate? = try {
    beforeImportWork()
    previewEstimate(plan, default, overrides, existing, now)
} catch (e: OutOfMemoryError) {
    null
}
