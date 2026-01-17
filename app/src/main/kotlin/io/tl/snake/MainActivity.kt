package io.tl.snake

import android.content.Context
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tl.snake.logic.*
import io.tl.snake.ui.theme.MyTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
        enableEdgeToEdge()
        setContent {
            MyTheme {
                var settings by remember { mutableStateOf(GameSettings()) }
                var showSet by remember { mutableStateOf(false) }
                var state by remember { mutableStateOf(SnakeState(highScore = sp.getInt("hs", 0))) }
                val context = LocalContext.current

                LaunchedEffect(state.isGameOver) {
                    if (state.isGameOver && settings.enableVibration) {
                        try {
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                        } catch (e: Exception) {}
                    }
                }

                LaunchedEffect(state.highScore) { sp.edit().putInt("hs", state.highScore).apply() }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("SNAKE EVO", fontWeight = FontWeight.Black) },
                            actions = {
                                if (state.isStarted && !state.isGameOver) {
                                    IconButton(onClick = { state = state.copy(isPaused = !state.isPaused) }) {
                                        Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                                    }
                                }
                                IconButton(onClick = { 
                                    state = state.copy(isPaused = true) 
                                    showSet = true 
                                }) { Icon(Icons.Default.Settings, null) }
                            }
                        )
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        GameContent(state, settings, onStateChange = { state = it })
                        if (showSet) {
                            SettingsDialog(settings, { showSet = false }) { settings = it }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameContent(state: SnakeState, settings: GameSettings, onStateChange: (SnakeState) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val currentState by rememberUpdatedState(state)

    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(0.75f, 1.05f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "s")

    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!currentState.isGameOver && !currentState.isPaused && currentState.isStarted) {
            val speed = (GameConfig.BASE_SPEED - (currentState.score / 100 * 5) + currentState.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
            delay(speed)
            var next = gameTick(currentState, settings)
            if (next.isInvincible) {
                val rem = next.invincibleTimeLeft - speed
                next = if (rem <= 0) next.copy(isInvincible = false) else next.copy(invincibleTimeLeft = rem)
            }
            onStateChange(next)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            ScoreColumn("HIGH", state.highScore, colorScheme.secondary)
            SpeedIndicator((GameConfig.BASE_SPEED - (state.score/100*5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED))
            ScoreColumn("SCORE", state.score, colorScheme.primary)
        }

        BoxWithConstraints(Modifier.fillMaxSize().weight(1f)) {
            val cellBase = 22.dp
            val density = LocalContext.current.resources.displayMetrics.density
            
            val gridW = if (settings.dynamicGrid) (constraints.maxWidth / (cellBase.value * density)).toInt().coerceIn(10, 30) else 20
            val gridH = if (settings.dynamicGrid) (constraints.maxHeight / (cellBase.value * density)).toInt().coerceIn(10, 45) else 20
            
            // 关键修复：显式转换为 Float 以适配 Offset 和 Size
            val cellSizePx = (constraints.maxWidth.toFloat() / gridW).coerceAtMost(constraints.maxHeight.toFloat() / gridH)
            val canvasWidthPx = cellSizePx * gridW
            val canvasHeightPx = cellSizePx * gridH

            LaunchedEffect(gridW, gridH) {
                if (state.gridWidth != gridW || state.gridHeight != gridH) {
                    onStateChange(state.copy(gridWidth = gridW, gridHeight = gridH))
                }
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size((canvasWidthPx / density).dp, (canvasHeightPx / density).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceContainerHigh)
                        .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                            if (state.isStarted && !state.isPaused && !state.isGameOver) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    onStateChange(handleSwipe(currentState, drag.x, drag.y))
                                }
                            }
                        }
                ) {
                    val dimAlpha by animateFloatAsState(if (!state.isStarted) 0.8f else 0f, label = "dim")

                    Canvas(Modifier.fillMaxSize().graphicsLayer(alpha = 1f - dimAlpha)) {
                        // 修复：所有坐标计算均显式 Float 化
                        if (settings.showGrid) {
                            for (i in 0..gridW) drawLine(colorScheme.outlineVariant.copy(0.2f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, size.height), 1f)
                            for (i in 0..gridH) drawLine(colorScheme.outlineVariant.copy(0.2f), Offset(0f, i * cellSizePx), Offset(size.width, i * cellSizePx), 1f)
                        }
                        
                        state.objects.forEach { obj ->
                            drawCircle(obj.type.color, (cellSizePx / 3f) * pulse, Offset(obj.pos.first * cellSizePx + cellSizePx / 2f, obj.pos.second * cellSizePx + cellSizePx / 2f))
                        }

                        state.snake.forEachIndexed { i, p ->
                            val fraction = i.toFloat() / state.snake.size.coerceAtLeast(1)
                            val baseColor = if (i == 0) colorScheme.primary else colorScheme.primaryContainer
                            val color = baseColor.copy(alpha = (1f - fraction * 0.7f).coerceAtLeast(0.3f))
                            
                            // 修复：绘制矩形参数 Float 化
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(p.first * cellSizePx + 1.5f, p.second * cellSizePx + 1.5f),
                                size = Size(cellSizePx - 3f, cellSizePx - 3f),
                                cornerRadius = CornerRadius(if(i==0) 6.dp.toPx() else 4.dp.toPx())
                            )
                        }
                    }

                    if (!state.isStarted) {
                        Button(
                            onClick = { onStateChange(state.copy(isStarted = true, isPaused = false)) },
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("START GAME", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (state.isGameOver) {
        ResultDialog(state) { onStateChange(SnakeState(highScore = state.highScore, isStarted = true, gridWidth = state.gridWidth, gridHeight = state.gridHeight)) }
    } else if (state.isPaused) {
        PauseStatsDialog(state) { onStateChange(state.copy(isPaused = false)) }
    }
}

@Composable
fun ResultDialog(state: SnakeState, onRestart: () -> Unit) {
    AlertDialog(onDismissRequest = {}, confirmButton = { Button(onClick = onRestart) { Text("RESTART") } },
        title = { Text(if(state.score > state.highScore) "🎊 NEW RECORD!" else "GAME OVER", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("${state.score}", fontSize = 56.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                StatsList(state.itemsCollected)
            }
        }
    )
}

@Composable
fun PauseStatsDialog(state: SnakeState, onResume: () -> Unit) {
    AlertDialog(onDismissRequest = onResume, confirmButton = { Button(onClick = onResume) { Text("RESUME") } },
        title = { Text("GAME PAUSED") },
        text = { StatsList(state.itemsCollected) }
    )
}

@Composable
fun StatsList(items: Map<ItemType, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ItemType.entries.forEach { type ->
            val count = items[type] ?: 0
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(type.label, color = type.color.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                Text("$count", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SpeedIndicator(current: Long) {
    val progress = ((150 - current).toFloat() / 90 * 100).toInt().coerceIn(0, 100)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SPEED", style = MaterialTheme.typography.labelSmall)
        Text("$progress/100", fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun ScoreColumn(l: String, v: Int, c: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(l, style = MaterialTheme.typography.labelSmall)
        Text("$v", style = MaterialTheme.typography.titleLarge, color = c, fontWeight = FontWeight.Bold)
    }
}

fun handleSwipe(s: SnakeState, x: Float, y: Float): SnakeState {
    val d = when {
        Math.abs(x) > Math.abs(y) -> if(x > 0 && s.direction != Direction.LEFT) Direction.RIGHT else if(x < 0 && s.direction != Direction.RIGHT) Direction.LEFT else s.direction
        else -> if(y > 0 && s.direction != Direction.UP) Direction.DOWN else if(y < 0 && s.direction != Direction.DOWN) Direction.UP else s.direction
    }
    return s.copy(direction = d)
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Game Settings") }, confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ToggleRow("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                ToggleRow("Dynamic Grid", settings.dynamicGrid) { onUpdate(settings.copy(dynamicGrid = it)) }
                ToggleRow("Loop", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                ToggleRow("Vibration", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                
                Column {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Max Items", style = MaterialTheme.typography.bodyMedium)
                        Text("${settings.maxObjects}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    // 改进：增加 steps 属性，实现有档位的滑动 (1-10 共 9 个档位)
                    Slider(
                        value = settings.maxObjects.toFloat(),
                        onValueChange = { onUpdate(settings.copy(maxObjects = it.toInt())) },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            }
        }
    )
}

@Composable
fun ToggleRow(l: String, v: Boolean, onClear: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(l, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Switch(v, onClear)
    }
}
