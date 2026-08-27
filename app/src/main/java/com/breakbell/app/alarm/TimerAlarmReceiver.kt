package com.breakbell.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND)
            ?.let { runCatching { AlarmKind.valueOf(it) }.getOrNull() }
            ?: return
        SessionEngine(context).onAlarm(kind)
    }
}
