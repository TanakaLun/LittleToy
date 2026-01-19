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
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
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

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("SNAKE EVO", fontWeight = FontWeight.Black) },
                            navigationIcon = {
                                // 垂直放置的分数 Chip 组
                                Column(Modifier.padding(start = 12.dp)) {
                                    ScoreChip(
                                        icon = Icons.Default.EmojiEvents,
                                        label = "HI",
                                        value = if(state.score > persistentHS) state.score else persistentHS,
                                        color = MaterialTheme.colorScheme.outline,
                                        onLongPress = {
                                            // 长按将最高分归零
                                            persistentHS = 0
                                            sp.edit().putInt("hs", 0).apply()
                                        }
                                    )
                                    ScoreChip(
                                        icon = Icons.Default.MilitaryTech,
                                        label = "SC",
                                        value = state.score,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            actions = {
                                if (state.isStarted && !state.isGameOver) {
                                    IconButton(onClick = { 
                                        state = state.copy(isPaused = !state.isPaused)
                                        if (settings.enableVibration) {
                                            vibrateShort(LocalContext.current)
                                        }
                                    }) {
                                        Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                                    }
                                }
                                IconButton(onClick = { 
                                    // 进入设置自动暂停
                                    if (state.isStarted && !state.isGameOver) state = state.copy(isPaused = true)
                                    showSettings = true 
                                    if (settings.enableVibration) {
                                        vibrateShort(LocalContext.current)
                                    }
                                }) { Icon(Icons.Default.Settings, null) }
                            }
                        )
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        GameContent(
                            state = state, 
                            settings = settings, 
                            onStateChange = { newState -> 
                                // 处理振动反馈
                                if (settings.enableVibration) {
                                    handleVibration(state, newState, LocalContext.current)
                                }
                                state = newState 
                            }
                        )
                        if (showSettings) {
                            SettingsDialog(
                                settings, 
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

// 振动工具函数
fun vibrateShort(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(50)
    }
}

fun vibrateLong(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(150)
    }
}

fun handleVibration(oldState: SnakeState, newState: SnakeState, context: Context) {
    // 吃到食物或道具
    if (newState.score > oldState.score && newState.itemsCollected != oldState.itemsCollected) {
        val lastItem = newState.itemsCollected.entries.lastOrNull { it.value > (oldState.itemsCollected[it.key] ?: 0) }
        lastItem?.let { (itemType, _) ->
            when (itemType) {
                ItemType.FOOD_POISON -> {
                    // 吃到毒食物，长振动
                    vibrateLong(context)
                }
                else -> {
                    // 吃到其他物品，短振动
                    vibrateShort(context)
                }
            }
        }
    }
    
    // 游戏结束
    if (!oldState.isGameOver && newState.isGameOver) {
        // 长振动表示游戏结束
        vibrateLong(context)
    }
    
    // 护盾消耗
    if (oldState.shieldCount > newState.shieldCount) {
        // 护盾消耗，短振动
        vibrateShort(context)
    }
}

@Composable
fun ScoreChip(
    icon: ImageVector, 
    label: String, 
    value: Int, 
    color: Color,
    onLongPress: (() -> Unit)? = null
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .padding(vertical = 2.dp)
            .pointerInput(onLongPress) {
                if (onLongPress != null) {
                    detectTapGestures(
                        onLongPress = {
                            onLongPress()
                        }
                    )
                }
            }
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(12.dp), tint = color)
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

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val density = LocalContext.current.resources.displayMetrics.density
            val availableWidth = constraints.maxWidth.toFloat()
            val availableHeight = constraints.maxHeight.toFloat()
            
            // 计算棋盘显示区域，留出顶部进度条空间
            val topPaddingForProgress = if (state.ghostTimeRemaining > 0 && !settings.isGhostPermanent) 30.dp else 0.dp
            val canvasPadding = 8.dp
            
            // 计算棋盘实际可用区域
            val canvasAvailableWidth = availableWidth - (canvasPadding * 2).toPx()
            val canvasAvailableHeight = availableHeight - (canvasPadding + topPaddingForProgress).toPx()
            
            // 计算棋盘尺寸
            val (gridW, gridH, cellSizePx) = if (settings.dynamicGrid) {
                // 动态网格模式：根据可用区域计算最大网格
                val minCellSize = 20f * density
                val maxW = (canvasAvailableWidth / minCellSize).toInt()
                val maxH = (canvasAvailableHeight / minCellSize).toInt()
                
                // 选择较小的网格尺寸以确保棋盘不会太大
                val targetGrid = minOf(maxW, maxH, 30) // 限制最大30x30
                
                // 重新计算单元格大小以完全填充可用空间
                val cellSize = minOf(
                    canvasAvailableWidth / targetGrid,
                    canvasAvailableHeight / targetGrid
                )
                
                Triple(targetGrid, targetGrid, cellSize)
            } else {
                // 固定网格模式：20x20，宽度适应屏幕
                val fixedGridSize = 20
                val cellSize = minOf(
                    canvasAvailableWidth / fixedGridSize,
                    canvasAvailableHeight / fixedGridSize
                )
                Triple(fixedGridSize, fixedGridSize, cellSize)
            }
            
            // 计算棋盘实际宽高
            val canvasWidth = cellSizePx * gridW
            val canvasHeight = cellSizePx * gridH
            
            // 计算居中偏移量
            val horizontalOffset = (availableWidth - canvasWidth) / 2
            val verticalOffset = (availableHeight - canvasHeight + topPaddingForProgress.toPx()) / 2

            // 监听动态棋盘逻辑，同步更新 State
            LaunchedEffect(gridW, gridH) {
                if (state.gridWidth != gridW || state.gridHeight != gridH) {
                    onStateChange(state.copy(gridWidth = gridW, gridHeight = gridH))
                }
            }

            // 棋盘顶部区域：道具进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topPaddingForProgress)
                    .padding(horizontal = horizontalOffset.dp)
                    .offset(y = (verticalOffset - canvasHeight / 2 - topPaddingForProgress.toPx() / 2).dp),
                contentAlignment = Alignment.Center
            ) {
                // Ghost 进度条 - 移到棋盘顶部
                if (state.ghostTimeRemaining > 0 && !settings.isGhostPermanent) {
                    val progress = state.ghostTimeRemaining.toFloat() / GameConfig.GHOST_DURATION_MS
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colorScheme.surfaceVariant.copy(0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(ItemType.GHOST.color)
                        )
                    }
                }
            }

            // 棋盘主体
            Box(
                modifier = Modifier
                    .width((canvasWidth / density).dp)
                    .height((canvasHeight / density).dp)
                    .offset(x = horizontalOffset.dp, y = verticalOffset.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant.copy(0.3f))
                    .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onStateChange(handleInput(currentState, drag.x, drag.y))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (settings.showGrid) {
                        for (i in 0..gridW) drawLine(
                            colorScheme.onSurface.copy(0.05f), 
                            Offset(i * cellSizePx, 0f), 
                            Offset(i * cellSizePx, canvasHeight)
                        )
                        for (i in 0..gridH) drawLine(
                            colorScheme.onSurface.copy(0.05f), 
                            Offset(0f, i * cellSizePx), 
                            Offset(canvasWidth, i * cellSizePx)
                        )
                    }
                    
                    state.objects.forEach { obj ->
                        drawCircle(
                            obj.type.color, 
                            cellSizePx * 0.35f, 
                            Offset(
                                obj.pos.first * cellSizePx + cellSizePx / 2f, 
                                obj.pos.second * cellSizePx + cellSizePx / 2f
                            )
                        )
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
                }

                // 护盾指示 (右上角)
                if (state.shieldCount > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        color = ItemType.SHIELD.color,
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, null, Modifier.size(12.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("${state.shieldCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                if (!state.isStarted) {
                    Button(
                        onClick = { 
                            onStateChange(state.copy(isStarted = true))
                            if (settings.enableVibration) {
                                vibrateShort(LocalContext.current)
                            }
                        }, 
                        Modifier.align(Alignment.Center)
                    ) {
                        Text("START")
                    }
                }
            }
        }
    }

    if (state.isGameOver) {
        ResultDialog(state) { 
            onStateChange(SnakeState(highScore = state.highScore, isStarted = true))
            if (settings.enableVibration) {
                vibrateShort(LocalContext.current)
            }
        }
    } else if (state.isPaused) {
        PauseStatsDialog(state) { 
            onStateChange(state.copy(isPaused = false))
            if (settings.enableVibration) {
                vibrateShort(LocalContext.current)
            }
        }
    }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
        title = { Text("Game Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingToggle("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                SettingToggle("Dynamic Grid", settings.dynamicGrid) { onUpdate(settings.copy(dynamicGrid = it)) }
                SettingToggle("Vibration", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                SettingToggle("Ghost Permanent", settings.isGhostPermanent) { onUpdate(settings.copy(isGhostPermanent = it)) }
                SliderSetting(
                    label = "Max Objects",
                    value = settings.maxObjects.toFloat(),
                    valueRange = 1f..10f,
                    steps = 8,
                    onValueChange = { onUpdate(settings.copy(maxObjects = it.toInt())) }
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Enabled Items", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ItemType.entries.forEach { type ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(type.icon, null, Modifier.size(18.dp), tint = type.color)
                            Spacer(Modifier.width(8.dp))
                            Text(type.label, fontSize = 14.sp)
                        }
                        Checkbox(
                            checked = settings.enabledItems[type] == true, 
                            onCheckedChange = {
                                val m = settings.enabledItems.toMutableMap()
                                m[type] = it
                                onUpdate(settings.copy(enabledItems = m))
                            }
                        )
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
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp)
            Text(value.toInt().toString(), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
fun ResultDialog(state: SnakeState, onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = {}, 
        confirmButton = { 
            Button(onClick = onRestart) { 
                Text("REPLAY") 
            } 
        },
        title = { Text("Game Over") },
        text = { 
            Column(
                modifier = Modifier.fillMaxWidth(), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) { 
                Text(
                    "${state.score}", 
                    fontSize = 56.sp, 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.primary
                ) 
                StatsList(state.itemsCollected)
            } 
        }
    )
}

@Composable
fun PauseStatsDialog(state: SnakeState, onResume: () -> Unit) {
    AlertDialog(
        onDismissRequest = onResume, 
        confirmButton = { 
            Button(onClick = onResume) { 
                Text("RESUME") 
            } 
        },
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
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Icon(type.icon, null, Modifier.size(16.dp), tint = type.color)
                Spacer(Modifier.width(8.dp))
                Text("${type.label}: $count", fontSize = 12.sp)
            }
        }
    }
}