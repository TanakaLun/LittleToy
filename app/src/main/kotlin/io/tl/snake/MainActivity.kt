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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        fun doVibrate(ms: Long) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(ms)
            }
        }

        enableEdgeToEdge()
        setContent {
            MyTheme {
                var settings by remember { mutableStateOf(loadSettings(sp)) }
                var showSettings by remember { mutableStateOf(false) }
                var persistentHS by remember { mutableStateOf(sp.getInt("hs", 0)) }
                var state by remember { mutableStateOf(SnakeState(highScore = persistentHS)) }

                // 监听游戏事件触发振动
                LaunchedEffect(state.lastEvent) {
                    if (settings.enableVibration) {
                        when (state.lastEvent) {
                            GameEvent.EAT_GOOD -> doVibrate(30L)
                            GameEvent.EAT_BAD -> doVibrate(80L)
                            GameEvent.SHIELD_LOST -> doVibrate(150L)
                            GameEvent.HIT_WALL -> doVibrate(250L)
                            else -> {}
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        Box(Modifier.fillMaxWidth().statusBarsPadding().height(70.dp)) {
                            // 左侧分数垂直显示
                            Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) {
                                ScoreChip(
                                    icon = Icons.Default.EmojiEvents, 
                                    label = "HI", 
                                    value = if(state.score > persistentHS) state.score else persistentHS, 
                                    color = MaterialTheme.colorScheme.outline,
                                    onLongClick = {
                                        persistentHS = 0
                                        sp.edit().putInt("hs", 0).apply()
                                        doVibrate(100L)
                                    }
                                )
                                ScoreChip(Icons.Default.MilitaryTech, "SC", state.score, MaterialTheme.colorScheme.primary)
                            }
                            // 标题绝对居中
                            Text("SNAKE EVO", fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
                            // 右侧设置
                            Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
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
                        }
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        GameContent(state, settings) { state = it }
                        if (showSettings) {
                            SettingsDialog(settings, { showSettings = false }, { settings = it; saveSettings(sp, it) })
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

    private fun loadSettings(sp: android.content.SharedPreferences): GameSettings {
        val jsonStr = sp.getString("settings", null) ?: return GameSettings()
        val json = JSONObject(jsonStr)
        val itemMap = ItemType.entries.associateWith { type ->
            json.optJSONObject("enabledItems")?.optBoolean(type.name, true) ?: true
        }
        return GameSettings(
            showGrid = json.optBoolean("showGrid", true),
            isLoopMode = json.optBoolean("isLoopMode", false),
            dynamicGrid = json.optBoolean("dynamicGrid", true),
            enableVibration = json.optBoolean("enableVibration", true),
            maxObjects = json.optInt("maxObjects", 5),
            isGhostPermanent = json.optBoolean("isGhostPermanent", false),
            enabledItems = itemMap
        )
    }

    private fun saveSettings(sp: android.content.SharedPreferences, s: GameSettings) {
        val json = JSONObject().apply {
            put("showGrid", s.showGrid); put("isLoopMode", s.isLoopMode); put("dynamicGrid", s.dynamicGrid)
            put("enableVibration", s.enableVibration); put("maxObjects", s.maxObjects); put("isGhostPermanent", s.isGhostPermanent)
            put("enabledItems", JSONObject().apply { s.enabledItems.forEach { (k, v) -> put(k.name, v) } })
        }
        sp.edit().putString("settings", json.toString()).apply()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScoreChip(icon: ImageVector, label: String, value: Int, color: Color, onLongClick: (() -> Unit)? = null) {
    Surface(
        color = color.copy(alpha = 0.1f), 
        shape = RoundedCornerShape(16.dp), 
        modifier = Modifier.padding(vertical = 1.dp).combinedClickable(
            onClick = {},
            onLongClick = onLongClick
        )
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(10.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text("$label: $value", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun GameContent(state: SnakeState, settings: GameSettings, onStateChange: (SnakeState) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val currentState by rememberUpdatedState(state)
    val currentSpeed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
    
    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!currentState.isGameOver && !currentState.isPaused && currentState.isStarted) {
            delay(currentSpeed)
            onStateChange(gameTick(currentState, settings, currentSpeed))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val density = LocalContext.current.resources.displayMetrics.density
        val cellBasePx = 24f * density
        
        val gridW = if (settings.dynamicGrid) (constraints.maxWidth / cellBasePx).toInt() else 20
        val gridH = if (settings.dynamicGrid) (constraints.maxHeight / cellBasePx).toInt() else 20
        
        val cellSizePx = (constraints.maxWidth.toFloat() / gridW).coerceAtMost(constraints.maxHeight.toFloat() / gridH)
        val finalW = cellSizePx * gridW
        val finalH = cellSizePx * gridH

        LaunchedEffect(gridW, gridH) {
            if (state.gridWidth != gridW || state.gridHeight != gridH) 
                onStateChange(state.copy(gridWidth = gridW, gridHeight = gridH))
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size((finalW / density).dp, (finalH / density).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant.copy(0.3f))
                    .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onStateChange(handleInput(currentState, drag.x, drag.y))
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (settings.showGrid) {
                        for (i in 0..gridW) drawLine(colorScheme.onSurface.copy(0.05f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, finalH))
                        for (i in 0..gridH) drawLine(colorScheme.onSurface.copy(0.05f), Offset(0f, i * cellSizePx), Offset(finalW, i * cellSizePx))
                    }
                    
                    state.objects.forEach { obj ->
                        drawCircle(obj.type.color, cellSizePx * 0.35f, Offset(obj.pos.first * cellSizePx + cellSizePx / 2, obj.pos.second * cellSizePx + cellSizePx / 2))
                    }
                    
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
                            topLeft = Offset(p.first * cellSizePx + 1.5f, p.second * cellSizePx + 1.5f),
                            size = Size(cellSizePx - 3f, cellSizePx - 3f),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                    }

                    // 【核心修改：进度条移动到顶部】
                    if (state.ghostTimeRemaining > 0 && !settings.isGhostPermanent) {
                        val progress = state.ghostTimeRemaining.toFloat() / GameConfig.GHOST_DURATION_MS
                        drawRect(ItemType.GHOST.color.copy(0.2f), Offset(0f, 0f), Size(finalW, 4.dp.toPx()))
                        drawRect(ItemType.GHOST.color, Offset(0f, 0f), Size(finalW * progress, 4.dp.toPx()))
                    }
                }

                if (state.shieldCount > 0) {
                    Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), color = ItemType.SHIELD.color, shape = RoundedCornerShape(4.dp)) {
                        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, Modifier.size(12.dp), tint = Color.White)
                            Text("${state.shieldCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }

                if (!state.isStarted) {
                    Button(onClick = { onStateChange(state.copy(isStarted = true)) }, Modifier.align(Alignment.Center)) { Text("START") }
                }
            }
        }
    }

    if (state.isGameOver) ResultDialog(state) { onStateChange(SnakeState(highScore = state.highScore, isStarted = true)) }
    else if (state.isPaused) PauseStatsDialog(state) { onStateChange(state.copy(isPaused = false)) }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
        title = { Text("Configuration") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingToggle("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                SettingToggle("Dynamic Grid", settings.dynamicGrid) { onUpdate(settings.copy(dynamicGrid = it)) }
                SettingToggle("Vibration", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                SettingToggle("Permanent Ghost", settings.isGhostPermanent) { onUpdate(settings.copy(isGhostPermanent = it)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ItemType.entries.forEach { type ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(type.icon, null, Modifier.size(18.dp), tint = type.color)
                            Text(type.label, Modifier.padding(start = 8.dp), fontSize = 14.sp)
                        }
                        Checkbox(checked = settings.enabledItems[type] == true, onCheckedChange = {
                            val m = settings.enabledItems.toMutableMap(); m[type] = it; onUpdate(settings.copy(enabledItems = m))
                        })
                    }
                }
            }
        }
    )
}

fun handleInput(s: SnakeState, dx: Float, dy: Float): SnakeState {
    val newDir = when {
        abs(dx) > abs(dy) -> if (dx > 0 && s.direction != Direction.LEFT) Direction.RIGHT else if (dx < 0 && s.direction != Direction.RIGHT) Direction.LEFT else s.direction
        else -> if (dy > 0 && s.direction != Direction.UP) Direction.DOWN else if (dy < 0 && s.direction != Direction.DOWN) Direction.UP else s.direction
    }
    return s.copy(direction = newDir)
}

@Composable
fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp); Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ResultDialog(state: SnakeState, onRestart: () -> Unit) {
    AlertDialog(onDismissRequest = {}, confirmButton = { Button(onClick = onRestart) { Text("REPLAY") } },
        title = { Text("Game Over") },
        text = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${state.score}", fontSize = 48.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            StatsList(state.itemsCollected)
        }}
    )
}

@Composable
fun PauseStatsDialog(state: SnakeState, onResume: () -> Unit) {
    AlertDialog(onDismissRequest = onResume, confirmButton = { Button(onClick = onResume) { Text("RESUME") } },
        title = { Text("Paused") }, 
        text = { 
            Column {
                Text("Current Score: ${state.score}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                StatsList(state.itemsCollected)
            }
        }
    )
}

@Composable
fun StatsList(items: Map<ItemType, Int>) {
    Column {
        items.filter { it.value > 0 }.forEach { (type, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Icon(type.icon, null, Modifier.size(16.dp), tint = type.color)
                Text("${type.label}: $count", Modifier.padding(start = 8.dp), fontSize = 13.sp)
            }
        }
    }
}
