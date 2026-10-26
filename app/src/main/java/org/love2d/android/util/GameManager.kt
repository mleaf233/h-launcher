package org.love2d.android.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.love2d.android.AppConfig
import org.love2d.android.AppConstants
import org.love2d.android.room.game.GameInfo
import java.io.File
import java.util.zip.ZipFile

/**
 * 负责管理游戏文件的安装、重命名、目录结构和数据库同步
 */
object GameManager {

    // 线程安全的协程作用域
    private val gameManagerScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    // 用于同步操作的互斥锁
    private val gameOperationMutex = Mutex()

    // 用于数据库同步的互斥锁
    private val syncMutex = Mutex()

    private fun getAppGameDir(context: Context): File {
        return AppConstants.getGameFolderPath(context)
    }

    private fun getLegacyGameDir(): File {
        return AppConstants.getLegacyGameFolderPath()
    }

    private fun ensureGameDirExists(directory: File): Boolean {
        return directory.exists() || directory.mkdirs()
    }

    private fun findGameFileCandidates(context: Context, name: String): List<File> {
        val normalizedName = name.removeSuffix(".love")
        return listOf(
            File(getAppGameDir(context), "$normalizedName.love"),
            File(getLegacyGameDir(), "$normalizedName.love")
        )
    }

    private fun resolveCurrentGameFile(context: Context, game: GameInfo): File? {
        val dbFile = game.filePath.takeIf { it.isNotBlank() }?.let(::File)
        if (dbFile != null && dbFile.exists() && dbFile.isFile) {
            return dbFile
        }
        return findGameFileCandidates(context, game.name).firstOrNull { it.exists() && it.isFile }
    }

    private fun hasLegacyGameAccess(): Boolean {
        val legacyDir = getLegacyGameDir()
        return runCatching {
            legacyDir.exists() && legacyDir.isDirectory && legacyDir.listFiles() != null
        }.getOrDefault(false)
    }

    private suspend fun ensureGameDbReady(context: Context) {
        GameDbUtil.init(context.applicationContext)
    }

    suspend fun migrateLegacyGamesIfNeeded(context: Context) {
        syncMutex.withLock {
            migrateLegacyGamesInternal(context)
        }
    }

    suspend fun hasPendingLegacyMigration(context: Context): Boolean {
        if (MMKVHelper.getBoolean(AppConfig.LEGACY_GAME_MIGRATION_COMPLETED, false)) {
            return false
        }

        val appGameDir = getAppGameDir(context)
        val appGameFiles = runCatching {
            appGameDir.listFiles { file ->
                file.isFile && file.extension.equals("love", ignoreCase = true)
            }
        }.getOrNull().orEmpty()
        val appGameFileNames = appGameFiles.mapTo(mutableSetOf()) { it.name }

        if (hasLegacyGameAccess()) {
            val legacyFiles = getLegacyGameDir().listFiles { file ->
                file.isFile && file.extension.equals("love", ignoreCase = true)
            }.orEmpty()
            if (legacyFiles.any { it.name !in appGameFileNames }) {
                return true
            }
        }

        ensureGameDbReady(context)
        val legacyRootPath = getLegacyGameDir().absolutePath
        return GameDbUtil.getGameDao().getAllGamesNow().any { game ->
            game.filePath.startsWith(legacyRootPath, ignoreCase = true)
        }
    }

