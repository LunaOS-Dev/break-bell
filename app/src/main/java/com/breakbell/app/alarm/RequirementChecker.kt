package com.breakbell.app.alarm

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class AlarmRequirements(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreenAlarms: Boolean,
    val doNotDisturbAccess: Boolean,
) {
    val isArmed: Boolean get() = notifications && exactAlarms && fullScreenAlarms
}

object RequirementChecker {
    fun read(context: Context): AlarmRequirements {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)
        val exact = AlarmScheduler(context).canScheduleExactAlarms()
        val fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()

        return AlarmRequirements(
            notifications = notifications,
            exactAlarms = exact,
            fullScreenAlarms = fullScreen,
            doNotDisturbAccess = notificationManager.isNotificationPolicyAccessGranted,
        )
    }
}
