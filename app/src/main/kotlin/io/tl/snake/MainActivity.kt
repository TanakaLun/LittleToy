package io.tl.snake

import android.content.Context
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
        val sp = getSharedPreferences("snake_v2", Context.MODE_PRIVATE)

        fun loadSettings(): GameSettings {
            val jsonStr = sp.getString("settings", null) ?: return GameSettings()
            return try {
                val json = JSONObject(jsonStr)
                val itemMap = ItemType.entries.associateWith { json.optJSONObject("enabledItems")?.optBoolean(it.name, true) ?: true }
                val thresholdMap = ItemType.entries.filter { it.canPermanent }.associateWith { json.optJSONObject("thresholds")?.optInt(it.name, 10) ?: 10 }
                GameSettings(json.optBoolean("showGrid", true), json.optBoolean("isLoopMode", false), json.optBoolean("dynamicGrid", true), json.optBoolean("enableVibration", true), json.optInt("maxObjects", 5), itemMap, thresholdMap)
            } catch (e: Exception) { GameSettings() }
        }

        fun saveSettings(s: GameSettings) {
            val json = JSONObject().apply {
                put("showGrid", s.showGrid); put("isLoopMode", s.isLoopMode); put("dynamicGrid", s.dynamicGrid); put("enableVibration", s.enableVibration); put("maxObjects", s.maxObjects)
                put("enabledItems", JSONObject().apply { s.enabledItems.forEach { (k, v) -> put(k.name, v) } })
                put("thresholds", JSONObject().apply { s.thresholds.forEach { (k, v) -> put(k.name, v) } })
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
                
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SNAKE EVO", fontWeight = FontWeight.Black, fontSize = 16.sp)
                                if (state.permanentEffects.isNotEmpty()) Row { state.permanentEffects.forEach { Icon(it.icon, null, Modifier.size(12.dp), tint = it.color) } }
                            }},
                            actions = {
                                IconButton(onClick = { if (state.isStarted && !state.isGameOver) state = state.copy(isPaused = !state.isPaused) }) { Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null) }
                                IconButton(onClick = { showSettings = true; state = state.copy(isPaused = true) }) { Icon(Icons.Default.Settings, null) }
                            }
                        )
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        GameContent(state, settings, persistentHS, onHSReset = { persistentHS = 0; sp.edit().putInt("hs", 0).apply(); state = state.copy(highScore = 0) }, onStateChange = { state = it })
                        if (showSettings) SettingsDialog(settings, onDismiss = { showSettings = false }) { settings = it; saveSettings(it) }
                    }
                }
                LaunchedEffect(state.isGameOver) { if (state.isGameOver && state.score > persistentHS) { persistentHS = state.score; sp.edit().putInt("hs", state.score).apply() } }
            }
        }
    }
}

