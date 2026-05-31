package io.tl.snake.network

import io.tl.snake.logic.Direction
import io.tl.snake.logic.GameConfig
import io.tl.snake.logic.GameObject
import io.tl.snake.logic.GameSettings
import io.tl.snake.logic.ItemType
import kotlin.random.Random

data class MultiplayerPlayer(
    val id: String,
    val name: String,
    val snake: List<Pair<Int, Int>>,
    val direction: Direction = Direction.UP,
    val score: Int = 0,
    val isAlive: Boolean = true,
    val shieldCount: Int = 0,
    val ghostTimeRemaining: Long = 0L,
    val invincibleTimeRemaining: Long = 0L,
    val disconnected: Boolean = false
)

data class MultiplayerGameState(
    val players: List<MultiplayerPlayer> = emptyList(),
    val objects: List<GameObject> = emptyList(),
    val gridWidth: Int = 100,
    val gridHeight: Int = 100,
    val isGameOver: Boolean = false,
    val winnerId: String = ""
) {
    fun updateDirection(playerId: String, dirStr: String): MultiplayerGameState {
        val dir = try { Direction.valueOf(dirStr) } catch (_: Exception) { return this }
        return copy(players = players.map { p ->
            if (p.id == playerId && p.isAlive) {
                val isValid = when (dir) {
                    Direction.UP -> p.direction != Direction.DOWN
                    Direction.DOWN -> p.direction != Direction.UP
                    Direction.LEFT -> p.direction != Direction.RIGHT
                    Direction.RIGHT -> p.direction != Direction.LEFT
                }
                if (isValid) p.copy(direction = dir) else p
            } else p
        })
    }

    companion object {
        fun create(players: List<Pair<String, String>>): MultiplayerGameState {
            val n = players.size
            val gridW = 100 * n
            val gridH = 100 * n

            val spawnCenters = players.mapIndexed { i, (id, name) ->
                val col = (i % n) * 100 + 50
                val row = (i / n) * 100 + 50
                id to (col to row)
            }

            val playerStates = players.mapIndexed { i, (id, name) ->
                val center = spawnCenters.find { it.first == id }?.second ?: (50 to 50)
                val dir = when (i % 4) { 0 -> Direction.DOWN; 1 -> Direction.UP; 2 -> Direction.RIGHT; else -> Direction.LEFT }
                val (dx, dy) = when (dir) {
                    Direction.UP -> 0 to 1; Direction.DOWN -> 0 to -1
                    Direction.LEFT -> 1 to 0; Direction.RIGHT -> -1 to 0
                }
                val startSnake = listOf(
                    center.first to center.second,
                    center.first + dx to center.second + dy,
                    center.first + dx * 2 to center.second + dy * 2
                )
                MultiplayerPlayer(id = id, name = name, snake = startSnake, direction = dir)
            }

            return MultiplayerGameState(
                players = playerStates,
                gridWidth = gridW,
                gridHeight = gridH
            )
        }
    }
}

