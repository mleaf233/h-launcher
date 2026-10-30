package org.love2d.android.ui.compose.page

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.love2d.android.ui.activity.GameManagerViewModel
import org.love2d.android.ui.activity.Screen
import org.love2d.android.ui.compose.EmptyLottie
import org.love2d.android.ui.compose.TopTitleBar
import org.love2d.android.util.ModMetadataUtil
import java.io.File
import java.net.URLEncoder

/**
 * 「去除版本限制」第一步：列出所有声明了带版本约束依赖的模组。
 */
@Composable
fun VersionLimitModListPage(
    navController: NavController,
    viewModel: GameManagerViewModel,
) {
    val gameInfo by viewModel.currentGame.collectAsState()
    val modPath = gameInfo?.modPath.orEmpty()
    val modsFlow = remember(modPath) { viewModel.getPathMods(modPath) }
    val modList by modsFlow.collectAsState(initial = emptyList())

    // 只保留「至少有一条依赖带版本约束」的模组
    val entries = remember(modList) {
        modList.mapNotNull { mod ->
            val meta = ModMetadataUtil.read(ModMetadataUtil.resolveModDir(mod)) ?: return@mapNotNull null
            val locked = meta.dependencies.count { it.hasVersionConstraint }
            if (locked == 0) null else mod to locked
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            TopTitleBar(navController = navController, title = "去除版本限制")
            Box(modifier = Modifier.fillMaxSize()) {
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyLottie(emptyText = "没有需要处理的模组", isShowAdd = false)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(
                            top = 10.dp,
                            start = 10.dp,
                            end = 10.dp,
                            bottom = 125.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(entries) { entry ->
                            val mod = entry.first
                            val lockedCount = entry.second
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable {
                                        val encoded = URLEncoder.encode(
                                            ModMetadataUtil.resolveModDir(mod).absolutePath,
                                            "UTF-8"
                                        )
                                        navController.navigate(
                                            Screen.VERSION_LIMIT_DETAIL.name + "/$encoded"
                                        )
                                    }
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = mod.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(repeatDelayMillis = 1, iterations = Int.MAX_VALUE)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "作者: ${mod.author}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "$lockedCount 个前置模组带有版本要求，点击查看",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「去除版本限制」第二步：列出该模组的依赖，逐条移除版本约束。
 */
@Composable
fun VersionLimitDetailPage(
    navController: NavController,
    modDirPath: String,
) {
    val context = LocalContext.current
    val modDir = remember(modDirPath) { File(modDirPath) }
    // 写文件后 +1 触发重新读取
    var refresh by remember(modDirPath) { mutableIntStateOf(0) }
    val metadata = remember(modDirPath, refresh) { ModMetadataUtil.read(modDir) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            TopTitleBar(navController = navController, title = metadata?.modName ?: "去除版本限制")
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    metadata == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyLottie(emptyText = "未找到 metadata 文件", isShowAdd = false)
                    }

                    metadata.dependencies.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyLottie(emptyText = "该模组没有声明前置模组", isShowAdd = false)
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(
                            top = 10.dp,
                            start = 10.dp,
                            end = 10.dp,
                            bottom = 125.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(metadata.dependencies) { dep ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        RoundedCornerShape(24.dp)
                                    )
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = dep.id,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (dep.hasVersionConstraint)
                                        "版本要求: ${dep.constraints.joinToString(" 且 ")}"
                                    else
                                        "已无版本限制",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                if (dep.hasVersionConstraint) {
                                    Button(
                                        onClick = {
                                            val ok = ModMetadataUtil.stripVersion(modDir, dep)
                                            if (ok) {
                                                refresh += 1
                                                Toast.makeText(
                                                    context,
                                                    "已去除 ${dep.id} 的版本限制",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "修改失败，请检查 metadata 文件",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    ) {
                                        Text("移除版本限制")
                                    }
                                } else {
                                    Text(
                                        text = "保留该前置，但不再校验版本号",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
