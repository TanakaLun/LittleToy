package io.tl.snake.logic

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

object GameConfig {
    const val BASE_SPEED = 150L
    const val MIN_SPEED = 60L
    const val VERSION = "1.9.1"
}

enum class Direction { UP, DOWN, LEFT, RIGHT }

enum class ItemType(val color: Color, val score: Int, val weight: Float, val label: String) {
    FOOD_BASIC(Color(0xFF4CAF50), 10, 0.7f, "Basic Food"),
    FOOD_GOLD(Color(0xFFFFD700), 30, 0.15f, "Golden Food"),
    FOOD_POISON(Color(0xFF9C27B0), -20, 0.05f, "Poison"),
    SHIELD(Color(0xFF2196F3), 50, 0.03f, "Shield"),
    SLOW(Color(0xFFFF9800), 50, 0.04f, "Slow Down"),
    GHOST(Color(0xFFE91E63), 50, 0.03f, "Ghost Ball")
}

data class GameObject(val pos: Pair<Int, Int>, val type: ItemType)

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
    val hasShield: Boolean = false,
    val isGhostMode: Boolean = false,
    val speedModifier: Long = 0,
    val isInvincible: Boolean = false,
    val invincibleTimeLeft: Long = 0,
    val gridWidth: Int = 20,
    val gridHeight: Int = 20
)

data class GameSettings(
    val showGrid: Boolean = true,
    val isLoopMode: Boolean = false,
    val dynamicGrid: Boolean = true,
    val enableVibration: Boolean = true,
    val maxObjects: Int = 5
)

fun gameTick(state: SnakeState, settings: GameSettings): SnakeState {
    if (state.isGameOver || state.isPaused || !state.isStarted) return state
    
    val head = state.snake.first()
    var nX = when (state.direction) { 
        Direction.LEFT -> head.first - 1 
        Direction.RIGHT -> head.first + 1 
        else -> head.first 
    }
    var nY = when (state.direction) { 
        Direction.UP -> head.second - 1 
        Direction.DOWN -> head.second + 1 
        else -> head.second 
    }

    if (settings.isLoopMode) {
        nX = (nX + state.gridWidth) % state.gridWidth
        nY = (nY + state.gridHeight) % state.gridHeight
    } else if (nX !in 0 until state.gridWidth || nY !in 0 until state.gridHeight) {
        return if (state.hasShield) triggerShield(state) else state.copy(isGameOver = true)
    }

    val nH = nX to nY
    if (state.snake.contains(nH) && !state.isInvincible && !state.isGhostMode) {
        return if (state.hasShield) triggerShield(state) else state.copy(isGameOver = true)
    }

    val nS = state.snake.toMutableList().apply { add(0, nH) }
    var sc = state.score
    val ic = state.itemsCollected.toMutableMap()
    var gm = state.isGhostMode
    var sm = state.speedModifier
    var sh = state.hasShield

    val hitObject = state.objects.find { it.pos == nH }
    val remainingObjects = state.objects.toMutableList()
    
    if (hitObject != null) {
        remainingObjects.remove(hitObject)
        sc += hitObject.type.score
        ic[hitObject.type] = (ic[hitObject.type] ?: 0) + 1
        when(hitObject.type) {
            ItemType.SHIELD -> sh = true
            ItemType.SLOW -> sm += 20
            ItemType.GHOST -> gm = true
            else -> {}
        }
        if (hitObject.type.score <= 0) nS.removeAt(nS.size - 1)
    } else {
        nS.removeAt(nS.size - 1)
    }

    if (remainingObjects.size < settings.maxObjects && Random.nextFloat() < 0.2f) {
        val newPos = Random.nextInt(state.gridWidth) to Random.nextInt(state.gridHeight)
        if (!nS.contains(newPos) && remainingObjects.none { it.pos == newPos }) {
            val type = ItemType.entries.let { e ->
                val r = Random.nextFloat()
                var acc = 0f
                e.first { acc += it.weight; r <= acc }
            }
            remainingObjects.add(GameObject(newPos, type))
        }
    }

    return state.copy(
        snake = nS, objects = remainingObjects, score = sc.coerceAtLeast(0), 
        itemsCollected = ic, isGhostMode = gm, speedModifier = sm, hasShield = sh
    )
}

private fun triggerShield(s: SnakeState) = s.copy(
    snake = listOf(s.gridWidth/2 to s.gridHeight/2, s.gridWidth/2 to s.gridHeight/2 + 1),
    hasShield = false, isInvincible = true, invincibleTimeLeft = 3000L
)
