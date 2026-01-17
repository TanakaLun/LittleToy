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

                // 震动处理（增加异常捕获防止未授权闪退）
                LaunchedEffect(state.isGameOver) {
                    if (state.isGameOver && settings.enableVibration) {
                        try {
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            if (state.score > state.highScore) {
                                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1))
                            } else {
                                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                LaunchedEffect(state.highScore) { sp.edit().putInt("hs", state.highScore).apply() }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("SNAKE EVO", fontWeight = FontWeight.Black) },
                            actions = {
                                IconButton(onClick = { showSet = true }) { Icon(Icons.Default.Settings, null) }
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

    // 动效：缩放与呼吸
    val infinite = rememberInfiniteTransition()
    val pulse by infinite.animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(600), RepeatMode.Reverse))

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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 顶部栏
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            ScoreColumn("HIGH", state.highScore, colorScheme.secondary)
            SpeedIndicator((GameConfig.BASE_SPEED - (state.score/100*5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED))
            ScoreColumn("SCORE", state.score, colorScheme.primary)
        }

        Spacer(Modifier.height(16.dp))

        // 动态棋盘测算
        BoxWithConstraints(Modifier.fillMaxSize().weight(1f)) {
            val cellPx = 20.dp.value // 假设基准大小
            val gridW = (constraints.maxWidth / cellPx).toInt().coerceIn(10, 30)
            val gridH = (constraints.maxHeight / cellPx).toInt().coerceIn(10, 40)
            
            // 棋盘布局约束：让画布精确等于格数*格大
            val canvasW = gridW * cellPx
            val canvasH = gridH * cellPx

            LaunchedEffect(gridW, gridH) {
                if (state.gridWidth != gridW || state.gridHeight != gridH) {
                    onStateChange(state.copy(gridWidth = gridW, gridHeight = gridH))
                }
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(canvasW.dp, canvasH.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceContainerHigh)
                        .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                onStateChange(handleSwipe(currentState, drag.x, drag.y))
                            }
                        }
                ) {
                    val dimAlpha by animateFloatAsState(if (!state.isStarted) 0.7f else 0f)

                    Canvas(Modifier.fillMaxSize().graphicsLayer(alpha = 1f - dimAlpha)) {
                        val cS = size.width / gridW
                        if (settings.showGrid) {
                            for (i in 0..gridW) drawLine(colorScheme.outlineVariant.copy(0.1f), Offset(i*cS, 0f), Offset(i*cS, size.height))
                            for (i in 0..gridH) drawLine(colorScheme.outlineVariant.copy(0.1f), Offset(0f, i*cS), Offset(size.width, i*cS))
                        }
                        // 绘制物体
                        state.objects.forEach { obj ->
                            drawCircle(
                                color = obj.type.color,
                                radius = (cS / 3) * pulse,
                                center = Offset(obj.pos.first * cS + cS / 2, obj.pos.second * cS + cS / 2)
                            )
                        }
                        // 绘制蛇
                        state.snake.forEachIndexed { i, p ->
                            val color = if (i == 0) colorScheme.primary else colorScheme.primaryContainer.copy(alpha = if(state.isGhostMode) 0.5f else 1f)
                            drawRoundRect(color, Offset(p.first*cS+1f, p.second*cS+1f), Size(cS-2f, cS-2f), CornerRadius(4.dp.toPx()))
                        }
                    }

                    if (!state.isStarted) {
                        Button(
                            onClick = { onStateChange(state.copy(isStarted = true, isPaused = false)) },
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("START GAME", Modifier.padding(8.dp))
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
fun SpeedIndicator(current: Long) {
    val progress = ((150 - current).toFloat() / 90 * 100).toInt().coerceIn(0, 100)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SPEED", style = MaterialTheme.typography.labelSmall)
        Text("$progress/100", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PauseStatsDialog(state: SnakeState, onResume: () -> Unit) {
    AlertDialog(onDismissRequest = onResume, confirmButton = { Button(onClick = onResume) { Text("RESUME") } },
        title = { Text("STATS") },
        text = {
            Column {
                StatRow("Food Eaten", "${state.foodEaten}")
                ItemType.values().filter { it.score > 10 || it.weight < 0.1f }.forEach {
                    StatRow(it.name, "${state.itemsCollected[it] ?: 0}")
                }
            }
        }
    )
}

@Composable
fun ResultDialog(state: SnakeState, onRestart: () -> Unit) {
    val isNewRecord = state.score > state.highScore && state.score > 0
    AlertDialog(onDismissRequest = {}, confirmButton = { Button(onClick = onRestart) { Text("RESTART") } },
        title = { Text(if(isNewRecord) "🎊 NEW RECORD!" else "GAME OVER") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.score}", fontSize = 48.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("BEST: ${state.highScore}")
            }
        }
    )
}

@Composable
fun StatRow(l: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
        Text(l, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontWeight = FontWeight.Bold)
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
        Math.abs(x) > Math.abs(y) -> if(x>0 && s.direction != Direction.LEFT) Direction.RIGHT else if(x<0 && s.direction != Direction.RIGHT) Direction.LEFT else s.direction
        else -> if(y>0 && s.direction != Direction.UP) Direction.DOWN else if(y<0 && s.direction != Direction.DOWN) Direction.UP else s.direction
    }
    return s.copy(direction = d)
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Settings") }, confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow("Grid Line", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                ToggleRow("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                ToggleRow("Vibration", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Max Objects")
                    Slider(settings.maxObjects.toFloat(), { onUpdate(settings.copy(maxObjects = it.toInt())) }, valueRange = 1f..10f)
                }
            }
        }
    )
}

@Composable
fun ToggleRow(l: String, v: Boolean, onClear: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(l); Spacer(Modifier.weight(1f)); Switch(v, onClear)
    }
}
