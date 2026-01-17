package io.tl.snake.logic

import kotlin.random.Random

object GameConfig {
    const val VERSION = "1.4.0"
    const val BASE_SPEED = 150L
    const val MIN_SPEED = 60L
}

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class ItemType { FOOD, SHIELD, SLOW, GHOST }
data class SpecialItem(val pos: Pair<Int, Int>, val type: ItemType)

data class SnakeState(
    val snake: List<Pair<Int, Int>> = listOf(5 to 10, 5 to 11, 5 to 12),
    val food: Pair<Int, Int> = 5 to 5,
    val specialItem: SpecialItem? = null,
    val direction: Direction = Direction.UP,
    val isGameOver: Boolean = false,
    val isPaused: Boolean = false,
    val isStarted: Boolean = false,
    val score: Int = 0,
    val highScore: Int = 0,
    val foodEaten: Int = 0,
    val itemsCollected: Map<ItemType, Int> = emptyMap(),
    val hasShield: Boolean = false,
    val isGhostMode: Boolean = false, // 穿透道具
    val speedModifier: Long = 0,      // 减速道具效果
    val isInvincible: Boolean = false,
    val invincibleTimeLeft: Long = 0,
    val gridWidth: Int = 20,
    val gridHeight: Int = 20
)

data class GameSettings(
    val showGrid: Boolean = true,
    val isDeveloperMode: Boolean = false,
    val isLoopMode: Boolean = false,
    val dynamicGrid: Boolean = true,
    val enableVibration: Boolean = true
)

fun gameTick(state: SnakeState, settings: GameSettings): SnakeState {
    if (state.isGameOver || state.isPaused || !state.isStarted) return state
    
    val head = state.snake.first()
    var nX = when (state.direction) { Direction.LEFT -> head.first - 1; Direction.RIGHT -> head.first + 1; else -> head.first }
    var nY = when (state.direction) { Direction.UP -> head.second - 1; Direction.DOWN -> head.second + 1; else -> head.second }

    // 边界处理
    if (settings.isLoopMode) {
        nX = (nX + state.gridWidth) % state.gridWidth
        nY = (nY + state.gridHeight) % state.gridHeight
    } else if (nX !in 0 until state.gridWidth || nY !in 0 until state.gridHeight) {
        return if (state.hasShield) triggerShield(state) else state.copy(isGameOver = true)
    }

    val nH = nX to nY
    // 碰撞身体检测：如果是鬼魂模式则不触发死亡
    if (state.snake.contains(nH) && !state.isInvincible && !state.isGhostMode) {
        return if (state.hasShield) triggerShield(state) else state.copy(isGameOver = true)
    }

    val nS = state.snake.toMutableList().apply { add(0, nH) }
    var sc = state.score
    var fd = state.food
    var itm = state.specialItem
    var sh = state.hasShield
    var fe = state.foodEaten
    val ic = state.itemsCollected.toMutableMap()
    var gm = state.isGhostMode
    var sm = state.speedModifier

    // 吃到食物
    if (nH == state.food) {
        sc += 10
        fe += 1
        fd = Random.nextInt(state.gridWidth) to Random.nextInt(state.gridHeight)
        // 随机生成道具
        if (Random.nextFloat() < 0.2f && itm == null) {
            val type = ItemType.values().filter { it != ItemType.FOOD }.random()
            itm = SpecialItem(Random.nextInt(state.gridWidth) to Random.nextInt(state.gridHeight), type)
        }
    } else { nS.removeAt(nS.size - 1) }

    // 拾取道具
    if (nH == itm?.pos) {
        ic[itm!!.type] = (ic[itm!!.type] ?: 0) + 1
        when(itm!!.type) {
            ItemType.SHIELD -> sh = true
            ItemType.SLOW -> sm += 30 // 减速
            ItemType.GHOST -> gm = true // 开启穿透
            else -> {}
        }
        itm = null
        sc += 50
    }

    return state.copy(
        snake = nS, food = fd, score = sc, specialItem = itm, hasShield = sh, 
        foodEaten = fe, itemsCollected = ic, isGhostMode = gm, speedModifier = sm,
        isStarted = true, highScore = if (sc > state.highScore) sc else state.highScore
    )
}

private fun triggerShield(s: SnakeState) = s.copy(
    snake = listOf(5 to 10, 5 to 11, 5 to 12), 
    direction = Direction.UP, hasShield = false, 
    isInvincible = true, invincibleTimeLeft = 3000L
)
