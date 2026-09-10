package com.breakbell.app.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.breakbell.app.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransitionIntegrationTest {
    private lateinit var context: Context
    private lateinit var store: BreakBellStore
    private lateinit var engine: SessionEngine
    private lateinit var work: AppState
    private val start = 1_000_000L

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        context.getSharedPreferences("break_bell", Context.MODE_PRIVATE).edit().clear().commit()
        store = BreakBellStore(context)
        engine = SessionEngine(context)
        work = TimerTransitions.startWorkday(AppState(pattern = listOf(BlockPlan(5, 3), BlockPlan.Deep)), start)
        store.writeState(work)
    }

    @Test fun `bookmark autosaves across store recreation without altering timer and rejects late edits`() {
        val at = WorkTransition.headsUpAt(work)
        assertFalse(store.updateBookmark("Too early", start, start, at - 1))
        assertTrue(store.updateBookmark("Next result\nα", start, start, at))
        val reopened = BreakBellStore(context).readState()
        assertEquals(work.copy(pendingBookmark = "Next result\nα"), reopened)
        assertFalse(store.updateBookmark("Old session", start - 1, start, at))
        assertFalse(store.updateBookmark("Old block", start, start - 1, at))
        assertFalse(store.updateBookmark("Late", start, start, work.phaseEndsAt))
        assertEquals("Next result\nα", store.readState().pendingBookmark)
        engine.onAlarm(AlarmKind.WORK_END, work.phaseEndsAt)
        assertFalse(store.updateBookmark("Stale UI", start, start, at))
        assertEquals(Phase.WAITING_FOR_BREAK, store.readState().phase)
        engine.acknowledgeBreak(work.phaseEndsAt + 10_000L)
        val rest = BreakBellStore(context).readState()
        assertEquals(work.phaseEndsAt + 10_000L + 180_000L, rest.phaseEndsAt)
        assertFalse(store.updateBookmark("Still late", start, start, rest.phaseStartedAt))
        SessionEngine(context).onAlarm(AlarmKind.BREAK_END, rest.phaseEndsAt)
        val resumed = BreakBellStore(context).readState()
        assertEquals("Next result\nα", resumed.resumeBookmark)
        assertEquals("", resumed.pendingBookmark)
        assertEquals(BlockPlan.Deep, resumed.currentBlock)
        assertEquals(rest.phaseEndsAt + 45 * 60_000L, resumed.phaseEndsAt)
        assertEquals(1, resumed.completedBreaks)
    }

    @Test fun `editing and clearing are optional and bounded`() {
        val at = WorkTransition.headsUpAt(work)
        assertTrue(store.updateBookmark("x".repeat(800), start, start, at))
        assertEquals(WorkTransition.MAX_BOOKMARK_LENGTH, store.readState().pendingBookmark.length)
        assertTrue(store.updateBookmark("", start, start, at))
        assertEquals("", BreakBellStore(context).readState().pendingBookmark)
    }

    @Test fun `heads up is quiet one shot and leaves mandatory deadline armed`() {
        engine.rescheduleAfterSystemChange(start)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertTrue(alarms.any { it.triggerAtTime == work.phaseEndsAt })
        assertTrue(alarms.any { it.triggerAtTime == WorkTransition.headsUpAt(work) })
        val manager = context.getSystemService(NotificationManager::class.java)
        engine.onAlarm(AlarmKind.WORK_HEADS_UP, WorkTransition.headsUpAt(work) - 1)
        assertNull(shadowOf(manager).getNotification(TransitionNotification.NOTIFICATION_ID))
        engine.onAlarm(AlarmKind.WORK_HEADS_UP, WorkTransition.headsUpAt(work))
        val notification = shadowOf(manager).getNotification(TransitionNotification.NOTIFICATION_ID)
        assertNotNull(notification)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertNull(notification.fullScreenIntent)
        assertTrue(notification.actions.isNullOrEmpty())
        assertEquals(work.copy(headsUpShown = true), store.readState())
        assertEquals(NotificationManager.IMPORTANCE_LOW,
            manager.getNotificationChannel(TransitionNotification.CHANNEL_ID).importance)
        assertEquals("Find a stopping point—break in 1 minute.",
            notification.extras.getString(Notification.EXTRA_TITLE))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
        manager.cancel(TransitionNotification.NOTIFICATION_ID)
        SessionEngine(context).onAlarm(AlarmKind.WORK_HEADS_UP, WorkTransition.headsUpAt(work) + 1)
        assertNull(shadowOf(manager).getNotification(TransitionNotification.NOTIFICATION_ID))
        engine.onAlarm(AlarmKind.WORK_END, work.phaseEndsAt)
        assertEquals(Phase.WAITING_FOR_BREAK, store.readState().phase)
        assertEquals(0L, store.readState().phaseEndsAt)
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .any { it.triggerAtTime == work.phaseEndsAt + SessionEngine.NAG_INTERVAL_MILLIS })
        assertEquals(AlarmSoundService.ALERT_BREAK_DUE,
            shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
                .getStringExtra(AlarmSoundService.EXTRA_ALERT_TYPE))
    }

    @Test fun `restoration during heads up catches up once and overdue work skips heads up`() {
        val at = WorkTransition.headsUpAt(work) + 10_000L
        engine.rescheduleAfterSystemChange(at)
        assertTrue(store.readState().headsUpShown)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(shadowOf(manager).getNotification(TransitionNotification.NOTIFICATION_ID))
        manager.cancel(TransitionNotification.NOTIFICATION_ID)
        SessionEngine(context).rescheduleAfterSystemChange(at + 1)
        assertNull(shadowOf(manager).getNotification(TransitionNotification.NOTIFICATION_ID))
        store.writeState(work)
        engine.rescheduleAfterSystemChange(work.phaseEndsAt)
        assertEquals(Phase.WAITING_FOR_BREAK, store.readState().phase)
        engine.onAlarm(AlarmKind.WORK_HEADS_UP, work.phaseEndsAt)
        assertNull(shadowOf(manager).getNotification(TransitionNotification.NOTIFICATION_ID))
    }

    @Test fun `empty bookmark cannot block enforcement and break must still be acknowledged`() {
        engine.onAlarm(AlarmKind.BREAK_END, work.phaseEndsAt)
        assertEquals(Phase.WORK, store.readState().phase)
        engine.onAlarm(AlarmKind.WORK_END, work.phaseEndsAt)
        engine.onAlarm(AlarmKind.BREAK_END, work.phaseEndsAt + 1)
        assertEquals(Phase.WAITING_FOR_BREAK, store.readState().phase)
        engine.dismissCurrentSound()
        assertEquals(Phase.WAITING_FOR_BREAK, store.readState().phase)
        engine.onAlarm(AlarmKind.BREAK_NAG, work.phaseEndsAt + 60_000L)
        assertEquals(Phase.WAITING_FOR_BREAK, store.readState().phase)
        engine.acknowledgeBreak(work.phaseEndsAt + 70_000L)
        val rest = store.readState()
        engine.acknowledgeBreak(rest.phaseStartedAt + 1)
        assertEquals(rest, store.readState())
        engine.onAlarm(AlarmKind.WORK_HEADS_UP, rest.phaseStartedAt + 1)
        assertEquals(rest, store.readState())
        engine.onAlarm(AlarmKind.BREAK_END, rest.phaseEndsAt)
        assertEquals(Phase.WORK, store.readState().phase)
        assertEquals("", store.readState().resumeBookmark)
        assertEquals(1, store.readState().completedBreaks)
    }

    @Test fun `ending workday clears transition state and all alarms`() {
        store.updateBookmark("Draft", start, start, WorkTransition.headsUpAt(work))
        engine.rescheduleAfterSystemChange(WorkTransition.headsUpAt(work))
        engine.endWorkday(work.phaseEndsAt - 1)
        val idle = BreakBellStore(context).readState()
        assertFalse(idle.isActive)
        assertEquals("", idle.pendingBookmark)
        assertEquals("", idle.resumeBookmark)
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertNull(shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(TransitionNotification.NOTIFICATION_ID))
    }

    @Test fun `restoring a break preserves its deadline and saved bookmark until completion`() {
        val rest = TimerTransitions.beginBreak(
            TimerTransitions.markBreakDue(work.copy(pendingBookmark = "Continue here"), work.phaseEndsAt),
            work.phaseEndsAt + 20_000L)
        store.writeState(rest)
        engine.rescheduleAfterSystemChange(rest.phaseEndsAt - 1)
        assertEquals(rest, store.readState())
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(rest.phaseEndsAt, alarms.single().triggerAtTime)
        SessionEngine(context).rescheduleAfterSystemChange(rest.phaseEndsAt)
        assertEquals("Continue here", store.readState().resumeBookmark)
        assertEquals(1, store.readState().completedBreaks)
    }

    @Test fun `unavailable quiet notification cannot delay the break`() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        engine.rescheduleAfterSystemChange(WorkTransition.headsUpAt(work))
        assertEquals(work.phaseEndsAt, store.readState().phaseEndsAt)
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .any { it.triggerAtTime == work.phaseEndsAt })
        assertNull(shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(TransitionNotification.NOTIFICATION_ID))
        engine.onAlarm(AlarmKind.WORK_END, work.phaseEndsAt)
        assertEquals(Phase.WAITING_FOR_BREAK, store.readState().phase)
    }
}
