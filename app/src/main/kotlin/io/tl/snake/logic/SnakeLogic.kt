package io.tl.snake.logic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.abs
import kotlin.random.Random

object GameConfig {
    const val BASE_SPEED = 150L
    const val MIN_SPEED = 50L
    const val VERSION = "2.0.0"
}

enum class Direction { UP, DOWN, LEFT, RIGHT }

enum class ItemType(val color: Color, val score: Int, val weight: Float, val label: String, val icon: ImageVector, val canPermanent: Boolean = true) {
    FOOD_BASIC(Color(0xFF4CAF50), 10, 0.7f, "Basic", Icons.Default.Fastfood),
    FOOD_GOLD(Color(0xFFFFD700), 30, 0.15f, "Gold", Icons.Default.Star),
    FOOD_POISON(Color(0xFF9C27B0), -20, 0.05f, "Poison", Icons.Default.Dangerous),
    SHIELD(Color(0xFF2196F3), 50, 0.03f, "Shield", Icons.Default.Shield),
    SLOW(Color(0xFFFF9800), 50, 0.04f, "Slow", Icons.Default.AvTimer, false),
    GHOST(Color(0xFFE91E63), 50, 0.03f, "Ghost", Icons.Default.Deblur),
    PORTAL(Color(0xFF00BCD4), 40, 0.02f, "Portal", Icons.Default.Cyclone), // 随机传送
    BOMB(Color(0xFFF44336), -10, 0.02f, "Bomb", Icons.Default.Eco) // 缩短身体
}

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
    val totalCollected: Map<ItemType, Int> = emptyMap(), // 累计拾取
    val gridWidth: Int = 20,
    val gridHeight: Int = 20,
    val speedModifier: Long = 0,
    val ghostTicks: Int = 0,
    val hasShield: Boolean = false,
    val permanentEffects: Set<ItemType> = emptySet()
)

data class GameSettings(
    val showGrid: Boolean = true,
    val isLoopMode: Boolean = false,
    val dynamicGrid: Boolean = true,
    val enableVibration: Boolean = true,
    val maxObjects: Int = 5,
    val enabledItems: Map<ItemType, Boolean> = ItemType.entries.associateWith { true },
    val thresholds: Map<ItemType, Int> = ItemType.entries.filter { it.canPermanent }.associateWith { 10 }
)

data class GameObject(val pos: Pair<Int, Int>, val type: ItemType)

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
        return if (state.hasShield || state.permanentEffects.contains(ItemType.SHIELD)) 
            state.copy(hasShield = false) else state.copy(isGameOver = true)
    }

    val nH = nX to nY
    val isGhost = state.ghostTicks > 0 || state.permanentEffects.contains(ItemType.GHOST)
    if (state.snake.contains(nH) && !isGhost) {
        return if (state.hasShield || state.permanentEffects.contains(ItemType.SHIELD)) 
            state.copy(hasShield = false) else state.copy(isGameOver = true)
    }

    var nS = state.snake.toMutableList().apply { add(0, nH) }
    var sc = state.score
    val tc = state.totalCollected.toMutableMap()
    val ic = state.itemsCollected.toMutableMap()
    var sm = state.speedModifier
    var gs = (state.ghostTicks - 1).coerceAtLeast(0)
    var activeShield = state.hasShield
    var perm = state.permanentEffects.toMutableSet()

    val hitObject = state.objects.find { it.pos == nH }
    val remainingObjects = state.objects.toMutableList()

    if (hitObject != null) {
        remainingObjects.remove(hitObject)
        sc += hitObject.type.score
        ic[hitObject.type] = (ic[hitObject.type] ?: 0) + 1
        tc[hitObject.type] = (tc[hitObject.type] ?: 0) + 1
        
        // 检查永久化
        if (hitObject.type.canPermanent && (tc[hitObject.type] ?: 0) >= (settings.thresholds[hitObject.type] ?: 999)) {
            perm.add(hitObject.type)
        }

        when(hitObject.type) {
            ItemType.SLOW -> sm += 15
            ItemType.GHOST -> gs = 20
            ItemType.SHIELD -> activeShield = true
            ItemType.BOMB -> repeat(3) { if(nS.size > 2) nS.removeAt(nS.size - 1) }
            ItemType.PORTAL -> {
                val emptyPos = (0 until state.gridWidth).flatMap { x -> (0 until state.gridHeight).map { y -> x to y } }
                    .filter { !nS.contains(it) }.randomOrNull() ?: nH
                nS[0] = emptyPos
            }
            else -> {}
        }
        if (hitObject.type.score <= 0 && hitObject.type != ItemType.BOMB) nS.removeAt(nS.size - 1)
    } else {
        nS.removeAt(nS.size - 1)
    }

    // 动态生成物体逻辑
    if (remainingObjects.size < settings.maxObjects && Random.nextFloat() < 0.3f) {
        val totalCells = state.gridWidth * state.gridHeight
        val isCongested = nS.size >= totalCells / 3 && !isGhost
        
        val candidates = ItemType.entries.filter { settings.enabledItems[it] == true }
        val weightedList = candidates.flatMap { type ->
            var w = (type.weight * 100).toInt()
            if (isCongested) {
                if (type.score > 0) w /= 3 // 降低正面食物权重
                if (type.score < 0) w *= 4 // 增加负面/功能性道具权重
            }
            List(w) { type }
        }
        
        val newPos = (0 until state.gridWidth).flatMap { x -> (0 until state.gridHeight).map { y -> x to y } }
            .filter { pos -> !nS.contains(pos) && remainingObjects.none { it.pos == pos } }
            .randomOrNull()
        
        if (newPos != null && weightedList.isNotEmpty()) {
            remainingObjects.add(GameObject(newPos, weightedList.random()))
        }
    }

    return state.copy(snake = nS, objects = remainingObjects, score = sc.coerceAtLeast(0), 
        itemsCollected = ic, totalCollected = tc, speedModifier = sm, ghostTicks = gs, 
        hasShield = activeShield, permanentEffects = perm)
}