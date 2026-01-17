package io.tl.snake.logic

import kotlin.random.Random

object GameConfig {
    const val GRID_SIZE = 20
    const val VERSION = "1.3.3"
}

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class ItemType { FOOD, SHIELD }
data class SpecialItem(val pos: Pair<Int, Int>, val type: ItemType)

data class SnakeState(
    val snake: List<Pair<Int, Int>> = listOf(10 to 10, 10 to 11, 10 to 12),
    val food: Pair<Int, Int> = 5 to 5,
    val specialItem: SpecialItem? = null,
    val direction: Direction = Direction.UP,
    val isGameOver: Boolean = false,
    val isPaused: Boolean = false,
    val isStarted: Boolean = false,
    val score: Int = 0,
    val highScore: Int = 0,
    val hasShield: Boolean = false,
    val isInvincible: Boolean = false,
    val invincibleTimeLeft: Long = 0
)

data class GameSettings(
    val showGrid: Boolean = true,
    val isDeveloperMode: Boolean = false,
    val isLoopMode: Boolean = false
)

fun gameTick(state: SnakeState, settings: GameSettings): SnakeState {
    if (state.isGameOver || state.isPaused || !state.isStarted) return state
    val head = state.snake.first()
    var nX = when (state.direction) { Direction.LEFT -> head.first - 1; Direction.RIGHT -> head.first + 1; else -> head.first }
    var nY = when (state.direction) { Direction.UP -> head.second - 1; Direction.DOWN -> head.second + 1; else -> head.second }

    if (settings.isLoopMode) {
        nX = (nX + GameConfig.GRID_SIZE) % GameConfig.GRID_SIZE
        nY = (nY + GameConfig.GRID_SIZE) % GameConfig.GRID_SIZE
    } else if (nX !in 0 until GameConfig.GRID_SIZE || nY !in 0 until GameConfig.GRID_SIZE) {
        return if (state.hasShield) triggerShield(state) else state.copy(isGameOver = true)
    }

    val nH = nX to nY
    if (state.snake.contains(nH) && !state.isInvincible) {
        return if (state.hasShield) triggerShield(state) else state.copy(isGameOver = true)
    }

    val nS = state.snake.toMutableList().apply { add(0, nH) }
    var sc = state.score
    var fd = state.food
    var itm = state.specialItem
    var sh = state.hasShield

    if (nH == state.food) {
        sc += 10
        fd = Random.nextInt(GameConfig.GRID_SIZE) to Random.nextInt(GameConfig.GRID_SIZE)
        if (Random.nextFloat() < 0.1f && itm == null) itm = SpecialItem(Random.nextInt(GameConfig.GRID_SIZE) to Random.nextInt(GameConfig.GRID_SIZE), ItemType.SHIELD)
    } else { nS.removeAt(nS.size - 1) }

    if (nH == itm?.pos) { sh = true; itm = null; sc += 50 }

    return state.copy(snake = nS, food = fd, score = sc, specialItem = itm, hasShield = sh, isStarted = true, 
        highScore = if (sc > state.highScore) sc else state.highScore)
}

private fun triggerShield(s: SnakeState) = s.copy(snake = listOf(10 to 10, 10 to 11, 10 to 12), direction = Direction.UP, hasShield = false, isInvincible = true, invincibleTimeLeft = 5000L)
