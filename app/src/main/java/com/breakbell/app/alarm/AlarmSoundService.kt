package com.breakbell.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.breakbell.app.R
import com.breakbell.app.widget.WorkdayActionReceiver

class AlarmSoundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopAlarm()
            ACTION_START -> startAlarm(intent.getStringExtra(EXTRA_ALERT_TYPE) ?: ALERT_BREAK_DUE)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseResources()
        super.onDestroy()
    }

    private fun startAlarm(alertType: String) {
        releaseResources()
        val notification = buildNotification(alertType)
        startForeground(NOTIFICATION_ID, notification)

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BreakBell:AlarmSound",
        ).apply { acquire(ALARM_DURATION_MILLIS + 2_000L) }

        val uri = Settings.System.DEFAULT_ALARM_ALERT_URI
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
        player = createPlayer(uri)?.apply {
            isLooping = true
            start()
        }

        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 500L, 500L), 0))

        stopRunnable = Runnable { stopAlarm() }.also {
            handler.postDelayed(it, ALARM_DURATION_MILLIS)
        }
    }

    private fun createPlayer(uri: Uri): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(this@AlarmSoundService, uri)
            prepare()
        }
    }.getOrNull()

    private fun buildNotification(alertType: String): Notification {
        val isBreakDue = alertType == ALERT_BREAK_DUE
        val title = if (isBreakDue) "Your work block is over" else "Break complete"
        val body = if (isBreakDue) {
            "Acknowledge that you are stepping away."
        } else {
            "Your next work block has started."
        }

        val fullScreenIntent = PendingIntent.getActivity(
            this,
            501,
            Intent(this, AlarmActivity::class.java)
                .putExtra(EXTRA_ALERT_TYPE, alertType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val action = if (isBreakDue) {
            WorkdayActionReceiver.ACTION_ACKNOWLEDGE_BREAK
        } else {
            WorkdayActionReceiver.ACTION_DISMISS_SOUND
        }
        val actionIntent = PendingIntent.getBroadcast(
            this,
            502,
            Intent(this, WorkdayActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(
                R.drawable.ic_timer,
                if (isBreakDue) "I'M ON BREAK" else "DISMISS",
                actionIntent,
            )
            .build()
    }

    private fun stopAlarm() {
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseResources() {
        stopRunnable?.let(handler::removeCallbacks)
        stopRunnable = null
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        wakeLock?.runCatching { if (isHeld) release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "com.breakbell.app.action.START_SOUND"
        const val ACTION_STOP = "com.breakbell.app.action.STOP_SOUND"
        const val EXTRA_ALERT_TYPE = "alert_type"
        const val ALERT_BREAK_DUE = "break_due"
        const val ALERT_BREAK_COMPLETE = "break_complete"
        const val CHANNEL_ID = "break_alarms"
        const val NOTIFICATION_ID = 41
        const val ALARM_DURATION_MILLIS = 10_000L

        fun createNotificationChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 500L, 500L)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                if (manager.isNotificationPolicyAccessGranted) {
                    setBypassDnd(true)
                }
            }
            manager.createNotificationChannel(channel)
        }
    }
}
