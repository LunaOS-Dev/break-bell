package com.breakbell.app.data

enum class Phase {
    IDLE,
    WORK,
    WAITING_FOR_BREAK,
    BREAK,
}

data class AppState(
    val isActive: Boolean = false,
    val phase: Phase = Phase.IDLE,
    val workdayStartedAt: Long = 0L,
    val phaseStartedAt: Long = 0L,
    val phaseEndsAt: Long = 0L,
    val pattern: List<BlockPlan> = listOf(BlockPlan.Quick),
    val currentBlockIndex: Int = 0,
    val completedBreaks: Int = 0,
    val headsUpShown: Boolean = false,
    val pendingBookmark: String = "",
    val resumeBookmark: String = "",
) {
    val currentBlock: BlockPlan
        get() = pattern.getOrElse(currentBlockIndex) { BlockPlan.Quick }
}

data class BlockPlan(
    val workMinutes: Int,
    val breakMinutes: Int,
) {
    val displayName: String
        get() = when (this) {
            Quick -> "Quick"
            Deep -> "Deep"
            Long -> "Long"
            else -> "Custom"
        }

    companion object {
        val Quick = BlockPlan(25, 5)
        val Deep = BlockPlan(45, 10)
        val Long = BlockPlan(60, 10)
        val presets = listOf(Quick, Deep, Long)
    }
}

data class WorkdayRecord(
    val startedAt: Long,
    val endedAt: Long,
    val completedBreaks: Int,
)

data class BridgeConfig(
    val address: String = "",
    val token: String = "",
) {
    val isConfigured: Boolean get() = address.isNotBlank() && token.isNotBlank()
}
