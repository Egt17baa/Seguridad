package app.seguridad.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One message waiting to be shown, kept outside any screen (it lives as long as its owner, usually the process), so an
 * activity re-created meanwhile still shows it. [shown] takes away only the very message that was on screen (compared
 * by identity): one posted between showing it and closing it is not lost, even if it reads the same.
 */
class Notice<T : Any> {
    private val lock = Any()
    private val current = MutableStateFlow<T?>(null)

    val value: StateFlow<T?> = current.asStateFlow()

    fun post(message: T) = synchronized(lock) {
        // Through null: a StateFlow keeps the old object when the new one is equal, and [shown] compares objects.
        current.value = null
        current.value = message
    }

    fun shown(message: T) = synchronized(lock) {
        if (current.value === message) current.value = null
    }
}
