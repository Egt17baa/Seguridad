package app.seguridad.health

import app.seguridad.imports.plainReportText
import app.seguridad.vault.VaultData
import app.seguridad.vault.backupCheckNotice

/*
 * 2.0, task 10 (phase B): «Salud» as missions (design «Ayuda y personalización»): one suggestion at a time, from what
 * «Salud» already computes, with «Ahora no» of the same weight (Abogado Digital, R-2), and no streaks, points, countdowns
 * nor guilt. A mission only navigates (to the entry to change, or to «Comprobar una copia»); none asks for the leak check,
 * which only its two buttons do (O-6). A title may hold an entry's title: it is data of the vault, kept only by the
 * screen of the open vault, which leaves at a lock.
 */

const val MISSION_REUSED = "reused"
const val MISSION_WEAK = "weak"
const val MISSION_NO_2FA = "no2fa"
const val MISSION_OLD = "old"
const val MISSION_BACKUP = "backup"

/** Where a mission goes. Only navigation: never the network, never a change by itself. */
sealed interface MissionAction {
    /** The route the navigation opens for it (AppNavHost; its destinations are checked in SimpleModeTest). */
    val routeBase: String

    /** The editor of [entryId]. */
    data class EditEntry(val entryId: String) : MissionAction {
        override val routeBase: String get() = "vault/edit"
    }

    /** «Comprobar una copia». */
    data object CheckBackup : MissionAction {
        override val routeBase: String get() = "settings/check-backup"
    }
}

/** One suggestion: [id] is its kind (what «Ahora no» puts off), [title] what it asks. */
data class Mission(val id: String, val title: String, val action: MissionAction)

/** «Ir» and «Ahora no»: the same weight on screen (R-2). */
const val MISSION_GO = "Ir"
const val MISSION_LATER = "Ahora no"

/** The switch of Ajustes that turns them all off (R-2), and its line. */
const val MISSIONS_SWITCH = "Sugerencias de «Salud»"
const val MISSIONS_LABEL = "Enseñar sugerencias"
const val MISSIONS_SHORT = "Una sugerencia cada vez en «Salud de la bóveda», con «Ahora no». Ninguna se conecta a internet."

/**
 * The missions of the open vault, in their order: repeated passwords, weak ones, a login without 2FA, old ones, and
 * a copy to make and check (when [backupCheckNotice] says so, from the same date). [items] is [vaultHealth] of
 * [data]'s live entries, with no leak check ([Issue.BREACHED] never makes one). Pure; the caller runs it off the main
 * thread.
 */
fun missions(items: List<HealthItem>, data: VaultData, now: Long): List<Mission> {
    val live = data.live.associateBy { it.id }
    fun with(issue: Issue) = items.filter { issue in it.issues && it.entryId in live }
    val out = ArrayList<Mission>()
    with(Issue.REUSED).let { if (it.isNotEmpty()) out += Mission(MISSION_REUSED, countTitle(it.size, "repetida", "repetidas"), MissionAction.EditEntry(it.first().entryId)) }
    with(Issue.WEAK).let { if (it.isNotEmpty()) out += Mission(MISSION_WEAK, countTitle(it.size, "débil", "débiles"), MissionAction.EditEntry(it.first().entryId)) }
    with(Issue.NO_2FA).firstOrNull()?.let { item ->
        // O-6: only the entry's own title, as plain text (no controls nor direction marks from the vault).
        val title = plainReportText(live.getValue(item.entryId).title, 60).trim()
        out += Mission(MISSION_NO_2FA, if (title.isEmpty()) "Activa el 2FA en un acceso" else "Activa el 2FA en «$title»", MissionAction.EditEntry(item.entryId))
    }
    with(Issue.OLD).let { if (it.isNotEmpty()) out += Mission(MISSION_OLD, countTitle(it.size, "antigua", "antiguas"), MissionAction.EditEntry(it.first().entryId)) }
    if (backupCheckNotice(data.lastCheckedBackupAt, now) != null) out += Mission(MISSION_BACKUP, "Haz y comprueba una copia", MissionAction.CheckBackup)
    return out
}

private fun countTitle(n: Int, one: String, many: String): String =
    if (n == 1) "Cambia 1 contraseña $one" else "Cambia $n contraseñas $many"

/** The one on screen: the first of [all] not put off. */
fun nextMission(all: List<Mission>, postponed: Set<String>): Mission? = all.firstOrNull { it.id !in postponed }

/**
 * «Ahora no» (design: hidden until the next unlock, in memory), per vault: what was put off at the vault's
 * [app.seguridad.vault.VaultStore.lockEpoch]. A lock moves the epoch on, so the next unlock sees every mission again;
 * another vault never sees this one's. Never saved.
 */
class MissionPostponements {
    private val byVault = HashMap<String, Pair<Int, Set<String>>>()

    @Synchronized
    fun postpone(vaultId: String, epoch: Int, missionId: String) {
        val now = byVault[vaultId]?.takeIf { it.first == epoch }?.second.orEmpty()
        byVault[vaultId] = epoch to (now + missionId)
    }

    @Synchronized
    fun postponed(vaultId: String, epoch: Int): Set<String> {
        val kept = byVault[vaultId] ?: return emptySet()
        if (kept.first == epoch) return kept.second
        byVault.remove(vaultId)
        return emptySet()
    }
}

/** The words that put pressure ([MissionsTest]): whole words, and «!» or «¡» anywhere. */
fun pressureWords(text: String): List<String> {
    val words = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).map { it.value }.toSet()
    val out = listOf("racha", "rachas", "puntos", "punto", "urgente", "ya").filter { it in words }.toMutableList()
    if ('!' in text) out += "!"
    if ('¡' in text) out += "¡"
    return out
}
