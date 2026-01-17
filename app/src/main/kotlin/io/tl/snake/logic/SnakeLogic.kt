package io.tl.snake.logic

import kotlin.random.Random

// 核心配置
object GameConfig {
    const val GRID_SIZE = 20
}

// 数据模型
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
    val score: Int = 0,
    val hasShield: Boolean = false,
    val isInvincible: Boolean = false,
    val invincibleTimeLeft: Long = 0
)

data class GameSettings(
    val showGrid: Boolean = true
)

// 纯逻辑函数：计算每一帧的状态变化
fun gameTick(state: SnakeState): SnakeState {
    if (state.isGameOver || state.isPaused) return state

    val head = state.snake.first()
    val newHead = when (state.direction) {
        Direction.UP -> head.first to (head.second - 1)
        Direction.DOWN -> head.first to (head.second + 1)
        Direction.LEFT -> head.first - 1 to head.second
        Direction.RIGHT -> head.first + 1 to head.second
    }

    // 碰撞检测
    val hitWall = newHead.first !in 0 until GameConfig.GRID_SIZE || 
                  newHead.second !in 0 until GameConfig.GRID_SIZE
    val hitSelf = state.snake.contains(newHead) && !state.isInvincible

    if (hitWall || hitSelf) {
        return if (state.hasShield) {
            // 触发免死：回到中心，开启5秒无敌
            state.copy(
                snake = listOf(10 to 10, 10 to 11, 10 to 12),
                direction = Direction.UP,
                hasShield = false,
                isInvincible = true,
                invincibleTimeLeft = 5000L
            )
        } else {
            state.copy(isGameOver = true)
        }
    }

    val newSnake = mutableListOf(newHead) + state.snake
    var currentScore = state.score
    var currentHasShield = state.hasShield
    var currentSpecialItem = state.specialItem
    var currentFood = state.food

    // 逻辑：吃到普通食物
    val finalSnake = if (newHead == state.food) {
        currentScore += 10
        currentFood = Random.nextInt(GameConfig.GRID_SIZE) to Random.nextInt(GameConfig.GRID_SIZE)
        // 15% 几率生成免死金牌
        if (Random.nextFloat() < 0.15f && currentSpecialItem == null) {
            currentSpecialItem = SpecialItem(
                Random.nextInt(GameConfig.GRID_SIZE) to Random.nextInt(GameConfig.GRID_SIZE),
                ItemType.SHIELD
            )
        }
        newSnake
    } else {
        newSnake.dropLast(1)
    }

    // 逻辑：吃到奖励物品
    if (newHead == currentSpecialItem?.pos) {
        if (currentSpecialItem?.type == ItemType.SHIELD) currentHasShield = true
        currentSpecialItem = null
        currentScore += 50
    }

    return state.copy(
        snake = finalSnake,
        food = currentFood,
        score = currentScore,
        specialItem = currentSpecialItem,
        hasShield = currentHasShield
    )
}
