package io.tl.snake

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

                LaunchedEffect(state.highScore) { sp.edit().putInt("hs", state.highScore).apply() }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("SNAKE EVO") },
                            actions = {
                                IconButton(onClick = { 
                                    state = state.copy(isPaused = true)
                                    showSet = true 
                                }) { Icon(Icons.Default.Settings, null) }
                            }
                        )
                    }
                ) { p ->
                    Box(Modifier.padding(p)) {
                        GameContent(state = state, settings = settings, onStateChange = { state = it })
                        
                        if (showSet) {
                            SettingsDialog(settings = settings, onDismiss = { showSet = false }, onUpdate = { settings = it })
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
    
    // 使用 rememberUpdatedState 确保 LaunchedEffect 内部循环总是拿到最新的值，而不需要重启 Effect
    val currentState by rememberUpdatedState(state)
    val currentSettings by rememberUpdatedState(settings)

    val infiniteTransition = rememberInfiniteTransition(label = "foodPulse")
    val foodPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // 只有在核心开关变化时才重启循环
    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!currentState.isGameOver && !currentState.isPaused && currentState.isStarted) {
            delay(120L)
            var next = gameTick(currentState, currentSettings)
            if (next.isInvincible) {
                val remain = next.invincibleTimeLeft - 120
                next = if (remain <= 0) next.copy(isInvincible = false) else next.copy(invincibleTimeLeft = remain)
            }
            onStateChange(next)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            ScoreColumn("HIGH", state.highScore, colorScheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.hasShield) Icon(Icons.Default.Shield, null, tint = Color(0xFF4CAF50))
                if (state.isInvincible) Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFC107))
            }
            ScoreColumn("SCORE", state.score, colorScheme.primary)
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .weight(1f).aspectRatio(1f)
                .background(colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
                .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                    if (state.isStarted && !state.isPaused && !state.isGameOver) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val nextDirState = handleSwipe(currentState, dragAmount.x, dragAmount.y)
                            if (nextDirState.direction != currentState.direction) {
                                onStateChange(nextDirState)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val cellSize = size.width / GameConfig.GRID_SIZE
                if (settings.showGrid) { // 这里现在会响应 settings 的变化了
                    for (i in 0..GameConfig.GRID_SIZE) {
                        val pos = i * cellSize
                        drawLine(colorScheme.outlineVariant.copy(0.2f), Offset(pos, 0f), Offset(pos, size.height), 1f)
                        drawLine(colorScheme.outlineVariant.copy(0.2f), Offset(0f, pos), Offset(size.width, pos), 1f)
                    }
                }
                state.snake.forEachIndexed { index, p ->
                    val alpha = if (state.isInvincible && (state.invincibleTimeLeft / 200 % 2 == 0L)) 0.4f else 1f
                    drawRoundRect(
                        color = if (index == 0) colorScheme.primary else colorScheme.primaryContainer.copy(alpha = alpha),
                        topLeft = Offset(p.first * cellSize + 2f, p.second * cellSize + 2f),
                        size = Size(cellSize - 4f, cellSize - 4f),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
                drawCircle(
                    color = colorScheme.tertiary,
                    radius = (cellSize / 3f) * foodPulse,
                    center = Offset(state.food.first * cellSize + cellSize / 2, state.food.second * cellSize + cellSize / 2)
                )
            }

            if (!state.isStarted) {
                Surface(
                    onClick = { onStateChange(state.copy(isStarted = true, isPaused = false)) },
                    color = colorScheme.primary, shape = CircleShape, shadowElevation = 8.dp
                ) {
                    Text("START GAME", Modifier.padding(horizontal = 32.dp, vertical = 16.dp), color = colorScheme.onPrimary)
                }
            }

            if (state.isStarted && (state.isPaused || state.isGameOver)) {
                OverlayCard(state) {
                    if (state.isGameOver) {
                        onStateChange(SnakeState(highScore = state.highScore, isStarted = true))
                    } else {
                        onStateChange(state.copy(isPaused = false))
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreColumn(l: String, v: Int, c: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(l, style = MaterialTheme.typography.labelSmall)
        Text("$v", style = MaterialTheme.typography.titleLarge, color = c, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OverlayCard(state: SnakeState, onClick: () -> Unit) {
    Surface(Modifier.padding(24.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if(state.isGameOver) "GAME OVER" else "PAUSED", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick) { Text(if(state.isGameOver) "RESTART" else "RESUME") }
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
    var clicks by remember { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Settings") }, confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Grid Line")
                    Spacer(Modifier.weight(1f)); Switch(settings.showGrid, { onUpdate(settings.copy(showGrid = it)) })
                }
                if (settings.isDeveloperMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Loop Mode")
                        Spacer(Modifier.weight(1f)); Switch(settings.isLoopMode, { onUpdate(settings.copy(isLoopMode = it)) })
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AssistChip(onClick = { if(++clicks >= 7) onUpdate(settings.copy(isDeveloperMode = true)) }, label = { Text("v${GameConfig.VERSION}") })
                }
            }
        }
    )
}
