package com.breakbell.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerTransitionsTest {
    private val start = 1_000_000L

    @Test
    fun `workday starts on first block in pattern`() {
        val initial = AppState(pattern = listOf(BlockPlan.Deep, BlockPlan.Long))

        val result = TimerTransitions.startWorkday(initial, start)

        assertTrue(result.isActive)
        assertEquals(Phase.WORK, result.phase)
        assertEquals(BlockPlan.Deep, result.currentBlock)
        assertEquals(start + 45 * 60_000L, result.phaseEndsAt)
    }

    @Test
    fun `break does not start until acknowledged`() {
        val working = TimerTransitions.startWorkday(AppState(), start)
        val due = TimerTransitions.markBreakDue(working, start + 25 * 60_000L)

        assertEquals(Phase.WAITING_FOR_BREAK, due.phase)
        assertEquals(0L, due.phaseEndsAt)

        val acknowledgedAt = start + 26 * 60_000L
        val resting = TimerTransitions.beginBreak(due, acknowledgedAt)
        assertEquals(Phase.BREAK, resting.phase)
        assertEquals(acknowledgedAt + 5 * 60_000L, resting.phaseEndsAt)
    }

    @Test
    fun `pattern advances and wraps after each break`() {
        val pattern = listOf(BlockPlan.Quick, BlockPlan.Deep, BlockPlan.Long)
        var state = TimerTransitions.startWorkday(AppState(pattern = pattern), start)

        state = TimerTransitions.beginNextWorkBlock(state, start + 1)
        assertEquals(BlockPlan.Deep, state.currentBlock)
        state = TimerTransitions.beginNextWorkBlock(state, start + 2)
        assertEquals(BlockPlan.Long, state.currentBlock)
        state = TimerTransitions.beginNextWorkBlock(state, start + 3)
        assertEquals(BlockPlan.Quick, state.currentBlock)
        assertEquals(3, state.completedBreaks)
    }
}
