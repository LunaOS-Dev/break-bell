package com.breakbell.app.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakbell.app.data.BreakBellStore
import com.breakbell.app.data.BreakRoastContext
import com.breakbell.app.data.BreakRoasts
import com.breakbell.app.data.Phase
import com.breakbell.app.ui.theme.BreakBellTheme

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showAlarm()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showAlarm()
    }

    private fun showAlarm() {
        val state = BreakBellStore(this).readState()
        val isWaiting = state.phase == Phase.WAITING_FOR_BREAK
        val roast = BreakRoasts.message(
            BreakRoastContext.BREAK_DUE,
            System.currentTimeMillis() - state.phaseStartedAt,
        )
        setContent {
            BreakBellTheme {
                AlarmScreen(
                    isBreakDue = isWaiting,
                    roast = roast,
                    bookmark = if (state.phase == Phase.WORK) state.resumeBookmark else "",
                    onAction = {
                        if (isWaiting) SessionEngine(this).acknowledgeBreak()
                        else SessionEngine(this).dismissCurrentSound()
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }
}

@Composable
internal fun AlarmScreen(isBreakDue: Boolean, roast: String, bookmark: String, onAction: () -> Unit) {
    BackHandler(enabled = isBreakDue) { }
    val ink = Color(0xFF171713)
    val paper = Color(0xFFF5F2E8)
    val signal = Color(0xFFE85D3F)

    Box(
        modifier = Modifier.fillMaxSize().background(if (isBreakDue) signal else ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (isBreakDue) "TIME TO STEP AWAY" else "BREAK COMPLETE",
                color = paper.copy(alpha = 0.76f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (isBreakDue) roast else "Your next work\nblock has started.",
                color = paper,
                fontSize = 42.sp,
                lineHeight = 47.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(52.dp))
            if (!isBreakDue && bookmark.isNotBlank()) {
                Text("Next, I’m going to $bookmark", color = paper, fontSize = 20.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
            }
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = paper,
                    contentColor = if (isBreakDue) signal else ink,
                ),
            ) {
                Text(
                    if (isBreakDue) "I'M STEPPING AWAY" else "BACK TO WORK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
            }
            if (isBreakDue) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Alarm repeats each minute. Tapping starts the timer; leaving the keyboard is the break.",
                    color = paper.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
