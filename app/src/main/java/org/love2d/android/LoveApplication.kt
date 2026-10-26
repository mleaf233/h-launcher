package org.love2d.android

import android.app.Application
import android.util.Log
import com.tencent.bugly.crashreport.CrashReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.love2d.android.ui.compose.RegexTest
import org.love2d.android.util.GameDbUtil
import org.love2d.android.util.GameManager
import org.love2d.android.util.MMKVHelper
import org.love2d.android.util.ModDbUtil
import org.love2d.android.util.ModIsolationDebugger
import java.io.File
import kotlin.properties.Delegates

/**
 * ClassName LoveApplication
 * Description
 * Create by hjr
 * Date 2025/6/19 14:18
 */
class LoveApplication : Application() {

    companion object {
        var appContext: LoveApplication by Delegates.notNull<LoveApplication>()

        @JvmStatic
        var MOD_PATH: String? = null

        fun setModPath(path: String) {
            MOD_PATH = path
            Log.e("HJR", "MOD_PATH = $MOD_PATH")
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this

        //默认路径
        MOD_PATH = File(appContext.getExternalFilesDir(null), "mods").absolutePath

        // 测试游戏名称正则表达式
        RegexTest.testPattern()

        // 测试模组隔离功能
        ModIsolationDebugger.testModIsolation()

        MMKVHelper.init(appContext)
        CoroutineScope(Dispatchers.IO).launch {
            GameDbUtil.init(appContext)
            ModDbUtil.init(appContext)
            GameManager.syncGamesWithDatabase(appContext.applicationContext)
        }

        CrashReport.initCrashReport(applicationContext, "5863f1fa73", true)
    }
}