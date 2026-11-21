package app.seguridad.health

import app.seguridad.vault.VaultData

/**
 * Branch review, C m-1: one result of «Salud»: its [items] and, from them without the leak check, its [missions].
 * Round 2 of the fixes of the attack, point 9: [noMemory] when working it out ran out of memory ([healthView]); then it
 * holds nothing, and the screen shows [app.seguridad.vault.LIST_NO_MEMORY] instead of anything else.
 */
internal data class HealthView(val items: List<HealthItem>, val missions: List<Mission>?, val noMemory: Boolean = false) {
    companion object {
        /** Round 2, point 9: what «Salud» holds after running out of memory: nothing. */
        val NO_MEMORY = HealthView(emptyList(), null, noMemory = true)
    }
}

/** Round 2, point 9: runs right before [healthView] works «Salud» out; only tests change it (an [OutOfMemoryError] made by hand). */
@Volatile internal var beforeHealthCompute: () -> Unit = {}

/**
 * What «Salud» shows of [data] at [now], with the leaks found ([breached]): every item of [vaultHealth] over the live
 * entries and, unless [missionsOff], the missions from them without the leak check (O-6). Pure and long: the caller runs
 * it on Dispatchers.Default. Round 2 of the fixes of the attack, point 9: running out of memory here (a vault too big for
 * the phone) never closes the app: it gives [HealthView.NO_MEMORY], and the vault stays open, as the list does
 * (VaultList.kt, RT-25 part a). R11c is widened to this one call. Round 3: [active] runs between entries and before the
 * missions (the screen passes `job.ensureActive()`, so a computation whose inputs changed stops); a CancellationException
 * it throws goes out as it is, never as [HealthView.NO_MEMORY] (only an OutOfMemoryError is caught).
 */
internal fun healthView(data: VaultData, now: Long, breached: Map<String, Int>, missionsOff: Boolean, active: () -> Unit = {}): HealthView = try {
    beforeHealthCompute()
    val all = vaultHealth(data.live, now, breached, active)
    val noLeaks = all.map { it.copy(issues = it.issues - Issue.BREACHED) }.filter { it.issues.isNotEmpty() }
    active() // Before the missions, too.
    HealthView(all, if (missionsOff) null else missions(noLeaks, data, now))
} catch (e: OutOfMemoryError) {
    HealthView.NO_MEMORY
}
