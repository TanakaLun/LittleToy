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

                // 振动处理
                LaunchedEffect(state.isGameOver) {
                    if (state.isGameOver && settings.enableVibration) {
                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                        } else {
                            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        }
                        if (state.score > state.highScore) {
                            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1))
                        } else {
                            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                }

                LaunchedEffect(state.highScore) { sp.edit().putInt("hs", state.highScore).apply() }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("SNAKE EVO", fontWeight = FontWeight.Black) },
                            actions = {
                                IconButton(onClick = { 
                                    if (state.isStarted && !state.isGameOver) {
                                        state = state.copy(isPaused = true)
                                        showSet = true 
                                    }
                                }) { Icon(Icons.Default.Settings, null) }
                            }
                        )
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        GameContent(state = state, settings = settings, onStateChange = { state = it })
                        
                        if (showSet) {
                            SettingsDialog(settings, { 
                                showSet = false
                                if (!state.isGameOver) state = state.copy(isPaused = false) 
                            }) { settings = it }
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

    // --- 游戏循环 ---
    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!currentState.isGameOver && !currentState.isPaused && currentState.isStarted) {
            val dynamicDelay = (GameConfig.BASE_SPEED - (currentState.score / 100 * 5) + currentState.speedModifier)
                .coerceAtLeast(GameConfig.MIN_SPEED)
            delay(dynamicDelay)
            
            var next = gameTick(currentState, settings)
            if (next.isInvincible) {
                val rem = next.invincibleTimeLeft - dynamicDelay
                next = if (rem <= 0) next.copy(isInvincible = false) else next.copy(invincibleTimeLeft = rem)
            }
            onStateChange(next)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        // 动态适配棋盘尺寸
        val density = LocalContext.current.resources.displayMetrics.density
        val availableWidth = constraints.maxWidth / density
        val availableHeight = (constraints.maxHeight - 150 * density) / density // 减去分数栏空间
        
        val size = if (settings.dynamicGrid) (availableWidth / 20).coerceAtMost(availableHeight / 20) else 18f
        val gridW = (availableWidth / size).toInt().coerceAtMost(30)
        val gridH = (availableHeight / size).toInt().coerceAtMost(40)

        // 初始化棋盘
        LaunchedEffect(gridW, gridH) {
            if (state.gridWidth != gridW || state.gridHeight != gridH) {
                onStateChange(state.copy(gridWidth = gridW, gridHeight = gridH))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 分数与速度组件
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                ScoreColumn("HIGH", state.highScore, colorScheme.secondary)
                
                // 速度组件 now/max
                SpeedIndicator(
                    current = (GameConfig.BASE_SPEED - (state.score/100*5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED),
                    color = colorScheme.outline
                )
                
                ScoreColumn("SCORE", state.score, colorScheme.primary)
            }

            Spacer(Modifier.height(16.dp))

            // 棋盘区域
            Box(
                modifier = Modifier
                    .weight(1f).aspectRatio(gridW.toFloat() / gridH)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                        if (state.isStarted && !state.isPaused && !state.isGameOver) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                onStateChange(handleSwipe(currentState, drag.x, drag.y))
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // 背景变暗蒙版（未开始时）
                val dimAlpha by animateFloatAsState(if (!state.isStarted) 0.6f else 0f, label = "")

                Canvas(Modifier.fillMaxSize().padding(8.dp).graphicsLayer(alpha = 1f - dimAlpha)) {
                    val cS = size.dp.toPx()
                    if (settings.showGrid) {
                        for (i in 0..state.gridWidth) drawLine(colorScheme.outlineVariant.copy(0.15f), Offset(i*cS, 0f), Offset(i*cS, gridH*cS), 1f)
                        for (i in 0..state.gridHeight) drawLine(colorScheme.outlineVariant.copy(0.15f), Offset(0f, i*cS), Offset(gridW*cS, i*cS), 1f)
                    }
                    // 蛇身
                    state.snake.forEachIndexed { i, p ->
                        val color = when {
                            state.isInvincible -> colorScheme.tertiary.copy(alpha = 0.6f)
                            state.isGhostMode -> colorScheme.primary.copy(alpha = 0.5f)
                            else -> if (i == 0) colorScheme.primary else colorScheme.primaryContainer
                        }
                        drawRoundRect(color, Offset(p.first*cS+1f, p.second*cS+1f), Size(cS-2f, cS-2f), CornerRadius(4.dp.toPx()))
                    }
                    // 食物与道具
                    drawCircle(colorScheme.error, cS/3f, Offset(state.food.first*cS+cS/2, state.food.second*cS+cS/2))
                    state.specialItem?.let {
                        val itemColor = when(it.type) {
                            ItemType.SHIELD -> Color(0xFF4CAF50)
                            ItemType.SLOW -> Color(0xFF2196F3)
                            ItemType.GHOST -> Color(0xFF9C27B0)
                            else -> Color.Gray
                        }
                        drawRect(itemColor, Offset(it.pos.first*cS+4f, it.pos.second*cS+4f), Size(cS-8f, cS-8f))
                    }
                }

                // 未开始时的 Start 按钮 (更方正，强调感)
                if (!state.isStarted) {
                    Surface(
                        onClick = { onStateChange(state.copy(isStarted = true)) },
                        color = colorScheme.primary,
                        shape = RoundedCornerShape(12.dp), // 减小圆角
                        shadowElevation = 12.dp
                    ) {
                        Text("START GAME", Modifier.padding(horizontal = 40.dp, vertical = 20.dp), 
                            fontWeight = FontWeight.Bold, color = colorScheme.onPrimary)
                    }
                }
            }
        }
        
        // 死亡结算与暂停 Dialog
        if (state.isGameOver) {
            ResultDialog(state) { onStateChange(SnakeState(highScore = state.highScore, isStarted = true, gridWidth = gridW, gridHeight = gridH)) }
        } else if (state.isPaused) {
            PauseStatsDialog(state) { onStateChange(state.copy(isPaused = false)) }
        }
    }
}

