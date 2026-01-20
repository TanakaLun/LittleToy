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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tl.snake.logic.*
import kotlin.random.Random

@Composable
fun GameContent(
    state: SnakeState,
    settings: GameSettings,
    persistentHS: Int,
    onHSReset: () -> Unit,
    onStateChange: (SnakeState) -> Unit,
    onHandleInput: (Float, Float) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isNewRecord = state.isGameOver && state.score > state.highScore

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Status Bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = {}, onLongClick = onHSReset),
                    color = colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, null, Modifier.size(18.dp), tint = colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("BEST", style = MaterialTheme.typography.labelSmall)
                            Text("${if(state.score > persistentHS) state.score else persistentHS}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                InputChip(selected = true, onClick = {}, label = { Text("${state.score}", fontWeight = FontWeight.Bold) }, leadingIcon = { Icon(Icons.Default.Score, null, Modifier.size(18.dp)) })
            }

            // Board Area (严格遵照参考逻辑)
            BoxWithConstraints(Modifier.fillMaxSize().weight(1f).padding(20.dp)) {
                val density = LocalContext.current.resources.displayMetrics.density
                val cellBasePx = 22f * density
                val gridW = if (settings.dynamicGrid) (constraints.maxWidth / cellBasePx).toInt().coerceIn(10, 30) else 20
                val gridH = if (settings.dynamicGrid) (constraints.maxHeight / cellBasePx).toInt().coerceIn(10, 45) else 20
                val cellSizePx = (constraints.maxWidth.toFloat() / gridW).coerceAtMost(constraints.maxHeight.toFloat() / gridH)
                val dW = cellSizePx * gridW
                val dH = cellSizePx * gridH

                LaunchedEffect(gridW, gridH) {
                    if (state.gridWidth != gridW || state.gridHeight != gridH) onStateChange(state.copy(gridWidth = gridW, gridHeight = gridH))
                }

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size((dW / density).dp, (dH / density).dp).clip(RoundedCornerShape(16.dp)).background(colorScheme.surfaceVariant.copy(0.5f))
                        .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                            if (state.isStarted && !state.isPaused && !state.isGameOver) {
                                detectDragGestures { change, drag -> change.consume(); onHandleInput(drag.x, drag.y) }
                            }
                        }
                    ) {
                        val dimAlpha by animateFloatAsState(if (!state.isStarted) 0.8f else 0f, label = "dim")
                        Canvas(Modifier.fillMaxSize().graphicsLayer(alpha = 1f - dimAlpha)) {
                            if (settings.showGrid) {
                                for (i in 0..gridW) drawLine(colorScheme.onSurface.copy(0.05f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, dH))
                                for (i in 0..gridH) drawLine(colorScheme.onSurface.copy(0.05f), Offset(0f, i * cellSizePx), Offset(dW, i * cellSizePx))
                            }
                            state.objects.forEach { obj ->
                                drawCircle(obj.type.color, cellSizePx / 3f, Offset(obj.pos.first * cellSizePx + cellSizePx / 2f, obj.pos.second * cellSizePx + cellSizePx / 2f))
                            }
                            state.snake.forEachIndexed { i, p ->
                                val color = if (i == 0) colorScheme.primary else colorScheme.primary.copy(alpha = 0.6f)
                                drawRoundRect(color, Offset(p.first * cellSizePx + 1f, p.second * cellSizePx + 1f), Size(cellSizePx - 2f, cellSizePx - 2f), CornerRadius(6.dp.toPx()))
                            }
                        }
                        if (!state.isStarted) {
                            val focusRequester = remember { FocusRequester() }
                            Button(onClick = { onStateChange(state.copy(isStarted = true, isPaused = false)) }, modifier = Modifier.align(Alignment.Center).focusRequester(focusRequester)) {
                                Text("START GAME")
                            }
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        }
                    }
                }
            }
        }
        if (isNewRecord) ConfettiEffect()
    }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    AlertDialog(onDismissRequest = onDismiss, 
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp), modifier = Modifier.focusRequester(focusRequester)) { Text("Apply") } },
        title = { Text("Configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingSection("General") {
                    SettingToggle("Dynamic Grid", settings.dynamicGrid) { onUpdate(settings.copy(dynamicGrid = it)) }
                    SettingToggle("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                    SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                    SettingToggle("Haptic", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                }
                SettingSection("Items") {
                    ItemType.entries.forEach { type ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(type.icon, null, Modifier.size(20.dp), tint = type.color)
                                Text(type.label, Modifier.padding(start = 12.dp))
                            }
                            Checkbox(checked = settings.enabledItems[type] == true, onCheckedChange = { 
                                val m = settings.enabledItems.toMutableMap(); m[type] = it; onUpdate(settings.copy(enabledItems = m))
                            })
                        }
                    }
                }
            }
        }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp)); content()
        HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}

@Composable
fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label); Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun StatsList(items: Map<ItemType, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ItemType.entries.forEach { type ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(type.icon, null, Modifier.size(18.dp), tint = type.color)
                    Text(type.label, Modifier.padding(start = 8.dp))
                }
                Text("${items[type] ?: 0}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ResultDialog(state: SnakeState, onRestart: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    AlertDialog(onDismissRequest = {}, confirmButton = { Button(onClick = onRestart, modifier = Modifier.focusRequester(focusRequester)) { Text("REPLAY") } },
        title = { Text("Game Over") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.score}", fontSize = 56.sp, fontWeight = FontWeight.Black)
                StatsList(state.itemsCollected)
            }
        }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun PauseStatsDialog(state: SnakeState, onResume: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    AlertDialog(onDismissRequest = onResume, confirmButton = { Button(onClick = onResume, modifier = Modifier.focusRequester(focusRequester)) { Text("RESUME") } },
        title = { Text("Paused") }, text = { StatsList(state.itemsCollected) }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun ConfettiEffect() {
    val particles = remember { List(50) { ConfettiParticle() } }
    val progress by rememberInfiniteTransition(label = "").animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "")
    Canvas(Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val y = (p.startY + (progress * 1500f * p.speed)) % size.height
            val x = p.startX + (progress * 200f * p.drift)
            drawRect(p.color, Offset(x, y), Size(15f, 30f), alpha = 1f - (y / size.height).coerceIn(0f, 1f))
        }
    }
}

class ConfettiParticle {
    val startX = Random.nextFloat() * 1000f
    val startY = -Random.nextFloat() * 1000f
    val speed = Random.nextFloat() * 0.5f + 0.5f
    val drift = Random.nextFloat() * 2f - 1f
    val color = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), 1f)
}
