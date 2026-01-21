package io.tl.snake.logic

import kotlin.random.Random

object GameConfig {
    const val BASE_SPEED = 150L
    const val MIN_SPEED = 50L
    const val GHOST_DURATION_MS = 15000L
    const val INVINCIBLE_DURATION_MS = 15000L
    const val ITEM_DECAY_MS = 15000L
}

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class ControlMode { SWIPE, BUTTONS, TV_REMOTE } // 增加电视遥控模式

enum class ItemType(val colorHex: Long, val score: Int, val weight: Float, val label: String) {
    FOOD_BASIC(0xFF4CAF50, 10, 0.6f, "Basic"),
    FOOD_GOLD(0xFFFFD700, 30, 0.12f, "Gold"),
    FOOD_POISON(0xFF9C27B0, -20, 0.05f, "Poison"),
    DIAMOND(0xFF00E5FF, 150, 0.015f, "Diamond"),
    COFFEE(0xFF8D6E63, 20, 0.04f, "Coffee"),
    CHILI(0xFFFF5252, 100, 0.03f, "Chili"),
    SHIELD(0xFF2196F3, 50, 0.03f, "Shield"),
    CLOVER(0xFF8BC34A, 40, 0.02f, "Clover"),
    SLOW(0xFFFF9800, 50, 0.035f, "Slow"),
    GHOST(0xFFE91E63, 50, 0.03f, "Ghost")
}

data class GameObject(val pos: Pair<Int, Int>, val type: ItemType, val timeLeft: Long = GameConfig.ITEM_DECAY_MS)

data class GameSettings(
    val showGrid: Boolean = true,
    val isLoopMode: Boolean = false,
    val dynamicGrid: Boolean = true,
    val enableVibration: Boolean = true,
    val maxObjects: Int = 5,
    val isGhostPermanent: Boolean = false,
    val enableItemDecay: Boolean = true,
    val targetCellSize: Float = 22f,
    val controlMode: ControlMode = ControlMode.SWIPE,
    val isTVMode: Boolean = false, // 增加 TV 模式标志
    val enabledItems: Map<ItemType, Boolean> = ItemType.entries.associateWith { true }
)

data class SnakeState(
    val snake: List<Pair<Int, Int>> = listOf(5 to 10, 5 to 11, 5 to 12),
    val objects: List<GameObject> = emptyList(),
    val direction: Direction = Direction.UP,
    val isGameOver: Boolean = false,
    val isPaused: Boolean = false,
    val isStarted: Boolean = false,
    val score: Int = 0,
    val highScore: Int = 0,
    val itemsCollected: Map<ItemType, Int> = emptyMap(),
    val gridWidth: Int = 20,
    val gridHeight: Int = 20,
    val speedModifier: Long = 0,
    val shieldCount: Int = 0,
    val ghostTimeRemaining: Long = 0L,
    val invincibleTimeRemaining: Long = 0L,
    val lastEvent: GameEvent? = null
)

enum class GameEvent { EAT_GOOD, EAT_BAD, HIT_WALL, SHIELD_BREAK }

