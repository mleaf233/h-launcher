package org.love2d.android.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.love2d.android.room.game.GameDao
import org.love2d.android.room.game.GameDatabase
import org.love2d.android.room.game.GameInfo

object GameDbUtil {

    @Volatile
    private var db: GameDatabase? = null

    private val initializationMutex = Mutex()

    suspend fun init(context: Context) = initializationMutex.withLock {
        if (db == null) {
            db = GameDatabase.getInstance(context)
        }
    }

    fun getGameDao(): GameDao {
        return checkInitialized().gameDao()
    }

    fun getAllGames(): Flow<List<GameInfo>> {
        return checkInitialized().gameDao().getAllGames()
    }

    suspend fun getGameById(id: String): GameInfo? {
        return withContext(Dispatchers.IO) {
            checkInitialized().gameDao().getGameById(id)
        }
    }

    suspend fun insertGame(game: GameInfo) {
        withContext(Dispatchers.IO) {
            checkInitialized().gameDao().insertGame(game)
        }
    }

    suspend fun updateGame(game: GameInfo) {
        withContext(Dispatchers.IO) {
            checkInitialized().gameDao().updateGame(game)
        }
    }

    suspend fun deleteGame(game: GameInfo) {
        withContext(Dispatchers.IO) {
            checkInitialized().gameDao().deleteGame(game)
        }
    }

    suspend fun deleteAllGames() {
        withContext(Dispatchers.IO) {
            checkInitialized().gameDao().deleteAllGames()
        }
    }

    private fun checkInitialized(): GameDatabase {
        return db ?: throw IllegalStateException("GameDbUtil is not initialized. Call GameDbUtil.init(context) first.")
    }
}
