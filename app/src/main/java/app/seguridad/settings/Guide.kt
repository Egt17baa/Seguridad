package app.seguridad.settings

import app.seguridad.vault.VaultLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * 2.0, task 10 (phase B): the two guides (design «Guía inicial»; owner, 14-11-2026). The whole one for a new install
 * (the plan's four screens: the master password cannot be recovered, the copy, the fingerprint, several vaults) and a
 * short one, of what 2.0 brings, for whoever updated from 1.x. Each sentence says what the code does, on every version
 * of Android the app runs on (none of these depends on it); nothing outside 2.0. Both can be skipped and seen again
 * from Ajustes. Skipping never skips «Antes de empezar» ([openingItem], O-7).
 */

/** Which guide. */
enum class GuideKind { FULL, WHATS_NEW }

/** The guide [Settings.guideDue] asks for; null when none is. */
val GuideDue.kind: GuideKind?
    get() = when (this) {
        GuideDue.FULL -> GuideKind.FULL
        GuideDue.WHATS_NEW -> GuideKind.WHATS_NEW
        GuideDue.NONE -> null
    }

/** One screen of a guide. */
data class GuidePage(val title: String, val text: String)

/** The whole guide, for a new install. */
val FULL_GUIDE = listOf(
    GuidePage("La contraseña maestra", "Abre tu bóveda. Si la olvidas, nadie puede recuperar lo guardado."),
    GuidePage(
        "La copia",
        "En Ajustes puedes exportar una copia o elegir hasta ${VaultLimits.BACKUP_TARGETS_MAX} carpetas para la copia automática. " +
            "Las que tienen calendario solo se hacen al abrir Clavero.",
    ),
    GuidePage(
        "La huella",
        "Si el móvil tiene huella, puedes activarla en el menú de la bóveda abierta. Por seguridad, cada 7 días pide la contraseña maestra.",
    ),
    GuidePage(
        "Varias bóvedas",
        "Puedes tener hasta ${VaultLimits.VAULTS_MAX} bóvedas, cada una con su contraseña maestra. Solo hay una abierta a la vez.",
    ),
)

/** What is new in 2.0, for whoever updated from 1.x: at most four screens of one or two lines. */
val WHATS_NEW_GUIDE = listOf(
    GuidePage("Varias bóvedas y carpetas", "Hasta ${VaultLimits.VAULTS_MAX} bóvedas, cada una con su contraseña, y carpetas dentro de cada una."),
    GuidePage("Papelera e historial", "Lo que borras pasa ${VaultLimits.TRASH_DAYS} días en la papelera. Al cambiar una entrada se guarda su versión anterior."),
    GuidePage(
        "Copias en varias carpetas",
        "Hasta ${VaultLimits.BACKUP_TARGETS_MAX} carpetas, cada una con su calendario. Las copias con calendario solo se hacen al abrir Clavero.",
    ),
    GuidePage("Tipos nuevos", "Tarjeta bancaria, red wifi, licencia y pasaporte, cada uno con sus campos."),
)

/** The pages of [kind]. */
fun guidePages(kind: GuideKind): List<GuidePage> = if (kind == GuideKind.FULL) FULL_GUIDE else WHATS_NEW_GUIDE

const val GUIDE_SKIP = "Saltar"
const val GUIDE_NEXT = "Siguiente"
const val GUIDE_DONE = "Entendido"

/** Where it is in the guide: «2 de 4». */
fun guideStep(index: Int, of: Int): String = "${index + 1} de $of"

/** Ajustes: the section and its two buttons. */
const val GUIDE_SECTION_TITLE = "Guía"
const val GUIDE_OPEN_FULL = "Ver la guía"
const val GUIDE_OPEN_NEW = "Ver las novedades de la 2.0"

/**
 * The guide on screen, for the whole app (in AppContainer, so a rotation keeps it): the one [Settings.guideDue] asks for
 * at the start, once in this run, or the one asked for from Ajustes ([request]). [done] (finished or skipped) saves it
 * as seen ([withGuideSeen]) in [scope], not cancellable; if that cannot be saved, it is not shown again in this run (no
 * loop) and the next start shows it again, as nothing changed on disk.
 */
class GuideHolder(private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val _requested = MutableStateFlow<GuideKind?>(null)
    private val _dismissed = MutableStateFlow(false)

    /** Asked for from Ajustes; null when none is. */
    val requested: StateFlow<GuideKind?> = _requested.asStateFlow()

    /** The due one was finished or skipped in this run. */
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    /** The guide to show with [s]: the one asked for, or the one due if not dismissed in this run. */
    fun current(s: Settings): GuideKind? = _requested.value ?: if (_dismissed.value) null else s.guideDue.kind

    fun request(kind: GuideKind) {
        _requested.value = kind
    }

    /** Finished or skipped: it goes, and it is saved as seen. */
    fun done() {
        _requested.value = null
        _dismissed.value = true
        scope.launch {
            withContext(NonCancellable) {
                try {
                    settings.update { it.withGuideSeen() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A full disk: not again in this run; guideSeen is unchanged on disk, so the next start shows it.
                }
            }
        }
    }
}
