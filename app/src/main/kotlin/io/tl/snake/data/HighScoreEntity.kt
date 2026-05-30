package io.tl.snake.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "high_scores")
data class HighScoreEntity(
    @PrimaryKey val id: Int = 1,
    val score: Int = 0
)
