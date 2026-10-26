package org.love2d.android.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.love2d.android.room.mod.ModInfo
import org.love2d.android.room.mod.ModInfoDao

/**
 * 模组隔离功能调试工具
 * 用于测试和验证按游戏隔离的模组管理功能
 */
object ModIsolationDebugger {

    /**
     * 测试模组隔离功能
     */
    fun testModIsolation() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ModIsolation", "=== 开始测试模组隔离功能 ===")

                // 1. 获取游戏模组数量统计
                val modCounts = ModDbUtil.getModCountByGame()
                Log.d("ModIsolation", "游戏模组数量统计:")
                modCounts.forEach { (gameId, count) ->
                    Log.d("ModIsolation", "  游戏 $gameId: $count 个模组")
                }

                // 2. 检查数据库中所有模组的game_id分布
                val allMods = ModDbUtil.getAllMods()
                allMods.collect { mods ->
                    Log.d("ModIsolation", "数据库中总共有 ${mods.size} 个模组")

                    val gameIdDistribution = mutableMapOf<String, Int>()
                    val emptyGameIdMods = mutableListOf<ModInfo>()

                    mods.forEach { mod ->
                        if (mod.game_id.isBlank()) {
                            emptyGameIdMods.add(mod)
                        } else {
                            gameIdDistribution[mod.game_id] = gameIdDistribution.getOrDefault(mod.game_id, 0) + 1
                        }
                    }

                    Log.d("ModIsolation", "Game ID 分布:")
                    gameIdDistribution.forEach { (gameId, count) ->
                        Log.d("ModIsolation", "  $gameId: $count 个模组")
                    }

                    Log.d("ModIsolation", "未关联游戏的模组数量: ${emptyGameIdMods.size}")
                    if (emptyGameIdMods.isNotEmpty()) {
                        Log.d("ModIsolation", "未关联游戏的模组列表:")
                        emptyGameIdMods.forEach { mod ->
                            Log.d("ModIsolation", "  - ${mod.name} (${mod.id}) from: ${mod.from}")
                        }
                    }

                    // 3. 测试查询特定游戏的模组
                    testGameSpecificMods("va231des") // 使用版本号风格的游戏ID
                    testGameSpecificMods("beta_release")
                }

            } catch (e: Exception) {
                Log.e("ModIsolation", "测试模组隔离功能时发生错误", e)
            }
        }
    }

    /**
     * 测试特定游戏的模组查询
     */
    private suspend fun testGameSpecificMods(gameId: String) {
        try {
            val gameMods = ModDbUtil.getModsByGameIdSync(gameId)
            Log.d("ModIsolation", "游戏 $gameId 的模组 (${gameMods.size} 个):")
            gameMods.forEach { mod ->
                Log.d("ModIsolation", "  - ${mod.name} (ID: ${mod.id}, Path: ${mod.installPath})")
            }
        } catch (e: Exception) {
            Log.e("ModIsolation", "查询游戏 $gameId 的模组时发生错误", e)
        }
    }

    /**
     * 创建测试数据
     */
    fun createTestData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ModIsolation", "=== 创建测试数据 ===")

                // 创建测试游戏1的模组（使用版本号风格的游戏ID）
                val mod1 = ModInfo().apply {
                    isLocal = true
                    name = "Test Mod 1"
                    id = "test_mod_1"
                    game_id = "va231des" // 使用版本号风格的游戏ID
                    game_name = "Test Game 1"
                    installPath = "/path/to/game1/mods"
                    from = "test"
                    author = "Test Author"
                    version = "1.0.0"
                }

                // 创建测试游戏2的模组
                val mod2 = ModInfo().apply {
                    isLocal = true
                    name = "Test Mod 2"
                    id = "test_mod_2"
                    game_id = "beta_release" // 使用另一个版本号风格的游戏ID
                    game_name = "Test Game 2"
                    installPath = "/path/to/game2/mods"
                    from = "test"
                    author = "Test Author"
                    version = "1.0.0"
                }

                // 创建共享模组（没有game_id）
                val mod3 = ModInfo().apply {
                    isLocal = true
                    name = "Shared Mod"
                    id = "shared_mod"
                    game_id = ""
                    game_name = ""
                    installPath = "/path/to/shared/mods"
                    from = "test"
                    author = "Test Author"
                    version = "1.0.0"
                }

                ModDbUtil.insertMod(mod1)
                ModDbUtil.insertMod(mod2)
                ModDbUtil.insertMod(mod3)

                Log.d("ModIsolation", "测试数据创建完成")

            } catch (e: Exception) {
                Log.e("ModIsolation", "创建测试数据时发生错误", e)
            }
        }
    }

    /**
     * 清理测试数据
     */
    fun cleanupTestData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ModIsolation", "=== 清理测试数据 ===")

                ModDbUtil.deleteModInGame("va231des", "test_mod_1")
                ModDbUtil.deleteModInGame("beta_release", "test_mod_2")

                // 删除共享模组需要通过其他方式
                val sharedMod = ModDbUtil.getModInGame("", "shared_mod")
                sharedMod?.let { ModDbUtil.deleteMod(it) }

                Log.d("ModIsolation", "测试数据清理完成")

            } catch (e: Exception) {
                Log.e("ModIsolation", "清理测试数据时发生错误", e)
            }
        }
    }

    /**
     * 验证模组隔离逻辑
     */
    fun validateModIsolation() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ModIsolation", "=== 验证模组隔离逻辑 ===")

                // 验证游戏1的模组
                val game1Mods = ModDbUtil.getModsByGameIdSync("va231des")
                val hasOnlyGame1Mods = game1Mods.all { it.game_id == "va231des" }
                Log.d("ModIsolation", "游戏(va231des)模组隔离验证: ${if (hasOnlyGame1Mods) "通过" else "失败"}")

                // 验证游戏2的模组
                val game2Mods = ModDbUtil.getModsByGameIdSync("beta_release")
                val hasOnlyGame2Mods = game2Mods.all { it.game_id == "beta_release" }
                Log.d("ModIsolation", "游戏(beta_release)模组隔离验证: ${if (hasOnlyGame2Mods) "通过" else "失败"}")

                // 验证游戏交叉隔离
                val game1HasGame2Mods = game1Mods.any { it.game_id == "beta_release" }
                val game2HasGame1Mods = game2Mods.any { it.game_id == "va231des" }
                val crossIsolationValid = !game1HasGame2Mods && !game2HasGame1Mods
                Log.d("ModIsolation", "游戏交叉隔离验证: ${if (crossIsolationValid) "通过" else "失败"}")

                Log.d("ModIsolation", "=== 模组隔离验证完成 ===")

            } catch (e: Exception) {
                Log.e("ModIsolation", "验证模组隔离逻辑时发生错误", e)
            }
        }
    }
}