fun gameTick(state: SnakeState, settings: GameSettings): SnakeState {
    if (state.isGameOver || state.isPaused || !state.isStarted) return state
    
    val currentSpeed = (GameConfig.BASE_SPEED - (state.score / 100 * 5) + state.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
    val head = state.snake.first()
    var nX = when (state.direction) { 
        Direction.LEFT -> head.first - 1; Direction.RIGHT -> head.first + 1; else -> head.first 
    }
    var nY = when (state.direction) { 
        Direction.UP -> head.second - 1; Direction.DOWN -> head.second + 1; else -> head.second 
    }

    val isInvincible = state.invincibleTimeRemaining > 0
    val isGhostActive = settings.isGhostPermanent || state.ghostTimeRemaining > 0 || isInvincible
    val activeLoopMode = settings.isLoopMode || isInvincible

    if (activeLoopMode) {
        nX = (nX + state.gridWidth) % state.gridWidth
        nY = (nY + state.gridHeight) % state.gridHeight
    } else if (nX !in 0 until state.gridWidth || nY !in 0 until state.gridHeight) {
        return if (state.shieldCount > 0) state.copy(shieldCount = state.shieldCount - 1, invincibleTimeRemaining = GameConfig.INVINCIBLE_DURATION_MS, lastEvent = GameEvent.SHIELD_BREAK)
        else state.copy(isGameOver = true, lastEvent = GameEvent.HIT_WALL)
    }

    val nH = nX to nY
    if (state.snake.contains(nH) && !isGhostActive) {
        return if (state.shieldCount > 0) state.copy(shieldCount = state.shieldCount - 1, invincibleTimeRemaining = GameConfig.INVINCIBLE_DURATION_MS, lastEvent = GameEvent.SHIELD_BREAK)
        else state.copy(isGameOver = true, lastEvent = GameEvent.HIT_WALL)
    }

    val nS = state.snake.toMutableList().apply { add(0, nH) }
    var sc = state.score
    val ic = state.itemsCollected.toMutableMap()
    var sm = state.speedModifier
    var sCount = state.shieldCount
    var gTime = if (settings.isGhostPermanent) 0L else (state.ghostTimeRemaining - currentSpeed).coerceAtLeast(0L)
    val iTime = (state.invincibleTimeRemaining - currentSpeed).coerceAtLeast(0L)

    val hitObject = state.objects.find { it.pos == nH }
    val remainingObjects = state.objects.asSequence()
        .map { it.copy(timeLeft = it.timeLeft - currentSpeed) }
        .filter { !settings.enableItemDecay || it.timeLeft > 0 }
        .filter { it.pos != nH }
        .toMutableList()
    
    if (hitObject != null) {
        sc += hitObject.type.score
        ic[hitObject.type] = (ic[hitObject.type] ?: 0) + 1
        val event = if (hitObject.type.score >= 0) GameEvent.EAT_GOOD else GameEvent.EAT_BAD
        when (hitObject.type) {
            ItemType.SLOW -> sm += 20
            ItemType.COFFEE -> sm += 40
            ItemType.CHILI -> sm -= 30
            ItemType.SHIELD -> sCount++
            ItemType.CLOVER -> sCount += Random.nextInt(1, 4)
            ItemType.GHOST -> if (!settings.isGhostPermanent) gTime = GameConfig.GHOST_DURATION_MS
            else -> {}
        }
        if (hitObject.type.score <= 0) nS.removeAt(nS.size - 1)
        return state.copy(snake = nS, objects = remainingObjects, score = sc.coerceAtLeast(0), itemsCollected = ic, speedModifier = sm, shieldCount = sCount, ghostTimeRemaining = gTime, invincibleTimeRemaining = iTime, lastEvent = event)
    } else {
        nS.removeAt(nS.size - 1)
    }

    val allowedTypes = ItemType.entries.filter { settings.enabledItems[it] == true }
    if (remainingObjects.size < settings.maxObjects && Random.nextFloat() < 0.12f && allowedTypes.isNotEmpty()) {
        val newPos = Random.nextInt(state.gridWidth) to Random.nextInt(state.gridHeight)
        if (!nS.contains(newPos) && remainingObjects.none { it.pos == newPos }) {
            val totalWeight = allowedTypes.sumOf { it.weight.toDouble() }
            val r = Random.nextDouble() * totalWeight
            var acc = 0.0
            val selectedType = allowedTypes.first { acc += it.weight; r <= acc }
            remainingObjects.add(GameObject(newPos, selectedType))
        }
    }
    return state.copy(snake = nS, objects = remainingObjects, score = sc.coerceAtLeast(0), itemsCollected = ic, speedModifier = sm, shieldCount = sCount, ghostTimeRemaining = gTime, invincibleTimeRemaining = iTime, lastEvent = null)
}
