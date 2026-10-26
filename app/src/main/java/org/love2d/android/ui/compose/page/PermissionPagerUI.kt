package org.love2d.android.ui.compose.page
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.launch
import org.love2d.android.ui.compose.PermissionGate
import org.love2d.android.util.AppFileUtils
import org.love2d.android.util.GameManager
import org.love2d.android.util.PermissionUtil

data class PermissionPageBean(val btnText: String, val content: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PermissionPagerUI(
    needsLegacyMigration: Boolean = false,
    onConfirm: () -> Unit
) {
    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storagePermissions = remember(context, needsLegacyMigration) {
        PermissionUtil.getStoragePermissions(context, needsLegacyMigration)
    }
    val needsStoragePermission = storagePermissions.isNotEmpty()

    val pages = buildList {
        if (needsStoragePermission) {
            add(
                PermissionPageBean(
                    btnText = "申请权限",
                    content = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        "检测到旧版游戏目录仍在共享存储中，需要授权一次以迁移旧数据。"
                    } else {
                        "获取文件读写权限，用于在旧版 Android 系统上创建和管理游戏目录。"
                    }
                )
            )
        }
        add(
            PermissionPageBean(
                btnText = "创建游戏目录",
                content = "创建游戏目录，用于存放游戏数据，存档，模组等。"
            )
        )
        add(
            PermissionPageBean(
                btnText = "创建mods目录",
                content = "创建mods目录，用于存放模组。"
            )
        )
    }

    var isRequestPermissionGranted by remember { mutableStateOf(false) }
    val gameFolderPageIndex = if (needsStoragePermission) 1 else 0
    val modsFolderPageIndex = pages.lastIndex

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            count = pages.size,
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) { page ->
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxSize(),
                elevation = CardDefaults.cardElevation(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(), // 撑满每一页
                    contentAlignment = Alignment.Center
                ) {
                    // 上一个箭头（仅在非第一页显示）
                    if (page != 0) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(page - 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "上一个"
                            )
                        }
                    }

                    // 中心内容
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = pages[page].content,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = {
                            if (needsStoragePermission && page == 0) {
                                isRequestPermissionGranted = true
                            } else if (page == gameFolderPageIndex) {
                                if (AppFileUtils.checkOrCreateLocalGameFolder(context)) {
                                    scope.launch {
                                        if (needsLegacyMigration && !needsStoragePermission) {
                                            GameManager.migrateLegacyGamesIfNeeded(context)
                                            GameManager.syncGamesWithDatabase(context)
                                        }
                                        pagerState.animateScrollToPage(page + 1)
                                    }
                                }
                            } else if (page == modsFolderPageIndex) {
                                if (AppFileUtils.checkOrCreateLocalModsFolder(context)) {
                                    onConfirm()
                                }
                            }
                        }) {
                            Text(pages[page].btnText)
                        }
                    }
                    // 页面指示器（可选）
                    HorizontalPagerIndicator(
                        pagerState = pagerState,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                    if (needsStoragePermission && isRequestPermissionGranted && page == 0) {
                        PermissionGate(
                            permissions = storagePermissions,
                            onPermissionDenied = {
                                isRequestPermissionGranted = false
                            },
                            onPermissionGranted = {
                                isRequestPermissionGranted = false
                                scope.launch {
                                    GameManager.migrateLegacyGamesIfNeeded(context)
                                    GameManager.syncGamesWithDatabase(context)
                                    pagerState.animateScrollToPage(page + 1)
                                }
                            },
                            onDismiss = {
                                isRequestPermissionGranted = false
                            }
                        )
                    }
                }
            }
        }
    }
}
