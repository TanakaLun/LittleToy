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
import kotlinx.coroutines.launch
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
                                    // 左侧分数列
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
                                    IconButton(onClick = { 
                                        state = state.copy(isPaused = !state.isPaused)
                                        triggerVibration(context, settings.enableVibration, 20L)
                                    }) {
                                        Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
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
                        GameContent(
                            state = state, 
                            settings = settings, 
                            persistentHS = persistentHS, 
                            onHSReset = { 
                                persistentHS = 0
                                sp.edit().putInt("hs", 0).apply()
                                state = state.copy(highScore = 0)
                                triggerVibration(context, settings.enableVibration, 100L)
                            },
                            onStateChange = { 
                                if (it.direction != state.direction) {
                                    triggerVibration(context, settings.enableVibration, 15L)
                                }
                                state = it 
                            }
                        )
                        if (showSettings) {
                            SettingsDialog(settings, onDismiss = { showSettings = false }) {
                                settings = it
                                saveSettings(it)
                            }
                        }
                    }
                }

                LaunchedEffect(state.isGameOver) {
                    if (state.isGameOver) {
                        if (state.score > persistentHS) {
                            persistentHS = state.score
                            sp.edit().putInt("hs", state.score).apply()
                            triggerVibration(context, settings.enableVibration, 300L)
                        } else {
                            triggerVibration(context, settings.enableVibration, 100L)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    val isNewRecord = state.isGameOver && state.score > state.highScore

    val currentSpeed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
    
    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!currentState.isGameOver && !currentState.isPaused && currentState.isStarted) {
            delay(currentSpeed)
            onStateChange(gameTick(currentState, settings, currentSpeed))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 原有的顶部状态栏 (保持美观)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                val speedRatio = ((GameConfig.BASE_SPEED - currentSpeed).toFloat() / (GameConfig.BASE_SPEED - GameConfig.MIN_SPEED) * 100).toInt().coerceIn(0, 100)
                Text("SPEED: $speedRatio%", style = MaterialTheme.typography.labelLarge, color = colorScheme.secondary, fontWeight = FontWeight.Bold)
                
                if (state.ghostTimeRemaining > 0 && !settings.isGhostPermanent) {
                    Text("GHOST ACTIVE", style = MaterialTheme.typography.labelSmall, color = ItemType.GHOST.color, fontWeight = FontWeight.Bold)
                }
            }

            // 棋盘区域
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
                            // 绘制网格
                            if (settings.showGrid) {
                                for (i in 0..gridW) drawLine(colorScheme.onSurface.copy(0.05f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, dH))
                                for (i in 0..gridH) drawLine(colorScheme.onSurface.copy(0.05f), Offset(0f, i * cellSizePx), Offset(dW, i * cellSizePx))
                            }
                            // 绘制道具
                            state.objects.forEach { obj ->
                                drawCircle(obj.type.color, cellSizePx / 3f, Offset(obj.pos.first * cellSizePx + cellSizePx / 2f, obj.pos.second * cellSizePx + cellSizePx / 2f))
                            }
                            // 绘制蛇 (层次感设计)
                            state.snake.forEachIndexed { i, p ->
                                val baseColor = when {
                                    i == 0 && (state.ghostTimeRemaining > 0 || settings.isGhostPermanent) -> ItemType.GHOST.color
                                    i == 0 && state.shieldCount > 0 -> ItemType.SHIELD.color
                                    i == 0 -> colorScheme.primary
                                    else -> colorScheme.primary
                                }
                                // 计算层次透明度：头部 1.0，尾部逐渐变淡
                                val alpha = (1f - (i.toFloat() / state.snake.size)).coerceAtLeast(0.2f)
                                
                                drawRoundRect(
                                    color = baseColor.copy(alpha = alpha), 
                                    topLeft = Offset(p.first * cellSizePx + 1f, p.second * cellSizePx + 1f), 
                                    size = Size(cellSizePx - 2f, cellSizePx - 2f), 
                                    cornerRadius = CornerRadius(6.dp.toPx())
                                )
                            }

                            // 动态进度条 (Ghost 剩余时间)
                            if (state.ghostTimeRemaining > 0 && !settings.isGhostPermanent) {
                                val progress = state.ghostTimeRemaining.toFloat() / GameConfig.GHOST_DURATION_MS
                                val barWidth = dW * 0.8f
                                val barHeight = 4.dp.toPx()
                                drawRoundRect(
                                    color = ItemType.GHOST.color.copy(alpha = 0.3f),
                                    topLeft = Offset((dW - barWidth) / 2, dH - 20.dp.toPx()),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(barHeight / 2)
                                )
                                drawRoundRect(
                                    color = ItemType.GHOST.color,
                                    topLeft = Offset((dW - barWidth) / 2, dH - 20.dp.toPx()),
                                    size = Size(barWidth * progress, barHeight),
                                    cornerRadius = CornerRadius(barHeight / 2)
                                )
                            }
                        }

                        // 护盾指示组件 (右上角)
                        if (state.shieldCount > 0) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                                color = ItemType.SHIELD.color,
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 4.dp
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(ItemType.SHIELD.icon, null, Modifier.size(16.dp), tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text("x${state.shieldCount}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        if (!state.isStarted) {
                            Button(onClick = { onStateChange(state.copy(isStarted = true, isPaused = false)) }, modifier = Modifier.align(Alignment.Center)) {
                                Text("START GAME")
                            }
                        }
                    }
                }
            }
        }

        if (isNewRecord) { ConfettiEffect() }
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
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Apply Settings")
            }
        },
        title = { Text("Configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingSection("General") {
                    SettingToggle("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                    SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                    SettingToggle("Haptic Feedback", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                }
                SettingSection("Abilities") {
                    SettingToggle("Ghost Permanent", settings.isGhostPermanent) { onUpdate(settings.copy(isGhostPermanent = it)) }
                }
                SettingSection("Item Spawning") {
                    ItemType.entries.forEach { type ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(type.icon, null, Modifier.size(20.dp), tint = type.color)
                                Spacer(Modifier.width(12.dp))
                                Text(type.label)
                            }
                            Checkbox(checked = settings.enabledItems[type] == true, onCheckedChange = { isChecked ->
                                val newMap = settings.enabledItems.toMutableMap()
                                newMap[type] = isChecked
                                onUpdate(settings.copy(enabledItems = newMap))
                            })
                        }
                    }
                }
                SettingSection("Difficulty") {
                    Text("Max Items: ${settings.maxObjects}")
                    Slider(value = settings.maxObjects.toFloat(), onValueChange = { onUpdate(settings.copy(maxObjects = it.toInt())) }, valueRange = 1f..10f)
                }
            }
        }
    )
}

