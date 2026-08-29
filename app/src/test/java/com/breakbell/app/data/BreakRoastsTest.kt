package com.breakbell.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BreakRoastsTest {
    @Test
    fun `roast changes when the reminder advances`() {
        val first = BreakRoasts.message(BreakRoastContext.BREAK_DUE, 0L)
        val second = BreakRoasts.message(BreakRoastContext.BREAK_DUE, 60_000L)

        assertNotEquals(first, second)
    }

    @Test
    fun `negative elapsed time uses the first roast`() {
        assertEquals(
            BreakRoasts.message(BreakRoastContext.BREAK_CLAIMED, 0L),
            BreakRoasts.message(BreakRoastContext.BREAK_CLAIMED, -1L),
        )
    }

    @Test
    fun `roast selection is deterministic for the same minute`() {
        assertEquals(
            BreakRoasts.message(BreakRoastContext.BREAK_DUE, 60_001L),
            BreakRoasts.message(BreakRoastContext.BREAK_DUE, 119_999L),
        )
    }
}
