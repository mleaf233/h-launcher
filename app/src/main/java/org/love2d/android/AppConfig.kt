package org.love2d.android

import org.love2d.android.util.MMKVGetHelper

object AppConfig {
    const val IS_FIRST_START = "is_first_start"
    const val LEGACY_GAME_MIGRATION_COMPLETED = "legacy_game_migration_completed"

    const val USER_USED_THEME = "user_use_theme"
    var userUsedTheme = MMKVGetHelper.getInt(USER_USED_THEME, -1)

    const val IS_PIRACY_PACKAGE = "is_piracy_package"

    val userNotice = """
            🆓 免费使用声明
        
            本软件完全免费，仅供学习交流，禁止商用！
        
            ❌ 禁止行为：
            • 任何形式的收费或售卖（包括闲鱼 / 淘宝 / 拼多多等平台）
            • 二次打包后收费
            • 捆绑其他软件获利
        
            ✅ 允许行为：
            • 个人学习使用
            • 技术交流分享
        
            ⚠️ 如果你是买的，那么你被骗了，请立即举报！
        """.trimIndent()
}
