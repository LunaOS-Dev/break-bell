package com.breakbell.app.alarm

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.breakbell.app.bridge.AgentBridge
import com.breakbell.app.data.AppState
import com.breakbell.app.data.BreakBellStore
import com.breakbell.app.data.Phase
import com.breakbell.app.data.TimerTransitions
import com.breakbell.app.data.WorkdayRecord
import com.breakbell.app.widget.BreakBellWidget

class SessionEngine(context: Context) {
    private val appContext = context.applicationContext
    private val store = BreakBellStore(appContext)
    private val scheduler = AlarmScheduler(appContext)

    fun startWorkday(now: Long = System.currentTimeMillis()): Boolean {
        val current = store.readState()
        if (current.isActive) return true
        if (!RequirementChecker.read(appContext).isArmed) return false

        val state = TimerTransitions.startWorkday(current, now)
        store.writeState(state)
        return runCatching {
            scheduler.schedule(AlarmKind.WORK_END, state.phaseEndsAt)
            BreakBellWidget.refreshAll(appContext)
            AgentBridge.publish(appContext, state)
            true
        }.getOrElse {
            store.writeState(current)
            false
        }
    }

    fun endWorkday(now: Long = System.currentTimeMillis()) {
        val current = store.readState()
        if (!current.isActive) return
        if (current.workdayStartedAt > 0L && now > current.workdayStartedAt) {
            store.addRecord(WorkdayRecord(current.workdayStartedAt, now, current.completedBreaks))
        }
        scheduler.cancelAll()
        stopSound()
        val idle = current.copy(
                isActive = false,
                phase = Phase.IDLE,
                workdayStartedAt = 0L,
                phaseStartedAt = 0L,
                phaseEndsAt = 0L,
                currentBlockIndex = 0,
                completedBreaks = 0,
            )
        store.writeState(idle)
        BreakBellWidget.refreshAll(appContext)
        AgentBridge.publish(appContext, idle)
    }

    fun acknowledgeBreak(now: Long = System.currentTimeMillis()) {
        val current = store.readState()
        if (!current.isActive || current.phase != Phase.WAITING_FOR_BREAK) return
        scheduler.cancel(AlarmKind.BREAK_NAG)
        stopSound()
        val state = TimerTransitions.beginBreak(current, now)
        store.writeState(state)
        scheduler.schedule(AlarmKind.BREAK_END, state.phaseEndsAt)
        BreakBellWidget.refreshAll(appContext)
        AgentBridge.publish(appContext, state)
    }

    fun dismissCurrentSound() = stopSound()

    fun onAlarm(kind: AlarmKind, now: Long = System.currentTimeMillis()) {
        val current = store.readState()
        if (!current.isActive) return

        when (kind) {
            AlarmKind.WORK_END -> {
                if (current.phase != Phase.WORK) return
                val waiting = TimerTransitions.markBreakDue(current, now)
                store.writeState(waiting)
                scheduler.schedule(AlarmKind.BREAK_NAG, now + NAG_INTERVAL_MILLIS)
                runCatching { startSound(AlarmSoundService.ALERT_BREAK_DUE) }
            }

            AlarmKind.BREAK_NAG -> {
                if (current.phase != Phase.WAITING_FOR_BREAK) return
                scheduler.schedule(AlarmKind.BREAK_NAG, now + NAG_INTERVAL_MILLIS)
                runCatching { startSound(AlarmSoundService.ALERT_BREAK_DUE) }
            }

            AlarmKind.BREAK_END -> {
                if (current.phase != Phase.BREAK) return
                val working = TimerTransitions.beginNextWorkBlock(current, now)
                store.writeState(working)
                scheduler.schedule(AlarmKind.WORK_END, working.phaseEndsAt)
                runCatching { startSound(AlarmSoundService.ALERT_BREAK_COMPLETE) }
            }
        }
        BreakBellWidget.refreshAll(appContext)
        AgentBridge.publish(appContext, store.readState())
    }

    fun rescheduleAfterSystemChange(now: Long = System.currentTimeMillis()) {
        val state = store.readState()
        scheduler.cancelAll()
        if (!state.isActive || !scheduler.canScheduleExactAlarms()) return

        when (state.phase) {
            Phase.WORK -> if (state.phaseEndsAt <= now) {
                onAlarm(AlarmKind.WORK_END, now)
            } else {
                scheduler.schedule(AlarmKind.WORK_END, state.phaseEndsAt)
            }

            Phase.BREAK -> if (state.phaseEndsAt <= now) {
                onAlarm(AlarmKind.BREAK_END, now)
            } else {
                scheduler.schedule(AlarmKind.BREAK_END, state.phaseEndsAt)
            }

            Phase.WAITING_FOR_BREAK -> scheduler.schedule(AlarmKind.BREAK_NAG, now + 1_000L)
            Phase.IDLE -> Unit
        }
        BreakBellWidget.refreshAll(appContext)
        AgentBridge.publish(appContext, store.readState())
    }

    private fun startSound(alertType: String) {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, AlarmSoundService::class.java)
                .setAction(AlarmSoundService.ACTION_START)
                .putExtra(AlarmSoundService.EXTRA_ALERT_TYPE, alertType),
        )
    }

    private fun stopSound() {
        appContext.stopService(Intent(appContext, AlarmSoundService::class.java))
    }

    companion object {
        const val NAG_INTERVAL_MILLIS = 60_000L
    }
}
