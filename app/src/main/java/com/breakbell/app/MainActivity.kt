package com.breakbell.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakbell.app.alarm.AlarmRequirements
import com.breakbell.app.alarm.AlarmSoundService
import com.breakbell.app.alarm.RequirementChecker
import com.breakbell.app.alarm.SessionEngine
import com.breakbell.app.bridge.AgentBridge
import com.breakbell.app.bridge.BridgeConnectionResult
import com.breakbell.app.data.AppState
import com.breakbell.app.data.BlockPlan
import com.breakbell.app.data.BreakBellStore
import com.breakbell.app.data.BridgeConfig
import com.breakbell.app.data.Phase
import com.breakbell.app.data.WorkdayRecord
import com.breakbell.app.ui.theme.BreakBellTheme
import com.breakbell.app.ui.theme.Ink
import com.breakbell.app.ui.theme.Muted
import com.breakbell.app.ui.theme.Paper
import com.breakbell.app.ui.theme.Rule
import com.breakbell.app.ui.theme.Sage
import com.breakbell.app.ui.theme.Signal
import com.breakbell.app.widget.BreakBellWidget
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmSoundService.createNotificationChannel(this)
        setContent {
            BreakBellTheme {
                BreakBellApp()
            }
        }
    }

    companion object {
        const val ACTION_OPEN_SETUP = "com.breakbell.app.action.OPEN_SETUP"
    }
}

@Composable
private fun BreakBellApp() {
    val context = LocalContext.current
    val activity = context as Activity
    val store = remember { BreakBellStore(context) }
    val engine = remember { SessionEngine(context) }
    var state by remember { mutableStateOf(store.readState()) }
    var requirements by remember { mutableStateOf(RequirementChecker.read(context)) }
    var history by remember { mutableStateOf(store.history()) }
    var bridgeConfig by remember { mutableStateOf(store.bridgeConfig()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        requirements = RequirementChecker.read(context)
        BreakBellWidget.refreshAll(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            state = store.readState()
            history = store.history()
            requirements = RequirementChecker.read(context)
            delay(1_000L)
        }
    }

    LaunchedEffect(requirements.doNotDisturbAccess) {
        AlarmSoundService.createNotificationChannel(context)
    }

    val background by animateColorAsState(
        targetValue = when (state.phase) {
            Phase.WORK -> Ink
            Phase.BREAK -> Sage
            Phase.WAITING_FOR_BREAK -> Signal
            Phase.IDLE -> Paper
        },
        label = "phase background",
    )

    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        if (state.isActive) {
            ActiveWorkday(
                state = state,
                now = now,
                onAcknowledge = { engine.acknowledgeBreak() },
                onEnd = { engine.endWorkday() },
            )
        } else {
            SetupWorkday(
                state = state,
                requirements = requirements,
                history = history,
                bridgeConfig = bridgeConfig,
                onPatternChanged = {
                    store.updatePattern(it)
                    state = store.readState()
                    BreakBellWidget.refreshAll(context)
                },
                onStart = { engine.startWorkday() },
                onBridgeChanged = { config, onResult ->
                    store.updateBridgeConfig(config)
                    bridgeConfig = store.bridgeConfig()
                    AgentBridge.verify(state, bridgeConfig, onResult)
                },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppNotificationSettings(activity)
                    }
                },
                onRequestExactAlarm = { openExactAlarmSettings(activity) },
                onRequestFullScreen = { openFullScreenSettings(activity) },
                onRequestDnd = {
                    activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                },
            )
        }
    }
}

