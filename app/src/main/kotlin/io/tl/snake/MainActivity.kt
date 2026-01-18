package io.tl.snake

import android.content.Context
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
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
                val thresholdsJson = json.optJSONObject("permThresholds")
                GameSettings(
                    showGrid = json.optBoolean("showGrid", true),
                    isLoopMode = json.optBoolean("isLoopMode", false),
                    dynamicGrid = json.optBoolean("dynamicGrid", true),
                    enableVibration = json.optBoolean("enableVibration", true),
                    maxObjects = json.optInt("maxObjects", 5),
                    enabledItems = ItemType.entries.associateWith { json.optJSONObject("enabledItems")?.optBoolean(it.name, true) ?: true },
                    useGradient = json.optBoolean("useGradient", true),
                    usePulse = json.optBoolean("usePulse", true),
                    permThresholds = ItemType.entries.associateWith { thresholdsJson?.optInt(it.name, if(it == ItemType.GHOST) 10 else if(it == ItemType.SHIELD) 5 else 0) ?: 0 }
                )
            } catch (e: Exception) { GameSettings() }
        }

        fun saveSettings(s: GameSettings) = sp.edit().putString("settings", JSONObject().apply {
            put("showGrid", s.showGrid); put("isLoopMode", s.isLoopMode); put("dynamicGrid", s.dynamicGrid)
            put("enableVibration", s.enableVibration); put("maxObjects", s.maxObjects)
            put("useGradient", s.useGradient); put("usePulse", s.usePulse)
            put("enabledItems", JSONObject().apply { s.enabledItems.forEach { (k, v) -> put(k.name, v) } })
            put("permThresholds", JSONObject().apply { s.permThresholds.forEach { (k, v) -> put(k.name, v) } })
        }.toString()).apply()

        enableEdgeToEdge()
        setContent {
            MyTheme {
                var settings by remember { mutableStateOf(loadSettings()) }
                var showSettings by remember { mutableStateOf(false) }
                var persistentHS by remember { mutableStateOf(sp.getInt("hs", 0)) }
                var state by remember { mutableStateOf(SnakeState(highScore = persistentHS)) }
                val context = LocalContext.current
                val pulse by rememberInfiniteTransition().animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse))

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(title = { Text("SNAKE EVO", fontWeight = FontWeight.Black) }, actions = {
                            if (state.isStarted && !state.isGameOver) IconButton(onClick = { state = state.copy(isPaused = !state.isPaused) }) { Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null) }
                            IconButton(onClick = { if (state.isStarted) state = state.copy(isPaused = true); showSettings = true }) { Icon(Icons.Default.Settings, null) }
                        })
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        GameContent(state, settings, persistentHS, pulse, onHSReset = { persistentHS = 0; sp.edit().putInt("hs", 0).apply(); state = state.copy(highScore = 0) }, onStateChange = { if (it.direction != state.direction) triggerVibration(context, settings.enableVibration, 10); state = it })
                        if (showSettings) SettingsDialog(settings, onDismiss = { showSettings = false }) { settings = it; saveSettings(it) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameContent(state: SnakeState, settings: GameSettings, persistentHS: Int, pulse: Float, onHSReset: () -> Unit, onStateChange: (SnakeState) -> Unit) {
    val currentState by rememberUpdatedState(state)
    var inputLocked by remember { mutableStateOf(false) }

    LaunchedEffect(state.isGameOver, state.isPaused, state.isStarted) {
        while (!currentState.isGameOver && !currentState.isPaused && currentState.isStarted) {
            delay((GameConfig.BASE_SPEED - (currentState.score / 100 * 5) + currentState.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED))
            onStateChange(gameTick(currentState, settings))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Surface(Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick={}, onLongClick=onHSReset), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(" BEST: ${maxOf(state.score, persistentHS)} ", Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
            }
            InputChip(selected = true, onClick = {}, label = { Text("SCORE: ${state.score}") })
        }
        BoxWithConstraints(Modifier.fillMaxSize().weight(1f).padding(16.dp)) {
            val density = LocalContext.current.resources.displayMetrics.density
            val cellPx = 22f * density
            val gridW = if(settings.dynamicGrid) (constraints.maxWidth/cellPx).toInt().coerceIn(10, 30) else 20
            val gridH = if(settings.dynamicGrid) (constraints.maxHeight/cellPx).toInt().coerceIn(10, 45) else 20
            val cellSize = (constraints.maxWidth.toFloat()/gridW).coerceAtMost(constraints.maxHeight.toFloat()/gridH)

            LaunchedEffect(gridW, gridH) { if (state.gridWidth != gridW || state.gridHeight != gridH) onStateChange(state.copy(gridWidth = gridW, gridHeight = gridH)) }

            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Box(Modifier.size((cellSize*gridW/density).dp, (cellSize*gridH/density).dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                    .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                        if (state.isStarted && !state.isPaused && !state.isGameOver) detectDragGestures(onDragStart={inputLocked=false}, onDrag={c, d -> c.consume(); if(!inputLocked){ val ns = handleInput(currentState, d.x, d.y); if(ns.direction != currentState.direction){ onStateChange(ns); inputLocked=true } }})
                    }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        if (settings.showGrid) {
                            for(i in 0..gridW) drawLine(Color.Gray.copy(0.1f), Offset(i*cellSize, 0f), Offset(i*cellSize, gridH*cellSize))
                            for(i in 0..gridH) drawLine(Color.Gray.copy(0.1f), Offset(0f, i*cellSize), Offset(gridW*cellSize, i*cellSize))
                        }
                        state.objects.forEach { drawCircle(it.type.color, cellSize/3.5f, Offset(it.pos.first*cellSize+cellSize/2, it.pos.second*cellSize+cellSize/2)) }
                        val isPermGhost = (state.cumulativeCounts[ItemType.GHOST] ?: 0) >= (settings.permThresholds[ItemType.GHOST] ?: Int.MAX_VALUE)
                        state.snake.forEachIndexed { i, p ->
                            val color = if (settings.useGradient) lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, i.toFloat()/state.snake.size) else MaterialTheme.colorScheme.primary
                            val alpha = if (settings.usePulse && (state.ghostTicks > 0 || isPermGhost)) pulse else 1f
                            drawRoundRect(if(i==0 && state.hasShield) Color.Cyan else color, Offset(p.first*cellSize+1f, p.second*cellSize+1f), Size(cellSize-2f, cellSize-2f), CornerRadius(4.dp.toPx()), alpha = alpha)
                        }
                    }
                    if (!state.isStarted) Button(onClick = { onStateChange(state.copy(isStarted = true)) }, Modifier.align(Alignment.Center)) { Text("START") }
                }
            }
        }
    }
    if (state.isGameOver) AlertDialog(onDismissRequest={}, confirmButton={ Button(onClick={ onStateChange(SnakeState(highScore=persistentHS, isStarted=true, gridWidth=state.gridWidth, gridHeight=state.gridHeight)) }){ Text("REPLAY") } }, title={ Text("Game Over") }, text={ Text("Score: ${state.score}") })
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = onDismiss) { Text("Done") } }, title = { Text("Settings") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Permanent Effects Threshold", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ItemType.entries.filter { it != ItemType.SLOW && it.score > 0 }.forEach { type ->
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(type.label, Modifier.weight(1f))
                    var text by remember { mutableStateOf(settings.permThresholds[type]?.toString() ?: "0") }
                    OutlinedTextField(value = text, onValueChange = { 
                        text = it.filter { c -> c.isDigit() }
                        val newVal = text.toIntOrNull() ?: 0
                        val newMap = settings.permThresholds.toMutableMap()
                        newMap[type] = newVal
                        onUpdate(settings.copy(permThresholds = newMap))
                    }, modifier = Modifier.width(80.dp), label = { Text("Goal") }, singleLine = true)
                }
            }
            HorizontalDivider()
            SettingToggle("Show Grid", settings.showGrid) { onUpdate(settings.copy(showGrid = it)) }
            SettingToggle("Loop Mode", settings.isLoopMode) { onUpdate(settings.copy(isLoopMode = it)) }
            SettingToggle("Body Gradient", settings.useGradient) { onUpdate(settings.copy(useGradient = it)) }
            SettingToggle("Status Pulse", settings.usePulse) { onUpdate(settings.copy(usePulse = it)) }
        }
    })
}

fun handleInput(s: SnakeState, dx: Float, dy: Float): SnakeState {
    if (abs(dx) < 15f && abs(dy) < 15f) return s
    val newDir = if (abs(dx) > abs(dy)) {
        if (dx > 0 && s.direction != Direction.LEFT) Direction.RIGHT else if (dx < 0 && s.direction != Direction.RIGHT) Direction.LEFT else s.direction
    } else {
        if (dy > 0 && s.direction != Direction.UP) Direction.DOWN else if (dy < 0 && s.direction != Direction.DOWN) Direction.UP else s.direction
    }
    return s.copy(direction = newDir)
}

fun triggerVibration(c: Context, e: Boolean, d: Long) {
    if (!e) return
    val v = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE)) else v.vibrate(d)
}

@Composable fun SettingToggle(l: String, c: Boolean, o: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().height(48.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(l); Switch(checked = c, onCheckedChange = o) } }
