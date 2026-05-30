package io.tl.snake.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HighScoreEntity::class, SettingsEntity::class, PlayerIdentityEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun highScoreDao(): HighScoreDao
    abstract fun settingsDao(): SettingsDao
    abstract fun playerIdentityDao(): PlayerIdentityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `player_identity` (`id` INTEGER NOT NULL, `playerId` TEXT NOT NULL, `playerName` TEXT NOT NULL, PRIMARY KEY(`id`))")
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "snake_database"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
