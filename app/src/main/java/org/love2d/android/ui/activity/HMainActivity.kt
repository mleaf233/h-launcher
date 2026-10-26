package org.love2d.android.ui.activity

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.jeremyliao.liveeventbus.LiveEventBus
import kotlinx.coroutines.launch
import org.love2d.android.AppConfig
import org.love2d.android.BaseComposeActivity
import org.love2d.android.script.GLSLFixerScript
import org.love2d.android.ui.compose.page.CreateModPackagePage
import org.love2d.android.ui.compose.page.DownloadInfoModPage
import org.love2d.android.ui.compose.page.EnhancedLogDetailPage
import org.love2d.android.ui.compose.page.EnhancedLovelyLogPage
import org.love2d.android.ui.compose.page.FinalFixedHeaderPage
import org.love2d.android.ui.compose.page.GameDetailPage
import org.love2d.android.ui.compose.page.HomePage
import org.love2d.android.ui.compose.page.ModPage
import org.love2d.android.ui.compose.page.ModDirectInstallPage
import org.love2d.android.ui.compose.page.ModPackagerPage
import org.love2d.android.ui.compose.page.ModPackagerImportPage
import org.love2d.android.ui.compose.page.ModStorePortalPage
import org.love2d.android.ui.compose.page.SaveManagerPage
import org.love2d.android.ui.compose.page.ShaderFixerModListPage
import org.love2d.android.ui.compose.page.ShadersFilePage
import org.love2d.android.ui.compose.page.SplashPage
import org.love2d.android.ui.resource.AppRoot
import org.love2d.android.ui.resource.LocalUserPreferredTheme
import org.love2d.android.util.FilePickerHelper
import org.love2d.android.util.GameManager
import org.love2d.android.util.GameStartUtil
import org.love2d.android.util.MMKVHelper
import org.love2d.android.util.ZipManager
import java.net.URLDecoder
import kotlin.system.exitProcess

/**
 * ClassName HMainActivity
 * Description
 * Create by hjr
 * Date 2025/6/23 13:40
 */
class HMainActivity : BaseComposeActivity(false) {
    val viewModel: GameManagerViewModel by viewModels {
        GameViewModelFactory(this) // 或 this (Activity)
    }

    @Composable
    override fun ScreenContent() {
        HAppNavHost(
            navController = rememberNavController(), localActivity = localActivity, viewModel = viewModel
        )
    }

