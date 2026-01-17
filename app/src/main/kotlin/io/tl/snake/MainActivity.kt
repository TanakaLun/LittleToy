package io.tl.snake

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.* // 修复 tween, repeatMode 等
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape // 修复 CircleShape
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
import androidx.compose.ui.text.font.FontWeight // 修复 FontWeight
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
                        // 修复错误 70e: 传入了正确的 settings 参数
                        GameContent(state, settings) { state = it }
                        
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
fun GameContent(state: SnakeState, settings: GameSettings, onUpdate: (SnakeState) -> Unit) {
    val color = MaterialTheme.colorScheme
    
    // 修复 71e-75e: 动画相关的 Unresolved reference
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val foodPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = ""
    )

    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!state.isGameOver && !state.isPaused && state.isStarted) {
            delay(120L)
            var next = gameTick(state, settings)
            if (state.isInvincible) {
                val rem = state.invincibleTimeLeft - 120
                next = if (rem <= 0) next.copy(isInvincible = false) else next.copy(invincibleTimeLeft = rem)
            }
            onUpdate(next)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            ScoreColumn("HIGH", state.highScore, color.secondary)
            ScoreColumn("SCORE", state.score, color.primary)
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.weight(1f).aspectRatio(1f).background(color.surfaceVariant, MaterialTheme.shapes.large)
                .pointerInput(Unit) { detectDragGestures { _, drag -> onUpdate(handleSwipe(state, drag.x, drag.y)) } },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val sz = size.width / GameConfig.GRID_SIZE
                if (settings.showGrid) {
                    for (i in 0..GameConfig.GRID_SIZE) {
                        drawLine(color.outlineVariant.copy(0.2f), Offset(i*sz, 0f), Offset(i*sz, size.height))
                        drawLine(color.outlineVariant.copy(0.2f), Offset(0f, i*sz), Offset(size.width, i*sz))
                    }
                }
                state.snake.forEachIndexed { i, p ->
                    drawRoundRect(if(i==0) color.primary else color.primaryContainer, Offset(p.first*sz+2f, p.second*sz+2f), Size(sz-4f, sz-4f), CornerRadius(4.dp.toPx()))
                }
                drawCircle(color.tertiary, (sz/3f)*foodPulse, Offset(state.food.first*sz+sz/2, state.food.second*sz+sz/2))
            }

            if (!state.isStarted) {
                // 修复 76e: CircleShape
                Surface(onClick = { onUpdate(state.copy(isStarted = true, isPaused = false)) }, shape = CircleShape, color = color.primary) {
                    Text("START", Modifier.padding(24.dp))
                }
            }

            if (state.isPaused || state.isGameOver) {
                OverlayCard(state) { onUpdate(if(state.isGameOver) SnakeState(highScore = state.highScore, isStarted = true) else state.copy(isPaused = false)) }
            }
        }
    }
}

@Composable
fun ScoreColumn(l: String, v: Int, c: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(l, style = MaterialTheme.typography.labelSmall)
        // 修复 77e: FontWeight
        Text("$v", style = MaterialTheme.typography.titleLarge, color = c, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OverlayCard(state: SnakeState, onClick: () -> Unit) {
    Surface(Modifier.padding(24.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if(state.isGameOver) "GAME OVER" else "PAUSED")
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
                    Text("Grid")
                    Spacer(Modifier.weight(1f)); Switch(settings.showGrid, { onUpdate(settings.copy(showGrid = it)) })
                }
                if (settings.isDeveloperMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Loop Mode")
                        Spacer(Modifier.weight(1f)); Switch(settings.isLoopMode, { onUpdate(settings.copy(isLoopMode = it)) })
                    }
                }
                AssistChip(onClick = { if(++clicks >= 7) onUpdate(settings.copy(isDeveloperMode = true)) }, label = { Text("v${GameConfig.VERSION}") })
            }
        }
    )
}
