package io.tl.snake

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.view.KeyEvent
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
    private var isTV by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTV = checkIsTV()
        val sp = getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)

        fun loadSettings(): GameSettings {
            val jsonStr = sp.getString("settings", null) ?: return GameSettings(dynamicGrid = isTV)
            val json = JSONObject(jsonStr)
            val itemsJson = json.optJSONObject("enabledItems")
            val itemMap = ItemType.entries.associateWith { itemsJson?.optBoolean(it.name, true) ?: true }
            return GameSettings(
                showGrid = json.optBoolean("showGrid", true),
                isLoopMode = json.optBoolean("isLoopMode", false),
                dynamicGrid = json.optBoolean("dynamicGrid", isTV), // TV默认开启
                maxObjects = json.optInt("maxObjects", 5),
                enabledItems = itemMap
            )
        }

        enableEdgeToEdge()
        setContent {
            MyTheme {
                var settings by remember { mutableStateOf(loadSettings()) }
                var showSettings by remember { mutableStateOf(false) }
                var persistentHS by remember { mutableStateOf(sp.getInt("hs", 0)) }
                var state by remember { mutableStateOf(SnakeState(highScore = persistentHS)) }
                val context = LocalContext.current

                // 核心循环逻辑
                LaunchedEffect(state.isStarted, state.isPaused, state.isGameOver) {
                    while (state.isStarted && !state.isPaused && !state.isGameOver) {
                        val currentSpeed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
                        delay(currentSpeed)
                        state = gameTick(state, settings)
                    }
                }

                Scaffold(
                    topBar = {
                        // 手机端显示AppBar，TV端隐藏
                        if (!isTV) {
                            CenterAlignedTopAppBar(
                                title = { Text("SNAKE EVO", fontWeight = FontWeight.Black) },
                                actions = {
                                    if (state.isStarted && !state.isGameOver) {
                                        IconButton(onClick = { state = state.copy(isPaused = !state.isPaused) }) {
                                            Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                                        }
                                    }
                                    IconButton(onClick = { 
                                        if (state.isStarted) state = state.copy(isPaused = true)
                                        showSettings = true 
                                    }) { Icon(Icons.Default.Settings, null) }
                                }
                            )
                        }
                    }
                ) { p ->
                    // 修复 dp 引用：确保导入了 androidx.compose.ui.unit.dp
                    Box(Modifier.padding(if(isTV) PaddingValues(0.dp) else p).fillMaxSize()) {
                        GameContent(
                            state = state, 
                            settings = settings, 
                            persistentHS = persistentHS, 
                            onHSReset = { 
                                persistentHS = 0
                                sp.edit().putInt("hs", 0).apply()
                                state = state.copy(highScore = 0)
                            },
                            onStateChange = { state = it }
                        )
                        if (showSettings) {
                            SettingsDialog(settings, onDismiss = { showSettings = false; if(state.isStarted) state = state.copy(isPaused = false) }) {
                                settings = it
                                val json = JSONObject().apply {
                                    put("showGrid", it.showGrid); put("isLoopMode", it.isLoopMode)
                                    put("dynamicGrid", it.dynamicGrid); put("maxObjects", it.maxObjects)
                                    put("enabledItems", JSONObject().apply { it.enabledItems.forEach { (k, v) -> put(k.name, v) } })
                                }
                                sp.edit().putString("settings", json.toString()).apply()
                            }
                        }
                    }
                }

                // 更新高分逻辑
                LaunchedEffect(state.isGameOver) {
                    if (state.isGameOver && state.score > persistentHS) {
                        persistentHS = state.score
                        sp.edit().putInt("hs", state.score).apply()
                    }
                }
            }
        }
    }

    private fun checkIsTV(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // TV 端菜单键/返回键呼出设置
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            // 这里逻辑会在 UI 层通过 showSettings 控制
            return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameContent(state: SnakeState, settings: GameSettings, persistentHS: Int, onHSReset: () -> Unit, onStateChange: (SnakeState) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        // 状态栏
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = {}, onLongClick = onHSReset),
                color = colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, null, Modifier.size(18.dp), tint = colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("${if(state.score > persistentHS) state.score else persistentHS}", fontWeight = FontWeight.Bold)
                }
            }
            InputChip(selected = true, onClick = {}, label = { Text("${state.score}", fontWeight = FontWeight.Bold) }, leadingIcon = { Icon(Icons.Default.Score, null, Modifier.size(18.dp)) })
        }

        // 动态棋盘区域 (严格遵循要求的逻辑)
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
                    modifier = Modifier.size((dW / density).dp, (dH / density).dp).clip(RoundedCornerShape(16.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .pointerInput(state.isStarted, state.isPaused, state.isGameOver) {
                            if (state.isStarted && !state.isPaused && !state.isGameOver) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val newDir = when {
                                        abs(drag.x) > abs(drag.y) -> if (drag.x > 0 && state.direction != Direction.LEFT) Direction.RIGHT else if (drag.x < 0 && state.direction != Direction.RIGHT) Direction.LEFT else state.direction
                                        else -> if (drag.y > 0 && state.direction != Direction.UP) Direction.DOWN else if (drag.y < 0 && state.direction != Direction.DOWN) Direction.UP else state.direction
                                    }
                                    onStateChange(state.copy(direction = newDir))
                                }
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
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
                        Button(onClick = { onStateChange(state.copy(isStarted = true)) }, Modifier.align(Alignment.Center)) { Text("START") }
                    }
                }
            }
        }
    }

    if (state.isGameOver) {
        AlertDialog(onDismissRequest = {}, confirmButton = { Button(onClick = { onStateChange(SnakeState(highScore = persistentHS, isStarted = true, gridWidth = state.gridWidth, gridHeight = state.gridHeight)) }) { Text("REPLAY") } },
            title = { Text("Game Over") }, text = { Text("Score: ${state.score}") })
    }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Apply") } },
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Dynamic Grid"); Switch(settings.dynamicGrid, { onUpdate(settings.copy(dynamicGrid = it)) })
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Loop Mode"); Switch(settings.isLoopMode, { onUpdate(settings.copy(isLoopMode = it)) })
                }
                HorizontalDivider()
                ItemType.entries.forEach { type ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(type.icon, null, Modifier.size(20.dp), tint = type.color)
                            Text(type.label, Modifier.padding(start = 8.dp))
                        }
                        Checkbox(settings.enabledItems[type] == true, { onUpdate(settings.copy(enabledItems = settings.enabledItems.toMutableMap().apply { put(type, it) })) })
                    }
                }
            }
        }
    )
}
