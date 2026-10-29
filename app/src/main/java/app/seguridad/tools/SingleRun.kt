package app.seguridad.tools

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * One run at a time. [start] gives a [Ticket], or null while another run holds it. Each ticket frees the guard once:
 * releasing it again, or late (a callback of a run that already ended), never frees a newer run.
 */
class SingleRun {
    private val holder = AtomicReference<Ticket?>(null)

    inner class Ticket internal constructor() {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) holder.compareAndSet(this, null)
        }
    }

    fun start(): Ticket? {
        val ticket = Ticket()
        return if (holder.compareAndSet(null, ticket)) ticket else null
    }
}
