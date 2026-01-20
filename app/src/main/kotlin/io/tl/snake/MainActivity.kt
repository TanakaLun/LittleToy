package io.tl.snake

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import io.tl.snake.logic.*
import io.tl.snake.ui.*
import io.tl.snake.ui.theme.MyTheme
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var state by mutableStateOf(SnakeState())
    private var settings by mutableStateOf(GameSettings())
    private var showSettings by mutableStateOf(false)
    private var persistentHS by mutableIntStateOf(0)
    private var isTV by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("snake_evo_prefs", Context.MODE_PRIVATE)
        isTV = isRunningOnTV()
        
        persistentHS = sp.getInt("hs", 0)
        settings = loadSettings(sp, isTV)
        state = SnakeState(highScore = persistentHS)

        enableEdgeToEdge()
        setContent {
            MyTheme {
                val context = LocalContext.current
                val currentSpeed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)

                LaunchedEffect(state.isStarted, state.isPaused, state.isGameOver) {
                    while (state.isStarted && !state.isPaused && !state.isGameOver) {
                        delay(currentSpeed)
                        val newState = gameTick(state, settings)
                        if (settings.enableVibration && newState.lastEvent != state.lastEvent) {
                            triggerVibration(context, 50)
                        }
                        state = newState
                    }
                }

                // 手机端显示 TopAppBar，TV端全屏
                Scaffold(
                    topBar = {
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
                    Box(Modifier.padding(if(isTV) PaddingValues(0.dp) else p).fillMaxSize()) {
                        GameContent(
                            state = state, settings = settings, persistentHS = persistentHS,
                            onHSReset = { persistentHS = 0; sp.edit().putInt("hs", 0).apply() },
                            onStateChange = { state = it },
                            onHandleInput = { dx, dy -> state = handleInput(state, dx, dy) }
                        )
                        if (showSettings) SettingsDialog(settings, onDismiss = { showSettings = false; if(state.isStarted) state = state.copy(isPaused = false) }) { settings = it; saveSettings(sp, it) }
                        if (state.isGameOver) {
                            ResultDialog(state) { 
                                if (state.score > persistentHS) { persistentHS = state.score; sp.edit().putInt("hs", state.score).apply() }
                                state = SnakeState(highScore = persistentHS, isStarted = true, gridWidth = state.gridWidth, gridHeight = state.gridHeight) 
                            }
                        } else if (state.isPaused && !showSettings) {
                            PauseStatsDialog(state) { state = state.copy(isPaused = false) }
                        }
                    }
                }
            }
        }
    }

    private fun isRunningOnTV(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val newDir = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (state.direction != Direction.DOWN) Direction.UP else null
            KeyEvent.KEYCODE_DPAD_DOWN -> if (state.direction != Direction.UP) Direction.DOWN else null
            KeyEvent.KEYCODE_DPAD_LEFT -> if (state.direction != Direction.RIGHT) Direction.LEFT else null
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (state.direction != Direction.LEFT) Direction.RIGHT else null
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> {
                if (state.isStarted && !state.isGameOver) {
                    state = state.copy(isPaused = true); showSettings = true; return true
                } else null
            }
            else -> null
        }
        newDir?.let { state = state.copy(direction = it); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun triggerVibration(context: Context, ms: Long) {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else v.vibrate(ms)
    }

    private fun handleInput(s: SnakeState, dx: Float, dy: Float): SnakeState {
        val nD = if (abs(dx) > abs(dy)) {
            if (dx > 0 && s.direction != Direction.LEFT) Direction.RIGHT else if (dx < 0 && s.direction != Direction.RIGHT) Direction.LEFT else s.direction
        } else {
            if (dy > 0 && s.direction != Direction.UP) Direction.DOWN else if (dy < 0 && s.direction != Direction.DOWN) Direction.UP else s.direction
        }
        return s.copy(direction = nD)
    }

    private fun loadSettings(sp: android.content.SharedPreferences, isTV: Boolean): GameSettings {
        val jsonStr = sp.getString("settings", null) ?: return GameSettings(dynamicGrid = isTV)
        val json = JSONObject(jsonStr)
        val itemsJson = json.optJSONObject("enabledItems")
        val itemMap = ItemType.entries.associateWith { itemsJson?.optBoolean(it.name, true) ?: true }
        return GameSettings(
            showGrid = json.optBoolean("showGrid", true),
            isLoopMode = json.optBoolean("isLoopMode", false),
            dynamicGrid = json.optBoolean("dynamicGrid", isTV),
            enableVibration = json.optBoolean("enableVibration", true),
            maxObjects = json.optInt("maxObjects", 5),
            enabledItems = itemMap
        )
    }

    private fun saveSettings(sp: android.content.SharedPreferences, s: GameSettings) {
        val json = JSONObject().apply {
            put("showGrid", s.showGrid); put("isLoopMode", s.isLoopMode); put("dynamicGrid", s.dynamicGrid)
            put("enableVibration", s.enableVibration); put("maxObjects", s.maxObjects)
            put("enabledItems", JSONObject().apply { s.enabledItems.forEach { (k, v) -> put(k.name, v) } })
        }
        sp.edit().putString("settings", json.toString()).apply()
    }
}
