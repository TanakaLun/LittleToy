package io.tl.snake.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_identity")
data class PlayerIdentityEntity(
    @PrimaryKey val id: Int = 1,
    val playerId: String = "",
    val playerName: String = ""
)
