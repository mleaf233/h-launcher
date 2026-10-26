package org.love2d.android

import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.love2d.android.util.MMKVGetHelper
import org.love2d.android.util.MMKVHelper

/**
 * ClassName ThemeConfig
 * Description
 * Create by hjr
 * Date 2025/9/30 09:47
 */
object ThemeConfig {
    const val LIGHT_THEME = 999
    const val DARK_THEME = -999
    const val SYSTEM_THEME = -1

    fun getUserUsedTheme(): Int {
        return if (AppConfig.userUsedTheme == -1) {
            SYSTEM_THEME
        } else {
            AppConfig.userUsedTheme
        }
    }

    fun setTheme(value: Boolean) {
        MMKVHelper.putInt(AppConfig.USER_USED_THEME, if (value) DARK_THEME else LIGHT_THEME)
    }

    @Composable
    fun rememberUserPreferredTheme(): MutableState<Boolean> {
        val currentTheme = getUserUsedTheme()
        val int = MMKVGetHelper.getInt(AppConfig.USER_USED_THEME, -1)
        Log.e("HJR", "AppConfig ThemeConfig currentTheme $currentTheme mmkvVal $int")
        val userUsedTheme = when (int) {
            LIGHT_THEME -> false
            DARK_THEME -> true
            else -> isSystemInDarkTheme()
        }

        return remember { mutableStateOf(userUsedTheme) }
    }
}