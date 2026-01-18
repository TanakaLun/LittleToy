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
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    @OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)

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
                            title = { Text("SNAKE EVO", fontWeight = FontWeight.Black) },
                            actions = {
                                IconButton(onClick = { 
                                    if (state.isStarted && !state.isGameOver) state = state.copy(isPaused = true)
                                    showSettings = true 
                                }) { Icon(Icons.Default.Settings, null) }
                            }
                        )
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        GameContent(state, settings, persistentHS, 
                            onHSReset = { 
                                persistentHS = 0
                                sp.edit().putInt("hs", 0).apply()
                                state = state.copy(highScore = 0)
                            },
                            onStateChange = { state = it }
                        )
                        if (showSettings) {
                            SettingsDialog(settings, onDismiss = { showSettings = false }) {
                                settings = it
                                saveSettings(it)
                            }
                        }
                    }
                }

                // 游戏结束保存分数
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GameContent(
    state: SnakeState, 
    settings: GameSettings, 
    persistentHS: Int, 
    onHSReset: () -> Unit,
    onStateChange: (SnakeState) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentState by rememberUpdatedState(state)

    val currentSpeed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
    
    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!currentState.isGameOver && !currentState.isPaused && currentState.isStarted) {
            delay(currentSpeed)
            onStateChange(gameTick(currentState, settings))
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部状态栏
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            // 最高分 Chip，支持长按归零
            Surface(
                onClick = {},
                onLongClick = onHSReset,
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.secondaryContainer,
                modifier = Modifier.height(48.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, null, Modifier.size(18.dp), tint = colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("BEST", style = MaterialTheme.typography.labelSmall)
                        Text("${if(state.score > persistentHS) state.score else persistentHS}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 速度比显示 (Progress / 100)
            val speedRatio = ((GameConfig.BASE_SPEED - currentSpeed).toFloat() / (GameConfig.BASE_SPEED - GameConfig.MIN_SPEED) * 100).toInt().coerceIn(0, 100)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SPEED RATIO", style = MaterialTheme.typography.labelSmall)
                Text("$speedRatio/100", fontWeight = FontWeight.Black, color = colorScheme.tertiary)
            }

            // 当前分 Chip
            InputChip(
                selected = true,
                onClick = {},
                label = { Text("${state.score}", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.Score, null, Modifier.size(18.dp)) }
            )
        }

        // 棋盘绘制 (保持之前的优化逻辑)
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                            if (state.isStarted && !state.isPaused && !state.isGameOver) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    onStateChange(handleInput(currentState, drag.x, drag.y))
                                }
                            }
                        }
                ) {
                    val dimAlpha by animateFloatAsState(if (!state.isStarted) 0.8f else 0f)
                    Canvas(Modifier.fillMaxSize().graphicsLayer(alpha = 1f - dimAlpha)) {
                        if (settings.showGrid) {
                            for (i in 0..gridW) drawLine(colorScheme.onSurface.copy(0.05f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, dH))
                            for (i in 0..gridH) drawLine(colorScheme.onSurface.copy(0.05f), Offset(0f, i * cellSizePx), Offset(dW, i * cellSizePx))
                        }
                        state.objects.forEach { obj ->
                            drawCircle(obj.type.color, cellSizePx / 3f, Offset(obj.pos.first * cellSizePx + cellSizePx / 2f, obj.pos.second * cellSizePx + cellSizePx / 2f))
                        }
                        state.snake.forEachIndexed { i, p ->
                            val color = if (i == 0) colorScheme.primary else colorScheme.primary.copy(alpha = 0.6f)
                            drawRoundRect(color, Offset(p.first * cellSizePx + 1f, p.second * cellSizePx + 1f), Size(cellSizePx - 2f, cellSizePx - 2f), CornerRadius(6.dp.toPx()))
                        }
                    }

                    if (!state.isStarted) {
                        Button(
                            onClick = { onStateChange(state.copy(isStarted = true, isPaused = false)) },
                            modifier = Modifier.align(Alignment.Center)
                        ) { Text("START GAME") }
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

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            // 使用填充式按钮替代 Done
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Apply Settings")
            }
        },
        title = { Text("Configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 常规设置
                SettingSection("General") {
                    SettingToggle("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                    SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                    SettingToggle("Haptic Feedback", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                }

                // 道具生成控制
                SettingSection("Item Spawning") {
                    ItemType.entries.forEach { type ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(type.icon, null, Modifier.size(20.dp), tint = type.color)
                                Spacer(Modifier.width(12.dp))
                                Text(type.label, style = MaterialTheme.typography.bodyMedium)
                            }
                            Checkbox(
                                checked = settings.enabledItems[type] == true,
                                onCheckedChange = { isChecked ->
                                    val newMap = settings.enabledItems.toMutableMap()
                                    newMap[type] = isChecked
                                    onUpdate(settings.copy(enabledItems = newMap))
                                }
                            )
                        }
                    }
                }
                
                SettingSection("Difficulty") {
                    Text("Max Items: ${settings.maxObjects}", style = MaterialTheme.typography.labelLarge)
                    Slider(value = settings.maxObjects.toFloat(), onValueChange = { onUpdate(settings.copy(maxObjects = it.toInt())) }, valueRange = 1f..10f)
                }
            }
        }
    )
}

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        content()
        HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}

@Composable
fun StatsList(items: Map<ItemType, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ItemType.entries.forEach { type ->
            val count = items[type] ?: 0
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(type.icon, null, Modifier.size(18.dp), tint = type.color)
                    Spacer(Modifier.width(8.dp))
                    Text(type.label)
                }
                Text("$count", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 辅助逻辑
fun handleInput(s: SnakeState, dx: Float, dy: Float): SnakeState {
    val newDir = when {
        abs(dx) > abs(dy) -> {
            if (dx > 0 && s.direction != Direction.LEFT) Direction.RIGHT 
            else if (dx < 0 && s.direction != Direction.RIGHT) Direction.LEFT 
            else s.direction
        }
        abs(dy) > abs(dx) -> {
            if (dy > 0 && s.direction != Direction.UP) Direction.DOWN 
            else if (dy < 0 && s.direction != Direction.DOWN) Direction.UP 
            else s.direction
        }
        else -> s.direction
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
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.score}", fontSize = 56.sp, fontWeight = FontWeight.Black)
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
