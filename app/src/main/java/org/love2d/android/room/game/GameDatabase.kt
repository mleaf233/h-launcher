package org.love2d.android.room.game

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GameInfo::class],
    version = 5,
    exportSchema = false
)
abstract class GameDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 给 games 表新增 savePath 字段，默认值是空字符串
                database.execSQL("ALTER TABLE games ADD COLUMN savePath TEXT NOT NULL DEFAULT ''")
            }
        }

        // 从版本3到版本4的迁移：将Int类型的id字段转换为String类型
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 由于Room不支持直接修改主键类型，我们需要重新创建表
                // 1. 创建临时表
                database.execSQL("""
                    CREATE TABLE games_temp (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        modPath TEXT NOT NULL,
                        iconPath TEXT,
                        savePath TEXT NOT NULL DEFAULT '',
                        isEnableMod INTEGER NOT NULL DEFAULT 1,
                        createTime INTEGER NOT NULL DEFAULT 0,
                        lastPlayed INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 2. 复制数据，将Int类型的id转换为String
                database.execSQL("""
                    INSERT INTO games_temp (id, name, filePath, modPath, iconPath, savePath, isEnableMod, createTime, lastPlayed)
                    SELECT CAST(id AS TEXT), name, filePath, modPath, iconPath, savePath, isEnableMod, createTime, lastPlayed FROM games
                """.trimIndent())

                // 3. 删除原表
                database.execSQL("DROP TABLE games")

                // 4. 重命名临时表
                database.execSQL("ALTER TABLE games_temp RENAME TO games")
            }
        }

        // 从版本4到版本5的迁移：添加游戏时长统计字段
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加游戏时长统计字段
                database.execSQL("ALTER TABLE games ADD COLUMN totalPlayTime INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE games ADD COLUMN sessionStartTime INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getInstance(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "game_database"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
