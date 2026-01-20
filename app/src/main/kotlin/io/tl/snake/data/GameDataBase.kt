package io.tl.snake.data

import android.content.Context
import androidx.room.*
import io.tl.snake.logic.GameSettings
import io.tl.snake.logic.ItemType
import org.json.JSONObject

@Entity(tableName = "game_data")
data class GameDataEntity(
    @PrimaryKey val id: Int = 1,
    val highScore: Int = 0,
    val settingsJson: String
)

@Dao
interface GameDao {
    @Query("SELECT * FROM game_data WHERE id = 1")
    suspend fun getGameData(): GameDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGameData(data: GameDataEntity)
}

@Database(entities = [GameDataEntity::class], version = 1)
abstract class GameDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile private var instance: GameDatabase? = null
        fun getInstance(context: Context): GameDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, GameDatabase::class.java, "snake.db").build().also { instance = it }
            }
    }
}

// 转换器：将 GameSettings 序列化为数据库字符串
object SettingsConverter {
    fun toJson(s: GameSettings): String = JSONObject().apply {
        put("showGrid", s.showGrid)
        put("isLoopMode", s.isLoopMode)
        put("dynamicGrid", s.dynamicGrid)
        put("enableVibration", s.enableVibration)
        put("maxObjects", s.maxObjects)
        put("enableItemDecay", s.enableItemDecay)
        put("targetCellSize", s.targetCellSize.toDouble())
        put("isGhostPermanent", s.isGhostPermanent)
        put("enabledItems", JSONObject().apply { s.enabledItems.forEach { (k, v) -> put(k.name, v) } })
    }.toString()

    fun fromJson(jsonStr: String): GameSettings {
        val json = JSONObject(jsonStr)
        val itemMap = ItemType.entries.associateWith { type -> 
            json.optJSONObject("enabledItems")?.optBoolean(type.name, true) ?: true 
        }
        return GameSettings(
            showGrid = json.optBoolean("showGrid", true),
            isLoopMode = json.optBoolean("isLoopMode", false),
            dynamicGrid = json.optBoolean("dynamicGrid", true),
            enableVibration = json.optBoolean("enableVibration", true),
            maxObjects = json.optInt("maxObjects", 5),
            enableItemDecay = json.optBoolean("enableItemDecay", true),
            targetCellSize = json.optDouble("targetCellSize", 22.0).toFloat(),
            isGhostPermanent = json.optBoolean("isGhostPermanent", false),
            enabledItems = itemMap
        )
    }
}
