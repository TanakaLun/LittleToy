package io.tl.snake

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import io.tl.snake.logic.*
import io.tl.snake.ui.theme.MyTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPrefs = getSharedPreferences("snake_data", Context.MODE_PRIVATE)
        
        enableEdgeToEdge()
        setContent {
            MyTheme {
                var settings by remember { mutableStateOf(GameSettings()) }
                var showSettings by remember { mutableStateOf(false) }
                
                // 初始化状态时从持久化读取最高分
                var gameState by remember { 
                    mutableStateOf(SnakeState(highScore = sharedPrefs.getInt("high_score", 0))) 
                }

                // 监听状态中的最高分变化并保存
                LaunchedEffect(gameState.highScore) {
                    sharedPrefs.edit().putInt("high_score", gameState.highScore).apply()
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("SNAKE MD3") },
                            actions = {
                                IconButton(onClick = { 
                                    gameState = gameState.copy(isPaused = true)
                                    showSettings = true 
                                }) {
                                    Icon(Icons.Default.Settings, null)
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) {
                        GameContent(gameState) { gameState = it }
                        
                        if (showSettings) {
                            SettingsDialog(
                                settings = settings,
                                onDismiss = { showSettings = false },
                                onUpdate = { settings = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameContent(
    state: SnakeState,
    settings: GameSettings,
    onStateChange: (SnakeState) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    // --- 1. 游戏主循环 (时钟) ---
    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!state.isGameOver && !state.isPaused && state.isStarted) {
            // 动态速度：每得50分提速一次
            val tickRate = (150 - (state.score / 50 * 10)).coerceAtLeast(80).toLong()
            delay(tickRate)
            
            var next = gameTick(state, settings)
            
            // 处理无敌时间衰减
            if (state.isInvincible) {
                val remain = state.invincibleTimeLeft - tickRate
                next = if (remain <= 0) next.copy(isInvincible = false) 
                       else next.copy(invincibleTimeLeft = remain)
            }
            onStateChange(next)
        }
    }

    // --- 2. 视觉动画配置 ---
    // 食物呼吸效果动画
    val infiniteTransition = rememberInfiniteTransition(label = "foodScale")
    val foodPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 分数展示栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreColumn("HIGH SCORE", state.highScore, colorScheme.secondary)
            
            // 状态图标指示器
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.hasShield) Icon(Icons.Default.Shield, "Shield", tint = Color(0xFF4CAF50))
                if (state.isInvincible) Icon(Icons.Default.Bolt, "Invincible", tint = Color(0xFFFFC107))
            }
            
            ScoreColumn("SCORE", state.score, colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 3. 游戏画布区 ---
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .background(colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
                // 手势监听
                .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                    if (state.isStarted && !state.isPaused && !state.isGameOver) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onStateChange(handleSwipe(state, dragAmount.x, dragAmount.y))
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val cellSize = size.width / GameConfig.GRID_SIZE

                // A. 绘制背景网格
                if (settings.showGrid) {
                    for (i in 0..GameConfig.GRID_SIZE) {
                        val pos = i * cellSize
                        drawLine(colorScheme.outlineVariant.copy(0.2f), Offset(pos, 0f), Offset(pos, size.height), 0.5.dp.toPx())
                        drawLine(colorScheme.outlineVariant.copy(0.2f), Offset(0f, pos), Offset(size.width, pos), 0.5.dp.toPx())
                    }
                }

                // B. 绘制特殊物品 (如果有)
                state.specialItem?.let { item ->
                    drawRect(
                        color = Color(0xFF4CAF50),
                        topLeft = Offset(item.pos.first * cellSize + 4f, item.pos.second * cellSize + 4f),
                        size = Size(cellSize - 8f, cellSize - 8f)
                    )
                }

                // C. 绘制食物 (带脉冲动画)
                drawCircle(
                    color = colorScheme.tertiary,
                    radius = (cellSize / 3f) * foodPulse,
                    center = Offset(state.food.first * cellSize + cellSize / 2, state.food.second * cellSize + cellSize / 2)
                )

                // D. 绘制蛇身
                state.snake.forEachIndexed { index, p ->
                    val isHead = index == 0
                    val alpha = if (state.isInvincible && (state.invincibleTimeLeft / 200 % 2 == 0L)) 0.3f else 1f
                    
                    drawRoundRect(
                        color = if (isHead) colorScheme.primary else colorScheme.primaryContainer.copy(alpha = alpha),
                        topLeft = Offset(p.first * cellSize + 1.5f, p.second * cellSize + 1.5f),
                        size = Size(cellSize - 3f, cellSize - 3f),
                        cornerRadius = CornerRadius(if (isHead) 8.dp.toPx() else 4.dp.toPx())
                    )
                }
            }

            // --- 4. 覆盖层逻辑 ---
            
            // 游戏未开始
            if (!state.isStarted) {
                StartGameButton { onStateChange(state.copy(isStarted = true, isPaused = false)) }
            }

            // 暂停或结束状态
            androidx.compose.animation.AnimatedVisibility(
                visible = (state.isPaused || state.isGameOver) && state.isStarted,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut()
            ) {
                OverlayCard(state) {
                    if (state.isGameOver) {
                        onStateChange(SnakeState(highScore = state.highScore, isStarted = true))
                    } else {
                        onStateChange(state.copy(isPaused = false))
                    }
                }
            }
        }
        
        Text(
            text = if(settings.isLoopMode) "Loop Mode Active" else "Classic Mode",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 12.dp),
            color = colorScheme.onSurfaceVariant.copy(0.5f)
        )
    }
}

@Composable
fun StartGameButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
        tonalElevation = 8.dp,
        modifier = Modifier.size(120.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(40.dp))
                Text("START", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun ScoreColumn(label: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f))
        androidx.compose.animation.AnimatedContent(
            targetState = score,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            }, label = "scoreAnim"
        ) { targetValue ->
            Text(
                text = targetValue.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun OverlayCard(state: SnakeState, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(0.9f), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if(state.isGameOver) "GAME OVER" else "PAUSED")
            Button(onClick = onClick, Modifier.padding(top = 8.dp)) {
                Text(if(state.isGameOver) "RESTART" else "CONTINUE")
            }
        }
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
    var devClicks by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show Grid")
                    Spacer(Modifier.weight(1f))
                    Switch(settings.showGrid, { onUpdate(settings.copy(showGrid = it)) })
                }
                if (settings.isDeveloperMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Loop Mode")
                        Spacer(Modifier.weight(1f))
                        Switch(settings.isLoopMode, { onUpdate(settings.copy(isLoopMode = it)) })
                    }
                }
                AssistChip(
                    onClick = { if(++devClicks >= 7) onUpdate(settings.copy(isDeveloperMode = true)) },
                    label = { Text("v${GameConfig.VERSION}") },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
