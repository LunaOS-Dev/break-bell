package com.breakbell.app.data

import android.content.Context

class BreakBellStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun readState(): AppState {
        val phase = runCatching {
            Phase.valueOf(preferences.getString(KEY_PHASE, Phase.IDLE.name) ?: Phase.IDLE.name)
        }.getOrDefault(Phase.IDLE)

        return AppState(
            isActive = preferences.getBoolean(KEY_ACTIVE, false),
            phase = phase,
            workdayStartedAt = preferences.getLong(KEY_WORKDAY_START, 0L),
            phaseStartedAt = preferences.getLong(KEY_PHASE_START, 0L),
            phaseEndsAt = preferences.getLong(KEY_PHASE_END, 0L),
            pattern = readPattern(),
            currentBlockIndex = preferences.getInt(KEY_CURRENT_BLOCK_INDEX, 0).coerceAtLeast(0),
            completedBreaks = preferences.getInt(KEY_COMPLETED_BREAKS, 0).coerceAtLeast(0),
            headsUpShown = preferences.getBoolean(KEY_HEADS_UP_SHOWN, false),
            pendingBookmark = preferences.getString(KEY_PENDING_BOOKMARK, "") ?: "",
            resumeBookmark = preferences.getString(KEY_RESUME_BOOKMARK, "") ?: "",
        )
    }

    fun writeState(state: AppState) {
        preferences.edit()
            .putBoolean(KEY_ACTIVE, state.isActive)
            .putString(KEY_PHASE, state.phase.name)
            .putLong(KEY_WORKDAY_START, state.workdayStartedAt)
            .putLong(KEY_PHASE_START, state.phaseStartedAt)
            .putLong(KEY_PHASE_END, state.phaseEndsAt)
            .putString(KEY_PATTERN, encodePattern(state.pattern))
            .putInt(KEY_CURRENT_BLOCK_INDEX, state.currentBlockIndex)
            .putInt(KEY_COMPLETED_BREAKS, state.completedBreaks)
            .putBoolean(KEY_HEADS_UP_SHOWN, state.headsUpShown)
            .putString(KEY_PENDING_BOOKMARK, state.pendingBookmark)
            .putString(KEY_RESUME_BOOKMARK, state.resumeBookmark)
            .apply()
    }

    // Write only the note, never a UI snapshot of the timer. Late input cannot undo a break.
    fun updateBookmark(text: String, workdayStartedAt: Long, phaseStartedAt: Long, now: Long): Boolean {
        val current = readState()
        if (!WorkTransition.isHeadsUp(current, now) ||
            current.workdayStartedAt != workdayStartedAt || current.phaseStartedAt != phaseStartedAt
        ) return false
        preferences.edit()
            .putString(KEY_PENDING_BOOKMARK, text.take(WorkTransition.MAX_BOOKMARK_LENGTH))
            .apply()
        return true
    }

    fun updatePattern(pattern: List<BlockPlan>) {
        val safePattern = pattern
            .map { BlockPlan(it.workMinutes.coerceIn(1, 180), it.breakMinutes.coerceIn(1, 60)) }
            .take(MAX_PATTERN_LENGTH)
            .ifEmpty { listOf(BlockPlan.Quick) }
        preferences.edit().putString(KEY_PATTERN, encodePattern(safePattern)).apply()
    }

    fun addRecord(record: WorkdayRecord) {
        val line = listOf(record.startedAt, record.endedAt, record.completedBreaks).joinToString(",")
        val updated = (listOf(line) + rawHistory().lineSequence().filter { it.isNotBlank() })
            .take(MAX_HISTORY)
            .joinToString("\n")
        preferences.edit().putString(KEY_HISTORY, updated).apply()
    }

    fun history(): List<WorkdayRecord> = rawHistory()
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val values = line.split(',')
            if (values.size != 3) return@mapNotNull null
            val start = values[0].toLongOrNull() ?: return@mapNotNull null
            val end = values[1].toLongOrNull() ?: return@mapNotNull null
            val breaks = values[2].toIntOrNull() ?: return@mapNotNull null
            WorkdayRecord(start, end, breaks)
        }
        .toList()

    fun bridgeConfig(): BridgeConfig = BridgeConfig(
        address = preferences.getString(KEY_BRIDGE_ADDRESS, "") ?: "",
        token = preferences.getString(KEY_BRIDGE_TOKEN, "") ?: "",
    )

    fun updateBridgeConfig(config: BridgeConfig) {
        preferences.edit()
            .putString(KEY_BRIDGE_ADDRESS, config.address.trim().trimEnd('/'))
            .putString(KEY_BRIDGE_TOKEN, config.token.trim())
            .apply()
    }

    private fun rawHistory(): String = preferences.getString(KEY_HISTORY, "") ?: ""

    private fun readPattern(): List<BlockPlan> {
        val parsed = preferences.getString(KEY_PATTERN, null)
            ?.split(';')
            ?.mapNotNull { encoded ->
            val values = encoded.split(':')
            if (values.size != 2) return@mapNotNull null
            val work = values[0].toIntOrNull()?.coerceIn(1, 180) ?: return@mapNotNull null
            val rest = values[1].toIntOrNull()?.coerceIn(1, 60) ?: return@mapNotNull null
            BlockPlan(work, rest)
            }
            ?.take(MAX_PATTERN_LENGTH)
            .orEmpty()
        return parsed.ifEmpty { listOf(BlockPlan.Quick) }
    }

    private fun encodePattern(pattern: List<BlockPlan>): String =
        pattern.joinToString(";") { "${it.workMinutes}:${it.breakMinutes}" }

    private companion object {
        const val PREFERENCES = "break_bell"
        const val KEY_ACTIVE = "active"
        const val KEY_PHASE = "phase"
        const val KEY_WORKDAY_START = "workday_start"
        const val KEY_PHASE_START = "phase_start"
        const val KEY_PHASE_END = "phase_end"
        const val KEY_PATTERN = "pattern"
        const val KEY_CURRENT_BLOCK_INDEX = "current_block_index"
        const val KEY_COMPLETED_BREAKS = "completed_breaks"
        const val KEY_HEADS_UP_SHOWN = "heads_up_shown"
        const val KEY_PENDING_BOOKMARK = "pending_bookmark"
        const val KEY_RESUME_BOOKMARK = "resume_bookmark"
        const val KEY_HISTORY = "history"
        const val KEY_BRIDGE_ADDRESS = "bridge_address"
        const val KEY_BRIDGE_TOKEN = "bridge_token"
        const val MAX_HISTORY = 90
        const val MAX_PATTERN_LENGTH = 12
    }
}