    private suspend fun migrateLegacyGamesInternal(context: Context) {
        val appGameDir = getAppGameDir(context)
        if (!ensureGameDirExists(appGameDir)) {
            Log.w("GameManager", "Unable to create app game directory: ${appGameDir.absolutePath}")
            return
        }

        if (!hasLegacyGameAccess()) {
            return
        }

        val legacyGameDir = getLegacyGameDir()
        val legacyFiles = runCatching {
            legacyGameDir.listFiles { file -> file.isFile && file.extension.equals("love", ignoreCase = true) }
        }.getOrNull() ?: emptyArray()
        if (legacyFiles.isEmpty()) {
            MMKVHelper.putBoolean(AppConfig.LEGACY_GAME_MIGRATION_COMPLETED, true)
            return
        }

        ensureGameDbReady(context)
        val dao = GameDbUtil.getGameDao()
        val gamesInDb = dao.getAllGamesNow()
        var migrationSucceeded = true

        legacyFiles.forEach { legacyFile ->
            val targetFile = File(appGameDir, legacyFile.name)
            try {
                if (!targetFile.exists()) {
                    legacyFile.copyTo(targetFile, overwrite = false)
                    Log.d("GameManager", "Migrated legacy game: ${legacyFile.absolutePath} -> ${targetFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e("GameManager", "Failed to migrate legacy game: ${legacyFile.absolutePath}", e)
                migrationSucceeded = false
                return@forEach
            }

            val matchingGame = gamesInDb.firstOrNull { game ->
                game.filePath == legacyFile.absolutePath ||
                        game.filePath == targetFile.absolutePath ||
                        game.name == legacyFile.nameWithoutExtension
            }

            if (matchingGame != null && matchingGame.filePath != targetFile.absolutePath) {
                matchingGame.filePath = targetFile.absolutePath
                dao.updateGame(matchingGame)
            }
        }

        if (migrationSucceeded) {
            MMKVHelper.putBoolean(AppConfig.LEGACY_GAME_MIGRATION_COMPLETED, true)
        }
    }

    /**
     * 从 Uri 安装一个 .love 游戏文件到应用目录
     */
    fun installGame(context: Context, sourceUri: Uri, fileName: String): Boolean {
        val normalizedName = fileName.removeSuffix(".love")
        val destDir = getAppGameDir(context)
        if (!ensureGameDirExists(destDir)) {
            Log.e("GameManager", "Failed to create game directory: ${destDir.absolutePath}")
            return false
        }
        val destFile = File(destDir, "$normalizedName.love")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            // 使用线程安全的协程作用域
            gameManagerScope.launch {
                try {
                    ensureGameDbReady(context)
                    val name = normalizedName
                    val modsPath = createGameModsFolder(context, name)

                    val game = GameInfo(
                        id = generateGameId(name, destFile.absolutePath), // 生成唯一的字符串ID
                        name = name,
                        createTime = System.currentTimeMillis(),
                        filePath = destFile.absolutePath,
                        savePath = "",
                        modPath = modsPath,
                        lastPlayed = 0,
                        totalPlayTime = 0L,        // 初始化总游玩时长
                        sessionStartTime = 0L     // 初始化会话开始时间
                    )
                    GameDbUtil.insertGame(game)
                } catch (e: Exception) {
                    Log.e("GameManager", "Error inserting game to database", e)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 重命名游戏及其关联的文件夹和数据库记录
     */
    fun renameGame(context: Context, oldGame: GameInfo?, newName: String): Boolean {
        if (oldGame == null) return false

        val appGameDir = getAppGameDir(context)
        if (!ensureGameDirExists(appGameDir)) {
            Toast.makeText(context, "游戏目录不可用", Toast.LENGTH_SHORT).show()
            return false
        }
        val oldFile = resolveCurrentGameFile(context, oldGame)
        val newFile = File(appGameDir, "$newName.love")

        if (oldFile == null || !oldFile.exists() || !oldFile.isFile) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
            return false
        }
        if (newFile.exists()) {
            Toast.makeText(context, "同名文件已存在", Toast.LENGTH_SHORT).show()
            return false
        }

        val newModPath = renameGameModsFolder(context, oldGame.name, newName)
        renameSaveFolder(context, oldGame.name, newName)

        val renameSucceeded = if (oldFile.parentFile?.absolutePath == appGameDir.absolutePath) {
            oldFile.renameTo(newFile)
        } else {
            runCatching {
                oldFile.copyTo(newFile, overwrite = false)
                true
            }.getOrElse {
                Log.e("GameManager", "Failed to migrate legacy game during rename", it)
                false
            }
        }

        if (renameSucceeded) {
            oldGame.name = newName
            oldGame.modPath = newModPath
            oldGame.filePath = newFile.absolutePath
            // 使用线程安全的协程作用域和互斥锁
            gameManagerScope.launch {
                gameOperationMutex.withLock {
                    try {
                        ensureGameDbReady(context)
                        GameDbUtil.updateGame(oldGame)
                    } catch (e: Exception) {
                        Log.e("GameManager", "Error updating game in database", e)
                    }
                }
            }
            return true
        }
        return false
    }

    /**
     * 同步游戏目录下的 .love 文件与数据库记录
     */
    fun syncGamesWithDatabase(context: Context) {
        gameManagerScope.launch {
            syncMutex.withLock {
                try {
                    migrateLegacyGamesInternal(context)

                    val gameDir = getAppGameDir(context)
                    if (!ensureGameDirExists(gameDir) || !gameDir.isDirectory) return@launch

                    ensureGameDbReady(context)
                    val dao = GameDbUtil.getGameDao()
                    val gamesInDb = dao.getAllGamesNow()
                    val filesInDir = gameDir.listFiles { file ->
                        file.isFile && file.extension.equals("love", ignoreCase = true)
                    } ?: emptyArray()
                    val canAccessLegacyDir = hasLegacyGameAccess()

                    val filesByPath = filesInDir.associateBy { it.absolutePath }
                    val filesByName = filesInDir.associateBy { it.nameWithoutExtension }

                    gamesInDb.forEach { game ->
                        val resolvedFile = resolveCurrentGameFile(context, game)
                        if (resolvedFile != null && resolvedFile.absolutePath != game.filePath) {
                            game.filePath = resolvedFile.absolutePath
                            dao.updateGame(game)
                        }
                    }

                    val refreshedGamesInDb = dao.getAllGamesNow()

                    // 1. 添加数据库中不存在的游戏
                    val gamesToInsert = mutableListOf<GameInfo>()
                    filesInDir.filter { file ->
                        refreshedGamesInDb.none { game ->
                            game.filePath == file.absolutePath || game.name == file.nameWithoutExtension
                        }
                    }
                        .forEach { file ->
                            val name = file.nameWithoutExtension
                            val modsPath = createGameModsFolder(context, name)
                            val gameInfo = GameInfo(
                                id = generateGameId(name, file.absolutePath), // 生成唯一的字符串ID
                                name = name,
                                filePath = file.absolutePath,
                                savePath = "",
                                modPath = modsPath,
                                lastPlayed = System.currentTimeMillis(),
                                totalPlayTime = 0L,        // 初始化总游玩时长
                                sessionStartTime = 0L     // 初始化会话开始时间
                            )
                            gamesToInsert.add(gameInfo)
                        }

                    // 批量插入以提高性能
                    if (gamesToInsert.isNotEmpty()) {
                        gamesToInsert.forEach { game ->
                            dao.insertGame(game)
                        }
                    }

                    // 2. 删除数据库中有但文件中已不存在的游戏
                    val gamesToDelete = refreshedGamesInDb.filter { game ->
                        val dbFile = game.filePath.takeIf { it.isNotBlank() }?.let(::File)
                        if (!canAccessLegacyDir &&
                            dbFile?.absolutePath?.startsWith(getLegacyGameDir().absolutePath, ignoreCase = true) == true
                        ) {
                            return@filter false
                        }
                        val currentFile = when {
                            dbFile != null && filesByPath.containsKey(dbFile.absolutePath) -> dbFile
                            filesByName.containsKey(game.name) -> filesByName.getValue(game.name)
                            else -> null
                        }
                        currentFile == null
                    }

                    // 批量删除
                    gamesToDelete.forEach { gameToDelete ->
                        dao.deleteGame(gameToDelete)
                    }

                    Log.d("GameManager", "Database sync completed. Added: ${gamesToInsert.size}, Deleted: ${gamesToDelete.size}")
                } catch (e: Exception) {
                    Log.e("GameManager", "Error syncing games with database", e)
                }
            }
        }
    }

    /**
     * 根据游戏名检查对应的 .love 文件是否存在
     */
    fun doesGameFileExist(name: String): Boolean {
        val context = org.love2d.android.LoveApplication.appContext.applicationContext
        return findGameFileCandidates(context, name).any { it.exists() && it.isFile }
    }

    /**
     * 从 .love 文件内部读取指定路径的文本文件内容
     */
    fun readFileFromLove(loveFile: File, innerFilePath: String): String? {
        return try {
            ZipFile(loveFile).use { zip ->
                zip.getEntry(innerFilePath)?.let { entry ->
                    zip.getInputStream(entry).bufferedReader().readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 为指定游戏创建 Mods 文件夹
     */
    fun createGameModsFolder(context: Context, name: String): String {
        val modsDir = File(context.getExternalFilesDir(null), "mods_${name}")
        if (!modsDir.exists()) {
            modsDir.mkdirs()
        }
        return modsDir.absolutePath
    }

    private fun renameGameModsFolder(context: Context, oldName: String, newName: String): String {
        val modsRoot = context.getExternalFilesDir(null) ?: return ""
        val oldModsDir = File(modsRoot, "mods_${oldName}")
        val newModsDir = File(modsRoot, "mods_${newName}")

        if (oldModsDir.exists() && oldModsDir.isDirectory) {
            if (newModsDir.exists()) {
                Log.w("GameManager", "目标 Mods 文件夹已存在，未重命名: ${newModsDir.name}")
                return newModsDir.absolutePath
            }
            return if (oldModsDir.renameTo(newModsDir)) newModsDir.absolutePath else ""
        }
        Log.w("GameManager", "旧 Mods 文件夹不存在: ${oldModsDir.name}")
        return ""
    }

    private fun renameSaveFolder(context: Context, oldName: String, newName: String) {
        val oldSaveDir = File(context.getExternalFilesDir(null), "save/love/$oldName-LOVE")
        val newSaveDir = File(context.getExternalFilesDir(null), "save/love/$newName-LOVE")

        if (oldSaveDir.exists() && oldSaveDir.isDirectory) {
            if (newSaveDir.exists()) {
                Log.w("GameManager", "目标存档文件夹已存在，未重命名: ${newSaveDir.name}")
                return
            }
            if (!oldSaveDir.renameTo(newSaveDir)) {
                Log.w("GameManager", "存档文件夹重命名失败: $oldName -> $newName")
            }
        } else {
            Log.w("GameManager", "旧存档文件夹不存在: ${oldSaveDir.name}")
        }
    }

    /**
     * 生成唯一游戏ID，支持字符串如 "va231des"
     * 优先使用游戏名称，如果冲突则添加时间戳
     */
    private fun generateGameId(gameName: String, filePath: String): String {
        // 清理游戏名称，移除特殊字符
        val cleanName = gameName.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        // 如果名称为空，使用文件名
        val baseId = if (cleanName.isNotBlank()) {
            cleanName.lowercase()
        } else {
            File(filePath).nameWithoutExtension.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
        }

        // 添加简短的时间戳以避免冲突
        val timestamp = (System.currentTimeMillis() / 1000) % 10000 // 取后4位
        return "${baseId}_${timestamp}"
    }
}
