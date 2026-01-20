package io.tl.snake.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tl.snake.logic.*
import kotlin.random.Random

@Composable
fun MainGameContent(state: SnakeState, settings: GameSettings, onStateChange: (SnakeState) -> Unit, onDirectionChange: (Float, Float) -> Unit) {
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(160.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    ScoreChip(Icons.Default.EmojiEvents, "BEST", state.highScore, MaterialTheme.colorScheme.outline)
                    ScoreChip(Icons.Default.MilitaryTech, "SCORE", state.score, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    StatsList(state.itemsCollected)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SnakeCanvas(state, settings, onDirectionChange, onStateChange)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.SpaceBetween) {
                    ScoreChip(Icons.Default.EmojiEvents, "HI", state.highScore, MaterialTheme.colorScheme.outline)
                    Text("SNAKE EVO", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    ScoreChip(Icons.Default.MilitaryTech, "SC", state.score, MaterialTheme.colorScheme.primary)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SnakeCanvas(state, settings, onDirectionChange, onStateChange)
                }
            }
        }
        if (state.isGameOver && state.score > state.highScore) ConfettiEffect()
    }
}

@Composable
fun SnakeCanvas(state: SnakeState, settings: GameSettings, onDirectionChange: (Float, Float) -> Unit, onStateChange: (SnakeState) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "decay")
    val decayAlpha by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(200), RepeatMode.Reverse), label = "alpha")
    
    BoxWithConstraints(Modifier.aspectRatio(1f).padding(16.dp).clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceVariant.copy(0.3f)).border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))) {
        val density = LocalContext.current.resources.displayMetrics.density
        val cellSizePx = settings.targetCellSize * density
        val gridW = (constraints.maxWidth / cellSizePx).toInt()
        val gridH = (constraints.maxHeight / cellSizePx).toInt()

        LaunchedEffect(gridW, gridH) {
            if (state.gridWidth != gridW || state.gridHeight != gridH) onStateChange(state.copy(gridWidth = gridW, gridHeight = gridH))
        }

        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures { change, drag -> change.consume(); onDirectionChange(drag.x, drag.y) }
        }) {
            if (settings.showGrid) {
                for (i in 0..gridW) drawLine(colorScheme.onSurface.copy(0.05f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, size.height))
                for (i in 0..gridH) drawLine(colorScheme.onSurface.copy(0.05f), Offset(0f, i * cellSizePx), Offset(size.width, i * cellSizePx))
            }
            state.objects.forEach { obj ->
                val alpha = if (settings.enableItemDecay && obj.timeLeft < 5000L) decayAlpha else 1f
                drawCircle(obj.type.color.copy(alpha), cellSizePx * 0.35f, Offset(obj.pos.first * cellSizePx + cellSizePx/2, obj.pos.second * cellSizePx + cellSizePx/2))
            }
            state.snake.forEachIndexed { i, p ->
                val color = when {
                    i == 0 && state.invincibleTimeRemaining > 0 -> Color(0xFF00E5FF)
                    i == 0 && (state.ghostTimeRemaining > 0 || settings.isGhostPermanent) -> ItemType.GHOST.color
                    i == 0 && state.shieldCount > 0 -> ItemType.SHIELD.color
                    else -> colorScheme.primary
                }
                drawRoundRect(color.copy(alpha = (1f - i.toFloat()/state.snake.size).coerceAtLeast(0.3f)), Offset(p.first * cellSizePx + 2f, p.second * cellSizePx + 2f), Size(cellSizePx-4f, cellSizePx-4f), CornerRadius(4.dp.toPx()))
            }
        }
        if (!state.isStarted) Button(onClick = { onStateChange(state.copy(isStarted = true)) }, Modifier.align(Alignment.Center)) { Text("START") }
    }
}

@Composable
fun ResultDialog(state: SnakeState, onRestart: () -> Unit, onSettings: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    AlertDialog(onDismissRequest = {}, 
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSettings) { Text("SETTINGS") }
                Button(onClick = onRestart, modifier = Modifier.focusRequester(focusRequester)) { Text("REPLAY") }
            }
        },
        title = { Text("Game Over", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.score}", fontSize = 52.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp)); StatsList(state.itemsCollected)
            }
        }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    AlertDialog(onDismissRequest = onDismiss, 
        confirmButton = { Button(onClick = onDismiss, modifier = Modifier.focusRequester(focusRequester)) { Text("OK") } },
        title = { Text("Configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                SettingToggle("Vibration", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ItemType.entries.forEach { type ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(type.icon, null, Modifier.size(16.dp), tint = type.color)
                            Text(type.label, Modifier.padding(start = 8.dp), fontSize = 14.sp)
                        }
                        Checkbox(checked = settings.enabledItems[type] == true, onCheckedChange = {
                            val m = settings.enabledItems.toMutableMap(); m[type] = it; onUpdate(settings.copy(enabledItems = m))
                        })
                    }
                }
            }
        }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp); Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun StatsList(items: Map<ItemType, Int>) {
    val collected = items.filter { it.value > 0 }.toList()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        collected.chunked(2).forEach { row ->
            Row(Modifier.padding(vertical = 2.dp), Arrangement.spacedBy(12.dp)) {
                row.forEach { (type, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(type.icon, null, Modifier.size(12.dp), tint = type.color)
                        Text("${type.label}: $count", fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreChip(icon: ImageVector, label: String, value: Int, color: Color) {
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Text(" $label: $value", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ConfettiEffect() {
    val particles = remember { List(60) { ConfettiParticle() } }
    val progress by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)))
    Canvas(Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val y = (p.startY + (progress * 2000f * p.speed)) % size.height
            val x = p.startX + (progress * 400f * p.drift)
            drawRect(p.color, Offset(x, y), Size(12f, 24f), alpha = 1f - (y/size.height))
        }
    }
}

class ConfettiParticle {
    val startX = Random.nextFloat() * 2000f
    val startY = -Random.nextFloat() * 1000f
    val speed = Random.nextFloat() * 0.6f + 0.4f
    val drift = Random.nextFloat() * 2f - 1f
    val color = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), 1f)
}
