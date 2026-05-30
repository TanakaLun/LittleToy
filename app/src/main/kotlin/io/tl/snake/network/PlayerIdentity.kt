package io.tl.snake.network

import android.content.Context
import java.util.UUID

data class PlayerIdentity(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Player"
) {
    companion object {
        private const val PREFS_NAME = "snake_identity"
        private const val KEY_ID = "player_id"
        private const val KEY_NAME = "player_name"

        fun load(context: Context): PlayerIdentity {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = sp.getString(KEY_ID, null)
            val name = sp.getString(KEY_NAME, "Player") ?: "Player"
            return if (id != null) PlayerIdentity(id, name)
            else PlayerIdentity().also { it.save(context) }
        }

        fun save(context: Context, identity: PlayerIdentity) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_ID, identity.id)
                .putString(KEY_NAME, identity.name)
                .apply()
        }
    }

    fun save(context: Context) = save(context, this)
}
