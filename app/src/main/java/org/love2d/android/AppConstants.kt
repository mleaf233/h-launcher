package org.love2d.android

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 应用内使用的目录和常量
 */
object AppConstants {
    const val MODS_FOLDER_NAME = "mods"
    const val GAME_FOLDER_NAME = "hLauncherGame"

    fun getGameFolderPath(context: Context): File {
        return File(requireNotNull(context.getExternalFilesDir(null)), GAME_FOLDER_NAME)
    }

    fun getLegacyGameFolderPath(): File {
        return File(Environment.getExternalStorageDirectory(), GAME_FOLDER_NAME)
    }
}
