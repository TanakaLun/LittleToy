package io.tl.snake.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.tl.snake.data.*
import io.tl.snake.logic.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = GameDatabase.getInstance(application).gameDao()
    
    var state by mutableStateOf(SnakeState())
        private set

    var settings by mutableStateOf(GameSettings())
        private set

    init {
        viewModelScope.launch {
            val data = dao.getGameData()
            if (data != null) {
                settings = SettingsConverter.fromJson(data.settingsJson)
                state = state.copy(highScore = data.highScore)
            }
        }
        startGameLoop()
    }

    private fun startGameLoop() {
        viewModelScope.launch {
            while (true) {
                if (state.isStarted && !state.isPaused && !state.isGameOver) {
                    val speed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier)
                        .coerceAtLeast(GameConfig.MIN_SPEED)
                    delay(speed)
                    state = gameTick(state, settings)
                    
                    if (state.isGameOver) {
                        checkAndSaveHighScore()
                    }
                } else {
                    delay(100)
                }
            }
        }
    }

    fun updateDirection(dx: Float, dy: Float) {
        val newDir = when {
            kotlin.math.abs(dx) > kotlin.math.abs(dy) -> 
                if (dx > 0 && state.direction != Direction.LEFT) Direction.RIGHT 
                else if (dx < 0 && state.direction != Direction.RIGHT) Direction.LEFT 
                else state.direction
            else -> 
                if (dy > 0 && state.direction != Direction.UP) Direction.DOWN 
                else if (dy < 0 && state.direction != Direction.DOWN) Direction.UP 
                else state.direction
        }
        state = state.copy(direction = newDir)
    }

    fun togglePause() { state = state.copy(isPaused = !state.isPaused) }
    
    fun setPaused(paused: Boolean) { state = state.copy(isPaused = paused) }

    fun startGame() { state = state.copy(isStarted = true) }

    fun restartGame() {
        state = SnakeState(
            highScore = state.highScore,
            isStarted = true,
            gridWidth = state.gridWidth,
            gridHeight = state.gridHeight
        )
    }

    fun updateGridSize(w: Int, h: Int) {
        if (state.gridWidth != w || state.gridHeight != h) {
            state = state.copy(gridWidth = w, gridHeight = h)
        }
    }

    fun updateSettings(newSettings: GameSettings) {
        settings = newSettings
        saveData()
    }

    fun resetHighScore() {
        state = state.copy(highScore = 0)
        saveData()
    }

    private fun checkAndSaveHighScore() {
        if (state.score > state.highScore) {
            state = state.copy(highScore = state.score)
            saveData()
        }
    }

    private fun saveData() {
        viewModelScope.launch {
            dao.saveGameData(GameDataEntity(
                highScore = state.highScore,
                settingsJson = SettingsConverter.toJson(settings)
            ))
        }
    }
}
