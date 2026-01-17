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
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)

        fun loadSettings(): GameSettings {
            val jsonStr = sp.getString("settings", null) ?: return GameSettings()
            return try {
                val json = JSONObject(jsonStr)
                GameSettings(
                    showGrid = json.optBoolean("showGrid", true),
                    isLoopMode = json.optBoolean("isLoopMode", false),
                    dynamicGrid = json.optBoolean("dynamicGrid", true),
                    enableVibration = json.optBoolean("enableVibration", true),
                    maxObjects = json.optInt("maxObjects", 5)
                )
            } catch (e: Exception) { GameSettings() }
        }

        fun saveSettings(s: GameSettings) {
            val json = JSONObject().apply {
                put("showGrid", s.showGrid)
                put("isLoopMode", s.isLoopMode)
                put("dynamicGrid", s.dynamicGrid)
                put("enableVibration", s.enableVibration)
                put("maxObjects", s.maxObjects)
            }
            sp.edit().putString("settings", json.toString()).apply()
        }

        enableEdgeToEdge()
        setContent {
            MyTheme {
                var settings by remember { mutableStateOf(loadSettings()) }
                var showSettings by remember { mutableStateOf(false) }
                var persistentHS by remember { mutableStateOf(sp.getInt("hs", 0)) }
                var state by remember { mutableStateOf(SnakeState(highScore = persistentHS)) }
                val context = LocalContext.current

                LaunchedEffect(state.isGameOver) {
                    if (state.isGameOver) {
                        if (state.score > persistentHS) {
                            persistentHS = state.score
                            sp.edit().putInt("hs", state.score).apply()
                        }
                        if (settings.enableVibration) {
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("SNAKE EVO", fontWeight = FontWeight.Black) },
                            actions = {
                                if (state.isStarted && !state.isGameOver) {
                                    IconButton(onClick = { state = state.copy(isPaused = !state.isPaused) }) {
                                        Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                                    }
                                }
                                IconButton(onClick = { 
                                    if (state.isStarted && !state.isGameOver) state = state.copy(isPaused = true)
                                    showSettings = true 
                                }) { Icon(Icons.Default.Settings, null) }
                            }
                        )
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        GameContent(state, settings, persistentHS, onStateChange = { state = it })
                        if (showSettings) {
                            SettingsDialog(settings, onDismiss = { showSettings = false }) {
                                settings = it
                                saveSettings(it)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameContent(state: SnakeState, settings: GameSettings, persistentHS: Int, onStateChange: (SnakeState) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val currentState by rememberUpdatedState(state)

    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!currentState.isGameOver && !currentState.isPaused && currentState.isStarted) {
            val speed = (GameConfig.BASE_SPEED - (currentState.score / 100 * 5) + currentState.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
            delay(speed)
            onStateChange(gameTick(currentState, settings))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            val displayHS = if (state.score > persistentHS) state.score else persistentHS
            ScoreColumn("HIGH SCORE", displayHS, colorScheme.secondary)
            ScoreColumn("CURRENT", state.score, colorScheme.primary)
        }

        BoxWithConstraints(Modifier.fillMaxSize().weight(1f).padding(20.dp)) {
            val density = LocalContext.current.resources.displayMetrics.density
            val cellBasePx = 22f * density
            val gridW = if (settings.dynamicGrid) (constraints.maxWidth / cellBasePx).toInt().coerceIn(10, 30) else 20
            val gridH = if (settings.dynamicGrid) (constraints.maxHeight / cellBasePx).toInt().coerceIn(10, 45) else 20
            val cellSizePx = (constraints.maxWidth.toFloat() / gridW).coerceAtMost(constraints.maxHeight.toFloat() / gridH)
            val dW = cellSizePx * gridW
            val dH = cellSizePx * gridH

            LaunchedEffect(gridW, gridH) {
                if (state.gridWidth != gridW || state.gridHeight != gridH) {
                    onStateChange(state.copy(gridWidth = gridW, gridHeight = gridH))
                }
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size((dW / density).dp, (dH / density).dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceContainerHigh)
                        .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                            if (state.isStarted && !state.isPaused && !state.isGameOver) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // 核心改进：严格的轴向判定
                                    onStateChange(handleInput(currentState, dragAmount.x, dragAmount.y))
                                }
                            }
                        }
                ) {
                    val dimAlpha by animateFloatAsState(if (!state.isStarted) 0.8f else 0f, label = "dim")
                    val pulse by rememberInfiniteTransition().animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(800), RepeatMode.Reverse))

                    Canvas(Modifier.fillMaxSize().graphicsLayer(alpha = 1f - dimAlpha)) {
                        if (settings.showGrid) {
                            for (i in 0..gridW) drawLine(colorScheme.outlineVariant.copy(0.1f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, dH), 1f)
                            for (i in 0..gridH) drawLine(colorScheme.outlineVariant.copy(0.1f), Offset(0f, i * cellSizePx), Offset(dW, i * cellSizePx), 1f)
                        }
                        state.objects.forEach { obj ->
                            drawCircle(obj.type.color, (cellSizePx / 3.5f) * pulse, Offset(obj.pos.first * cellSizePx + cellSizePx / 2f, obj.pos.second * cellSizePx + cellSizePx / 2f))
                        }
                        state.snake.forEachIndexed { i, p ->
                            val color = if (i == 0) colorScheme.primary else colorScheme.primaryContainer.copy(alpha = (1f - (i.toFloat()/state.snake.size) * 0.6f).coerceAtLeast(0.4f))
                            drawRoundRect(color, Offset(p.first * cellSizePx + 1f, p.second * cellSizePx + 1f), Size(cellSizePx - 2f, cellSizePx - 2f), CornerRadius(if(i==0) 8.dp.toPx() else 4.dp.toPx()))
                        }
                    }

                    if (!state.isStarted) {
                        Surface(
                            onClick = { onStateChange(state.copy(isStarted = true, isPaused = false, highScore = persistentHS)) },
                            color = colorScheme.primary, shape = RoundedCornerShape(16.dp), shadowElevation = 8.dp, modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text("START GAME", Modifier.padding(horizontal = 32.dp, vertical = 16.dp), fontWeight = FontWeight.Bold, color = colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }

    if (state.isGameOver) {
        ResultDialog(state, persistentHS) { 
            onStateChange(SnakeState(highScore = persistentHS, isStarted = true, gridWidth = state.gridWidth, gridHeight = state.gridHeight)) 
        }
    } else if (state.isPaused) {
        PauseStatsDialog(state) { onStateChange(state.copy(isPaused = false)) }
    }
}

// 核心：基于滑动分量的纯净输入判定
fun handleInput(s: SnakeState, dx: Float, dy: Float): SnakeState {
    val newDir = when {
        abs(dx) > abs(dy) -> { // 水平滑动更显著
            if (dx > 0 && s.direction != Direction.LEFT) Direction.RIGHT 
            else if (dx < 0 && s.direction != Direction.RIGHT) Direction.LEFT 
            else s.direction
        }
        abs(dy) > abs(dx) -> { // 垂直滑动更显著
            if (dy > 0 && s.direction != Direction.UP) Direction.DOWN 
            else if (dy < 0 && s.direction != Direction.DOWN) Direction.UP 
            else s.direction
        }
        else -> s.direction
    }
    return s.copy(direction = newDir)
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } },
        title = { Text("Game Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingToggle("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                SettingToggle("Dynamic Board", settings.dynamicGrid) { onUpdate(settings.copy(dynamicGrid = it)) }
                SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                SettingToggle("Haptic Feedback", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                HorizontalDivider()
                Text("Max Items On Screen: ${settings.maxObjects}", style = MaterialTheme.typography.labelLarge)
                Slider(value = settings.maxObjects.toFloat(), onValueChange = { onUpdate(settings.copy(maxObjects = it.toInt())) }, valueRange = 1f..10f, steps = 8)
            }
        }
    )
}

@Composable
fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ScoreColumn(label: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
fun ResultDialog(state: SnakeState, oldHS: Int, onRestart: () -> Unit) {
    val isNew = state.score > oldHS && state.score > 0
    AlertDialog(onDismissRequest = {}, confirmButton = { Button(onClick = onRestart) { Text("PLAY AGAIN") } },
        title = { Text(if(isNew) "NEW RECORD!" else "GAME OVER", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.score}", fontSize = 64.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                if(isNew) Text("Previous Best: $oldHS", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                StatsList(state.itemsCollected)
            }
        }
    )
}

@Composable
fun PauseStatsDialog(state: SnakeState, onResume: () -> Unit) {
    AlertDialog(onDismissRequest = onResume, confirmButton = { Button(onClick = onResume) { Text("RESUME") } },
        title = { Text("Paused") }, text = { StatsList(state.itemsCollected) }
    )
}

@Composable
fun StatsList(items: Map<ItemType, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ItemType.entries.forEach { type ->
            val count = items[type] ?: 0
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(type.label, color = type.color)
                Text("$count", fontWeight = FontWeight.Bold)
            }
        }
    }
}
