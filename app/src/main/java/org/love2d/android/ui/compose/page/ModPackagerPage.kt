package org.love2d.android.ui.compose.page

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.love2d.android.room.mod.ModInfo
import org.love2d.android.ui.activity.GameManagerViewModel
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModPackagerPage(
    viewModel: GameManagerViewModel,
    navController: NavController,
    localActivity: Activity
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // 获取当前游戏信息
    val currentGame = viewModel.currentGame.collectAsState().value

    // 状态管理
    // selectedMods 存储的是通过 getModId 获取的唯一标识符
    var selectedMods by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectAll by remember { mutableStateOf(true) }
    var packName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var availableMods by remember { mutableStateOf<List<ModInfo>>(emptyList()) }

    // 搜索与动画状态
    var searchText by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val listState = rememberLazyListState()

    // ---------------------------------------------------------
    // 1. 定义一个获取唯一ID的安全函数 [修复核心]
    // ---------------------------------------------------------
    fun getModId(mod: ModInfo): String {
        // 优先使用 resultName，如果为空，则使用 name 拼上 hashCode 防止 ID 冲突
        return if (!mod.resultName.isNullOrBlank()) {
            mod.resultName
        } else {
            "${mod.name ?: "Unknown"}_${mod.hashCode()}"
        }
    }

    // 处理返回键：如果搜索栏展开，按返回键优先关闭搜索栏
    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        searchText = ""
    }

    // 过滤后的模组列表
    val filteredMods = remember(availableMods, searchText) {
        if (searchText.isBlank()) {
            availableMods
        } else {
            availableMods.filter { mod ->
                (mod.name ?: mod.resultName).contains(searchText, ignoreCase = true) ||
                        mod.description.contains(searchText, ignoreCase = true) ||
                        (mod.version ?: "").contains(searchText, ignoreCase = true)
            }
        }
    }

    // 加载可用的mod列表
    LaunchedEffect(currentGame?.modPath) {
        currentGame?.modPath?.let { modPath ->
            scope.launch {
                try {
                    viewModel.getPathMods(modPath).collect { mods ->
                        val previousSelected = selectedMods
                        availableMods = mods

                        if (previousSelected.isEmpty()) {
                            // 默认全选：使用安全ID
                            selectedMods = mods.map { getModId(it) }.toSet()
                            isSelectAll = true
                        } else {
                            // 保持用户的选择，但更新全选状态
                            val allIds = mods.map { getModId(it) }
                            isSelectAll = mods.isNotEmpty() && selectedMods.containsAll(allIds)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ModPackager", "加载mod列表失败: ${e.message}", e)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("创建整合包", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            // 底部操作栏：仅保留创建按钮
            if (availableMods.isNotEmpty()) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                // 即使按钮置灰，为了双重保险，这里也可以保留空检查
                                if (packName.isBlank() || selectedMods.isEmpty()) return@Button

                                scope.launch {
                                    isCreating = true
                                    // 过滤出选中的模组
                                    val modsToPack = availableMods.filter {
                                        selectedMods.contains(getModId(it))
                                    }

                                    createModPack(
                                        context,
                                        packName,
                                        modsToPack,
                                        selectedMods.toList()
                                    ) { success, message ->
                                        isCreating = false
                                        if (success) {
                                            Log.d("ModPackager", "成功: $message")
                                        }
                                    }
                                }
                            },
                            // --- 关键修改：恢复严格的禁用逻辑 ---
                            // 只有同时满足：1.没在打包 2.有名字 3.有选中模组 时，按钮才变亮
                            enabled = !isCreating && packName.isNotBlank() && selectedMods.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("打包中...")
                            } else {
                                // 根据状态显示不同文字，提示用户还差什么
                                val buttonText = when {
                                    packName.isBlank() -> "请输入整合包名称"
                                    selectedMods.isEmpty() -> "请至少选择一个模组"
                                    else -> "生成并分享整合包"
                                }
                                Text(buttonText)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // 内容区域
        if (availableMods.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize()
            ) {
                // --- 区域 1: 基础信息 ---
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 24.dp)
                    ) {
                        SectionTitle(title = "基础信息")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = packName,
                            onValueChange = { packName = it },
                            label = { Text("整合包名称") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("例如: 我的整合包 v1.0") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                // --- 区域 2: 模组选择 (头部吸顶) ---
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // 标题栏 <-> 搜索框 切换动画
                            AnimatedContent(
                                targetState = isSearchExpanded,
                                transitionSpec = {
                                    if (targetState) {
                                        // 展开：从右向左
                                        (slideInHorizontally { it } + fadeIn()).togetherWith(
                                            slideOutHorizontally { -it } + fadeOut())
                                    } else {
                                        // 收起：从左向右
                                        (slideInHorizontally { -it } + fadeIn()).togetherWith(
                                            slideOutHorizontally { it } + fadeOut())
                                    }
                                },
                                label = "search_bar_transition"
                            ) { expanded ->
                                if (expanded) {
                                    // === 搜索框状态 ===
                                    LaunchedEffect(Unit) {
                                        focusRequester.requestFocus()
                                    }

                                    OutlinedTextField(
                                        value = searchText,
                                        onValueChange = { searchText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .focusRequester(focusRequester),
                                        placeholder = { Text("搜索模组...") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = null)
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                searchText = ""
                                                isSearchExpanded = false
                                            }) {
                                                Icon(Icons.Default.Clear, contentDescription = "关闭搜索")
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(28.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.Transparent
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                                    )
                                } else {
                                    // === 标题栏状态 ===
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp), // 高度对齐
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SectionTitle(title = "模组选择")

                                        Spacer(modifier = Modifier.weight(1f))

                                        FilledTonalIconButton(
                                            onClick = { isSearchExpanded = true },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "搜索",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // 全选控制行 [修复：使用安全ID逻辑]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        // 获取当前过滤后的所有唯一ID
                                        val currentIds = filteredMods.map { getModId(it) }
                                        val isAllSelected = selectedMods.containsAll(currentIds)

                                        // 切换全选状态
                                        isSelectAll = !isAllSelected
                                        selectedMods = if (!isAllSelected) {
                                            // 全选：在原有基础上加上当前过滤列表的所有ID
                                            selectedMods + currentIds
                                        } else {
                                            // 取消全选：移除当前过滤列表的ID
                                            selectedMods - currentIds.toSet()
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 实时计算全选状态
                                val currentIds = filteredMods.map { getModId(it) }
                                val isActuallyAllSelected = currentIds.isNotEmpty() && selectedMods.containsAll(currentIds)

                                Checkbox(
                                    checked = isActuallyAllSelected,
                                    onCheckedChange = null, // 点击由 Row 处理
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (isActuallyAllSelected) "取消全选" else "全选所有",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = if (searchText.isNotEmpty()) "找到 ${filteredMods.size} 个结果" else "共 ${filteredMods.size} 个",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }

                // --- 区域 3: 模组列表 ---
                items(
                    items = filteredMods,
                    // [修复：使用 getModId 作为 Key]
                    key = { getModId(it) }
                ) { mod ->
                    val modId = getModId(mod)
                    val isSelected = selectedMods.contains(modId)

                    ModSelectionItem(
                        mod = mod,
                        isSelected = isSelected,
                        onToggle = {
                            // [修复：使用唯一ID进行切换]
                            selectedMods = if (isSelected) {
                                selectedMods - modId
                            } else {
                                selectedMods + modId
                            }
                            // 更新全选标识
                            val currentIds = filteredMods.map { getModId(it) }
                            isSelectAll = currentIds.isNotEmpty() && selectedMods.containsAll(currentIds)
                        }
                    )
                }

                // 底部留白
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// --- 辅助组件 ---

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
}

@Composable
fun ModSelectionItem(
    mod: ModInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                else
                    Color.Transparent
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle) // 确保 Row 可点击
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null, // Checkbox 不处理点击，交给 Row
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mod.name ?: mod.resultName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!mod.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = mod.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!mod.version.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "v${mod.version}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = "暂无可用的模组",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- 后端逻辑 ---

private suspend fun createModPack(
    context: android.content.Context,
    packName: String,
    mods: List<ModInfo>,
    selectedModNames: List<String>,
    onResult: (Boolean, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "mod_pack_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            val packDir = File(tempDir, packName)
            packDir.mkdirs()

            mods.forEach { mod ->
                // 这里我们传递的是对象，业务逻辑可能依赖 resultName 或 path
                Log.d("ModPackager", "正在处理mod: ${mod.name}")
                // TODO: 实际的文件复制逻辑，根据 mod 的 path 属性复制到 packDir
            }

            val zipFile = File(context.cacheDir, "${packName}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.zip")
            createZipFromDirectory(packDir, zipFile)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "模组整合包: $packName")
                putExtra(Intent.EXTRA_TEXT, "分享模组整合包: $packName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "分享整合包"))
            tempDir.deleteRecursively()
            onResult(true, "整合包创建并分享成功")

        } catch (e: Exception) {
            Log.e("ModPackager", "创建整合包失败: ${e.message}", e)
            onResult(false, "创建失败: ${e.message}")
        }
    }
}

private fun File.deleteRecursively(): Boolean {
    return try {
        if (this.isDirectory) {
            this.listFiles()?.forEach { it.deleteRecursively() }
        }
        this.delete()
    } catch (e: Exception) {
        false
    }
}

private fun createZipFromDirectory(sourceDir: File, zipFile: File) {
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
        sourceDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val entry = ZipEntry(file.relativeTo(sourceDir).path)
                entry.time = file.lastModified()
                zipOut.putNextEntry(entry)

                file.inputStream().use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
    }
}