    override fun initListener() {
        val isPiracy: MutableState<Boolean> =
            mutableStateOf(MMKVHelper.getBoolean(AppConfig.IS_PIRACY_PACKAGE, false))
        if (isPiracy.value) {
            exitProcess(0)
        }

        LiveEventBus.get("KILL_GAME_PROCESS", String::class.java).observe(this@HMainActivity) {
            Log.e("HMainActivity", "KILL_GAME_PROCESS")
            GameStartUtil.killGameProcess(localActivity)
        }
        lifecycleScope.launch {
            viewModel.gameName.collect { name ->
                if (name.isCreate) {
                    if (name.gameName.isNotBlank()) {
                        // 安全检查fileUrl，避免空指针异常
                        val fileUrl = viewModel.fileUrl
                        if (fileUrl != null) {
                            GameManager.installGame(
                                localContext, fileUrl, name.gameName
                            )
                        } else {
                            Toast.makeText(localActivity, "游戏文件路径无效", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    if (name.gameName.isNotBlank()) {
                        // 安全检查currentGame，避免空指针异常
                        val currentGame = viewModel.currentGame.value
                        if (currentGame != null) {
                            GameManager.renameGame(
                                localContext,
                                currentGame,
                                name.gameName,
                            )
                        } else {
                            Toast.makeText(localActivity, "当前游戏信息无效", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                viewModel.releaseGameNameField()
            }
        }

        LiveEventBus.get("GAME_ACTIVITY_DESTROY", Boolean::class.java).observe(this@HMainActivity) {
            //kill :game_progress
            GameStartUtil.kill(this@HMainActivity)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                FilePickerHelper.REQUEST_GAME_CODE_FILE -> {
                    val handleFileResult = FilePickerHelper.handleLoveFileResult(localActivity, data) {
                        viewModel.fileUrl = it
                        viewModel.inputGameName()
                    }
                    if (handleFileResult) {
                        Toast.makeText(localActivity, "游戏安装成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(localActivity, "游戏安装失败", Toast.LENGTH_SHORT).show()
                    }
                }

                FilePickerHelper.REQUEST_MOD_CODE_FILE -> {
                    val currentGame = viewModel.currentGame.value
                    val modPath = currentGame?.modPath
                    FilePickerHelper.handleZipFileResult(localActivity, data) { modUri ->
                        // 正确的调用，使用 ZipManager 来处理 ZIP 安装，并传递游戏信息
                        ZipManager.installModFromUri(
                            localContext,
                            modPath = modPath.orEmpty(),
                            zipUri = modUri,
                            gameId = currentGame?.id ?: "",
                            gameName = currentGame?.name ?: ""
                        )
                    }
                }

                else -> {}
            }

        }

    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HAppNavHost(
    navController: NavHostController,
    startDestination: String = NavigationItem.Splash.route,
    localActivity: Activity,
    viewModel: GameManagerViewModel,
) {
    val context = LocalContext.current

    val systemUiController = rememberSystemUiController()
    val currentTheme = LocalUserPreferredTheme.current

    AppRoot() {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Screen.SPLASH.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                SplashPage(navController = navController)
            }

            composable(Screen.HOME.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                HomePage(
                    navController = navController, localActivity = localActivity, viewModel = viewModel, currentTheme
                )
                SideEffect {
                    systemUiController.setStatusBarColor(
                        color = Color.Transparent, // 或 MaterialTheme.colorScheme.background 等
                        darkIcons = currentTheme.value
                    )
                }
            }

            composable(Screen.DETAIL.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                GameDetailPage(
                    viewModel = viewModel,
                    navController = navController,
                    localActivity = localActivity
                )
                SideEffect {
                    systemUiController.setStatusBarColor(
                        color = Color.Transparent, // 或 MaterialTheme.colorScheme.background 等
                        darkIcons = !currentTheme.value
                    )
                }
            }

            composable(Screen.MOD.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                ModPage(navController = navController, localActivity, viewModel = viewModel)
            }

            composable(Screen.LOG.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                EnhancedLovelyLogPage(navController = navController, viewModel = viewModel)
            }

            composable(Screen.LOG_DETAIL.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                EnhancedLogDetailPage(navController = navController, viewModel = viewModel)
            }

            composable(Screen.CREATE_MOD_PACKAGE.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                CreateModPackagePage(navController = navController, viewModel = viewModel)
            }

            composable(Screen.MOD_PORTAL.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                ModStorePortalPage(navController = navController)
            }

            composable(Screen.MOD_DIRECT_INSTALL.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                ModDirectInstallPage(navController = navController, viewModel = viewModel)
            }

            composable(Screen.MOD_ONLINE.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                FinalFixedHeaderPage(navController = navController, viewModel = viewModel)
            }

            composable(Screen.DOWNLOAD_INFO_LIST.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                DownloadInfoModPage(navController = navController, viewModel = viewModel)
            }

            composable(Screen.SHADER_FIXER_MOD_LIST.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                ShaderFixerModListPage(
                    navController = navController,
                    localActivity = localActivity,
                    modPath = viewModel.currentGame.value?.modPath.orEmpty(),
                    viewModel = viewModel
                )
            }

            // --- 在这里添加新的页面路由 ---
            composable(
                // 1. 定义路由，并用 "{}" 声明一个名为 "shaderPath" 的参数
                route = Screen.SHADERS_FIXER.name + "/{shaderPath}",
                // 2. 定义参数类型
                arguments = listOf(navArgument("shaderPath") { type = NavType.StringType }),
                // 您可以沿用其他页面的过渡动画效果
                enterTransition = { fadeIn(animationSpec = tween(durationMillis = 300)) },
                exitTransition = { fadeOut(animationSpec = tween(durationMillis = 300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 300)) },
                popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 300)) }
            ) { backStackEntry ->
                // 3. 从路由中获取参数值
                val encodedPath = backStackEntry.arguments?.getString("shaderPath")
                if (encodedPath != null) {
                    // 4. 对路径进行解码，以防包含特殊字符
                    val shaderPath = URLDecoder.decode(encodedPath, "UTF-8")
                    ShadersFilePage(
                        navController = navController,
                        context = context,
                        shadersFolderPath = shaderPath
                    )
                } else {
                    // 如果路径为空，可以自动返回上一页
                    navController.popBackStack()
                }
            }

            composable(Screen.SAVE_MANAGER.name, enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                SaveManagerPage(
                    navController = navController,
                    localActivity = localActivity,
                    viewModel = viewModel
                )
            }

            composable("mod_packager", enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                ModPackagerPage(
                    viewModel = viewModel,
                    navController = navController,
                    localActivity = localActivity
                )
            }

            composable("mod_packager_share", enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                ModPackagerPage(
                    viewModel = viewModel,
                    navController = navController,
                    localActivity = localActivity
                )
            }

            composable("mod_packager_import", enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }, popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 300))
            }, popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 300))
            }) {
                ModPackagerImportPage(
                    viewModel = viewModel,
                    navController = navController,
                    localActivity = localActivity
                )
            }
        }
    }
}
