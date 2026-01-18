package io.tl.snake

import android.content.Context
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)

        fun triggerVibration(context: Context, enabled: Boolean, duration: Long = 50L) {
            if (!enabled) return
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }

        fun loadSettings(): GameSettings {
            val jsonStr = sp.getString("settings", null) ?: return GameSettings()
            return try {
                val json = JSONObject(jsonStr)
                val itemsJson = json.optJSONObject("enabledItems")
                val itemMap = ItemType.entries.associateWith { type ->
                    itemsJson?.optBoolean(type.name, true) ?: true
                }
                GameSettings(
                    showGrid = json.optBoolean("showGrid", true),
                    isLoopMode = json.optBoolean("isLoopMode", false),
                    dynamicGrid = json.optBoolean("dynamicGrid", true),
                    enableVibration = json.optBoolean("enableVibration", true),
                    maxObjects = json.optInt("maxObjects", 5),
                    isGhostPermanent = json.optBoolean("isGhostPermanent", false),
                    enabledItems = itemMap
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
                put("isGhostPermanent", s.isGhostPermanent)
                put("enabledItems", JSONObject().apply {
                    s.enabledItems.forEach { (k, v) -> put(k.name, v) }
                })
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

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 分数垂直放置在标题左侧
                                    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(end = 12.dp)) {
                                        Text("HI: ${if(state.score > persistentHS) state.score else persistentHS}", 
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Text("SC: ${state.score}", 
                                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text("SNAKE EVO", fontWeight = FontWeight.Black) 
                                }
                            },
                            actions = {
                                if (state.isStarted && !state.isGameOver) {
                                    IconButton(onClick = { state = state.copy(isPaused = !state.isPaused) }) {
                                        Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                                    }
                                }
                                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, null) }
                            }
                        )
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        GameContent(
                            state = state, 
                            settings = settings, 
                            persistentHS = persistentHS, 
                            onStateChange = { state = it }
                        )
                        if (showSettings) {
                            SettingsDialog(
                                settings = settings, 
                                onDismiss = { showSettings = false },
                                onUpdate = { 
                                    settings = it
                                    saveSettings(it)
                                }
                            )
                        }
                    }
                }

                LaunchedEffect(state.isGameOver) {
                    if (state.isGameOver && state.score > persistentHS) {
                        persistentHS = state.score
                        sp.edit().putInt("hs", state.score).apply()
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
    persistentHS: Int, 
    onStateChange: (SnakeState) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentState by rememberUpdatedState(state)
    val currentSpeed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
    
    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!currentState.isGameOver && !currentState.isPaused && currentState.isStarted) {
            delay(currentSpeed)
            onStateChange(gameTick(currentState, settings, currentSpeed))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 状态指示
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), Arrangement.SpaceBetween) {
                Text("MODE: ${if(settings.isLoopMode) "LOOP" else "WALL"}", style = MaterialTheme.typography.labelLarge)
                if (state.ghostTimeRemaining > 0 && !settings.isGhostPermanent) {
                    Text("GHOST MODE", color = ItemType.GHOST.color, fontWeight = FontWeight.Bold)
                }
            }

            // 画布
            BoxWithConstraints(Modifier.fillMaxSize().weight(1f).padding(20.dp)) {
                val density = LocalContext.current.resources.displayMetrics.density
                val cellBasePx = 22f * density
                val gridW = if (settings.dynamicGrid) (constraints.maxWidth / cellBasePx).toInt() else 20
                val gridH = if (settings.dynamicGrid) (constraints.maxHeight / cellBasePx).toInt() else 20
                val cellSizePx = (constraints.maxWidth.toFloat() / gridW).coerceAtMost(constraints.maxHeight.toFloat() / gridH)
                val dW = cellSizePx * gridW
                val dH = cellSizePx * gridH

                LaunchedEffect(gridW, gridH) {
                    if (state.gridWidth != gridW) onStateChange(state.copy(gridWidth = gridW, gridHeight = gridH))
                }

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size((dW / density).dp, (dH / density).dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorScheme.surfaceVariant.copy(0.4f))
                            .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    onStateChange(handleInput(currentState, drag.x, drag.y))
                                }
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (settings.showGrid) {
                                for (i in 0..gridW) drawLine(colorScheme.onSurface.copy(0.05f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, dH))
                                for (i in 0..gridH) drawLine(colorScheme.onSurface.copy(0.05f), Offset(0f, i * cellSizePx), Offset(dW, i * cellSizePx))
                            }
                            // 道具
                            state.objects.forEach { obj ->
                                drawCircle(obj.type.color, cellSizePx / 3f, Offset(obj.pos.first * cellSizePx + cellSizePx / 2f, obj.pos.second * cellSizePx + cellSizePx / 2f))
                            }
                            // 蛇身渐变逻辑
                            state.snake.forEachIndexed { i, p ->
                                val alpha = (1f - (i.toFloat() / state.snake.size)).coerceAtLeast(0.2f)
                                val color = when {
                                    i == 0 && (state.ghostTimeRemaining > 0 || settings.isGhostPermanent) -> ItemType.GHOST.color
                                    i == 0 && state.shieldCount > 0 -> ItemType.SHIELD.color
                                    i == 0 -> colorScheme.primary
                                    else -> colorScheme.primary
                                }
                                drawRoundRect(
                                    color = color.copy(alpha = alpha),
                                    topLeft = Offset(p.first * cellSizePx + 1f, p.second * cellSizePx + 1f),
                                    size = Size(cellSizePx - 2f, cellSizePx - 2f),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                            }
                            
                            // Ghost 限时进度条
                            if (state.ghostTimeRemaining > 0 && !settings.isGhostPermanent) {
                                val progress = state.ghostTimeRemaining.toFloat() / GameConfig.GHOST_DURATION_MS
                                drawRect(
                                    color = ItemType.GHOST.color.copy(0.3f),
                                    topLeft = Offset(0f, dH - 4.dp.toPx()),
                                    size = Size(dW, 4.dp.toPx())
                                )
                                drawRect(
                                    color = ItemType.GHOST.color,
                                    topLeft = Offset(0f, dH - 4.dp.toPx()),
                                    size = Size(dW * progress, 4.dp.toPx())
                                )
                            }
                        }

                        // 护盾指示 (右上角)
                        if (state.shieldCount > 0) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                color = ItemType.SHIELD.color,
                                shape = RoundedCornerShape(6.dp),
                                shadowElevation = 4.dp
                            ) {
                                Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Shield, null, Modifier.size(14.dp), tint = Color.White)
                                    Text("${state.shieldCount}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (!state.isStarted) {
                            Button(onClick = { onStateChange(state.copy(isStarted = true)) }, Modifier.align(Alignment.Center)) {
                                Text("START")
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.isGameOver) {
        ResultDialog(state, persistentHS) { onStateChange(SnakeState(highScore = persistentHS, isStarted = true)) }
    } else if (state.isPaused) {
        PauseStatsDialog(state) { onStateChange(state.copy(isPaused = false)) }
    }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingToggle("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                SettingToggle("Ghost Permanent", settings.isGhostPermanent) { onUpdate(settings.copy(isGhostPermanent = it)) }
                Divider(Modifier.padding(vertical = 8.dp))
                Text("Item Spawn:", style = MaterialTheme.typography.labelLarge)
                ItemType.entries.forEach { type ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(type.label, style = MaterialTheme.typography.bodySmall)
                        Checkbox(checked = settings.enabledItems[type] == true, onCheckedChange = {
                            val m = settings.enabledItems.toMutableMap()
                            m[type] = it
                            onUpdate(settings.copy(enabledItems = m))
                        })
                    }
                }
            }
        }
    )
}

fun handleInput(s: SnakeState, x: Float, y: Float): SnakeState {
    val newDir = when {
        abs(x) > abs(y) -> if (x > 0 && s.direction != Direction.LEFT) Direction.RIGHT else if (x < 0 && s.direction != Direction.RIGHT) Direction.LEFT else s.direction
        else -> if (y > 0 && s.direction != Direction.UP) Direction.DOWN else if (y < 0 && s.direction != Direction.DOWN) Direction.UP else s.direction
    }
    return s.copy(direction = newDir)
}

@Composable
fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ResultDialog(state: SnakeState, oldHS: Int, onRestart: () -> Unit) {
    AlertDialog(onDismissRequest = {}, confirmButton = { Button(onClick = onRestart) { Text("REPLAY") } },
        title = { Text("Game Over") },
        text = { Column(Alignment.CenterHorizontally) { Text("${state.score}", fontSize = 48.sp, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
fun PauseStatsDialog(state: SnakeState, onResume: () -> Unit) {
    AlertDialog(onDismissRequest = onResume, confirmButton = { Button(onClick = onResume) { Text("RESUME") } },
        title = { Text("Paused") }, text = { Text("Current Score: ${state.score}") }
    )
}