@Composable
private fun SetupWorkday(
    state: AppState,
    requirements: AlarmRequirements,
    history: List<WorkdayRecord>,
    bridgeConfig: BridgeConfig,
    onPatternChanged: (List<BlockPlan>) -> Unit,
    onStart: () -> Unit,
    onBridgeChanged: (BridgeConfig, (BridgeConnectionResult) -> Unit) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onRequestDnd: () -> Unit,
) {
    var patternMode by remember(state.pattern) { mutableStateOf(state.pattern.size > 1) }
    var customWork by remember { mutableIntStateOf(35) }
    var customBreak by remember { mutableIntStateOf(8) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Text(
            "BREAK BELL",
            color = Signal,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            letterSpacing = 2.2.sp,
        )
        Spacer(Modifier.height(30.dp))
        Text(
            "Build today's rhythm.",
            color = Ink,
            fontWeight = FontWeight.Black,
            fontSize = 42.sp,
            lineHeight = 45.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Choose one repeating block or arrange a pattern. Your plan locks when the day starts.",
            color = Muted,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(30.dp))

        ModeSelector(
            patternMode = patternMode,
            onModeChanged = { wantsPattern ->
                patternMode = wantsPattern
                if (!wantsPattern && state.pattern.size > 1) {
                    onPatternChanged(listOf(state.pattern.first()))
                }
            },
        )
        Spacer(Modifier.height(24.dp))
        SectionLabel(if (patternMode) "ADD TO PATTERN" else "CHOOSE A BLOCK")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlockPlan.presets.forEach { block ->
                PresetButton(
                    block = block,
                    selected = !patternMode && state.pattern.firstOrNull() == block,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onPatternChanged(if (patternMode) state.pattern + block else listOf(block))
                    },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("TODAY'S LOOP")
        Spacer(Modifier.height(8.dp))
        state.pattern.forEachIndexed { index, block ->
            PatternRow(
                index = index,
                block = block,
                canRemove = state.pattern.size > 1,
                onRemove = { onPatternChanged(state.pattern.toMutableList().also { it.removeAt(index) }) },
            )
        }

        Spacer(Modifier.height(22.dp))
        Text("Custom block", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stepper(
                label = "WORK",
                value = customWork,
                range = 1..180,
                onValueChanged = { customWork = it },
                modifier = Modifier.weight(1f),
            )
            Stepper(
                label = "BREAK",
                value = customBreak,
                range = 1..60,
                onValueChanged = { customBreak = it },
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(
            onClick = {
                val custom = BlockPlan(customWork, customBreak)
                onPatternChanged(if (patternMode) state.pattern + custom else listOf(custom))
            },
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (patternMode) "Add custom block" else "Use custom block")
        }

        Spacer(Modifier.height(22.dp))
        ArmStatus(
            requirements = requirements,
            onRequestNotifications = onRequestNotifications,
            onRequestExactAlarm = onRequestExactAlarm,
            onRequestFullScreen = onRequestFullScreen,
            onRequestDnd = onRequestDnd,
        )
        Spacer(Modifier.height(20.dp))
        AgentBridgeSettings(config = bridgeConfig, onSave = onBridgeChanged)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            enabled = requirements.isArmed,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            shape = RoundedCornerShape(19.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Signal, disabledContainerColor = Rule),
        ) {
            Text("START WORKDAY", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(42.dp))
            SectionLabel("RECENT WORKDAYS")
            Spacer(Modifier.height(8.dp))
            history.take(7).forEach { HistoryRow(it) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActiveWorkday(
    state: AppState,
    now: Long,
    onAcknowledge: () -> Unit,
    onEnd: () -> Unit,
) {
    val contentColor = Paper
    val remaining = max(0L, state.phaseEndsAt - now)
    val total = when (state.phase) {
        Phase.WORK -> state.currentBlock.workMinutes * 60_000L
        Phase.BREAK -> state.currentBlock.breakMinutes * 60_000L
        else -> 1L
    }
    val progress = if (state.phaseEndsAt > 0L) remaining.toFloat() / total else 1f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("BREAK BELL", color = contentColor.copy(alpha = .68f), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            TextButton(onClick = onEnd, colors = ButtonDefaults.textButtonColors(contentColor = contentColor)) {
                Text("END DAY", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(0.55f))
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "phase",
        ) { phase ->
            Text(
                when (phase) {
                    Phase.WORK -> "${state.currentBlock.displayName.uppercase(Locale.US)} · FOCUS"
                    Phase.BREAK -> "ON BREAK"
                    Phase.WAITING_FOR_BREAK -> "BREAK OVERDUE"
                    Phase.IDLE -> "READY"
                },
                color = contentColor.copy(alpha = .76f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )
        }
        Spacer(Modifier.height(22.dp))
        TimerDial(
            remainingMillis = remaining,
            progress = progress,
            waiting = state.phase == Phase.WAITING_FOR_BREAK,
            color = contentColor,
        )
        Spacer(Modifier.height(28.dp))

        if (state.phase == Phase.WAITING_FOR_BREAK) {
            Button(
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Signal),
            ) {
                Text("I'M ON BREAK", fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Rings for 10 seconds every minute until acknowledged.",
                color = contentColor.copy(alpha = .7f),
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                if (state.phase == Phase.WORK) "Break: ${state.currentBlock.breakMinutes} min" else "Next block starts automatically",
                color = contentColor.copy(alpha = .7f),
                fontSize = 16.sp,
            )
        }

        Spacer(Modifier.weight(0.7f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Metric("STARTED", formatTime(state.workdayStartedAt), contentColor)
            Metric("BREAKS", state.completedBreaks.toString(), contentColor, Alignment.End)
        }
    }
}

@Composable
private fun TimerDial(remainingMillis: Long, progress: Float, waiting: Boolean, color: Color) {
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f), label = "timer progress")
    Box(modifier = Modifier.size(278.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = color.copy(alpha = .16f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        AnimatedContent(
            targetState = if (waiting) "OVERDUE" else formatDuration(remainingMillis),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "countdown",
        ) { display ->
            Text(
                display,
                color = color,
                fontSize = if (waiting) 32.sp else 54.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = if (waiting) 1.sp else (-1).sp,
            )
        }
    }
}

@Composable
private fun ModeSelector(patternMode: Boolean, onModeChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Rule.copy(alpha = .55f), RoundedCornerShape(16.dp)).padding(4.dp),
    ) {
        listOf(false to "LOOP ONE", true to "LOOP PATTERN").forEach { (mode, label) ->
            val selected = patternMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) Ink else Color.Transparent, RoundedCornerShape(13.dp))
                    .clickable { onModeChanged(mode) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected) Paper else Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PresetButton(block: BlockPlan, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = Ink)
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(16.dp),
        colors = colors,
        border = if (selected) null else ButtonDefaults.outlinedButtonBorder(enabled = true),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(block.displayName, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text("${block.workMinutes}/${block.breakMinutes}", fontSize = 12.sp)
        }
    }
}

@Composable
private fun PatternRow(index: Int, block: BlockPlan, canRemove: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text((index + 1).toString(), color = Paper, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(block.displayName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("${block.workMinutes} min work · ${block.breakMinutes} min break", color = Muted, fontSize = 14.sp)
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove block", tint = Muted)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onValueChanged: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color.White.copy(alpha = .45f), RoundedCornerShape(16.dp)).padding(10.dp)) {
        Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onValueChanged((value - 1).coerceAtLeast(range.first)) }) {
                Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label")
            }
            Text("$value", color = Ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
            IconButton(onClick = { onValueChanged((value + 1).coerceAtMost(range.last)) }) {
                Icon(Icons.Rounded.Add, contentDescription = "Increase $label")
            }
        }
        Text("MIN", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ArmStatus(
    requirements: AlarmRequirements,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onRequestDnd: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(20.dp)).padding(18.dp)) {
        Text(
            if (requirements.isArmed) "READY TO RING" else "ARM BREAK BELL",
            color = if (requirements.isArmed) Color(0xFF9AD5B8) else Color(0xFFFFAB96),
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (requirements.isArmed) "Required Android access is on. The home-screen widget can start your day."
            else "Allow these once so alarms arrive on time and open over the lock screen.",
            color = Paper.copy(alpha = .7f),
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(12.dp))
        RequirementRow("Notifications", requirements.notifications, onRequestNotifications)
        RequirementRow("Exact alarms", requirements.exactAlarms, onRequestExactAlarm)
        RequirementRow("Full-screen alarms", requirements.fullScreenAlarms, onRequestFullScreen)
        RequirementRow("Do Not Disturb override", requirements.doNotDisturbAccess, onRequestDnd, optional = true)
    }
}

@Composable
private fun RequirementRow(label: String, granted: Boolean, onClick: () -> Unit, optional: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !granted) { onClick() }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Rounded.Check else Icons.Rounded.Add,
            contentDescription = null,
            tint = if (granted) Color(0xFF9AD5B8) else Paper,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = Paper, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(
            if (granted) "ON" else if (optional) "OPTIONAL" else "ALLOW",
            color = Paper.copy(alpha = .55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AgentBridgeSettings(
    config: BridgeConfig,
    onSave: (BridgeConfig, (BridgeConnectionResult) -> Unit) -> Unit,
) {
    var address by remember(config) { mutableStateOf(config.address) }
    var token by remember(config) { mutableStateOf(config.token) }
    var result by remember { mutableStateOf<BridgeConnectionResult?>(null) }
    var checking by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("AGENT BRIDGE · OPTIONAL")
        Spacer(Modifier.height(7.dp))
        Text(
            "Pair your computer so agents know when human maintenance is due. Only timer phase is sent.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it; result = null },
            label = { Text("Pairing address") },
            placeholder = { Text("http://192.168.1.20:8765") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; result = null },
            label = { Text("Pairing token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        TextButton(
            onClick = {
                checking = true
                result = null
                onSave(BridgeConfig(address, token)) {
                    result = it
                    checking = false
                }
            },
            enabled = address.isNotBlank() && token.isNotBlank() && !checking,
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(if (result == BridgeConnectionResult.CONNECTED) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (checking) "Checking…" else "Save and verify")
        }
        result?.let { bridgeResult ->
            Text(
                text = when (bridgeResult) {
                    BridgeConnectionResult.CONNECTED -> "Connected — authenticated status received."
                    BridgeConnectionResult.TOKEN_REJECTED -> "Token rejected — check every character and try again."
                    BridgeConnectionResult.UNREACHABLE -> "Computer unreachable — connect to the same Wi-Fi and allow Private network access."
                    BridgeConnectionResult.SERVER_ERROR -> "Bridge returned an error — restart it and try again."
                },
                color = if (bridgeResult == BridgeConnectionResult.CONNECTED) Sage else Signal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun HistoryRow(record: WorkdayRecord) {
    val durationMinutes = (record.endedAt - record.startedAt) / 60_000L
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(formatDate(record.startedAt), color = Ink, fontWeight = FontWeight.Bold)
            Text("${formatTime(record.startedAt)} – ${formatTime(record.endedAt)}", color = Muted, fontSize = 14.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMinutes(durationMinutes), color = Ink, fontWeight = FontWeight.Bold)
            Text("${record.completedBreaks} breaks", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = alignment) {
        Text(label, color = color.copy(alpha = .56f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
}

private fun openAppNotificationSettings(activity: Activity) {
    activity.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
    )
}

private fun openExactAlarmSettings(activity: Activity) {
    activity.startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:${activity.packageName}")),
    )
}

private fun openFullScreenSettings(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.parse("package:${activity.packageName}")),
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%02d:%02d".format(Locale.US, totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("h:mm a"))

private fun formatDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE, MMM d"))

private fun formatMinutes(minutes: Long): String = if (minutes < 60L) {
    "${minutes}m"
} else {
    "${minutes / 60L}h ${minutes % 60L}m"
}
