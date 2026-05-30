package io.tl.snake.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HighScoreDao {
    @Query("SELECT * FROM high_scores WHERE id = 1")
    suspend fun get(): HighScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HighScoreEntity)
}
