package io.tl.snake.network

import java.util.UUID

data class PlayerIdentity(
    val playerId: String = UUID.randomUUID().toString(),
    val playerName: String = ""
)
