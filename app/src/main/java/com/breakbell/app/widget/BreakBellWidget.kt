package com.breakbell.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.breakbell.app.MainActivity
import com.breakbell.app.R
import com.breakbell.app.alarm.RequirementChecker
import com.breakbell.app.data.BreakBellStore
import com.breakbell.app.data.Phase
import java.text.DateFormat
import java.util.Date

class BreakBellWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BreakBellWidget::class.java)
            manager.getAppWidgetIds(component).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val state = BreakBellStore(context).readState()
            val armed = RequirementChecker.read(context).isArmed
            val views = RemoteViews(context.packageName, R.layout.widget_break_bell)

            views.setTextViewText(R.id.widget_phase, phaseLabel(state.phase))
            views.setTextViewText(
                R.id.widget_status,
                when {
                    !state.isActive && !armed -> "Open app to arm"
                    !state.isActive -> "Ready to start"
                    state.phase == Phase.WAITING_FOR_BREAK -> "Take your break"
                    state.phaseEndsAt > 0L -> "Until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.phaseEndsAt))}"
                    else -> "Workday active"
                },
            )
            views.setTextViewText(R.id.widget_action, if (state.isActive) "END" else "START")

            val actionIntent = if (!state.isActive && !armed) {
                PendingIntent.getActivity(
                    context,
                    801,
                    Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_SETUP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getBroadcast(
                    context,
                    802,
                    Intent(context, WorkdayActionReceiver::class.java).setAction(
                        if (state.isActive) WorkdayActionReceiver.ACTION_END_WORKDAY
                        else WorkdayActionReceiver.ACTION_START_WORKDAY,
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            views.setOnClickPendingIntent(R.id.widget_action, actionIntent)

            val openApp = PendingIntent.getActivity(
                context,
                803,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            manager.updateAppWidget(widgetId, views)
        }

        private fun phaseLabel(phase: Phase): String = when (phase) {
            Phase.IDLE -> "BREAK BELL"
            Phase.WORK -> "FOCUSING"
            Phase.WAITING_FOR_BREAK -> "BREAK OVERDUE"
            Phase.BREAK -> "BREAK TIMER"
        }
    }
}
