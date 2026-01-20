package io.tl.snake

import android.content.Context
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        settings = loadSettings(sp)
        state = SnakeState(highScore = sp.getInt("hs", 0))

        enableEdgeToEdge()
        setContent {
            MyTheme {
                LaunchedEffect(state.isStarted, state.isPaused, state.isGameOver) {
                    while (state.isStarted && !state.isPaused && !state.isGameOver) {
                        val speed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
                        delay(speed)
                        val newState = gameTick(state, settings, nextDir)
                        if (settings.enableVibration && newState.lastEvent != state.lastEvent) {
                            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                            else vibrator.vibrate(50)
                        }
                        state = newState
                    }
                }

                Surface {
                    MainGameContent(state, settings, { state = it }, { dx, dy -> nextDir = InputHandler.handleSwipe(state.direction, dx, dy) })
                    if (state.isGameOver) {
                        ResultDialog(state, { 
                            if (state.score > state.highScore) sp.edit().putInt("hs", state.score).apply()
                            state = SnakeState(highScore = sp.getInt("hs", 0), isStarted = true)
                            nextDir = Direction.UP 
                        }, { showSettings = true })
                    }
                    if (showSettings) SettingsDialog(settings, { showSettings = false }, { settings = it; saveSettings(sp, it) })
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        InputHandler.handleKeyEvent(state.direction, keyCode)?.let { nextDir = it; return true }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (state.isStarted && !state.isGameOver) {
                state = state.copy(isPaused = true); showSettings = true; return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) { showSettings = true; state = state.copy(isPaused = true); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun loadSettings(sp: android.content.SharedPreferences): GameSettings {
        val jsonStr = sp.getString("settings", null) ?: return GameSettings()
        val json = JSONObject(jsonStr)
        val itemMap = ItemType.entries.associateWith { json.optJSONObject("enabledItems")?.optBoolean(it.name, true) ?: true }
        return GameSettings(
            showGrid = json.optBoolean("showGrid", true),
            isLoopMode = json.optBoolean("isLoopMode", false),
            enableVibration = json.optBoolean("enableVibration", true),
            enabledItems = itemMap
        )
    }

    private fun saveSettings(sp: android.content.SharedPreferences, s: GameSettings) {
        val json = JSONObject().apply {
            put("showGrid", s.showGrid); put("isLoopMode", s.isLoopMode); put("enableVibration", s.enableVibration)
            put("enabledItems", JSONObject().apply { s.enabledItems.forEach { (k, v) -> put(k.name, v) } })
        }
        sp.edit().putString("settings", json.toString()).apply()
    }
}
