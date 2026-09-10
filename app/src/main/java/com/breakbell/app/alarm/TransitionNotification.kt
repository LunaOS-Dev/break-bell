package com.breakbell.app.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.breakbell.app.MainActivity
import com.breakbell.app.R
import com.breakbell.app.data.AppState
import com.breakbell.app.data.WorkTransition

class TransitionNotification(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(state: AppState, now: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Quiet transitions", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        val openApp = PendingIntent.getActivity(
            context, 901, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(WorkTransition.message(state, now))
                .setContentText("Optional bookmark: Next, I’m going to ___.")
                .setContentIntent(openApp)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setTimeoutAfter((state.phaseEndsAt - now).coerceAtLeast(1L))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build(),
        )
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    companion object {
        const val CHANNEL_ID = "quiet_transitions"
        const val NOTIFICATION_ID = 42
    }
}
