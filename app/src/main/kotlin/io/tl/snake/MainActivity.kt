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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    SnakeGameScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SnakeGameScreen(modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf(SnakeState()) }
    var highScore by remember { mutableIntStateOf(0) }
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(state.score) {
        if (state.score > highScore) highScore = state.score
    }

    LaunchedEffect(state.isGameOver, state.isPaused) {
        while (!state.isGameOver && !state.isPaused) {
            val speed = (150 - (state.score / 50 * 10)).coerceAtLeast(80).toLong()
            delay(speed)
            state = moveSnake(state)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreDisplay("SCORE", state.score, colorScheme.primary)
            
            FilledTonalIconButton(
                onClick = { state = state.copy(isPaused = !state.isPaused) },
                enabled = !state.isGameOver,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null
                )
            }
            
            ScoreDisplay("BEST", highScore, colorScheme.secondary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .background(colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge)
                .pointerInput(state.isPaused || state.isGameOver) {
                    if (!state.isPaused && !state.isGameOver) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            state = handleDirectionChange(state, dragAmount.x, dragAmount.y)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val cellSize = size.width / GameConfig.GRID_SIZE

                drawCircle(
                    color = colorScheme.tertiary,
                    radius = cellSize / 2.8f,
                    center = Offset(
                        state.food.first * cellSize + cellSize / 2,
                        state.food.second * cellSize + cellSize / 2
                    )
                )

                state.snake.forEachIndexed { index, segment ->
                    val isHead = index == 0
                    drawRoundRect(
                        color = if (isHead) colorScheme.primary else colorScheme.primaryContainer,
                        topLeft = Offset(segment.first * cellSize + 1.5f, segment.second * cellSize + 1.5f),
                        size = Size(cellSize - 3f, cellSize - 3f),
                        cornerRadius = CornerRadius(if (isHead) 8.dp.toPx() else 4.dp.toPx())
                    )
                }
            }

            // 修复：显式使用顶层 AnimatedVisibility 函数，避免 Box 作用域冲突
            androidx.compose.animation.AnimatedVisibility(
                visible = state.isPaused || state.isGameOver,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                OverlayCard(state) { state = SnakeState() }
            }
        }
        
        Text(
            text = "Swipe to Control",
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
fun ScoreDisplay(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        AnimatedContent(
            targetState = value,
            transitionSpec = { slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut() },
            label = "scoreAnimation"
        ) { targetValue ->
            Text("$targetValue", style = MaterialTheme.typography.titleLarge, color = color)
        }
    }
}

@Composable
fun OverlayCard(state: SnakeState, onRestart: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (state.isGameOver) "GAME OVER" else "PAUSED",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRestart) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("RESTART")
            }
        }
    }
}
