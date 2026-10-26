package org.love2d.android.room.mod

/**
 * ClassName ModDao
 * Description
 * Create by hjr
 * Date 2025/6/25 15:19
 */
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModInfoDao {
    /** 根据 installPath 查询所有 mod（不唯一） */
    @Query("SELECT * FROM mod_list WHERE installPath = :installPath")
    fun getAllByInstallPathFlow(installPath: String): Flow<List<ModInfo>>

    @Query("SELECT * FROM mod_list WHERE installPath = :installPath")
    suspend fun getAllByInstallPath(installPath: String): List<ModInfo>

    /** 插入单个 mod，存在相同 room_id 会替换 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mod: ModInfo): Long

    @Query("SELECT * FROM mod_list WHERE installPath = :installPath AND id = :modId LIMIT 1")
    suspend fun getModInPath(installPath: String, modId: String): ModInfo?

    /** 批量插入多个 mod */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mods: List<ModInfo>)

    /** 更新已有的 mod（通过 room_id） */
    @Update
    suspend fun update(mod: ModInfo)

    /** 删除某个 mod */
    @Delete
    suspend fun delete(mod: ModInfo)

    /** 删除指定 installPath 的所有 mod */
    @Query("DELETE FROM mod_list WHERE installPath = :installPath")
    suspend fun deleteByInstallPath(installPath: String)

    /** 根据 room_id 删除 */
    @Query("DELETE FROM mod_list WHERE room_id = :roomId")
    suspend fun deleteByRoomId(roomId: Int)

    /** 删除所有 */
    @Query("DELETE FROM mod_list")
    suspend fun deleteAll()

    /** 获取所有 mod（按 room_id 降序） */
    @Query("SELECT * FROM mod_list ORDER BY room_id DESC")
    fun getAll(): Flow<List<ModInfo>>

    /** 根据 room_id 查询 */
    @Query("SELECT * FROM mod_list WHERE room_id = :roomId LIMIT 1")
    suspend fun getByRoomId(roomId: Int): ModInfo?

    /** 根据业务 ID（mod id）查询 */
    @Query("SELECT * FROM mod_list WHERE id = :modId LIMIT 1")
    suspend fun getByModId(modId: String): ModInfo?

    /** 获取所有 modId 相同的 mod */
    @Query("SELECT * FROM mod_list WHERE id = :modId")
    suspend fun getAllByModId(modId: String): List<ModInfo>

    /** 判断是否存在指定业务 ID */
    @Query("SELECT EXISTS(SELECT 1 FROM mod_list WHERE id = :modId)")
    suspend fun existsByModId(modId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM mod_list WHERE installPath = :installPath AND id = :modId)")
    suspend fun existsByInstallPathAndModId(installPath: String, modId: String): Boolean

    // 新增：按游戏ID查询模组
    @Query("SELECT * FROM mod_list WHERE game_id = :gameId ORDER BY room_id DESC")
    suspend fun getAllByGameId(gameId: String): List<ModInfo>

    @Query("SELECT * FROM mod_list WHERE game_id = :gameId ORDER BY room_id DESC")
    fun getAllByGameIdFlow(gameId: String): Flow<List<ModInfo>>

    @Query("SELECT * FROM mod_list WHERE game_id = :gameId AND id = :modId LIMIT 1")
    suspend fun getModInGame(gameId: String, modId: String): ModInfo?

    @Query("SELECT EXISTS(SELECT 1 FROM mod_list WHERE game_id = :gameId AND id = :modId)")
    suspend fun existsByGameIdAndModId(gameId: String, modId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM mod_list WHERE game_id = :gameId)")
    suspend fun existsByGameId(gameId: String): Boolean

    @Query("DELETE FROM mod_list WHERE game_id = :gameId")
    suspend fun deleteByGameId(gameId: String)

    @Query("DELETE FROM mod_list WHERE game_id = :gameId AND id = :modId")
    suspend fun deleteByGameIdAndModId(gameId: String, modId: String)

    // 获取所有模组，按游戏ID分组（用于调试和管理）
    @Query("SELECT game_id, COUNT(*) as count FROM mod_list WHERE game_id != '' GROUP BY game_id ORDER BY count DESC")
    suspend fun getModCountByGame(): List<GameModCount>

    // 数据类用于存储游戏模组数量统计
    data class GameModCount(
        val game_id: String,
        val count: Int
    )

}