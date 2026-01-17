package io.tl.snake.logic

import kotlin.random.Random

object GameConfig {
    const val GRID_SIZE = 20
    const val VERSION = "1.3.0"
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
    val isStarted: Boolean = false, // 是否已经点击“开始”
    val score: Int = 0,
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
    var nextX = when (state.direction) {
        Direction.LEFT -> head.first - 1
        Direction.RIGHT -> head.first + 1
        else -> head.first
    }
    var nextY = when (state.direction) {
        Direction.UP -> head.second - 1
        Direction.DOWN -> head.second + 1
        else -> head.second
    }

    // 碰撞检测与轮回逻辑
    val isOutOfBounds = nextX !in 0 until GameConfig.GRID_SIZE || nextY !in 0 until GameConfig.GRID_SIZE
    
    if (isOutOfBounds) {
        if (settings.isLoopMode) {
            // 轮回模式：坐标取模实现穿墙
            nextX = (nextX + GameConfig.GRID_SIZE) % GameConfig.GRID_SIZE
            nextY = (nextY + GameConfig.GRID_SIZE) % GameConfig.GRID_SIZE
        } else if (state.hasShield) {
            return triggerShield(state)
        } else {
            return state.copy(isGameOver = true)
        }
    }

    val newHead = nextX to nextY
    // 自身碰撞检测
    if (state.snake.contains(newHead) && !state.isInvincible) {
        return if (state.hasShield) triggerShield(state) else state.copy(isGameOver = true)
    }

    // 移动逻辑：使用 MutableList 确保 removeAt 可用
    val newSnake = state.snake.toMutableList()
    newSnake.add(0, newHead)

    var currentScore = state.score
    var currentFood = state.food
    var currentItem = state.specialItem
    var currentShield = state.hasShield

    if (newHead == state.food) {
        currentScore += 10
        currentFood = Random.nextInt(GameConfig.GRID_SIZE) to Random.nextInt(GameConfig.GRID_SIZE)
        // 10% 几率产生盾牌
        if (Random.nextFloat() < 0.1f && currentItem == null) {
            currentItem = SpecialItem(Random.nextInt(GameConfig.GRID_SIZE) to Random.nextInt(GameConfig.GRID_SIZE), ItemType.SHIELD)
        }
    } else {
        newSnake.removeAt(newSnake.size - 1)
    }

    if (newHead == currentItem?.pos) {
        currentShield = true
        currentItem = null
        currentScore += 50
    }

    return state.copy(
        snake = newSnake, food = currentFood, score = currentScore,
        specialItem = currentItem, hasShield = currentShield
    )
}

private fun triggerShield(state: SnakeState) = state.copy(
    snake = listOf(10 to 10, 10 to 11, 10 to 12),
    direction = Direction.UP,
    hasShield = false,
    isInvincible = true,
    invincibleTimeLeft = 5000L
)