@Composable
fun SpeedIndicator(current: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SPEED", style = MaterialTheme.typography.labelSmall, color = color.copy(0.6f))
        // 假设最大速度逻辑：150为最慢，60为最快
        val progress = ((150 - current).toFloat() / (150 - 60) * 100).toInt().coerceIn(0, 100)
        Text("$progress/100", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PauseStatsDialog(state: SnakeState, onResume: () -> Unit) {
    AlertDialog(
        onDismissRequest = onResume,
        title = { Text("PAUSED", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow("Food Eaten", "${state.foodEaten}")
                StatRow("Shields", "${state.itemsCollected[ItemType.SHIELD] ?: 0}")
                StatRow("Slow Potions", "${state.itemsCollected[ItemType.SLOW] ?: 0}")
                StatRow("Ghost Orbs", "${state.itemsCollected[ItemType.GHOST] ?: 0}")
            }
        },
        confirmButton = { Button(onClick = onResume) { Text("CONTINUE") } }
    )
}

@Composable
fun ResultDialog(state: SnakeState, onRestart: () -> Unit) {
    val isNewRecord = state.score > state.highScore && state.score != 0
    val animScale by animateFloatAsState(targetValue = 1.1f, animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "")

    AlertDialog(
        onDismissRequest = {},
        title = { 
            Text(if (isNewRecord) "🎊 NEW RECORD! 🎊" else "GAME OVER", 
                modifier = if(isNewRecord) Modifier.graphicsLayer(scaleX = animScale, scaleY = animScale) else Modifier) 
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("${state.score}", fontSize = 48.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("BEST: ${state.highScore}", style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = { Button(onClick = onRestart, shape = RoundedCornerShape(8.dp)) { Text("PLAY AGAIN") } }
    )
}

@Composable
fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

// ... 保持 ScoreColumn, handleSwipe 逻辑不变 ...

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleRow("Grid Line", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                ToggleRow("Dynamic Board", settings.dynamicGrid) { onUpdate(settings.copy(dynamicGrid = it)) }
                ToggleRow("Haptic Feedback", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                if (settings.isDeveloperMode) {
                    ToggleRow("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                }
                var clicks by remember { mutableIntStateOf(0) }
                TextButton(onClick = { if(++clicks >= 7) onUpdate(settings.copy(isDeveloperMode = true)) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("v${GameConfig.VERSION}", style = MaterialTheme.typography.labelSmall)
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
