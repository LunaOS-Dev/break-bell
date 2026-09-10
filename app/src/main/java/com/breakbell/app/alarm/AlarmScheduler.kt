package com.breakbell.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.breakbell.app.MainActivity

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    @Throws(SecurityException::class)
    fun schedule(kind: AlarmKind, triggerAtMillis: Long) {
        val operation = PendingIntent.getBroadcast(
            context,
            kind.requestCode,
            Intent(context, TimerAlarmReceiver::class.java)
                .setAction(ACTION_TIMER_ALARM)
                .putExtra(EXTRA_ALARM_KIND, kind.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showApp = PendingIntent.getActivity(
            context,
            900,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (kind == AlarmKind.WORK_HEADS_UP) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            return
        }
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showApp),
            operation,
        )
    }

    fun cancel(kind: AlarmKind) {
        val operation = PendingIntent.getBroadcast(
            context,
            kind.requestCode,
            Intent(context, TimerAlarmReceiver::class.java).setAction(ACTION_TIMER_ALARM),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    fun cancelAll() = AlarmKind.entries.forEach(::cancel)

    companion object {
        const val ACTION_TIMER_ALARM = "com.breakbell.app.action.TIMER_ALARM"
        const val EXTRA_ALARM_KIND = "alarm_kind"
    }
}
