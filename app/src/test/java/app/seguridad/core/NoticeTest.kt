package app.seguridad.core

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** A message that waits outside the screens until it was shown (T11-d, M-4). */
class NoticeTest {
    private class Message(val text: String) {
        override fun equals(other: Any?) = other is Message && other.text == text
        override fun hashCode() = text.hashCode()
    }

    @Test fun itWaitsUntilShown() {
        val n = Notice<Message>()
        assertNull(n.value.value)
        val a = Message("a")
        n.post(a)
        assertSame(a, n.value.value)
        n.shown(a)
        assertNull(n.value.value)
    }

    @Test fun oneThatArrivesBetweenShowingAndClosingIsNotLost() {
        val n = Notice<Message>()
        val a = Message("a")
        n.post(a)
        val onScreen = n.value.value!!
        val b = Message("b")
        n.post(b)
        n.shown(onScreen)
        assertSame(b, n.value.value)
    }

    @Test fun anEqualOneThatArrivesLaterIsStillANewOne() {
        val n = Notice<Message>()
        val first = Message("same")
        n.post(first)
        val onScreen = n.value.value!!
        val second = Message("same")
        n.post(second)
        n.shown(onScreen)
        assertSame(second, n.value.value)
        n.shown(second)
        assertNull(n.value.value)
    }
}
