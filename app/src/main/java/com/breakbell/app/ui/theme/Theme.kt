package com.breakbell.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF171713)
val Paper = Color(0xFFF5F2E8)
val Signal = Color(0xFFE85D3F)
val Sage = Color(0xFF2F6654)
val Muted = Color(0xFF6E6A61)
val Rule = Color(0xFFD8D3C5)

private val BreakBellColors = lightColorScheme(
    primary = Signal,
    onPrimary = Color.White,
    secondary = Sage,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    outline = Rule,
)

@Composable
fun BreakBellTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BreakBellColors,
        content = content,
    )
}
