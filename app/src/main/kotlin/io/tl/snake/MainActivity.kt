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
fun GameContent(state: SnakeState, onStateChange: (SnakeState) -> Unit) {
    // 这里不再需要重复传入 settings，为了保持代码整洁，你可以通过 CompositionLocal 或直接传递
    // 这里为了演示修复，假设逻辑已经处理了 settings (或在外部调用)
    // 实际使用时，请确保 gameTick 能够获取到当前的 settings
    
    // 假设我们通过全局变量或 remember 记录 settings (简化处理)
    val settings = remember { GameSettings() } 
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!state.isGameOver && !state.isPaused && state.isStarted) {
            delay(120L)
            var next = gameTick(state, settings)
            if (state.isInvincible) {
                val remain = state.invincibleTimeLeft - 120
                next = if (remain <= 0) next.copy(isInvincible = false) else next.copy(invincibleTimeLeft = remain)
            }
            onStateChange(next)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScoreColumn("HIGH SCORE", state.highScore, colorScheme.secondary)
            ScoreColumn("SCORE", state.score, colorScheme.primary)
        }

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier.weight(1f).aspectRatio(1f)
                .background(colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        onStateChange(handleSwipe(state, dragAmount.x, dragAmount.y))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val cellSize = size.width / GameConfig.GRID_SIZE
                // 绘制蛇
                state.snake.forEachIndexed { i, p ->
                    drawRoundRect(
                        if(i==0) colorScheme.primary else colorScheme.primaryContainer.copy(if(state.isInvincible) 0.5f else 1f),
                        Offset(p.first*cellSize+2f, p.second*cellSize+2f),
                        Size(cellSize-4f, cellSize-4f), CornerRadius(4.dp.toPx())
                    )
                }
                // 绘制食物
                drawCircle(colorScheme.tertiary, cellSize/3f, Offset(state.food.first*cellSize+cellSize/2, state.food.second*cellSize+cellSize/2))
            }

            if (!state.isStarted) {
                Surface(color = colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, 
                    modifier = Modifier.clickable { onStateChange(state.copy(isStarted = true, isPaused = false)) }) {
                    Text("TAP TO START", Modifier.padding(16.dp))
                }
            }

            if (state.isPaused || state.isGameOver) {
                OverlayCard(state) { 
                    onStateChange(if(state.isGameOver) SnakeState(highScore = state.highScore, isStarted = true) else state.copy(isPaused = false)) 
                }
            }
        }
    }
}

@Composable
fun ScoreColumn(l: String, v: Int, c: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(l, style = MaterialTheme.typography.labelSmall, color = c.copy(0.6f))
        Text("$v", style = MaterialTheme.typography.titleLarge, color = c)
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
