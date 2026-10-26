package app.seguridad.settings

import app.seguridad.core.appJson
import app.seguridad.core.writeTextAtomically
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class SettingsStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val heapKiB: () -> Long = { Runtime.getRuntime().maxMemory() / 1024 },
) {
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    private val mutex = Mutex()

    /** Reads the saved settings; a missing, damaged or unreadable file just leaves the defaults. */
    suspend fun load() = mutex.withLock {
        val loaded = withContext(io) {
            try {
                if (file.exists()) appJson.decodeFromString<Settings>(file.readText()) else Settings()
            } catch (e: Exception) {
                Settings()
            }
        }
        _settings.value = sane(loaded)
    }

    suspend fun update(change: (Settings) -> Settings) = mutex.withLock {
        val next = sane(change(_settings.value))
        withContext(io) { file.writeTextAtomically(appJson.encodeToString(Settings.serializer(), next)) }
        _settings.value = next
    }

    private fun sane(settings: Settings): Settings {
        var s = settings
        if (s.kdf == KdfProfile.HIGH && !KdfProfile.HIGH.availableOn(heapKiB())) s = s.copy(kdf = KdfProfile.STANDARD)
        if (s.autoBackupKeep !in AUTO_BACKUP_KEEP_CHOICES) s = s.copy(autoBackupKeep = AUTO_BACKUP_KEEP_DEFAULT)
        return s
    }
}
