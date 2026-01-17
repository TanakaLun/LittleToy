package io.tl.snake

import kotlin.random.Random

enum class Direction { UP, DOWN, LEFT, RIGHT }

data class SnakeState(
    val snake: List<Pair<Int, Int>> = listOf(10 to 10, 10 to 11, 10 to 12),
    val food: Pair<Int, Int> = 5 to 5,
    val direction: Direction = Direction.UP,
    val isGameOver: Boolean = false,
    val isPaused: Boolean = false, // 新增：暂停状态
    val score: Int = 0
)

object GameConfig {
    const val GRID_SIZE = 20
}

fun handleDirectionChange(current: SnakeState, x: Float, y: Float): SnakeState {
    val newDirection = when {
        kotlin.math.abs(x) > kotlin.math.abs(y) -> {
            if (x > 0 && current.direction != Direction.LEFT) Direction.RIGHT 
            else if (x < 0 && current.direction != Direction.RIGHT) Direction.LEFT 
            else current.direction
        }
        else -> {
            if (y > 0 && current.direction != Direction.UP) Direction.DOWN 
            else if (y < 0 && current.direction != Direction.DOWN) Direction.UP 
            else current.direction
        }
    }
    return current.copy(direction = newDirection)
}

fun moveSnake(state: SnakeState): SnakeState {
    if (state.isGameOver || state.isPaused) return state

    val head = state.snake.first()
    val newHead = when (state.direction) {
        Direction.UP -> head.first to (head.second - 1)
        Direction.DOWN -> head.first to (head.second + 1)
        Direction.LEFT -> head.first - 1 to head.second
        Direction.RIGHT -> head.first + 1 to head.second
    }

    if (newHead.first !in 0 until GameConfig.GRID_SIZE ||
        newHead.second !in 0 until GameConfig.GRID_SIZE ||
        state.snake.contains(newHead)
    ) {
        return state.copy(isGameOver = true)
    }

    val newSnake = mutableListOf(newHead) + state.snake
    return if (newHead == state.food) {
        val newFood = Pair(Random.nextInt(GameConfig.GRID_SIZE), Random.nextInt(GameConfig.GRID_SIZE))
        state.copy(snake = newSnake, food = newFood, score = state.score + 10)
    } else {
        state.copy(snake = newSnake.dropLast(1))
    }
}