@Composable
fun GameContent(state: SnakeState, settings: GameSettings, persistentHS: Int, onHSReset: () -> Unit, onStateChange: (SnakeState) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme; val currentState by rememberUpdatedState(state)
    val speed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
    
    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!currentState.isGameOver && !currentState.isPaused && currentState.isStarted) { delay(speed); onStateChange(gameTick(currentState, settings)) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            InfoChip("BEST", "${maxOf(state.score, persistentHS)}", Icons.Default.EmojiEvents, colorScheme.tertiary)
            InfoChip("SCORE", "${state.score}", Icons.Default.Score, colorScheme.primary)
        }
        BoxWithConstraints(Modifier.weight(1f).padding(vertical = 16.dp)) {
            val d = LocalContext.current.resources.displayMetrics.density
            val gW = if (settings.dynamicGrid) (constraints.maxWidth / (22 * d)).toInt().coerceIn(10, 30) else state.gridWidth
            val gH = if (settings.dynamicGrid) (constraints.maxHeight / (22 * d)).toInt().coerceIn(10, 45) else state.gridHeight
            val cellSize = minOf(constraints.maxWidth.toFloat() / gW, constraints.maxHeight.toFloat() / gH)
            
            LaunchedEffect(gW, gH) { if (state.gridWidth != gW || state.gridHeight != gH) onStateChange(state.copy(gridWidth = gW, gridHeight = gH)) }

            Box(Modifier.size((cellSize * gW / d).dp, (cellSize * gH / d).dp).clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceVariant.copy(0.3f))
                .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                    var acc = Offset.Zero
                    detectDragGestures(onDragStart = { acc = Offset.Zero }) { change, drag ->
                        change.consume(); acc += drag
                        if (abs(acc.x) > 40f || abs(acc.y) > 40f) {
                            val newDir = if (abs(acc.x) > abs(acc.y)) (if (acc.x > 0 && state.direction != Direction.LEFT) Direction.RIGHT else if (acc.x < 0 && state.direction != Direction.RIGHT) Direction.LEFT else state.direction)
                            else (if (acc.y > 0 && state.direction != Direction.UP) Direction.DOWN else if (acc.y < 0 && state.direction != Direction.DOWN) Direction.UP else state.direction)
                            if (newDir != state.direction) { onStateChange(state.copy(direction = newDir)); acc = Offset.Zero }
                        }
                    }
                }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (settings.showGrid) {
                        for (i in 0..gW) drawLine(Color.Gray.copy(0.1f), Offset(i * cellSize, 0f), Offset(i * cellSize, gH * cellSize))
                        for (i in 0..gH) drawLine(Color.Gray.copy(0.1f), Offset(0f, i * cellSize), Offset(gW * cellSize, i * cellSize))
                    }
                    state.objects.forEach { drawCircle(it.type.color, cellSize / 3f, Offset(it.pos.first * cellSize + cellSize / 2f, it.pos.second * cellSize + cellSize / 2f)) }
                    state.snake.forEachIndexed { i, p ->
                        val color = if (state.permanentEffects.contains(ItemType.GHOST) || state.ghostTicks > 0) colorScheme.primary.copy(0.4f)
                        else if (state.permanentEffects.contains(ItemType.SHIELD) && i == 0) Color.Cyan else lerp(colorScheme.primary, colorScheme.outline, i.toFloat() / state.snake.size.coerceAtLeast(1))
                        drawRoundRect(color, Offset(p.first * cellSize + 1f, p.second * cellSize + 1f), Size(cellSize - 2f, cellSize - 2f), CornerRadius(4 * d))
                    }
                }
                if (!state.isStarted) Button(onClick = { onStateChange(state.copy(isStarted = true, isPaused = false)) }, Modifier.align(Alignment.Center)) { Text("START") }
            }
        }
    }
    // 处理弹窗逻辑
    if (state.isGameOver) {
        ResultDialog(state) { onStateChange(SnakeState(highScore = state.highScore, isStarted = true, gridWidth = state.gridWidth, gridHeight = state.gridHeight)) }
    } else if (state.isPaused) {
        PauseStatsDialog(state) { onStateChange(state.copy(isPaused = false)) }
    }
}

@Composable fun PauseStatsDialog(state: SnakeState, onResume: () -> Unit) {
    AlertDialog(onDismissRequest = onResume, confirmButton = { Button(onClick = onResume) { Text("RESUME") } },
        title = { Text("Paused") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemType.entries.forEach { type ->
                    val count = state.itemsCollected[type] ?: 0
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Row { Icon(type.icon, null, Modifier.size(18.dp), tint = type.color); Spacer(Modifier.width(8.dp)); Text(type.label) }
                        Text("$count", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

@Composable fun InfoChip(l: String, v: String, i: ImageVector, c: Color) {
    Surface(color = c.copy(0.1f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, c.copy(0.2f))) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(i, null, Modifier.size(16.dp), tint = c); Spacer(Modifier.width(4.dp))
            Column { Text(l, fontSize = 10.sp); Text(v, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}

@Composable fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
        title = { Text("Configuration") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                HorizontalDivider()
                Text("Permanent Thresholds (Collect Count)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ItemType.entries.filter { it.canPermanent }.forEach { type ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(type.icon, null, Modifier.size(16.dp), tint = type.color); Spacer(Modifier.width(8.dp)); Text(type.label, fontSize = 14.sp) }
                        var txt by remember { mutableStateOf(settings.thresholds[type]?.toString() ?: "10") }
                        OutlinedTextField(value = txt, onValueChange = { txt = it; it.toIntOrNull()?.let { v -> val m = settings.thresholds.toMutableMap(); m[type] = v.coerceAtLeast(0); onUpdate(settings.copy(thresholds = m)) } }, modifier = Modifier.width(80.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }
            }
        }
    )
}

@Composable fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(label); Switch(checked = checked, onCheckedChange = onCheckedChange) }
}

@Composable fun ResultDialog(state: SnakeState, onRestart: () -> Unit) {
    AlertDialog(onDismissRequest = {}, confirmButton = { Button(onClick = onRestart) { Text("RETRY") } }, title = { Text("Game Over") }, text = { Text("Final Score: ${state.score}\nUnlocks: ${state.permanentEffects.size}", fontSize = 18.sp) })
}