fun multiplayerGameTick(state: MultiplayerGameState, settings: GameSettings? = null): MultiplayerGameState {
    if (state.isGameOver) return state

    val currentSpeed = GameConfig.BASE_SPEED
    val maxObjs = GameConfig.dynamicMaxObjects(state.gridWidth, state.gridHeight)

    val updatedPlayers = state.players.map { player ->
        if (!player.isAlive || player.disconnected) return@map player

        val head = player.snake.first()
        var nX = when (player.direction) {
            Direction.LEFT -> head.first - 1
            Direction.RIGHT -> head.first + 1
            else -> head.first
        }
        var nY = when (player.direction) {
            Direction.UP -> head.second - 1
            Direction.DOWN -> head.second + 1
            else -> head.second
        }

        nX = (nX + state.gridWidth) % state.gridWidth
        nY = (nY + state.gridHeight) % state.gridHeight

        val newHead = nX to nY

        var newSnake = player.snake.toMutableList().apply { add(0, newHead) }
        var newScore = player.score
        var newShieldCount = player.shieldCount
        var newGhostTime = (player.ghostTimeRemaining - currentSpeed).coerceAtLeast(0L)
        var newInvincibleTime = (player.invincibleTimeRemaining - currentSpeed).coerceAtLeast(0L)

        val headCollisionPlayers = state.players.filter { other ->
            other.id != player.id && other.isAlive && other.snake.first() == newHead
        }

        if (headCollisionPlayers.isNotEmpty()) {
            newScore = ((newScore + 1) / 2).coerceAtLeast(0)
            return@map player.copy(
                snake = emptyList(),
                score = newScore,
                isAlive = false,
                shieldCount = newShieldCount,
                ghostTimeRemaining = newGhostTime,
                invincibleTimeRemaining = newInvincibleTime
            )
        }

        val hitBody = state.players.filter { other ->
            other.id != player.id && other.isAlive &&
                other.snake.drop(1).any { it == newHead }
        }

        if (hitBody.isNotEmpty()) {
            newScore = ((newScore + 1) / 2).coerceAtLeast(0)
            return@map player.copy(
                snake = emptyList(),
                score = newScore,
                isAlive = false,
                shieldCount = newShieldCount,
                ghostTimeRemaining = newGhostTime,
                invincibleTimeRemaining = newInvincibleTime
            )
        }

        newSnake.removeAt(newSnake.size - 1)
        return@map player.copy(
            snake = newSnake,
            score = newScore,
            isAlive = true,
            shieldCount = newShieldCount,
            ghostTimeRemaining = newGhostTime,
            invincibleTimeRemaining = newInvincibleTime
        )
    }.toMutableList()

    var updatedObjects = state.objects.toMutableList()
    updatedObjects = updatedObjects.map { it.copy(timeLeft = it.timeLeft - currentSpeed) }
        .filter { it.timeLeft > 0 }
        .toMutableList()

    for (i in updatedPlayers.indices) {
        val player = updatedPlayers[i]
        if (!player.isAlive || player.disconnected) continue

        val head = player.snake.first()
        val hitObj = updatedObjects.find { it.pos == head }
        if (hitObj != null) {
            updatedObjects.removeIf { it.pos == head }
            val newScore = (player.score + hitObj.type.score).coerceAtLeast(0)
            var newSnake = player.snake.toMutableList()
            if (hitObj.type.score <= 0) {
                newSnake.removeAt(newSnake.size - 1)
            }
            updatedPlayers[i] = player.copy(snake = newSnake, score = newScore)
        }
    }

    if (updatedObjects.size < maxObjs && Random.nextFloat() < 0.15f) {
        val newPos = Random.nextInt(state.gridWidth) to Random.nextInt(state.gridHeight)
        val allOccupied = updatedPlayers.filter { it.isAlive }.flatMap { it.snake }.toSet() +
            updatedObjects.map { it.pos }.toSet()
        if (newPos !in allOccupied) {
            val allowedTypes = ItemType.entries.filter { it.weight > 0.01f }
            if (allowedTypes.isNotEmpty()) {
                val totalWeight = allowedTypes.sumOf { it.weight.toDouble() }
                val r = Random.nextDouble() * totalWeight
                var acc = 0.0
                val selectedType = allowedTypes.first { acc += it.weight; r <= acc }
                updatedObjects.add(GameObject(newPos, selectedType))
            }
        }
    }

    val alivePlayers = updatedPlayers.filter { it.isAlive }
    val isGameOver = alivePlayers.size <= 1
    val winner = if (isGameOver) updatedPlayers.maxByOrNull { it.score }?.id ?: "" else ""

    return state.copy(
        players = updatedPlayers,
        objects = updatedObjects,
        isGameOver = isGameOver,
        winnerId = winner
    )
}
