package io.tl.snake.logic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.random.Random

object GameConfig {
    const val BASE_SPEED = 150L
    const val MIN_SPEED = 60L
    const val GHOST_DURATION_MS = 15000L 
}

enum class Direction { UP, DOWN, LEFT, RIGHT }

enum class ItemType(val color: Color, val score: Int, val weight: Float, val label: String, val icon: ImageVector) {
    FOOD_BASIC(Color(0xFF4CAF50), 10, 0.7f, "Basic", Icons.Default.Fastfood),
    FOOD_GOLD(Color(0xFFFFD700), 30, 0.15f, "Gold", Icons.Default.Star),
    FOOD_POISON(Color(0xFF9C27B0), -20, 0.05f, "Poison", Icons.Default.Dangerous),
    SHIELD(Color(0xFF2196F3), 50, 0.03f, "Shield", Icons.Default.Shield),
    SLOW(Color(0xFFFF9800), 50, 0.04f, "Slow", Icons.Default.AvTimer),
    GHOST(Color(0xFFE91E63), 50, 0.03f, "Ghost", Icons.Default.Deblur)
}

data class GameObject(val pos: Pair<Int, Int>, val type: ItemType)

data class GameSettings(
    val showGrid: Boolean = true,
    val isLoopMode: Boolean = false,
    val dynamicGrid: Boolean = true,
    val enableVibration: Boolean = true,
    val maxObjects: Int = 5,
    val isGhostPermanent: Boolean = false,
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
    val ghostTimeRemaining: Long = 0L 
)

fun gameTick(state: SnakeState, settings: GameSettings, tickDuration: Long): SnakeState {
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

    val isGhostActive = settings.isGhostPermanent || state.ghostTimeRemaining > 0

    var hitWall = false
    if (settings.isLoopMode) {
        nX = (nX + state.gridWidth) % state.gridWidth
        nY = (nY + state.gridHeight) % state.gridHeight
    } else if (nX !in 0 until state.gridWidth || nY !in 0 until state.gridHeight) {
        hitWall = true
    }

    val nH = nX to nY
    val hitSelf = state.snake.contains(nH) && !isGhostActive

    if (hitWall || hitSelf) {
        return if (state.shieldCount > 0) {
            // 护盾消耗逻辑
            state.copy(shieldCount = state.shieldCount - 1)
        } else {
            state.copy(isGameOver = true)
        }
    }

    val nS = state.snake.toMutableList().apply { add(0, nH) }
    var sc = state.score
    val ic = state.itemsCollected.toMutableMap()
    var sm = state.speedModifier
    var sCount = state.shieldCount
    var gTime = if (settings.isGhostPermanent) 0L else (state.ghostTimeRemaining - tickDuration).coerceAtLeast(0L)

    val hitObject = state.objects.find { it.pos == nH }
    val remainingObjects = state.objects.toMutableList()
    
    if (hitObject != null) {
        remainingObjects.remove(hitObject)
        sc += hitObject.type.score
        ic[hitObject.type] = (ic[hitObject.type] ?: 0) + 1
        
        when (hitObject.type) {
            ItemType.SLOW -> sm += 15
            ItemType.SHIELD -> sCount++
            ItemType.GHOST -> if (!settings.isGhostPermanent) gTime = GameConfig.GHOST_DURATION_MS
            else -> {}
        }
        if (hitObject.type.score <= 0) nS.removeAt(nS.size - 1)
    } else {
        nS.removeAt(nS.size - 1)
    }

    val allowedTypes = ItemType.entries.filter { settings.enabledItems[it] == true }
    if (remainingObjects.size < settings.maxObjects && Random.nextFloat() < 0.15f && allowedTypes.isNotEmpty()) {
        val newPos = Random.nextInt(state.gridWidth) to Random.nextInt(state.gridHeight)
        if (!nS.contains(newPos) && remainingObjects.none { it.pos == newPos }) {
            val totalWeight = allowedTypes.sumOf { it.weight.toDouble() }
            val r = Random.nextDouble() * totalWeight
            var acc = 0.0
            val selectedType = allowedTypes.first { acc += it.weight; r <= acc }
            remainingObjects.add(GameObject(newPos, selectedType))
        }
    }

    return state.copy(
        snake = nS, objects = remainingObjects, score = sc.coerceAtLeast(0), 
        itemsCollected = ic, speedModifier = sm, shieldCount = sCount, ghostTimeRemaining = gTime
    )
}