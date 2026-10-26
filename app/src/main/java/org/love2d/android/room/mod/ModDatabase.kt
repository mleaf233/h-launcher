package org.love2d.android.room.mod

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.love2d.android.DatabaseConfig

/**
 * ClassName ModDatabase
 * Description
 * Create by hjr
 * Date 2025/6/25 15:21
 */
@Database(
    entities = [ModInfo::class],
    version = DatabaseConfig.DATABASE_VERSION,
    exportSchema = false
)
abstract class ModDatabase : RoomDatabase() {

    abstract fun modInfoDao(): ModInfoDao

    companion object {
        @Volatile
        private var INSTANCE: ModDatabase? = null

        // 数据库迁移：从版本2到版本3，添加game_id字段
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加game_id字段，默认值为空字符串
                database.execSQL("ALTER TABLE mod_list ADD COLUMN game_id TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): ModDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ModDatabase::class.java,
                    "mod_database"
                )
                .addMigrations(MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
