package io.tl.snake.logic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.random.Random

object GameConfig { const val BASE_SPEED = 150L; const val MIN_SPEED = 60L; const val VERSION = "2.9.7" }
enum class Direction { UP, DOWN, LEFT, RIGHT }

enum class ItemType(val color: Color, val score: Int, val weight: Float, val label: String, val icon: ImageVector) {
    FOOD_BASIC(Color(0xFF4CAF50), 10, 0.7f, "Basic", Icons.Default.Fastfood),
    FOOD_GOLD(Color(0xFFFFD700), 30, 0.15f, "Gold", Icons.Default.Star),
    FOOD_POISON(Color(0xFF9C27B0), -20, 0.05f, "Poison", Icons.Default.Dangerous),
    SHIELD(Color(0xFF2196F3), 50, 0.03f, "Shield", Icons.Default.Shield),
    SLOW(Color(0xFFFF9800), 50, 0.04f, "Slow", Icons.Default.AvTimer),
    GHOST(Color(0xFFE91E63), 50, 0.03f, "Ghost", Icons.Default.Deblur),
    MAGNET(Color(0xFFCDDC39), 20, 0.02f, "Magnet", Icons.Default.VerticalAlignBottom) // 新增：磁铁（示例）
}

data class GameObject(val pos: Pair<Int, Int>, val type: ItemType)
data class SnakeState(
    val snake: List<Pair<Int, Int>> = listOf(5 to 10, 5 to 11, 5 to 12),
    val objects: List<GameObject> = emptyList(),
    val direction: Direction = Direction.UP,
    val isGameOver: Boolean = false, val isPaused: Boolean = false, val isStarted: Boolean = false,
    val score: Int = 0, val highScore: Int = 0,
    val itemsCollected: Map<ItemType, Int> = emptyMap(),
    val gridWidth: Int = 20, val gridHeight: Int = 20,
    val speedModifier: Long = 0,
    val ghostTicks: Int = 0, val hasShield: Boolean = false,
    val cumulativeCounts: Map<ItemType, Int> = emptyMap() // 追踪单局累积量
)

data class GameSettings(
    val showGrid: Boolean = true, val isLoopMode: Boolean = false, val dynamicGrid: Boolean = true,
    val enableVibration: Boolean = true, val maxObjects: Int = 5,
    val enabledItems: Map<ItemType, Boolean> = ItemType.entries.associateWith { true },
    val useGradient: Boolean = true, val usePulse: Boolean = true,
    // 平衡性：永久效果阈值（0表示关闭）
    val permThresholds: Map<ItemType, Int> = mapOf(ItemType.GHOST to 10, ItemType.SHIELD to 5)
)

fun gameTick(state: SnakeState, settings: GameSettings): SnakeState {
    if (state.isGameOver || state.isPaused || !state.isStarted) return state
    val head = state.snake.first()
    var nX = when (state.direction) { Direction.LEFT -> head.first-1; Direction.RIGHT -> head.first+1; else -> head.first }
    var nY = when (state.direction) { Direction.UP -> head.second-1; Direction.DOWN -> head.second+1; else -> head.second }
    
    if (settings.isLoopMode) { nX = (nX + state.gridWidth) % state.gridWidth; nY = (nY + state.gridHeight) % state.gridHeight }
    val isWallHit = nX !in 0 until state.gridWidth || nY !in 0 until state.gridHeight
    
    // 判定永久 Ghost 效果
    val isPermGhost = (state.cumulativeCounts[ItemType.GHOST] ?: 0) >= (settings.permThresholds[ItemType.GHOST] ?: Int.MAX_VALUE)
    val isBodyHit = state.snake.contains(nX to nY) && state.ghostTicks <= 0 && !isPermGhost
    
    if (isWallHit || isBodyHit) {
        val isPermShield = (state.cumulativeCounts[ItemType.SHIELD] ?: 0) >= (settings.permThresholds[ItemType.SHIELD] ?: Int.MAX_VALUE)
        return if (state.hasShield || isPermShield) state.copy(hasShield = false) else state.copy(isGameOver = true)
    }

    val nH = nX to nY
    val nS = state.snake.toMutableList().apply { add(0, nH) }
    var sc = state.score; var sm = state.speedModifier; var gs = (state.ghostTicks - 1).coerceAtLeast(0)
    var activeShield = state.hasShield
    val ic = state.itemsCollected.toMutableMap()
    val cc = state.cumulativeCounts.toMutableMap()

    val hitObj = state.objects.find { it.pos == nH }
    val remObjs = state.objects.toMutableList()
    if (hitObj != null) {
        remObjs.remove(hitObj)
        sc += hitObj.type.score
        ic[hitObj.type] = (ic[hitObj.type] ?: 0) + 1
        cc[hitObj.type] = (cc[hitObj.type] ?: 0) + 1
        when(hitObj.type) {
            ItemType.SLOW -> sm += 15
            ItemType.GHOST -> gs = 20
            ItemType.SHIELD -> activeShield = true
            else -> {}
        }
        if (hitObj.type.score <= 0) nS.removeAt(nS.size - 1)
    } else nS.removeAt(nS.size - 1)

    // 食物生成逻辑优化
    val occupancy = nS.size.toFloat() / (state.gridWidth * state.gridHeight)
    val allowed = ItemType.entries.filter { settings.enabledItems[it] == true }
    if (remObjs.size < settings.maxObjects && Random.nextFloat() < 0.3f && allowed.isNotEmpty()) {
        val newPos = Random.nextInt(state.gridWidth) to Random.nextInt(state.gridHeight)
        if (!nS.contains(newPos)) {
            // 动态权重调整：当占据1/3时，剧毒概率增加 5 倍，金苹果权重降为 0
            val selected = allowed.let { list ->
                val totalW = list.sumOf { type ->
                    var w = type.weight.toDouble()
                    if (occupancy >= 0.33f && !isPermGhost) {
                        if (type == ItemType.FOOD_POISON) w *= 5.0
                        if (type.score > 10) w *= 0.1
                    }
                    w
                }
                val r = Random.nextDouble() * totalW
                var acc = 0.0
                list.first { type ->
                    var w = type.weight.toDouble()
                    if (occupancy >= 0.33f && !isPermGhost) {
                        if (type == ItemType.FOOD_POISON) w *= 5.0
                        if (type.score > 10) w *= 0.1
                    }
                    acc += w; r <= acc
                }
            }
            remObjs.add(GameObject(newPos, selected))
        }
    }
    return state.copy(snake = nS, objects = remObjs, score = sc.coerceAtLeast(0), itemsCollected = ic, cumulativeCounts = cc, speedModifier = sm, ghostTicks = gs, hasShield = activeShield)
}
