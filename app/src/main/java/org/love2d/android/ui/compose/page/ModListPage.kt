package org.love2d.android.ui.compose.page

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.love2d.android.room.mod.ModInfo
import org.love2d.android.ui.activity.GameManagerViewModel
import org.love2d.android.ui.compose.EmptyLottie
import org.love2d.android.util.FilePickerHelper
import org.love2d.android.util.startNetUri

@Composable
fun EnhancedModListPage(
    localActivity: Activity,
    modPath: String,
    viewModel: GameManagerViewModel,
    modDelete: (ModInfo) -> Unit,
    navController: NavController? = null,
) {
    val modsFlow = remember(modPath) { viewModel.getPathMods(modPath) }
    val modList by modsFlow.collectAsState(initial = emptyList())

    // FAB菜单状态
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    // 处理返回键：如果菜单展开，按返回键先关闭菜单
    BackHandler(enabled = isFabMenuExpanded) {
        isFabMenuExpanded = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. 主要内容区域 ---
        if (modList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 假设 EmptyLottie 接受 onClick，如果不接受可以去掉外层的 clickable
                EmptyLottie(emptyText = "暂未添加模组", isShowAdd = false) {}
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    top = 10.dp,
                    start = 10.dp,
                    end = 10.dp,
                    bottom = 125.dp // 为 FAB 留出足够空间
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(modList.size) { index ->
                    EnhancedModCard(
                        modInfo = modList[index], onDelete = modDelete
                    )
                }
            }
        }

        // --- 2. 蒙层 (Scrim) ---
        // 当菜单展开时显示一个半透明黑色背景，点击可关闭菜单
        AnimatedVisibility(
            visible = isFabMenuExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null // 移除点击涟漪
                    ) {
                        isFabMenuExpanded = false
                    }
            )
        }

        // --- 3. 统一的 FAB 菜单逻辑 ---
        // 将菜单逻辑移到最外层，不再依赖 isEmpty 判断来渲染不同的 UI 树
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp), // 稍微增加一点边距，符合 Material 规范
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp) // 按钮之间的间距
        ) {

            // -- 菜单项列表 --
            // 注意：顺序是从下往上的，所以这里写在前面的会显示在上面
            // 我们可以用 Column 的 verticalArrangement 来控制，这里手动排列

            // 1. 分享整合包 (仅在有模组时显示)
            //if (modList.isNotEmpty()) {
            //    FabMenuItemAnimated(
            //        isVisible = isFabMenuExpanded,
            //        text = "分享整合包",
            //        icon = Icons.Default.Share,
            //        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            //        onClick = {
            //            navController?.navigate("mod_packager_share")
            //            isFabMenuExpanded = false
            //        }
            //    )
            //}

            // 2. 添加整合包
            FabMenuItemAnimated(
                isVisible = isFabMenuExpanded,
                text = "添加整合包",
                icon = Icons.Default.Archive,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = {
                    navController?.navigate("mod_packager_import")
                    isFabMenuExpanded = false
                }
            )

            // 3. 添加模组
            FabMenuItemAnimated(
                isVisible = isFabMenuExpanded,
                text = "添加模组",
                icon = Icons.Default.Add,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = {
                    FilePickerHelper.openFilePicker(localActivity, FilePickerHelper.REQUEST_MOD_CODE_FILE)
                    isFabMenuExpanded = false
                }
            )

            // -- 主 FAB 按钮 --
            val rotation by animateFloatAsState(
                targetValue = if (isFabMenuExpanded) 45f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), // 弹簧效果
                label = "fab_rotation"
            )

            FloatingActionButton(
                onClick = { isFabMenuExpanded = !isFabMenuExpanded },
                containerColor = if (isFabMenuExpanded)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isFabMenuExpanded)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (isFabMenuExpanded) "关闭菜单" else "打开菜单",
                    modifier = Modifier.rotate(rotation) // 使用 rotate 修饰符更流畅
                )
            }
        }
    }
}

/**
 * 封装的带动画菜单项
 * 使用 AnimatedVisibility 实现顺滑的 出现+位移 效果
 */
@Composable
private fun FabMenuItemAnimated(
    isVisible: Boolean,
    text: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 } // 从自身高度的一半位置向上滑入
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 }
        ) + fadeOut()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 4.dp) // 微调对齐
        ) {
            // 文字标签 (带阴影的卡片样式)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // 小 FAB 按钮
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(16.dp),
                color = containerColor,
                shadowElevation = 6.dp, // 增加阴影，更有层次感
                modifier = Modifier.size(56.dp) // 标准 FAB 大小，也可以改小一点如 48.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// EnhancedModCard 保持你原来的代码不变，或者按需微调
// 为了完整性，这里保留 Card 的引用，实际代码中不需要再次复制下面的 Card 代码
@Composable
fun EnhancedModCard(
    modInfo: ModInfo,
    onDelete: (ModInfo) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = modInfo.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
                    .basicMarquee(repeatDelayMillis = 1, iterations = Int.MAX_VALUE)
            )
            IconButton(onClick = { onDelete(modInfo) }) {
                Icon(Icons.Default.Delete, contentDescription = "删除模组")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "作者: ${modInfo.author}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetadataItem(
                icon = if (modInfo.isLocal) Icons.Default.Source else Icons.Default.ShoppingBasket, text = "来源: ${modInfo.from}"
            )
            if (!modInfo.isLocal) {
                MetadataItem(
                    icon = Icons.Filled.Link, text = "下载地址: ${modInfo.github_repo_url}"
                ) {
                    context.startNetUri(modInfo.github_repo_url)
                }
            }
            MetadataItem(
                icon = if (modInfo.isLocal) Icons.Default.PhoneAndroid else Icons.Default.Cloud,
                text = if (modInfo.isLocal) "本地安装" else "网络来源"
            )
            MetadataItem(
                icon = Icons.Default.CalendarToday, text = "安装于: ${modInfo.created_at}"
            )
        }
    }
}

// 辅助组件：MetadataItem (假设你之前的代码里有，这里为了不报错补全一个简单的版本)
@Composable
fun MetadataItem(
    icon: ImageVector,
    text: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}