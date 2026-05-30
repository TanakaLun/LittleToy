package io.tl.snake.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayerIdentityDao {
    @Query("SELECT * FROM player_identity WHERE id = 1")
    suspend fun get(): PlayerIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayerIdentityEntity)
}
