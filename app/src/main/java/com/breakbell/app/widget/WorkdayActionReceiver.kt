package com.breakbell.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.breakbell.app.alarm.SessionEngine

class WorkdayActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = SessionEngine(context)
        when (intent.action) {
            ACTION_START_WORKDAY -> engine.startWorkday()
            ACTION_END_WORKDAY -> engine.endWorkday()
            ACTION_ACKNOWLEDGE_BREAK -> engine.acknowledgeBreak()
            ACTION_DISMISS_SOUND -> engine.dismissCurrentSound()
        }
        BreakBellWidget.refreshAll(context)
    }

    companion object {
        const val ACTION_START_WORKDAY = "com.breakbell.app.action.START_WORKDAY"
        const val ACTION_END_WORKDAY = "com.breakbell.app.action.END_WORKDAY"
        const val ACTION_ACKNOWLEDGE_BREAK = "com.breakbell.app.action.ACKNOWLEDGE_BREAK"
        const val ACTION_DISMISS_SOUND = "com.breakbell.app.action.DISMISS_SOUND"
    }
}
