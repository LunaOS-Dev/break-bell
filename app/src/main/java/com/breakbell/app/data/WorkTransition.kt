package com.breakbell.app.data

/** Optional transition aids never change a timer deadline or phase. */
object WorkTransition {
    const val MAX_BOOKMARK_LENGTH = 500

    fun leadMillis(state: AppState): Long =
        minOf(120_000L, state.currentBlock.workMinutes * 60_000L / 5)

    fun headsUpAt(state: AppState): Long = state.phaseEndsAt - leadMillis(state)

    fun isHeadsUp(state: AppState, now: Long): Boolean =
        state.isActive && state.phase == Phase.WORK &&
            now >= headsUpAt(state) && now < state.phaseEndsAt

    fun message(state: AppState, now: Long): String {
        val seconds = ((state.phaseEndsAt - now).coerceAtLeast(1L) + 999L) / 1_000L
        val time = when {
            seconds % 60L == 0L -> "${seconds / 60} ${if (seconds == 60L) "minute" else "minutes"}"
            else -> "$seconds seconds"
        }
        return "Find a stopping point—break in $time."
    }
}
