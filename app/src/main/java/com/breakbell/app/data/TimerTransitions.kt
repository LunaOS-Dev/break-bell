package com.breakbell.app.data

object TimerTransitions {
    fun startWorkday(current: AppState, now: Long): AppState {
        val pattern = current.pattern.ifEmpty { listOf(BlockPlan.Quick) }
        return current.copy(
            isActive = true,
            phase = Phase.WORK,
            workdayStartedAt = now,
            phaseStartedAt = now,
            phaseEndsAt = now + pattern.first().workMinutes.minutesInMillis,
            pattern = pattern,
            currentBlockIndex = 0,
            completedBreaks = 0,
            headsUpShown = false,
            pendingBookmark = "",
            resumeBookmark = "",
        )
    }

    fun markBreakDue(current: AppState, now: Long): AppState = current.copy(
        phase = Phase.WAITING_FOR_BREAK,
        phaseStartedAt = now,
        phaseEndsAt = 0L,
    )

    fun beginBreak(current: AppState, now: Long): AppState = current.copy(
        phase = Phase.BREAK,
        phaseStartedAt = now,
        phaseEndsAt = now + current.currentBlock.breakMinutes.minutesInMillis,
    )

    fun beginNextWorkBlock(current: AppState, now: Long): AppState {
        val pattern = current.pattern.ifEmpty { listOf(BlockPlan.Quick) }
        val nextIndex = (current.currentBlockIndex + 1) % pattern.size
        return current.copy(
            phase = Phase.WORK,
            phaseStartedAt = now,
            phaseEndsAt = now + pattern[nextIndex].workMinutes.minutesInMillis,
            pattern = pattern,
            currentBlockIndex = nextIndex,
            completedBreaks = current.completedBreaks + 1,
            headsUpShown = false,
            resumeBookmark = current.pendingBookmark.trim(),
            pendingBookmark = "",
        )
    }

    private val Int.minutesInMillis: Long get() = this * 60_000L
}