@Composable
fun ConfettiEffect() {
    val particles = remember { List(50) { ConfettiParticle() } }
    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing))
    )

    Canvas(Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val y = (p.startY + (progress * 1500f * p.speed)) % size.height
            val x = p.startX + (progress * 200f * p.drift)
            drawRect(
                color = p.color,
                topLeft = Offset(x, y),
                size = Size(15f, 30f),
                alpha = 1f - (y / size.height).coerceIn(0f, 1f)
            )
        }
    }
}

class ConfettiParticle {
    val startX = Random.nextFloat() * 1000f
    val startY = -Random.nextFloat() * 1000f
    val speed = Random.nextFloat() * 0.5f + 0.5f
    val drift = Random.nextFloat() * 2f - 1f
    val color = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), 1f)
}

// ... Rest of the helper components (SettingsDialog, StatsList, ResultDialog, etc.) ...
// ensure SettingsDialog button is a Button as requested
@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Apply Settings")
            }
        },
        title = { Text("Configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingSection("General") {
                    SettingToggle("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
                    SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
                    SettingToggle("Haptic Feedback", settings.enableVibration) { onUpdate(settings.copy(enableVibration = it)) }
                }
                SettingSection("Item Spawning") {
                    ItemType.entries.forEach { type ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(type.icon, null, Modifier.size(20.dp), tint = type.color)
                                Spacer(Modifier.width(12.dp))
                                Text(type.label)
                            }
                            Checkbox(checked = settings.enabledItems[type] == true, onCheckedChange = { isChecked ->
                                val newMap = settings.enabledItems.toMutableMap()
                                newMap[type] = isChecked
                                onUpdate(settings.copy(enabledItems = newMap))
                            })
                        }
                    }
                }
                SettingSection("Difficulty") {
                    Text("Max Items: ${settings.maxObjects}")
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