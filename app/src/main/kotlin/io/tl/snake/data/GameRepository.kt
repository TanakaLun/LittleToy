package io.tl.snake.data

import android.content.Context
import io.tl.snake.logic.ControlMode
import io.tl.snake.logic.GameSettings
import io.tl.snake.logic.ItemType
import io.tl.snake.network.PlayerIdentity
import org.json.JSONObject
import java.util.UUID

class GameRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val highScoreDao = db.highScoreDao()
    private val settingsDao = db.settingsDao()
    private val playerIdentityDao = db.playerIdentityDao()

    suspend fun loadHighScore(): Int {
        migrateFromSharedPrefsIfNeeded()
        return highScoreDao.get()?.score ?: 0
    }

    suspend fun saveHighScore(score: Int) {
        highScoreDao.upsert(HighScoreEntity(score = score))
    }

    suspend fun loadSettings(): GameSettings? {
        migrateFromSharedPrefsIfNeeded()
        val entity = settingsDao.get() ?: return null
        return parseSettingsJson(entity.json)
    }

    suspend fun saveSettings(settings: GameSettings) {
        val json = encodeSettingsToJson(settings)
        settingsDao.upsert(SettingsEntity(json = json))
    }

    private fun parseSettingsJson(jsonStr: String): GameSettings {
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
            controlMode = ControlMode.valueOf(json.optString("controlMode", "SWIPE")),
            isTVMode = json.optBoolean("isTVMode", false),
            enabledItems = itemMap
        )
    }

    private fun encodeSettingsToJson(settings: GameSettings): String {
        return JSONObject().apply {
            put("showGrid", settings.showGrid)
            put("isLoopMode", settings.isLoopMode)
            put("dynamicGrid", settings.dynamicGrid)
            put("enableVibration", settings.enableVibration)
            put("maxObjects", settings.maxObjects)
            put("enableItemDecay", settings.enableItemDecay)
            put("targetCellSize", settings.targetCellSize.toDouble())
            put("isGhostPermanent", settings.isGhostPermanent)
            put("controlMode", settings.controlMode.name)
            put("isTVMode", settings.isTVMode)
            put("enabledItems", JSONObject().apply {
                settings.enabledItems.forEach { (k, v) -> put(k.name, v) }
            })
        }.toString()
    }

    private suspend fun migrateFromSharedPrefsIfNeeded() {
        val hsExists = highScoreDao.get() != null
        if (hsExists) return

        val sp = context.getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
        if (!sp.contains("hs") && !sp.contains("settings")) return

        val hs = sp.getInt("hs", 0)
        if (hs > 0) highScoreDao.upsert(HighScoreEntity(score = hs))

        val settingsStr = sp.getString("settings", null)
        if (settingsStr != null) {
            settingsDao.upsert(SettingsEntity(json = settingsStr))
        }

        sp.edit().clear().apply()
    }

    suspend fun loadPlayerIdentity(): PlayerIdentity {
        migratePlayerIdentityFromSharedPrefsIfNeeded()
        val entity = playerIdentityDao.get()
        if (entity != null) {
            return PlayerIdentity(playerId = entity.playerId, playerName = entity.playerName)
        }
        val newIdentity = PlayerIdentity()
        playerIdentityDao.upsert(PlayerIdentityEntity(playerId = newIdentity.playerId, playerName = newIdentity.playerName))
        return newIdentity
    }

    suspend fun savePlayerIdentity(identity: PlayerIdentity) {
        playerIdentityDao.upsert(PlayerIdentityEntity(playerId = identity.playerId, playerName = identity.playerName))
    }

    private suspend fun migratePlayerIdentityFromSharedPrefsIfNeeded() {
        if (playerIdentityDao.get() != null) return
        val sp = context.getSharedPreferences("snake_identity", Context.MODE_PRIVATE)
        val id = sp.getString("player_id", null)
        val name = sp.getString("player_name", "Player") ?: "Player"
        if (id != null) {
            playerIdentityDao.upsert(PlayerIdentityEntity(playerId = id, playerName = name))
            sp.edit().clear().apply()
        }
    }
}
