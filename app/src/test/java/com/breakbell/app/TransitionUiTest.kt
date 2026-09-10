package com.breakbell.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.breakbell.app.alarm.AlarmScreen
import com.breakbell.app.data.*
import com.breakbell.app.ui.theme.BreakBellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class TransitionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `typing saves without submit and editor yields to break then displays bookmark`() {
        val store = BreakBellStore(RuntimeEnvironment.getApplication())
        val work = TimerTransitions.startWorkday(AppState(), 1_000_000L)
        store.writeState(work)
        val state = mutableStateOf(work)
        val now = mutableStateOf(WorkTransition.headsUpAt(work))
        compose.setContent {
            BreakBellTheme {
                ActiveWorkday(state.value, now.value, onAcknowledge = {}, onEnd = {},
                    onBookmarkChanged = {
                        store.updateBookmark(it, work.workdayStartedAt, work.phaseStartedAt, now.value)
                        state.value = store.readState()
                    })
            }
        }
        compose.onNodeWithText("Find a stopping point—break in 2 minutes.").assertExists()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Review the next result")
        compose.runOnIdle {
            assertEquals("Review the next result", BreakBellStore(RuntimeEnvironment.getApplication()).readState().pendingBookmark)
            assertEquals(work.phaseEndsAt, store.readState().phaseEndsAt)
            now.value = work.phaseEndsAt
        }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle {
            state.value = TimerTransitions.markBreakDue(store.readState(), now.value)
        }
        compose.onNodeWithText("I'M STEPPING AWAY").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle {
            val rest = TimerTransitions.beginBreak(state.value, now.value)
            now.value = rest.phaseEndsAt
            state.value = TimerTransitions.beginNextWorkBlock(rest, now.value)
        }
        compose.onNodeWithText("Next, I’m going to Review the next result").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun `completion screen shows saved next step with existing back to work action`() {
        compose.setContent {
            BreakBellTheme {
                AlarmScreen(isBreakDue = false, roast = "", bookmark = "Check the chart", onAction = {})
            }
        }
        compose.onNodeWithText("Next, I’m going to Check the chart").assertExists()
        compose.onNodeWithText("BACK TO WORK").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }
}
