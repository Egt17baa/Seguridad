package app.seguridad.settings

import app.seguridad.crypto.KdfParams
import kotlinx.serialization.Serializable

@Serializable
enum class LockAfter(val ms: Long, val label: String) {
    ON_EXIT(0, "Al salir"),
    MIN_1(60_000, "1 minuto"),
    MIN_5(300_000, "5 minutos"),
    MIN_15(900_000, "15 minutos"),
}

@Serializable
enum class ClipboardClear(val seconds: Int) { S15(15), S45(45), S90(90) }

@Serializable
enum class KdfProfile(val params: KdfParams, val label: String) {
    STANDARD(KdfParams(64 * 1024, 3, 1), "Estándar (64 MiB)"),
    HIGH(KdfParams(128 * 1024, 4, 1), "Alto (128 MiB)"),
}

@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Serializable
data class Settings(
    val lockAfter: LockAfter = LockAfter.MIN_5,
    val clipboard: ClipboardClear = ClipboardClear.S45,
    val kdf: KdfProfile = KdfProfile.STANDARD,
    val theme: ThemeMode = ThemeMode.DARK,
    val noticeAccepted: Boolean = false,
)

/**
 * Whether to offer this profile for new data: STANDARD always; HIGH only when [heapKiB] / 2 can hold its memory.
 * Stricter than what [KdfParams.checkSane] accepts when reading (a quarter of the heap free), so data made here
 * also opens on phones with a somewhat smaller heap.
 */
fun KdfProfile.availableOn(heapKiB: Long): Boolean = this == KdfProfile.STANDARD || heapKiB / 2 >= params.memoryKiB
