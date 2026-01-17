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
import io.tl.snake.ui.theme.MyTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyTheme {
                var settings by remember { mutableStateOf(GameSettings()) }
                var showSettings by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        SmallTopAppBar(
                            title = { Text("Snake MD3", style = MaterialTheme.typography.titleMedium) },
                            actions = {
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        SnakeGameScreen(settings)
                        if (showSettings) {
                            SettingsDialog(
                                settings = settings,
                                onDismiss = { showSettings = false },
                                onSettingsChange = { settings = it }
                            )
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

    // 游戏主循环
    LaunchedEffect(state.isGameOver, state.isPaused) {
        while (!state.isGameOver && !state.isPaused) {
            val baseSpeed = (160 - (state.score / 50 * 10)).coerceAtLeast(70)
            delay(baseSpeed.toLong())
            
            // 处理无敌时间递减
            if (state.isInvincible) {
                val nextTime = state.invincibleTimeLeft - baseSpeed
                if (nextTime <= 0) {
                    state = state.copy(isInvincible = false, invincibleTimeLeft = 0)
                } else {
                    state = state.copy(invincibleTimeLeft = nextTime)
                }
            }
            state = gameTick(state)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 顶部信息栏
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            ScoreDisplay("SCORE", state.score, colorScheme.primary)
            // Buff 状态图标
            Row {
                if (state.hasShield) Icon(Icons.Default.Shield, "Shield", tint = Color(0xFF4CAF50))
                if (state.isInvincible) Icon(Icons.Default.AutoAwesome, "Invincible", tint = Color(0xFFFFEB3B))
            }
            FilledTonalIconButton(onClick = { state = state.copy(isPaused = !state.isPaused) }) {
                Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier.weight(1f).aspectRatio(1f)
                .background(colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge)
                .pointerInput(state.isPaused || state.isGameOver) {
                    detectDragGestures { _, dragAmount ->
                        state = handleDirectionChange(state, dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val cellSize = size.width / GameConfig.GRID_SIZE

                // 1. 绘制方格辅助线
                if (settings.showGrid) {
                    for (i in 0..GameConfig.GRID_SIZE) {
                        val pos = i * cellSize
                        drawLine(colorScheme.outlineVariant, Offset(pos, 0f), Offset(pos, size.height), 1f)
                        drawLine(colorScheme.outlineVariant, Offset(0f, pos), Offset(size.width, pos), 1f)
                    }
                }

                // 2. 绘制食物与奖励
                drawCircle(colorScheme.tertiary, cellSize / 3, Offset(state.food.first * cellSize + cellSize / 2, state.food.second * cellSize + cellSize / 2))
                state.specialItem?.let {
                    val itemColor = if (it.type == ItemType.SHIELD) Color(0xFF4CAF50) else Color(0xFF2196F3)
                    drawRect(itemColor, Offset(it.pos.first * cellSize + 4f, it.pos.second * cellSize + 4f), Size(cellSize - 8f, cellSize - 8f))
                }

                // 3. 绘制蛇
                state.snake.forEachIndexed { index, segment ->
                    val alpha = if (state.isInvincible) 0.5f else 1.0f
                    drawRoundRect(
                        color = if (index == 0) colorScheme.primary else colorScheme.primaryContainer.copy(alpha = alpha),
                        topLeft = Offset(segment.first * cellSize + 2f, segment.second * cellSize + 2f),
                        size = Size(cellSize - 4f, cellSize - 4f),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
            }

            // 覆盖层 (暂停/结束)
            androidx.compose.animation.AnimatedVisibility(
                visible = state.isPaused || state.isGameOver,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                GameOverlay(state, onRestart = { state = SnakeState() }, onResume = { state = state.copy(isPaused = false) })
            }
        }
    }
}

// --- 逻辑函数 ---

fun gameTick(state: SnakeState): SnakeState {
    val head = state.snake.first()
    val newHead = when (state.direction) {
        Direction.UP -> head.first to (head.second - 1)
        Direction.DOWN -> head.first to (head.second + 1)
        Direction.LEFT -> head.first - 1 to head.second
        Direction.RIGHT -> head.first + 1 to head.second
    }

    // 碰撞检测逻辑
    val hitWall = newHead.first !in 0 until GameConfig.GRID_SIZE || newHead.second !in 0 until GameConfig.GRID_SIZE
    val hitSelf = state.snake.contains(newHead) && !state.isInvincible

    if (hitWall || hitSelf) {
        return if (state.hasShield) {
            // 触发免死：回到中心，开启无敌
            state.copy(
                snake = listOf(10 to 10, 10 to 11, 10 to 12),
                direction = Direction.UP,
                hasShield = false,
                isInvincible = true,
                invincibleTimeLeft = 5000
            )
        } else {
            state.copy(isGameOver = true)
        }
    }

    val newSnake = mutableListOf(newHead) + state.snake
    var score = state.score
    var hasShield = state.hasShield
    var specialItem = state.specialItem
    var isInvincible = state.isInvincible
    var invincibleTime = state.invincibleTimeLeft

    // 检查食物
    val finalSnake = if (newHead == state.food) {
        score += 10
        // 概率生成特殊物品 (15% 几率)
        if (Random.nextFloat() < 0.15f && specialItem == null) {
            specialItem = SpecialItem(Random.nextInt(20) to Random.nextInt(20), ItemType.SHIELD)
        }
        newSnake
    } else {
        newSnake.dropLast(1)
    }

    // 检查特殊物品
    if (newHead == specialItem?.pos) {
        if (specialItem?.type == ItemType.SHIELD) hasShield = true
        specialItem = null
        score += 50
    }

    return state.copy(snake = finalSnake, score = score, food = if (newHead == state.food) Random.nextInt(20) to Random.nextInt(20) else state.food, specialItem = specialItem, hasShield = hasShield)
}

@Composable
fun GameOverlay(state: SnakeState, onRestart: () -> Unit, onResume: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (state.isGameOver) "GAME OVER" else "PAUSED", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            if (!state.isGameOver) {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("Continue")
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null)
                Text("Restart")
            }
        }
    }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onSettingsChange: (GameSettings) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show Grid Lines")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = settings.showGrid, onCheckedChange = { onSettingsChange(settings.copy(showGrid = it)) })
                }
            }
        }
    )
}

// 辅助 UI 组件 (复用之前的)
@Composable
fun ScoreDisplay(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        Text("$value", style = MaterialTheme.typography.titleLarge, color = color)
    }
}

fun handleDirectionChange(current: SnakeState, x: Float, y: Float): SnakeState {
    val newDir = when {
        kotlin.math.abs(x) > kotlin.math.abs(y) -> if (x > 0 && current.direction != Direction.LEFT) Direction.RIGHT else if (x < 0 && current.direction != Direction.RIGHT) Direction.LEFT else current.direction
        else -> if (y > 0 && current.direction != Direction.UP) Direction.DOWN else if (y < 0 && current.direction != Direction.DOWN) Direction.UP else current.direction
    }
    return current.copy(direction = newDir)
}
