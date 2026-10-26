package org.love2d.android.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.love2d.android.room.game.GameInfo
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 游戏时长统计工具类
 * 负责管理游戏游玩时长的记录、计算和格式化显示
 */
object GameTimeTracker {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    /**
     * 开始游戏会话计时
     */
    fun startGameSession(game: GameInfo, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 设置会话开始时间
                val updatedGame = game.copy(
                    sessionStartTime = System.currentTimeMillis()
                )

                // 更新数据库
                val repository = org.love2d.android.room.game.GameRepository(
                    org.love2d.android.room.game.GameDatabase.getInstance(context).gameDao()
                )
                repository.updateGame(updatedGame)

                Log.d("GameTimeTracker", "游戏会话开始: ${game.name}")
            } catch (e: Exception) {
                Log.e("GameTimeTracker", "开始游戏会话失败: ${e.message}", e)
            }
        }
    }

    /**
     * 结束游戏会话计时并更新总时长
     */
    fun endGameSession(game: GameInfo, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (game.sessionStartTime > 0) {
                    val sessionDuration = System.currentTimeMillis() - game.sessionStartTime
                    val newTotalPlayTime = game.totalPlayTime + sessionDuration

                    val updatedGame = game.copy(
                        totalPlayTime = newTotalPlayTime,
                        sessionStartTime = 0L, // 重置会话开始时间
                        lastPlayed = System.currentTimeMillis()
                    )

                    // 更新数据库
                    val repository = org.love2d.android.room.game.GameRepository(
                        org.love2d.android.room.game.GameDatabase.getInstance(context).gameDao()
                    )
                    repository.updateGame(updatedGame)

                    val durationText = formatDuration(sessionDuration)
                    Log.d("GameTimeTracker", "游戏会话结束: ${game.name}, 本次游玩时长: $durationText")
                }
            } catch (e: Exception) {
                Log.e("GameTimeTracker", "结束游戏会话失败: ${e.message}", e)
            }
        }
    }

    /**
     * 获取格式化的创建时间
     */
    fun getFormattedCreateTime(game: GameInfo): String {
        return if (game.createTime > 0) {
            dateFormat.format(Date(game.createTime))
        } else {
            "未知"
        }
    }

    /**
     * 获取格式化的最后游玩时间
     */
    fun getFormattedLastPlayed(game: GameInfo): String {
        return if (game.lastPlayed > 0) {
            dateFormat.format(Date(game.lastPlayed))
        } else {
            "从未游玩"
        }
    }

    /**
     * 获取格式化的总游玩时长
     */
    fun getFormattedTotalPlayTime(game: GameInfo): String {
        return formatDuration(game.totalPlayTime)
    }

    /**
     * 获取游玩时长统计信息
     */
    fun getPlaytimeStatistics(game: GameInfo): PlaytimeStatistics {
        val now = System.currentTimeMillis()

        // 总游玩时长
        val totalPlayTime = game.totalPlayTime

        // 最后游玩时间距离现在的时长
        val timeSinceLastPlayed = if (game.lastPlayed > 0) now - game.lastPlayed else 0

        // 创建游戏距离现在的时长
        val timeSinceCreation = if (game.createTime > 0) now - game.createTime else 0

        // 计算平均每次游玩时长
        val sessionCount = calculateEstimatedSessionCount(game, now)
        val averageSessionTime = if (sessionCount > 0) totalPlayTime / sessionCount else 0

        return PlaytimeStatistics(
            totalPlayTime = totalPlayTime,
            formattedTotalPlayTime = formatDuration(totalPlayTime),
            lastPlayedTime = game.lastPlayed,
            formattedLastPlayed = getFormattedLastPlayed(game),
            createTime = game.createTime,
            formattedCreateTime = getFormattedCreateTime(game),
            timeSinceLastPlayed = timeSinceLastPlayed,
            timeSinceCreation = timeSinceCreation,
            estimatedSessionCount = sessionCount,
            averageSessionTime = averageSessionTime,
            formattedAverageSessionTime = formatDuration(averageSessionTime)
        )
    }

    /**
     * 估算游戏会话次数
     */
    private fun calculateEstimatedSessionCount(game: GameInfo, now: Long): Int {
        if (game.lastPlayed == 0L || game.createTime == 0L) return 0

        // 简单估算：基于最后游玩时间和创建时间的间隔
        val totalDays = TimeUnit.MILLISECONDS.toDays(now - game.createTime)
        val daysSinceLastPlayed = TimeUnit.MILLISECONDS.toDays(now - game.lastPlayed)

        // 如果最后一次游玩时间距离现在很近，可能正在游玩
        if (daysSinceLastPlayed <= 1 && game.totalPlayTime > 0) {
            // 假设平均每次游玩30分钟
            return (game.totalPlayTime / (30 * 60 * 1000)).toInt().coerceAtLeast(1)
        }

        // 否则基于总时长估算
        return if (game.totalPlayTime > 0) {
            (game.totalPlayTime / (30 * 60 * 1000)).toInt() // 假设平均每次30分钟
        } else 0
    }

    /**
     * 格式化时长为易读格式
     */
    fun formatDuration(milliseconds: Long): String {
        if (milliseconds <= 0) return "0小时0分钟"

        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
            hours > 0 -> "${hours}小时"
            minutes > 0 -> "${minutes}分钟"
            else -> "少于1分钟"
        }
    }

    /**
     * 格式化相对时间（如："2小时前"、"昨天"等）
     */
    fun formatRelativeTime(milliseconds: Long): String {
        if (milliseconds <= 0) return "刚刚"

        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val days = TimeUnit.MILLISECONDS.toDays(milliseconds)

        return when {
            seconds < 60 -> "${seconds}秒前"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days < 7 -> "${days}天前"
            days < 30 -> "${days / 7}周前"
            days < 365 -> "${days / 30}个月前"
            else -> "${days / 365}年前"
        }
    }

    /**
     * 游戏统计信息数据类
     */
    data class PlaytimeStatistics(
        val totalPlayTime: Long,                      // 总游玩时长（毫秒）
        val formattedTotalPlayTime: String,            // 格式化的总游玩时长
        val lastPlayedTime: Long,                      // 最后游玩时间（时间戳）
        val formattedLastPlayed: String,               // 格式化的最后游玩时间
        val createTime: Long,                           // 创建时间（时间戳）
        val formattedCreateTime: String,               // 格式化的创建时间
        val timeSinceLastPlayed: Long,                 // 距离最后游玩时长（毫秒）
        val timeSinceCreation: Long,                   // 距离创建时长（毫秒）
        val estimatedSessionCount: Int,                // 估算的游戏会话次数
        val averageSessionTime: Long,                  // 平均每次游玩时长（毫秒）
        val formattedAverageSessionTime: String        // 格式化的平均每次游玩时长
    )
}