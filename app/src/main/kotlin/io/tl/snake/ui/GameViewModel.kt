package io.tl.snake.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.tl.snake.logic.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val sp = application.getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
    
    var state by mutableStateOf(SnakeState())
        private set
    var settings by mutableStateOf(GameSettings())
        private set

    init {
        loadData()
        startGameLoop()
    }

    private fun loadData() {
        val hs = sp.getInt("hs", 0)
        val jsonStr = sp.getString("settings", null) ?: return
        try {
            val json = JSONObject(jsonStr)
            val itemMap = ItemType.entries.associateWith { type -> 
                json.optJSONObject("enabledItems")?.optBoolean(type.name, true) ?: true 
            }
            settings = GameSettings(
                showGrid = json.optBoolean("showGrid", true),
                isLoopMode = json.optBoolean("isLoopMode", false),
                dynamicGrid = json.optBoolean("dynamicGrid", true),
                enableVibration = json.optBoolean("enableVibration", true),
                maxObjects = json.optInt("maxObjects", 5),
                enableItemDecay = json.optBoolean("enableItemDecay", true),
                targetCellSize = json.optDouble("targetCellSize", 22.0).toFloat(),
                isGhostPermanent = json.optBoolean("isGhostPermanent", false),
                controlMode = ControlMode.valueOf(json.optString("controlMode", "SWIPE")),
                enabledItems = itemMap
            )
            state = state.copy(highScore = hs)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveData() {
        val json = JSONObject().apply {
            put("showGrid", settings.showGrid)
            put("isLoopMode", settings.isLoopMode)
            put("dynamicGrid", settings.dynamicGrid)
            put("enableVibration", settings.enableVibration)
            put("maxObjects", settings.maxObjects)
            put("enableItemDecay", settings.enableItemDecay)
            put("targetCellSize", settings.targetCellSize.toDouble())
            put("isGhostPermanent", settings.isGhostPermanent)
            put("controlMode", settings.controlMode.name)
            put("enabledItems", JSONObject().apply { 
                settings.enabledItems.forEach { (k, v) -> put(k.name, v) } 
            })
        }
        sp.edit().putString("settings", json.toString()).putInt("hs", state.highScore).apply()
    }

    private fun startGameLoop() {
        viewModelScope.launch {
            while (true) {
                if (state.isStarted && !state.isPaused && !state.isGameOver) {
                    val speed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
                    delay(speed)
                    state = gameTick(state, settings)
                    if (state.isGameOver) checkHighScore()
                } else delay(100)
            }
        }
    }

    // 处理滑动方向
    fun handleSwipe(dx: Float, dy: Float) {
        val newDir = when {
            kotlin.math.abs(dx) > kotlin.math.abs(dy) -> 
                if (dx > 0 && state.direction != Direction.LEFT) Direction.RIGHT 
                else if (dx < 0 && state.direction != Direction.RIGHT) Direction.LEFT else state.direction
            else -> 
                if (dy > 0 && state.direction != Direction.UP) Direction.DOWN 
                else if (dy < 0 && state.direction != Direction.DOWN) Direction.UP else state.direction
        }
        state = state.copy(direction = newDir)
    }

    // 直接设置方向（按钮使用）
    fun setDirection(dir: Direction) {
        val isValid = when (dir) {
            Direction.UP -> state.direction != Direction.DOWN
            Direction.DOWN -> state.direction != Direction.UP
            Direction.LEFT -> state.direction != Direction.RIGHT
            Direction.RIGHT -> state.direction != Direction.LEFT
        }
        if (isValid) state = state.copy(direction = dir)
    }

    fun togglePause() { state = state.copy(isPaused = !state.isPaused) }
    fun setPaused(paused: Boolean) { state = state.copy(isPaused = paused) }
    fun startGame() { state = state.copy(isStarted = true) }
    fun restartGame() {
        state = SnakeState(highScore = state.highScore, isStarted = true, gridWidth = state.gridWidth, gridHeight = state.gridHeight)
    }
    fun updateGridSize(w: Int, h: Int) { if (state.gridWidth != w || state.gridHeight != h) state = state.copy(gridWidth = w, gridHeight = h) }
    fun updateSettings(newSettings: GameSettings) { settings = newSettings; saveData() }
    fun resetHighScore() { state = state.copy(highScore = 0); saveData() }
    private fun checkHighScore() { if (state.score > state.highScore) { state = state.copy(highScore = state.score); saveData() } }
}
