package io.tl.snake.logic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.random.Random

object GameConfig {
    const val BASE_SPEED = 150L
    const val MIN_SPEED = 60L
    const val VERSION = "1.9.5"
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
    // 道具状态
    val ghostTicks: Int = 0,
    val hasShield: Boolean = false
)

data class GameSettings(
    val showGrid: Boolean = true,
    val isLoopMode: Boolean = false,
    val dynamicGrid: Boolean = true,
    val enableVibration: Boolean = true,
    val maxObjects: Int = 5,
    val enabledItems: Map<ItemType, Boolean> = ItemType.entries.associateWith { true }
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

    // 1. 处理边界检查
    var isCollision = false
    if (settings.isLoopMode) {
        nX = (nX + state.gridWidth) % state.gridWidth
        nY = (nY + state.gridHeight) % state.gridHeight
    } else if (nX !in 0 until state.gridWidth || nY !in 0 until state.gridHeight) {
        isCollision = true
    }

    val nH = nX to nY
    
    // 2. 处理身体碰撞 (Ghost 状态下忽略身体碰撞)
    if (state.snake.contains(nH) && state.ghostTicks <= 0) {
        isCollision = true
    }

    // 3. 护盾逻辑拦截死亡
    if (isCollision) {
        return if (state.hasShield) {
            // 消耗护盾，免死一回合（此时蛇不动，给玩家反应时间改变方向）
            state.copy(hasShield = false) 
        } else {
            state.copy(isGameOver = true)
        }
    }

    val nS = state.snake.toMutableList().apply { add(0, nH) }
    var sc = state.score
    val ic = state.itemsCollected.toMutableMap()
    var sm = state.speedModifier
    var gs = (state.ghostTicks - 1).coerceAtLeast(0)
    var activeShield = state.hasShield

    // 4. 处理道具碰撞
    val hitObject = state.objects.find { it.pos == nH }
    val remainingObjects = state.objects.toMutableList()
    
    if (hitObject != null) {
        remainingObjects.remove(hitObject)
        sc += hitObject.type.score
        ic[hitObject.type] = (ic[hitObject.type] ?: 0) + 1
        
        when(hitObject.type) {
            ItemType.SLOW -> sm += 15
            ItemType.GHOST -> gs = 20 // 持续20步
            ItemType.SHIELD -> activeShield = true
            else -> {}
        }
        
        if (hitObject.type.score <= 0) nS.removeAt(nS.size - 1)
    } else {
        nS.removeAt(nS.size - 1)
    }

    return state.copy(
        snake = nS, 
        objects = remainingObjects, 
        score = sc.coerceAtLeast(0), 
        itemsCollected = ic, 
        speedModifier = sm,
        ghostTicks = gs,
        hasShield = activeShield
    )
}
