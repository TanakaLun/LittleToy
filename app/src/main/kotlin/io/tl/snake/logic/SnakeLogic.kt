package io.tl.snake.logic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.random.Random

object GameConfig {
    const val BASE_SPEED = 150L
    const val MIN_SPEED = 50L
    const val ITEM_DECAY_MS = 15000L
}

enum class Direction { UP, DOWN, LEFT, RIGHT }

// 补全所有道具特性：钻石、辣椒、咖啡等
enum class ItemType(val color: Color, val score: Int, val weight: Float, val label: String, val icon: ImageVector) {
    FOOD_BASIC(Color(0xFF4CAF50), 10, 0.6f, "Basic", Icons.Default.Fastfood),
    FOOD_GOLD(Color(0xFFFFD700), 30, 0.12f, "Gold", Icons.Default.Star),
    FOOD_POISON(Color(0xFF9C27B0), -20, 0.05f, "Poison", Icons.Default.Dangerous),
    DIAMOND(Color(0xFF00E5FF), 150, 0.02f, "Diamond", Icons.Default.Diamond),
    CHILI(Color(0xFFFF5252), 40, 0.04f, "Chili", Icons.Default.Whatshot), // 辣椒：加速
    COFFEE(Color(0xFF795548), 20, 0.04f, "Coffee", Icons.Default.Coffee), // 咖啡：减速/恢复
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
    val enabledItems: Map<ItemType, Boolean> = ItemType.entries.associateWith { true }
)

data class SnakeState(
    val snake: List<Pair<Int, Int>> = listOf(5 to 5, 5 to 6, 5 to 7),
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
    val invincibleTime: Long = 0L
)

fun gameTick(state: SnakeState, settings: GameSettings): SnakeState {
    if (state.isGameOver || state.isPaused || !state.isStarted) return state
    
    val head = state.snake.first()
    var nX = when (state.direction) { 
        Direction.LEFT -> head.first - 1; Direction.RIGHT -> head.first + 1; else -> head.first 
    }
    var nY = when (state.direction) { 
        Direction.UP -> head.second - 1; Direction.DOWN -> head.second + 1; else -> head.second 
    }

    // 逻辑判定
    if (settings.isLoopMode || state.invincibleTime > 0) {
        nX = (nX + state.gridWidth) % state.gridWidth
        nY = (nY + state.gridHeight) % state.gridHeight
    } else if (nX !in 0 until state.gridWidth || nY !in 0 until state.gridHeight) {
        return if (state.shieldCount > 0) state.copy(shieldCount = state.shieldCount - 1, invincibleTime = 3000L)
        else state.copy(isGameOver = true)
    }

    val nH = nX to nY
    if (state.snake.contains(nH) && state.ghostTimeRemaining <= 0 && state.invincibleTime <= 0) {
        return if (state.shieldCount > 0) state.copy(shieldCount = state.shieldCount - 1, invincibleTime = 3000L)
        else state.copy(isGameOver = true)
    }

    val nS = state.snake.toMutableList().apply { add(0, nH) }
    var sc = state.score
    val ic = state.itemsCollected.toMutableMap()
    var sCount = state.shieldCount
    var sm = state.speedModifier
    var gTime = (state.ghostTimeRemaining - 100).coerceAtLeast(0L)
    val iTime = (state.invincibleTime - 100).coerceAtLeast(0L)

    val hitObject = state.objects.find { it.pos == nH }
    val remainingObjects = state.objects.filter { it.pos != nH }.toMutableList()
    
    if (hitObject != null) {
        sc += hitObject.type.score
        ic[hitObject.type] = (ic[hitObject.type] ?: 0) + 1
        when (hitObject.type) {
            ItemType.SHIELD -> sCount++
            ItemType.GHOST -> gTime = 10000L
            ItemType.CHILI -> sm -= 30 // 加速
            ItemType.COFFEE -> sm += 20 // 减速
            ItemType.SLOW -> sm += 40
            else -> {}
        }
        if (hitObject.type.score <= 0) nS.removeAt(nS.size - 1)
    } else {
        nS.removeAt(nS.size - 1)
    }

    // 生成逻辑
    val allowedTypes = ItemType.entries.filter { settings.enabledItems[it] == true }
    if (remainingObjects.size < settings.maxObjects && Random.nextFloat() < 0.15f && allowedTypes.isNotEmpty()) {
        val newPos = Random.nextInt(state.gridWidth) to Random.nextInt(state.gridHeight)
        if (!nS.contains(newPos)) {
            val totalWeight = allowedTypes.sumOf { it.weight.toDouble() }
            val r = Random.nextDouble() * totalWeight
            var acc = 0.0
            val selectedType = allowedTypes.first { acc += it.weight; r <= acc }
            remainingObjects.add(GameObject(newPos, selectedType))
        }
    }

    return state.copy(snake = nS, objects = remainingObjects, score = sc.coerceAtLeast(0), itemsCollected = ic, shieldCount = sCount, speedModifier = sm, ghostTimeRemaining = gTime, invincibleTime = iTime)
}
