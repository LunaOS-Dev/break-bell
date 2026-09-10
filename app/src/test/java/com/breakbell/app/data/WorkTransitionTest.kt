package com.breakbell.app.data

import org.junit.Assert.*
import org.junit.Test

class WorkTransitionTest {
    private val start = 1_000_000L

    @Test fun `heads up begins exactly two minutes before ordinary work ends`() {
        val state = TimerTransitions.startWorkday(AppState(), start)
        val at = state.phaseEndsAt - 120_000L
        assertFalse(WorkTransition.isHeadsUp(state, at - 1))
        assertTrue(WorkTransition.isHeadsUp(state, at))
        assertEquals("Find a stopping point—break in 2 minutes.", WorkTransition.message(state, at))
        assertTrue(WorkTransition.isHeadsUp(state, state.phaseEndsAt - 1))
        assertFalse(WorkTransition.isHeadsUp(state, state.phaseEndsAt))
        assertFalse(WorkTransition.isHeadsUp(state, state.phaseEndsAt + 1))
    }

    @Test fun `all configurable work lengths have a positive capped lead after work begins`() {
        for (minutes in 1..180) {
            val state = TimerTransitions.startWorkday(AppState(pattern = listOf(BlockPlan(minutes, 7))), start)
            val lead = WorkTransition.leadMillis(state)
            assertEquals(minOf(120_000L, minutes * 12_000L), lead)
            assertTrue(WorkTransition.headsUpAt(state) > start)
            assertTrue(lead < minutes * 60_000L)
        }
        val short = TimerTransitions.startWorkday(AppState(pattern = listOf(BlockPlan(1, 1))), start)
        assertEquals("Find a stopping point—break in 12 seconds.",
            WorkTransition.message(short, WorkTransition.headsUpAt(short)))
    }

    @Test fun `heads up is never a break phase or an inactive prompt`() {
        val state = TimerTransitions.startWorkday(AppState(), start)
        val now = WorkTransition.headsUpAt(state)
        for (phase in listOf(Phase.IDLE, Phase.WAITING_FOR_BREAK, Phase.BREAK)) {
            assertFalse(WorkTransition.isHeadsUp(state.copy(phase = phase), now))
        }
        assertFalse(WorkTransition.isHeadsUp(state.copy(isActive = false), now))
    }

    @Test fun `bookmark returns once after the break and empty bookmarks need no response`() {
        for (note in listOf("", "  ", "Review the next result")) {
            val work = TimerTransitions.startWorkday(AppState(), start).copy(pendingBookmark = note)
            val due = TimerTransitions.markBreakDue(work, work.phaseEndsAt)
            assertEquals(note, due.pendingBookmark)
            val rest = TimerTransitions.beginBreak(due, work.phaseEndsAt + 5_000L)
            assertEquals(note, rest.pendingBookmark)
            assertEquals(work.phaseEndsAt + 5_000L + 300_000L, rest.phaseEndsAt)
            val resumed = TimerTransitions.beginNextWorkBlock(rest, rest.phaseEndsAt)
            assertEquals(note.trim(), resumed.resumeBookmark)
            assertEquals("", resumed.pendingBookmark)
            assertEquals(1, resumed.completedBreaks)
            assertFalse(resumed.headsUpShown)
            val nextRest = TimerTransitions.beginBreak(
                TimerTransitions.markBreakDue(resumed, resumed.phaseEndsAt), resumed.phaseEndsAt)
            assertEquals("", TimerTransitions.beginNextWorkBlock(nextRest, nextRest.phaseEndsAt).resumeBookmark)
        }
    }
}
