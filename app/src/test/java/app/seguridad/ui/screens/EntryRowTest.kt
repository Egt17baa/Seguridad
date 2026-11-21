package app.seguridad.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.0, task 6 (design «Deslizar»): the threshold of a swipe is 40 % of the row's width, and it acts when let go. */
class EntryRowTest {
    @Test fun aSwipeActsFrom40PercentOfTheWidth() {
        assertEquals(0.4f, SWIPE_THRESHOLD)
        val w = 1000
        assertNull(swipeAction(399f, w))
        assertNull(swipeAction(-399f, w))
        assertNull(swipeAction(0f, w))
        // To the right (left to right): the trash, which asks first; to the left (right to left): edit.
        assertEquals(SwipeAction.TRASH, swipeAction(400f, w))
        assertEquals(SwipeAction.EDIT, swipeAction(-400f, w))
        assertEquals(SwipeAction.TRASH, swipeAction(1000f, w))
        // A row not measured yet does nothing.
        assertNull(swipeAction(500f, 0))
    }
}
