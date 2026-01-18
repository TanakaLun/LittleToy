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

        // Helper to trigger vibration globally
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
                                // Trigger haptic on direction change
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
                            // Strong vibration for new record
                            triggerVibration(context, settings.enableVibration, 300L)
                        } else {
                            // Normal vibration for game over
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
            onStateChange(gameTick(currentState, settings))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Status Bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { },
                            onLongClick = onHSReset
                        ),
                    color = colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
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

                val speedRatio = ((GameConfig.BASE_SPEED - currentSpeed).toFloat() / (GameConfig.BASE_SPEED - GameConfig.MIN_SPEED) * 100).toInt().coerceIn(0, 100)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SPEED RATIO", style = MaterialTheme.typography.labelSmall)
                    Text("$speedRatio/100", fontWeight = FontWeight.Black, color = colorScheme.tertiary)
                }

                InputChip(
                    selected = true,
                    onClick = {},
                    label = { Text("${state.score}", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Score, null, Modifier.size(18.dp)) }
                )
            }

            // Board Area
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
                            Button(onClick = { onStateChange(state.copy(isStarted = true, isPaused = false)) }, modifier = Modifier.align(Alignment.Center)) {
                                Text("START GAME")
                            }
                        }
                    }
                }
            }
        }

        // --- Celebration Overlay ---
        if (isNewRecord) {
            ConfettiEffect()
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