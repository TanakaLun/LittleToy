package io.tl.snake.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.tl.snake.data.GameRepository
import io.tl.snake.logic.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)

    var state by mutableStateOf(SnakeState())
        private set
    var settings by mutableStateOf(GameSettings())
        private set
    var countdown by mutableStateOf(0)
        private set

    init {
        viewModelScope.launch { loadData() }
        startGameLoop()
    }

    private suspend fun loadData() {
        val hs = repository.loadHighScore()
        repository.loadSettings()?.let { settings = it }
        state = state.copy(highScore = hs)
    }

    private suspend fun saveData() {
        repository.saveHighScore(state.highScore)
        repository.saveSettings(settings)
    }

    private fun startGameLoop() {
        viewModelScope.launch {
            while (true) {
                if (state.isStarted && !state.isPaused && !state.isGameOver && countdown == 0) {
                    val speed = (GameConfig.speedForDifficulty(state.score, settings.difficulty) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
                    delay(speed)
                    state = gameTick(state, settings)
                    if (state.isGameOver) checkHighScore()
                } else delay(100)
            }
        }
    }

    fun startResumeCountdown() {
        if (state.isStarted && !state.isGameOver) {
            viewModelScope.launch {
                countdown = 3
                while (countdown > 0) {
                    delay(1000)
                    countdown--
                }
                state = state.copy(isPaused = false)
            }
        } else {
            state = state.copy(isPaused = false)
        }
    }

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

    fun setDirection(dir: Direction) {
        val isValid = when (dir) {
            Direction.UP -> state.direction != Direction.DOWN
            Direction.DOWN -> state.direction != Direction.UP
            Direction.LEFT -> state.direction != Direction.RIGHT
            Direction.RIGHT -> state.direction != Direction.LEFT
        }
        if (isValid) state = state.copy(direction = dir)
    }

    fun togglePause() {
        if (!state.isPaused) state = state.copy(isPaused = true)
        else startResumeCountdown()
    }

    fun setPaused(paused: Boolean) { state = state.copy(isPaused = paused) }
    fun startGame() { state = state.copy(isStarted = true) }
    fun restartGame() {
        state = SnakeState(highScore = state.highScore, isStarted = true, gridWidth = state.gridWidth, gridHeight = state.gridHeight)
    }
    fun updateGridSize(w: Int, h: Int) { if (state.gridWidth != w || state.gridHeight != h) state = state.copy(gridWidth = w, gridHeight = h) }

    fun updateSettings(newSettings: GameSettings) {
        settings = newSettings
        viewModelScope.launch { saveData() }
    }

    fun onAppBackground() {
        if (state.isStarted && !state.isPaused && !state.isGameOver) {
            state = state.copy(isPaused = true)
        }
    }

    fun endGame() {
        state = state.copy(isGameOver = true)
    }

    fun resetHighScore() {
        state = state.copy(highScore = 0)
        viewModelScope.launch { saveData() }
    }

    private fun checkHighScore() {
        if (state.score > state.highScore) {
            state = state.copy(highScore = state.score)
            viewModelScope.launch { saveData() }
        }
    }
}
