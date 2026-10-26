package org.love2d.android.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.love2d.android.room.mod.ModDatabase
import org.love2d.android.room.mod.ModInfo
import org.love2d.android.room.mod.ModInfoDao

/**
 * ClassName ModDbUtil
 * Description
 * Create by hjr
 * Date 2025/6/25 15:33
 */
object ModDbUtil {
    private lateinit var db: ModDatabase

    fun init(context: Context) {
        if (!::db.isInitialized) {
            db = ModDatabase.getInstance(context)
        }
    }

    fun getModInfoDao(): ModInfoDao {
        checkInitialized()
        return db.modInfoDao()
    }

    suspend fun getAllMods(): Flow<List<ModInfo>> {
        checkInitialized()
        return withContext(Dispatchers.IO) {
            db.modInfoDao().getAll()
        }
    }

    suspend fun pathMods(installPath: String): Flow<List<ModInfo>> {
        checkInitialized()
        return withContext(Dispatchers.IO) {
            db.modInfoDao().getAllByInstallPathFlow(installPath)
        }
    }

    suspend fun insertMod(mod: ModInfo) {
        checkInitialized()
        withContext(Dispatchers.IO) {
            db.modInfoDao().insert(mod)
        }
    }

    suspend fun deleteMod(mod: ModInfo) {
        checkInitialized()
        withContext(Dispatchers.IO) {
            db.modInfoDao().delete(mod)
        }
    }

    suspend fun updateMod(mod: ModInfo) {
        checkInitialized()
        withContext(Dispatchers.IO) {
            db.modInfoDao().update(mod)
        }
    }

    suspend fun deletePathMods(installPath: String) {
        checkInitialized()
        withContext(Dispatchers.IO) {
            db.modInfoDao().deleteByInstallPath(installPath)
        }
    }

    // 新增：按游戏ID管理模组的方法
    suspend fun getModsByGameId(gameId: String): Flow<List<ModInfo>> {
        checkInitialized()
        return withContext(Dispatchers.IO) {
            db.modInfoDao().getAllByGameIdFlow(gameId)
        }
    }

    suspend fun getModsByGameIdSync(gameId: String): List<ModInfo> {
        checkInitialized()
        return withContext(Dispatchers.IO) {
            db.modInfoDao().getAllByGameId(gameId)
        }
    }

    suspend fun insertModForGame(gameId: String, mod: ModInfo) {
        checkInitialized()
        withContext(Dispatchers.IO) {
            // 确保模组与游戏关联
            mod.game_id = gameId
            db.modInfoDao().insert(mod)
        }
    }

    suspend fun updateModForGame(gameId: String, mod: ModInfo) {
        checkInitialized()
        withContext(Dispatchers.IO) {
            // 确保模组与游戏关联
            mod.game_id = gameId
            db.modInfoDao().update(mod)
        }
    }

    suspend fun deleteModsForGame(gameId: String) {
        checkInitialized()
        withContext(Dispatchers.IO) {
            db.modInfoDao().deleteByGameId(gameId)
        }
    }

    suspend fun deleteModInGame(gameId: String, modId: String) {
        checkInitialized()
        withContext(Dispatchers.IO) {
            db.modInfoDao().deleteByGameIdAndModId(gameId, modId)
        }
    }

    suspend fun getModInGame(gameId: String, modId: String): ModInfo? {
        checkInitialized()
        return withContext(Dispatchers.IO) {
            db.modInfoDao().getModInGame(gameId, modId)
        }
    }

    suspend fun isModInstalledInGame(gameId: String, modId: String): Boolean {
        checkInitialized()
        return withContext(Dispatchers.IO) {
            db.modInfoDao().existsByGameIdAndModId(gameId, modId)
        }
    }

    suspend fun migrateModToGame(mod: ModInfo, gameId: String) {
        checkInitialized()
        withContext(Dispatchers.IO) {
            // 迁移现有模组到指定游戏
            mod.game_id = gameId
            db.modInfoDao().update(mod)
        }
    }

    // 获取游戏模组数量统计（用于调试）
    suspend fun getModCountByGame(): List<ModInfoDao.GameModCount> {
        checkInitialized()
        return withContext(Dispatchers.IO) {
            db.modInfoDao().getModCountByGame()
        }
    }

    private fun checkInitialized() {
        if (!ModDbUtil::db.isInitialized) {
            throw IllegalStateException("GameDbUtil is not initialized. Call GameDbUtil.init(context) first.")
        }
    }
}