package io.tl.snake.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tl.snake.logic.*
import kotlin.random.Random

@Composable
fun MainGameContent(
    state: SnakeState,
    settings: GameSettings,
    isTV: Boolean,
    onStateChange: (SnakeState) -> Unit,
    onOpenSettings: () -> Unit,
    onDirectionChange: (Float, Float) -> Unit
) {
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(Modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(Modifier.fillMaxSize().padding(16.dp)) {
                Column(Modifier.width(160.dp).fillMaxHeight(), Arrangement.Center) {
                    ScoreChip(Icons.Default.EmojiEvents, "HI", state.highScore, MaterialTheme.colorScheme.outline)
                    ScoreChip(Icons.Default.MilitaryTech, "SC", state.score, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    StatsList(state.itemsCollected)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SnakeCanvas(state, settings, onDirectionChange, onStateChange)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    ScoreChip(Icons.Default.EmojiEvents, "HI", state.highScore, MaterialTheme.colorScheme.outline)
                    Text("SNAKE EVO", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    ScoreChip(Icons.Default.MilitaryTech, "SC", state.score, MaterialTheme.colorScheme.primary)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SnakeCanvas(state, settings, onDirectionChange, onStateChange)
                }
            }
        }

        // 手机端专用控制按钮 (左下角/右下角)
        if (!isTV && state.isStarted && !state.isGameOver) {
            Row(Modifier.align(Alignment.BottomEnd).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onOpenSettings, shape = CircleShape, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(Icons.Default.Settings, "Settings")
                }
                FloatingActionButton(onClick = { onStateChange(state.copy(isPaused = !state.isPaused)) }, shape = CircleShape) {
                    Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, "Pause")
                }
            }
        }

        if (state.isGameOver && state.score > state.highScore) ConfettiEffect()
    }
}

@Composable
fun SnakeCanvas(state: SnakeState, settings: GameSettings, onDirectionChange: (Float, Float) -> Unit, onStateChange: (SnakeState) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    
    BoxWithConstraints(Modifier.aspectRatio(1f).padding(16.dp).clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceVariant.copy(0.3f)).border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))) {
        val density = LocalContext.current.resources.displayMetrics.density
        val cellSizePx = settings.targetCellSize * density
        
        // 动态棋盘计算：根据容器宽度自动调整格子数量
        val calcGridW = (constraints.maxWidth / cellSizePx).toInt()
        val calcGridH = (constraints.maxHeight / cellSizePx).toInt()

        LaunchedEffect(calcGridW, calcGridH, settings.dynamicGrid) {
            if (settings.dynamicGrid && (state.gridWidth != calcGridW || state.gridHeight != calcGridH)) {
                onStateChange(state.copy(gridWidth = calcGridW, gridHeight = calcGridH))
            }
        }

        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures { change, drag -> change.consume(); onDirectionChange(drag.x, drag.y) }
        }) {
            if (settings.showGrid) {
                for (i in 0..state.gridWidth) drawLine(colorScheme.onSurface.copy(0.05f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, state.gridHeight * cellSizePx))
                for (i in 0..state.gridHeight) drawLine(colorScheme.onSurface.copy(0.05f), Offset(0f, i * cellSizePx), Offset(state.gridWidth * cellSizePx, i * cellSizePx))
            }
            state.objects.forEach { obj ->
                drawCircle(obj.type.color, cellSizePx * 0.4f, Offset(obj.pos.first * cellSizePx + cellSizePx/2, obj.pos.second * cellSizePx + cellSizePx/2))
            }
            state.snake.forEachIndexed { i, p ->
                val color = when {
                    i == 0 && state.invincibleTime > 0 -> Color.Cyan
                    i == 0 && state.ghostTimeRemaining > 0 -> ItemType.GHOST.color
                    i == 0 && state.shieldCount > 0 -> ItemType.SHIELD.color
                    else -> colorScheme.primary
                }
                drawRoundRect(color.copy(alpha = (1f - i.toFloat()/state.snake.size).coerceAtLeast(0.3f)), Offset(p.first * cellSizePx + 2f, p.second * cellSizePx + 2f), Size(cellSizePx-4f, cellSizePx-4f), CornerRadius(4.dp.toPx()))
            }
        }
        
        if (!state.isStarted) {
            val focusRequester = remember { FocusRequester() }
            Button(onClick = { onStateChange(state.copy(isStarted = true)) }, Modifier.align(Alignment.Center).focusRequester(focusRequester)) {
                Text("START GAME")
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }
    }
}

@Composable
fun ResultDialog(state: SnakeState, onRestart: () -> Unit, onSettings: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    AlertDialog(onDismissRequest = {}, 
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSettings) { Text("SETTINGS") }
                Button(onClick = onRestart, Modifier.focusRequester(focusRequester)) { Text("REPLAY") }
            }
        },
        title = { Text("GAME OVER") },
        text = {
            Column(Modifier.fillMaxWidth(), Alignment.CenterHorizontally) {
                Text("${state.score}", fontSize = 56.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                StatsList(state.itemsCollected)
            }
        }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    AlertDialog(onDismissRequest = onDismiss, 
        confirmButton = { Button(onClick = onDismiss, Modifier.focusRequester(focusRequester)) { Text("DONE") } },
        title = { Text("SETTINGS") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingToggle("Dynamic Grid", settings.dynamicGrid) { onUpdate(settings.copy(dynamicGrid = it)) }
                SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                SettingToggle("Vibration", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                ItemType.entries.forEach { type ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(type.icon, null, Modifier.size(18.dp), tint = type.color)
                            Text(type.label, Modifier.padding(start = 12.dp))
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
        Text(label); Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun StatsList(items: Map<ItemType, Int>) {
    val collected = items.filter { it.value > 0 }.toList()
    Column(Alignment.CenterHorizontally) {
        collected.chunked(2).forEach { row ->
            Row(Modifier.padding(vertical = 2.dp), Arrangement.spacedBy(16.dp)) {
                row.forEach { (type, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(type.icon, null, Modifier.size(14.dp), tint = type.color)
                        Text("${type.label}: $count", fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreChip(icon: ImageVector, label: String, value: Int, color: Color) {
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Text(" $label: $value", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ConfettiEffect() {
    val progress by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)))
    Canvas(Modifier.fillMaxSize()) {
        repeat(40) { i ->
            val x = (Random(i).nextFloat() * size.width)
            val y = (progress * size.height * (1f + i % 5 * 0.2f)) % size.height
            drawRect(Color(Random(i).nextInt()), Offset(x, y), Size(12f, 24f))
        }
    }
}
