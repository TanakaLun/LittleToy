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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.tl.snake.logic.*
import io.tl.snake.ui.theme.MyTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
        
        enableEdgeToEdge()
        setContent {
            MyTheme {
                var settings by remember { mutableStateOf(GameSettings()) }
                var showSettings by remember { mutableStateOf(false) }
                // 从持久化读取最高分
                var highScore by remember { mutableIntStateOf(prefs.getInt("high_score", 0)) }
                
                var gameState by remember { mutableStateOf(SnakeState(highScore = highScore)) }

                // 监听最高分变化并保存
                LaunchedEffect(gameState.score) {
                    if (gameState.score > highScore) {
                        highScore = gameState.score
                        prefs.edit().putInt("high_score", highScore).apply()
                    }
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("SNAKE EVO") },
                            actions = {
                                IconButton(onClick = { 
                                    showSettings = true
                                    gameState = gameState.copy(isPaused = true) // 调出设置自动暂停
                                }) {
                                    Icon(Icons.Default.Settings, null)
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) {
                        GameScreen(gameState, settings, { gameState = it })
                        
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
fun GameScreen(state: SnakeState, settings: GameSettings, onStateChange: (SnakeState) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!state.isGameOver && !state.isPaused && state.isStarted) {
            delay(120)
            var nextState = gameTick(state, settings)
            if (state.isInvincible) {
                val remain = state.invincibleTimeLeft - 120
                nextState = if (remain <= 0) nextState.copy(isInvincible = false) 
                            else nextState.copy(invincibleTimeLeft = remain)
            }
            onStateChange(nextState)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScoreBoard("HIGH SCORE", state.highScore, colorScheme.secondary)
            ScoreBoard("SCORE", state.score, colorScheme.primary)
        }
        
        Spacer(Modifier.height(16.dp))

        Box(
            Modifier.weight(1f).aspectRatio(1f)
                .background(colorScheme.surfaceVariant, MaterialTheme.shapes.extraLarge)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        onStateChange(handleSwipe(state, dragAmount.x, dragAmount.y))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val cellSize = size.width / GameConfig.GRID_SIZE
                if (settings.showGrid) {
                    for (i in 0..GameConfig.GRID_SIZE) {
                        drawLine(colorScheme.onSurfaceVariant.copy(0.1f), Offset(i * cellSize, 0f), Offset(i * cellSize, size.height))
                        drawLine(colorScheme.onSurfaceVariant.copy(0.1f), Offset(0f, i * cellSize), Offset(size.width, i * cellSize))
                    }
                }
                // 绘制逻辑
                drawCircle(colorScheme.error, cellSize/3, Offset(state.food.first*cellSize + cellSize/2, state.food.second*cellSize + cellSize/2))
                state.snake.forEach { (x, y) ->
                    drawRoundRect(colorScheme.primary, Offset(x*cellSize+2f, y*cellSize+2f), Size(cellSize-4f, cellSize-4f), CornerRadius(4.dp.toPx()))
                }
            }

            // 起始控件
            if (!state.isStarted) {
                Button(onClick = { onStateChange(state.copy(isStarted = true, isPaused = false)) }) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("START GAME")
                }
            }
            
            if (state.isPaused && state.isStarted && !state.isGameOver) {
                TextButton(onClick = { onStateChange(state.copy(isPaused = false)) }) {
                    Text("RESUME", style = MaterialTheme.typography.headlineMedium)
                }
            }
            
            if (state.isGameOver) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GAME OVER", style = MaterialTheme.typography.headlineMedium, color = colorScheme.error)
                    Button(onClick = { onStateChange(SnakeState(highScore = state.highScore)) }) { Text("TRY AGAIN") }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    var clickCount by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleRow("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                
                if (settings.isDeveloperMode) {
                    Divider()
                    Text("Developer Options", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ToggleRow("Loop Mode (Wall-less)", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                }
                
                Spacer(Modifier.height(8.dp))
                
                // 版本号 Chip 与 开发者模式激活，向Google系App看齐😶‍🌫️
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AssistChip(
                        onClick = { 
                            clickCount++
                            if (clickCount >= 7 && !settings.isDeveloperMode) {
                                onUpdate(settings.copy(isDeveloperMode = true))
                            }
                        },
                        label = { Text("Version ${GameConfig.VERSION}") },
                        leadingIcon = { if(settings.isDeveloperMode) Icon(Icons.Default.Code, null, Modifier.size(16.dp)) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ScoreBoard(label: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(0.6f))
        Text("$score", style = MaterialTheme.typography.titleLarge, color = color)
    }
}

fun handleSwipe(state: SnakeState, x: Float, y: Float): SnakeState {
    val newDir = when {
        Math.abs(x) > Math.abs(y) -> if (x > 0 && state.direction != Direction.LEFT) Direction.RIGHT else if (x < 0 && state.direction != Direction.RIGHT) Direction.LEFT else state.direction
        else -> if (y > 0 && state.direction != Direction.UP) Direction.DOWN else if (y < 0 && state.direction != Direction.DOWN) Direction.UP else state.direction
    }
    return state.copy(direction = newDir)
}
