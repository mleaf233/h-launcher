package org.love2d.android.ui.compose

import android.util.Log

/**
 * 测试正则表达式是否支持中文、英文、数字和横线
 */
object RegexTest {

    // 与Dialog.kt中相同的正则表达式
    private val pattern = Regex("^[\u4e00-\u9fa5a-zA-Z0-9-]+$")

    /**
     * 测试各种输入是否通过验证
     */
    fun testPattern() {
        val testCases = mapOf(
            // 有效的测试用例
            "游戏名称" to true,
            "GameName" to true,
            "game123" to true,
            "游戏-123" to true,
            "MyGame-2024" to true,
            "中文-English" to true,
            "123" to true,
            "游戏" to true,
            "Game" to true,
            "test-game" to true,
            "游戏-测试" to true,
            "A" to true,
            "中" to true,
            "game-with-dashes" to true,

            // 无效的测试用例
            "game name" to false,  // 包含空格
            "game@name" to false, // 包含特殊字符
            "game#name" to false, // 包含特殊字符
            "game.name" to false, // 包含点号
            "game_name" to false, // 包含下划线
            "game(name)" to false,// 包含括号
            "" to false,          // 空字符串
            "   " to false,       // 只有空格
            "游戏 " to false,     // 中文加空格
            "game " to false,     // 英文加空格
            "game $" to false,    // 包含特殊字符
            "game%" to false,     // 包含特殊字符
        )

        Log.d("RegexTest", "开始测试正则表达式...")

        testCases.forEach { (input, expected) ->
            val actual = pattern.matches(input)
            val status = if (actual == expected) "✅ PASS" else "❌ FAIL"

            Log.d("RegexTest", "$status 输入: '$input' -> 期望: $expected, 实际: $actual")
        }

        Log.d("RegexTest", "正则表达式测试完成！")
    }
}