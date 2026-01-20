package io.tl.snake

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.tl.snake.logic.*
import io.tl.snake.ui.*
import io.tl.snake.ui.theme.MyTheme
import kotlinx.coroutines.delay
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var state by mutableStateOf(SnakeState())
    private var settings by mutableStateOf(GameSettings())
    private var showSettings by mutableStateOf(false)
    private var nextDir by mutableStateOf(Direction.UP)
    private var isTV by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 判断是否为 TV
        isTV = isRunningOnTV()
        
        val sp = getSharedPreferences("snake_evo_prefs", Context.MODE_PRIVATE)
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        settings = loadSettings(sp, isTV)
        state = SnakeState(highScore = sp.getInt("hs", 0))

        enableEdgeToEdge()
        setContent {
            MyTheme {
                LaunchedEffect(state.isStarted, state.isPaused, state.isGameOver) {
                    while (state.isStarted && !state.isPaused && !state.isGameOver) {
                        val speed = (GameConfig.BASE_SPEED - (state.score / 100 * 5)).coerceAtLeast(GameConfig.MIN_SPEED)
                        delay(speed)
                        val newState = gameTick(state, settings, nextDir)
                        if (settings.enableVibration && newState.lastEvent != state.lastEvent) {
                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                        state = newState
                    }
                }

                Surface {
                    MainGameContent(
                        state = state, 
                        settings = settings, 
                        isTV = isTV,
                        onStateChange = { state = it },
                        onOpenSettings = { showSettings = true; state = state.copy(isPaused = true) },
                        onDirectionChange = { dx, dy -> nextDir = InputHandler.handleSwipe(state.direction, dx, dy) }
                    )

                    if (state.isGameOver) {
                        ResultDialog(state, { 
                            if (state.score > state.highScore) sp.edit().putInt("hs", state.score).apply()
                            state = SnakeState(highScore = sp.getInt("hs", 0), isStarted = true, gridWidth = state.gridWidth, gridHeight = state.gridHeight)
                            nextDir = Direction.UP 
                        }, { showSettings = true })
                    }

                    if (showSettings) {
                        SettingsDialog(settings, { showSettings = false; state = state.copy(isPaused = false) }, { settings = it; saveSettings(sp, it) })
                    }
                }
            }
        }
    }

    private fun isRunningOnTV(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) return true
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_LAUNCHER)) return true
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        InputHandler.handleKeyEvent(state.direction, keyCode)?.let { nextDir = it; return true }
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (state.isStarted && !state.isGameOver) {
                state = state.copy(isPaused = true); showSettings = true; return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun loadSettings(sp: android.content.SharedPreferences, isTV: Boolean): GameSettings {
        val jsonStr = sp.getString("settings", null)
        if (jsonStr == null) return GameSettings(dynamicGrid = isTV) // 默认值逻辑：TV开启动态棋盘
        
        val json = JSONObject(jsonStr)
        val itemMap = ItemType.entries.associateWith { json.optJSONObject("enabledItems")?.optBoolean(it.name, true) ?: true }
        return GameSettings(
            showGrid = json.optBoolean("showGrid", true),
            isLoopMode = json.optBoolean("isLoopMode", false),
            dynamicGrid = json.optBoolean("dynamicGrid", isTV),
            enableVibration = json.optBoolean("enableVibration", true),
            enabledItems = itemMap
        )
    }

    private fun saveSettings(sp: android.content.SharedPreferences, s: GameSettings) {
        val json = JSONObject().apply {
            put("showGrid", s.showGrid); put("isLoopMode", s.isLoopMode); put("dynamicGrid", s.dynamicGrid)
            put("enableVibration", s.enableVibration)
            put("enabledItems", JSONObject().apply { s.enabledItems.forEach { (k, v) -> put(k.name, v) } })
        }
        sp.edit().putString("settings", json.toString()).apply()
    }
}
