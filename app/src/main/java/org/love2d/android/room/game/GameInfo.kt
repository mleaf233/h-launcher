package org.love2d.android.room.game

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "games")
data class GameInfo(
    @PrimaryKey val id: String = "",   // 唯一ID，支持字符串如 "va231des"
    var name: String,
    var filePath: String,
    var modPath : String,
    var iconPath: String? = null,
    var savePath : String,
    var isEnableMod : Boolean = true,
    var createTime : Long = 0L,
    var lastPlayed: Long = 0L,
    var totalPlayTime: Long = 0L,     // 总游玩时长（毫秒）
    var sessionStartTime: Long = 0L   // 当前会话开始时间（毫秒）
) : Parcelable