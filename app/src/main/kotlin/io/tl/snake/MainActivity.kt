package io.tl.snake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.tl.snake.logic.* // 引入逻辑包
import io.tl.snake.ui.theme.MyTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyTheme {
                var settings by remember { mutableStateOf(GameSettings()) }
                var showSettings by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Snake MD3", style = MaterialTheme.typography.titleLarge) },
                            actions = {
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Default.Settings, "Settings")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        SnakeGameScreen(settings)
                        
                        if (showSettings) {
                            SettingsDialog(settings, { showSettings = false }) { settings = it }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SnakeGameScreen(settings: GameSettings) {
    var state by remember { mutableStateOf(SnakeState()) }
    val colorScheme = MaterialTheme.colorScheme

    // 游戏循环：逻辑时钟
    LaunchedEffect(state.isGameOver, state.isPaused) {
        while (!state.isGameOver && !state.isPaused) {
            val tickRate = (160 - (state.score / 50 * 10)).coerceAtLeast(80).toLong()
            delay(tickRate)
            
            // 无敌倒计时逻辑
            if (state.isInvincible) {
                val remain = state.invincibleTimeLeft - tickRate
                state = if (remain <= 0) state.copy(isInvincible = false, invincibleTimeLeft = 0)
                        else state.copy(invincibleTimeLeft = remain)
            }
            
            state = gameTick(state) // 调用 Logic 包中的纯逻辑函数
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 记分与 Buff 状态栏
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScoreItem("SCORE", state.score, colorScheme.primary)
            
            // 动态 Buff 图标
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.hasShield) Icon(Icons.Default.Shield, null, tint = Color(0xFF4CAF50))
                if (state.isInvincible) Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFC107))
            }

            FilledTonalIconButton(onClick = { state = state.copy(isPaused = !state.isPaused) }) {
                Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
            }
        }

        Spacer(Modifier.height(20.dp))

        // 画布层
        Box(
            modifier = Modifier.weight(1f).aspectRatio(1f)
                .background(colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
                .pointerInput(state.isGameOver || state.isPaused) {
                    if (!state.isGameOver && !state.isPaused) {
                        detectDragGestures { _, dragAmount ->
                            state = handleSwipe(state, dragAmount.x, dragAmount.y)
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val cellSize = size.width / GameConfig.GRID_SIZE

                // 1. 辅助网格绘制
                if (settings.showGrid) {
                    for (i in 0..GameConfig.GRID_SIZE) {
                        drawLine(colorScheme.outlineVariant, Offset(i * cellSize, 0f), Offset(i * cellSize, size.height), 0.5.dp.toPx())
                        drawLine(colorScheme.outlineVariant, Offset(0f, i * cellSize), Offset(size.width, i * cellSize), 0.5.dp.toPx())
                    }
                }

                // 2. 绘制物品
                drawCircle(colorScheme.tertiary, cellSize / 3, Offset(state.food.first * cellSize + cellSize / 2, state.food.second * cellSize + cellSize / 2))
                state.specialItem?.let { 
                    drawRect(Color(0xFF4CAF50), Offset(it.pos.first * cellSize + 6f, it.pos.second * cellSize + 6f), Size(cellSize - 12f, cellSize - 12f))
                }

                // 3. 绘制蛇（带无敌透明效果）
                state.snake.forEachIndexed { index, pos ->
                    val alpha = if (state.isInvincible) 0.5f else 1f
                    drawRoundRect(
                        color = if (index == 0) colorScheme.primary else colorScheme.primaryContainer.copy(alpha = alpha),
                        topLeft = Offset(pos.first * cellSize + 2f, pos.second * cellSize + 2f),
                        size = Size(cellSize - 4f, cellSize - 4f),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
            }

            // 暂停/结束 弹窗
            androidx.compose.animation.AnimatedVisibility(
                visible = state.isPaused || state.isGameOver,
                enter = fadeIn() + scaleIn(), exit = fadeOut()
            ) {
                ControlOverlay(state, onResume = { state = state.copy(isPaused = false) }, onRestart = { state = SnakeState() })
            }
        }
    }
}

// --- 辅助 UI 组件 ---

@Composable
fun ControlOverlay(state: SnakeState, onResume: () -> Unit, onRestart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (state.isGameOver) "GAME OVER" else "PAUSED", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))
                if (!state.isGameOver) {
                    Button(onClick = onResume, Modifier.fillMaxWidth()) { Text("Continue Game") }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(onClick = onRestart, Modifier.fillMaxWidth()) { Text("Restart") }
            }
        }
    }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game Settings") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show Grid Lines")
                Spacer(Modifier.weight(1f))
                Switch(checked = settings.showGrid, onCheckedChange = { onUpdate(settings.copy(showGrid = it)) })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun ScoreItem(label: String, value: Int, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f))
        Text("$value", style = MaterialTheme.typography.headlineSmall, color = color)
    }
}

fun handleSwipe(state: SnakeState, x: Float, y: Float): SnakeState {
    val newDir = when {
        Math.abs(x) > Math.abs(y) -> if (x > 0 && state.direction != Direction.LEFT) Direction.RIGHT else if (x < 0 && state.direction != Direction.RIGHT) Direction.LEFT else state.direction
        else -> if (y > 0 && state.direction != Direction.UP) Direction.DOWN else if (y < 0 && state.direction != Direction.DOWN) Direction.UP else state.direction
    }
    return state.copy(direction = newDir)
}
