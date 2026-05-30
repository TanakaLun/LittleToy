package io.tl.snake.network

import io.tl.snake.logic.Direction
import io.tl.snake.logic.ItemType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val networkJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class PlayerInfo(
    val id: String,
    val name: String,
    val address: String = "",
    val isHost: Boolean = false
)

@Serializable
data class SerializedSnakeSegment(
    val x: Int,
    val y: Int
)

@Serializable
data class SerializedGameObject(
    val x: Int,
    val y: Int,
    val typeName: String,
    val timeLeft: Long
)

@Serializable
data class SerializedPlayerState(
    val id: String,
    val name: String,
    val snake: List<SerializedSnakeSegment>,
    val direction: String,
    val score: Int,
    val isAlive: Boolean,
    val shieldCount: Int,
    val ghostTimeRemaining: Long,
    val invincibleTimeRemaining: Long,
    val colorIndex: Int
)

@Serializable
data class SerializedGameState(
    val players: List<SerializedPlayerState>,
    val objects: List<SerializedGameObject>,
    val gridWidth: Int,
    val gridHeight: Int,
    val isGameOver: Boolean,
    val winnerId: String = ""
)

sealed interface ServerMessage {
    @Serializable
    data class Welcome(val playerId: String, val colorIndex: Int) : ServerMessage

    @Serializable
    data class PlayerJoined(val player: PlayerInfo, val colorIndex: Int) : ServerMessage

    @Serializable
    data class PlayerLeft(val playerId: String) : ServerMessage

    @Serializable
    data class GameState(val state: SerializedGameState) : ServerMessage

    @Serializable
    data class GameOver(val winnerId: String, val winnerName: String) : ServerMessage

    @Serializable
    data class Error(val message: String) : ServerMessage

    @Serializable
    data object GameStarted : ServerMessage

    @Serializable
    data class LobbyUpdate(val players: List<PlayerInfo>) : ServerMessage
}

sealed interface ClientMessage {
    @Serializable
    data class Join(val name: String) : ClientMessage

    @Serializable
    data class Direction(val dir: String) : ClientMessage

    @Serializable
    data object StartGame : ClientMessage

    @Serializable
    data object Leave : ClientMessage
}

@Serializable
data class WireMessage(
    val type: String,
    val json: String
)

fun serializeServerMessage(msg: ServerMessage): String {
    return when (msg) {
        is ServerMessage.Welcome -> WireMessage("Welcome", networkJson.encodeToString(msg))
        is ServerMessage.PlayerJoined -> WireMessage("PlayerJoined", networkJson.encodeToString(msg))
        is ServerMessage.PlayerLeft -> WireMessage("PlayerLeft", networkJson.encodeToString(msg))
        is ServerMessage.GameState -> WireMessage("GameState", networkJson.encodeToString(msg))
        is ServerMessage.GameOver -> WireMessage("GameOver", networkJson.encodeToString(msg))
        is ServerMessage.Error -> WireMessage("Error", networkJson.encodeToString(msg))
        is ServerMessage.GameStarted -> WireMessage("GameStarted", networkJson.encodeToString(msg))
        is ServerMessage.LobbyUpdate -> WireMessage("LobbyUpdate", networkJson.encodeToString(msg))
    }.let { networkJson.encodeToString(it) }
}

fun serializeClientMessage(msg: ClientMessage): String {
    return when (msg) {
        is ClientMessage.Join -> WireMessage("Join", networkJson.encodeToString(msg))
        is ClientMessage.Direction -> WireMessage("Direction", networkJson.encodeToString(msg))
        is ClientMessage.StartGame -> WireMessage("StartGame", networkJson.encodeToString(msg))
        is ClientMessage.Leave -> WireMessage("Leave", networkJson.encodeToString(msg))
    }.let { networkJson.encodeToString(it) }
}

inline fun <reified T> parseMessage(json: String): T {
    val wire = networkJson.decodeFromString<WireMessage>(json)
    return networkJson.decodeFromString<T>(wire.json)
}
