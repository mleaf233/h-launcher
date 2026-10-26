package org.love2d.android.ui.compose.page

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.love2d.android.ui.activity.GameManagerViewModel
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

// 文件树节点数据类
data class FileTreeNode(
    val file: File?,
    val children: List<FileTreeNode> = emptyList(),
    val level: Int = 0,
    val isExpanded: Boolean = false,
    val parentPath: String = ""
) {
    val isDirectory: Boolean get() = file?.isDirectory ?: false
    val name: String get() = file?.name ?: "未知"
    val size: Long get() = if (file?.isFile == true) file.length() else 0L
    val lastModified: Long get() = file?.lastModified() ?: 0L
    val relativePath: String get() = if (parentPath.isEmpty()) name else "$parentPath/$name"
    val exists: Boolean get() = file?.exists() ?: false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveManagerPage(
    viewModel: GameManagerViewModel,
    navController: NavController,
    localActivity: Activity
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileTreeNodes by remember { mutableStateOf<List<FileTreeNode>>(emptyList()) }
    var expandedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    // 使用 derivedStateOf 来优化展开状态计算
    val displayedNodes by remember {
        derivedStateOf {
            flattenFileTree(fileTreeNodes, expandedFolders)
        }
    }

    // 获取当前游戏信息
    val currentGame = viewModel.currentGame.collectAsState().value

    // 文件选择器启动器（用于导入存档）
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                importSaveFile(context, it, currentGame?.name ?: "") { success, message ->
                    if (success) {
                        scope.launch {
                            loadSaveFileTree(context, currentGame?.name ?: "") { nodes ->
                                fileTreeNodes = nodes
                            }
                        }
                    }
                    // 这里可以显示Toast或消息
                    Log.d("SaveManager", message)
                }
            }
        }
    }

    // 加载存档文件树
    LaunchedEffect(currentGame?.name) {
        currentGame?.name?.let { gameName ->
            loadSaveFileTree(context, gameName) { nodes ->
                fileTreeNodes = nodes
                isLoading = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text("${currentGame?.name ?: "未知游戏"} - 存档管理")
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 存档路径信息
                    item {
                        SavePathInfo(gameName = currentGame?.name ?: "")
                    }

                    // 操作按钮
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 导出存档按钮
                            Button(
                                onClick = {
                                    scope.launch {
                                        isExporting = true
                                        exportSaveFiles(
                                            context,
                                            currentGame?.name ?: ""
                                        ) { success, message ->
                                            isExporting = false
                                            Log.d("SaveManager", message)
                                        }
                                    }
                                },
                                enabled = fileTreeNodes.isNotEmpty() && !isExporting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isExporting) "导出中..." else "备份存档")
                            }

                            // 导入存档按钮
                            Button(
                                onClick = {
                                    importFileLauncher.launch("application/zip")
                                },
                                enabled = !isImporting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isImporting) "导入中..." else "导入存档")
                            }
                        }
                    }

                    // 存档文件列表
                    if (fileTreeNodes.isNotEmpty()) {
                        item {
                            Text(
                                text = "存档文件列表",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(
                            items = displayedNodes,
                            key = { (it.file?.absolutePath ?: "null") + it.relativePath } // 确保每个项目都有唯一的key
                        ) { node ->
                            FileTreeItem(
                                node = node,
                                isExpanded = expandedFolders.contains(node.relativePath),
                                onToggleExpand = { path ->
                                    val newExpandedFolders = if (expandedFolders.contains(path)) {
                                        expandedFolders - path
                                    } else {
                                        expandedFolders + path
                                    }
                                    expandedFolders = newExpandedFolders
                                }
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "暂无存档文件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavePathInfo(gameName: String) {
    val context = LocalContext.current
    val saveRootDir = File(context.getExternalFilesDir(null), "save")
    val savePath = "${saveRootDir.absolutePath}/$gameName"

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "存档路径",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = savePath,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FileTreeItem(
    node: FileTreeNode,
    isExpanded: Boolean,
    onToggleExpand: (String) -> Unit
) {
    // 添加防御性检查
    if (node.file == null || !node.exists) {
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = node.isDirectory && node.exists) {
                if (node.isDirectory && node.exists) {
                    onToggleExpand(node.relativePath)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩进显示层级
            if (node.level > 0) {
                Spacer(modifier = Modifier.width((node.level * 20).dp))
            }

            // 文件/文件夹图标
            Icon(
                imageVector = when {
                    node.isDirectory && isExpanded -> Icons.Default.FolderOpen
                    node.isDirectory -> Icons.Default.Folder
                    else -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                tint = if (node.isDirectory)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!node.isDirectory) {
                    Text(
                        text = formatFileSize(node.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatDate(node.lastModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 文件夹展开指示器
            if (node.isDirectory) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .then(
                            if (isExpanded) Modifier.rotate(90f) else Modifier
                        )
                )
            }
        }
    }
}

// 将文件树扁平化为列表，用于 LazyColumn 显示
private fun flattenFileTree(
    nodes: List<FileTreeNode>,
    expandedFolders: Set<String>
): List<FileTreeNode> {
    val result = mutableListOf<FileTreeNode>()

    fun addNode(node: FileTreeNode) {
        result.add(node)

        // 如果是文件夹且已展开，添加其子节点
        if (node.isDirectory && expandedFolders.contains(node.relativePath)) {
            node.children.forEach { child ->
                addNode(child)
            }
        }
    }

    nodes.forEach { addNode(it) }
    return result
}

// 加载存档文件树
private suspend fun loadSaveFileTree(
    context: android.content.Context,
    gameName: String,
    onResult: (List<FileTreeNode>) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val saveRootDir = File(context.getExternalFilesDir(null), "save")
            val saveDir = File(saveRootDir, gameName)
            val nodes = if (saveDir.exists()) {
                buildFileTree(saveDir)
            } else {
                emptyList()
            }
            onResult(nodes)
        } catch (e: Exception) {
            Log.e("SaveManager", "加载存档文件失败: ${e.message}", e)
            onResult(emptyList())
        }
    }
}

// 构建文件树结构
private fun buildFileTree(dir: File, level: Int = 0, parentPath: String = ""): List<FileTreeNode> {
    if (!dir.exists() || !dir.isDirectory) return emptyList()

    return try {
        val files = dir.listFiles()
        if (files == null) {
            Log.w("SaveManager", "无法列出目录内容: ${dir.absolutePath}")
            return emptyList()
        }

        files
            .filter { it != null } // 过滤空值
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })
            .map { file ->
                if (file == null) return@map null
                val currentPath = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"

                try {
                    if (file.isDirectory) {
                        // 递归构建子文件夹
                        val children = buildFileTree(file, level + 1, currentPath)
                        FileTreeNode(
                            file = file,
                            children = children,
                            level = level,
                            isExpanded = false,
                            parentPath = parentPath
                        )
                    } else {
                        // 文件节点
                        FileTreeNode(
                            file = file,
                            children = emptyList(),
                            level = level,
                            isExpanded = false,
                            parentPath = parentPath
                        )
                    }
                } catch (e: Exception) {
                    Log.e("SaveManager", "处理文件时出错: ${file.absolutePath}", e)
                    // 创建一个安全的占位节点
                    FileTreeNode(
                        file = null,
                        children = emptyList(),
                        level = level,
                        isExpanded = false,
                        parentPath = parentPath
                    )
                }
            }
            .filterNotNull() // 过滤掉处理失败的文件
    } catch (e: Exception) {
        Log.e("SaveManager", "构建文件树时出错: ${dir.absolutePath}", e)
        emptyList()
    }
}

// 导出存档文件为ZIP
private suspend fun exportSaveFiles(
    context: android.content.Context,
    gameName: String,
    onResult: (Boolean, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val saveRootDir = File(context.getExternalFilesDir(null), "save")
            val sourceDir = File(saveRootDir, gameName)
            if (!sourceDir.exists()) {
                onResult(false, "存档目录不存在")
                return@withContext
            }

            // 创建临时ZIP文件
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFileName = "${gameName}_存档备份_${timeStamp}.zip"
            val zipFile = File(context.cacheDir, zipFileName)

            // 使用 zip4j 创建ZIP文件
            val zipParameters = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = net.lingala.zip4j.model.enums.CompressionLevel.NORMAL
                isIncludeRootFolder = false
                defaultFolderPath = sourceDir.parentFile?.absolutePath
            }

            val zipFileObj = ZipFile(zipFile)
            zipFileObj.addFolder(sourceDir, zipParameters)

            // 分享ZIP文件
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "游戏存档备份")
                putExtra(Intent.EXTRA_TEXT, "游戏 $gameName 的存档备份")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "分享存档备份"))

            onResult(true, "存档导出成功")

        } catch (e: ZipException) {
            Log.e("SaveManager", "ZIP操作失败: ${e.message}", e)
            onResult(false, "ZIP操作失败: ${e.message}")
        } catch (e: Exception) {
            Log.e("SaveManager", "导出存档失败: ${e.message}", e)
            onResult(false, "导出失败: ${e.message}")
        }
    }
}

// 导入存档文件
private suspend fun importSaveFile(
    context: android.content.Context,
    uri: Uri,
    gameName: String,
    onResult: (Boolean, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val saveRootDir = File(context.getExternalFilesDir(null), "save")
            val targetDir = File(saveRootDir, gameName)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            // 将URI的内容复制到临时文件
            val tempZipFile = File(context.cacheDir, "temp_save_import_${System.currentTimeMillis()}.zip")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempZipFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // 使用 zip4j 解压文件到目标目录
            val zipFile = ZipFile(tempZipFile)
            zipFile.extractAll(targetDir.absolutePath)

            // 删除临时文件
            tempZipFile.delete()

            onResult(true, "存档导入成功")

        } catch (e: ZipException) {
            Log.e("SaveManager", "ZIP解压失败: ${e.message}", e)
            onResult(false, "ZIP解压失败: ${e.message}")
        } catch (e: Exception) {
            Log.e("SaveManager", "导入存档失败: ${e.message}", e)
            onResult(false, "导入失败: ${e.message}")
        }
    }
}

// 格式化文件大小
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

// 格式化日期
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}