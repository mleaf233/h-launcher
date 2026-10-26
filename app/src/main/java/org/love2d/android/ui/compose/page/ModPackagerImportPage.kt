package org.love2d.android.ui.compose.page

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import org.love2d.android.ui.activity.GameManagerViewModel
import org.love2d.android.room.mod.ModInfo
import org.love2d.android.util.ModDbUtil
import org.love2d.android.util.UriUtil
import java.io.File

/**
 * 整合包导入页面
 * 功能：
 * 1. 选择zip文件
 * 2. 解压并显示包内所有文件（文件夹结构）
 * 3. 允许用户选择要安装的文件/文件夹
 * 4. 执行安装操作
 */

// 文件树节点数据类
data class PackageFileNode(
    val path: String, // zip内的路径
    val name: String, // 文件/文件夹名
    val isDirectory: Boolean, // 是否是文件夹
    val size: Long, // 文件大小
    val children: List<PackageFileNode> = emptyList(), // 子节点
    val level: Int = 0, // 层级深度
    val parentPath: String = "", // 父路径
    val isSelected: Boolean = false, // 是否被选中
) {
    val fullPath: String get() = if (parentPath.isEmpty()) name else "$parentPath/$name"
    val displayName: String get() = if (isDirectory) "$name/" else name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModPackagerImportPage(
    viewModel: GameManagerViewModel,
    navController: NavController,
    localActivity: Activity,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 获取当前游戏信息
    val currentGame = viewModel.currentGame.collectAsState().value

    // 创建统一的临时目录
    val controlTempDir = remember {
        val dir = File(context.cacheDir, "control_temp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir.absolutePath
    }

    // 状态管理
    var selectedModFile by remember { mutableStateOf<Uri?>(null) }
    var fileTreeNodes by remember { mutableStateOf<List<PackageFileNode>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectAll by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var tempExtractDir by remember { mutableStateOf<String>("") }

    // 文件选择器启动器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedModFile = it
        }
    }

    // 加载包内文件
    LaunchedEffect(selectedModFile) {
        selectedModFile?.let {
            isLoading = true
            loadFilesFromPackage(context, it, controlTempDir) { (nodes, extractDir) ->
                fileTreeNodes = nodes
                tempExtractDir = extractDir
                // 默认全选所有文件
                selectedFiles = nodes.map { it.path }.toSet()
                isSelectAll = true
                isLoading = false
            }
        }
    }

    // 页面退出时清理所有临时文件
    DisposableEffect(Unit) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                try {
                    val controlDir = File(controlTempDir)
                    if (controlDir.exists()) {
                        Log.d("ModPackagerImport", "开始清理control_temp目录: ${controlDir.absolutePath}")

                        // 递归删除control_temp目录下的所有内容
                        controlDir.walkTopDown().forEach { file ->
                            if (file.absolutePath != controlDir.absolutePath) {
                                val deleted = file.deleteRecursively()
                                Log.d("ModPackagerImport", "删除文件: ${file.absolutePath}, 成功: $deleted")
                            }
                        }

                        // 重新创建空的control_temp目录
                        controlDir.mkdirs()
                        Log.d("ModPackagerImport", "清理完成，重新创建control_temp目录")
                    }
                } catch (e: Exception) {
                    Log.e("ModPackagerImport", "清理临时文件失败: ${e.message}", e)
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("导入整合包") }, navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }) { innerPadding ->
            if (selectedModFile == null) {
                // 文件选择状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "选择整合包文件",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                filePickerLauncher.launch("application/zip")
                            }) {
                            Text("选择文件")
                        }
                    }
                }
            } else {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding), contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(bottom = 80.dp), // 为悬浮按钮留出空间
                            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 文件信息
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = "已选择文件",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = selectedModFile?.lastPathSegment ?: "未知文件",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 全选控制和数量统计（合并到一行）
                            if (fileTreeNodes.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    isSelectAll = !isSelectAll
                                                    selectedFiles = if (isSelectAll) {
                                                        fileTreeNodes.map { it.path }.toSet()
                                                    } else {
                                                        emptySet()
                                                    }
                                                }
                                                .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "全选",
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Text(
                                                text = "${selectedFiles.size}/${fileTreeNodes.size} 个文件",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            Checkbox(
                                                checked = isSelectAll && selectedFiles.size == fileTreeNodes.size,
                                                onCheckedChange = { checked ->
                                                    isSelectAll = checked
                                                    selectedFiles = if (checked) {
                                                        fileTreeNodes.map { it.path }.toSet()
                                                    } else {
                                                        emptySet()
                                                    }
                                                })
                                        }
                                    }
                                }
                            }

                            // 文件列表
                            items(fileTreeNodes) { node ->
                                val isSelected = selectedFiles.contains(node.path)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFiles = if (isSelected) {
                                                selectedFiles - node.path
                                            } else {
                                                selectedFiles + node.path
                                            }
                                            // 更新全选状态
                                            isSelectAll = selectedFiles.size == fileTreeNodes.size
                                        }, colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected, onCheckedChange = { checked ->
                                                selectedFiles = if (checked) {
                                                    selectedFiles + node.path
                                                } else {
                                                    selectedFiles - node.path
                                                }
                                                // 更新全选状态
                                                isSelectAll = selectedFiles.size == fileTreeNodes.size
                                            })

                                        Spacer(modifier = Modifier.width(16.dp))

                                        // 文件/文件夹图标
                                        Icon(
                                            imageVector = if (node.isDirectory) Icons.Default.FolderOpen
                                            else Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (node.isDirectory) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
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
                                                    text = "大小: ${formatFileSize(node.size)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 如果没有文件，显示提示
                            if (fileTreeNodes.isEmpty()) {
                                item {
                                    Text(
                                        text = "该整合包中没有找到文件",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 32.dp)
                                    )
                                }
                            }
                        }

                        // 底部导入按钮
                        if (selectedFiles.isNotEmpty()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isImporting = true
                                        importSelectedFiles(
                                            context,
                                            selectedModFile!!,
                                            currentGame?.modPath.orEmpty(),
                                            selectedFiles.toList(),
                                            tempExtractDir,
                                            currentGame?.id.orEmpty(),
                                            currentGame?.name.orEmpty()
                                        ) { success, message ->
                                            Log.d("ModPackagerImport", "导入回调被调用，success: $success, message: $message")
                                            isImporting = false
                                            if (success) {
                                                Log.d("ModPackagerImport", "导入成功，准备退出页面")
                                                // 导入成功，直接关闭页面（清理由DisposableEffect处理）
                                                navController.popBackStack()
                                                Log.d("ModPackagerImport", "已调用navController.popBackStack()")
                                            } else {
                                                Log.d("ModPackagerImport", "导入失败: $message")
                                            }
                                        }
                                    }
                                },
                                enabled = !isImporting,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            ) {
                                if (isImporting) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text("导入中...")
                                    }
                                } else {
                                    Text("导入选中的文件")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 加载整合包中的所有文件
private suspend fun loadFilesFromPackage(
    context: android.content.Context,
    uri: Uri,
    controlTempDir: String,
    onResult: (Pair<List<PackageFileNode>, String>) -> Unit,
) {
    withContext(Dispatchers.IO) {
        try {
            Log.d("ModPackagerImport", "开始处理整合包，URI: $uri")
            Log.d("ModPackagerImport", "使用临时目录: $controlTempDir")

            // 检查control_temp目录是否存在
            val controlDir = File(controlTempDir)
            if (!controlDir.exists()) {
                controlDir.mkdirs()
                Log.d("ModPackagerImport", "创建control_temp目录: ${controlDir.absolutePath}")
            }

            // 将URI复制到临时文件
            val tempZipFile = File(controlTempDir, "temp_package_import_${System.currentTimeMillis()}.zip")

            var bytesCopied = 0L
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempZipFile.outputStream().use { outputStream ->
                    bytesCopied = inputStream.copyTo(outputStream)
                }
            } ?: throw Exception("无法读取输入流")

            Log.d("ModPackagerImport", "ZIP文件复制完成，大小: $bytesCopied 字节")
            Log.d("ModPackagerImport", "临时ZIP文件路径: ${tempZipFile.absolutePath}")
            Log.d("ModPackagerImport", "ZIP文件是否存在: ${tempZipFile.exists()}")

            // 创建临时解压目录
            val tempExtractDir = File(controlTempDir, "temp_extract_${System.currentTimeMillis()}")
            tempExtractDir.mkdirs()
            Log.d("ModPackagerImport", "创建解压目录: ${tempExtractDir.absolutePath}")

            try {
                // 先检查ZIP文件内容
                val zipFile = ZipFile(tempZipFile)
                val fileHeaders = zipFile.fileHeaders
                Log.d("ModPackagerImport", "ZIP包含文件数量: ${fileHeaders.size}")

                if (fileHeaders.isEmpty()) {
                    Log.w("ModPackagerImport", "ZIP文件为空或损坏")
                    onResult(Pair(emptyList(), ""))
                    return@withContext
                }

                fileHeaders.take(5).forEach { header ->
                    Log.d("ModPackagerImport", "ZIP文件头: ${header.fileName}, 大小: ${header.uncompressedSize}, 是否目录: ${header.isDirectory}")
                }

                // 完整解压整合包到临时目录
                Log.d("ModPackagerImport", "开始解压整个整合包...")
                zipFile.extractAll(tempExtractDir.absolutePath)
                Log.d("ModPackagerImport", "整合包解压完成")

                // 检查解压后的文件
                val extractedFiles = tempExtractDir.listFiles()
                Log.d("ModPackagerImport", "解压后的文件数量: ${extractedFiles?.size ?: 0}")

                if (extractedFiles?.isEmpty() == true) {
                    Log.w("ModPackagerImport", "解压后没有找到任何文件")
                    // 递归检查子目录
                    val allFiles = tempExtractDir.walkTopDown().toList()
                    Log.d("ModPackagerImport", "递归查找找到文件数量: ${allFiles.size}")
                    allFiles.take(10).forEach { file ->
                        Log.d("ModPackagerImport", "递归文件: ${file.absolutePath} (${if (file.isDirectory) "目录" else "文件"})")
                    }
                }

                extractedFiles?.forEach { file: File ->
                    Log.d("ModPackagerImport", "解压文件: ${file.name} (${if (file.isDirectory) "目录" else "文件"}, 大小: ${file.length()})")
                }

            } catch (e: Exception) {
                Log.e("ModPackagerImport", "解压过程中出现错误: ${e.message}", e)
                throw e
            }

            // 删除临时ZIP文件
            if (tempZipFile.exists()) {
                tempZipFile.delete()
                Log.d("ModPackagerImport", "删除临时ZIP文件")
            }

            // 只获取最外层的文件和文件夹
            val topLevelItems = getTopLevelFiles(tempExtractDir)
            Log.d("ModPackagerImport", "获取到顶级文件数量: ${topLevelItems.size}")

            if (topLevelItems.isEmpty()) {
                Log.w("ModPackagerImport", "没有获取到任何顶级文件，尝试深度搜索")
                // 深度搜索，找到所有文件和文件夹
                val deepSearchItems = mutableListOf<PackageFileNode>()
                tempExtractDir.walkTopDown().maxDepth(1).forEach { file ->
                    if (file.absolutePath != tempExtractDir.absolutePath) { // 排除根目录本身
                        val relativePath = file.relativeTo(tempExtractDir).path
                        val fileNode = PackageFileNode(
                            path = relativePath,
                            name = file.name,
                            isDirectory = file.isDirectory,
                            size = if (file.isFile) file.length() else 0L,
                            level = 0,
                            parentPath = "",
                            isSelected = false
                        )
                        deepSearchItems.add(fileNode)
                        Log.d("ModPackagerImport", "深度搜索找到: $relativePath")
                    }
                }
                Log.d("ModPackagerImport", "深度搜索找到文件数量: ${deepSearchItems.size}")
                onResult(Pair(deepSearchItems, tempExtractDir.absolutePath))
            } else {
                onResult(Pair(topLevelItems, tempExtractDir.absolutePath))
            }

        } catch (e: ZipException) {
            Log.e("ModPackagerImport", "ZIP读取失败: ${e.message}", e)
            onResult(Pair(emptyList(), ""))
        } catch (e: Exception) {
            Log.e("ModPackagerImport", "加载整合包失败: ${e.message}", e)
            e.printStackTrace()
            onResult(Pair(emptyList(), ""))
        }
    }
}

// 获取最外层的文件和文件夹
private fun getTopLevelFiles(dir: File): List<PackageFileNode> {
    val items = mutableListOf<PackageFileNode>()

    Log.d("ModPackagerImport", "getTopLevelFiles: 检查目录 ${dir.absolutePath}")
    Log.d("ModPackagerImport", "getTopLevelFiles: 目录是否存在 ${dir.exists()}")

    if (!dir.exists()) {
        Log.w("ModPackagerImport", "getTopLevelFiles: 目录不存在")
        return items
    }

    val files = dir.listFiles()
    Log.d("ModPackagerImport", "getTopLevelFiles: listFiles返回: ${files?.size ?: null}")

    if (files == null) {
        Log.w("ModPackagerImport", "getTopLevelFiles: listFiles返回null，可能是权限问题")
        return items
    }

    if (files.isEmpty()) {
        Log.w("ModPackagerImport", "getTopLevelFiles: 目录为空")
        return items
    }

    files.forEach { file ->
        Log.d("ModPackagerImport", "getTopLevelFiles: 处理文件 ${file.name} (${if (file.isDirectory) "目录" else "文件"})")
        val fileNode = PackageFileNode(
            path = file.name,
            name = file.name,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0L,
            level = 0,
            parentPath = "",
            isSelected = false
        )
        items.add(fileNode)
    }

    // 按名称排序，文件夹在前
    val sortedItems = items.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    Log.d("ModPackagerImport", "getTopLevelFiles: 处理完成，返回 ${sortedItems.size} 个项目")

    return sortedItems
}

// 从已解压的目录构建文件树
private fun buildFileTreeFromDirectory(dir: File, relativePath: String): PackageFileNode {
    val children = mutableListOf<PackageFileNode>()

    if (!dir.exists()) {
        return PackageFileNode(relativePath, File(relativePath).name, false, 0L, children)
    }

    dir.listFiles()?.forEach { file ->
        val childRelativePath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

        if (file.isDirectory) {
            // 递归构建子目录
            val childNode = buildFileTreeFromDirectory(file, childRelativePath)
            children.add(childNode)
        } else {
            // 添加文件节点
            val fileNode = PackageFileNode(
                path = childRelativePath,
                name = file.name,
                isDirectory = false,
                size = file.length(),
                level = childRelativePath.split("/").size - 1,
                parentPath = relativePath,
                isSelected = false
            )
            children.add(fileNode)
        }
    }

    return PackageFileNode(
        path = relativePath,
        name = File(relativePath).name.ifEmpty { "根目录" },
        isDirectory = true,
        size = 0L,
        children = children,
        level = relativePath.split("/").size - 1,
        parentPath = "",
        isSelected = false
    )
}

// 构建文件树结构（保留原有函数以备兼容）
private fun buildFileTree(fileHeaders: List<FileHeader>): PackageFileNode {
    val pathMap = mutableMapOf<String, PackageFileNode>()
    val rootChildren = mutableListOf<PackageFileNode>()

    // 按路径长度排序，确保父文件夹先创建
    val sortedHeaders = fileHeaders.sortedBy { it.fileName.split("/").size }

    sortedHeaders.forEach { fileHeader ->
        val fullPath = fileHeader.fileName.removeSuffix("/") // 移除末尾的斜杠
        val pathParts = fullPath.split("/").filter { it.isNotEmpty() }

        if (pathParts.isEmpty()) return@forEach

        var parentPath = ""

        // 创建路径上的所有文件夹
        for (i in pathParts.indices) {
            val currentPath = pathParts.subList(0, i + 1).joinToString("/")
            val isDirectory = i < pathParts.size - 1 || fileHeader.isDirectory

            if (!pathMap.containsKey(currentPath)) {
                val nodeName = pathParts[i]
                val parentNodePath = if (i > 0) {
                    pathParts.subList(0, i).joinToString("/")
                } else {
                    ""
                }

                val node = PackageFileNode(
                    path = currentPath,
                    name = nodeName,
                    isDirectory = isDirectory,
                    size = if (!isDirectory && i == pathParts.size - 1) fileHeader.uncompressedSize else 0L,
                    level = i,
                    parentPath = parentNodePath
                )

                pathMap[currentPath] = node

                // 添加到父节点的子节点或根节点
                if (parentNodePath.isEmpty()) {
                    rootChildren.add(node)
                } else {
                    val parentNode = pathMap[parentNodePath]
                    if (parentNode != null) {
                        val updatedChildren = parentNode.children.toMutableList()
                        updatedChildren.add(node)
                        // 由于data class是不可变的，我们需要重新创建节点
                        pathMap[parentNodePath] = parentNode.copy(children = updatedChildren)
                    }
                }
            } else {
                // 如果节点已存在但这是文件，更新大小
                val existingNode = pathMap[currentPath]!!
                if (!existingNode.isDirectory && i == pathParts.size - 1) {
                    pathMap[currentPath] = existingNode.copy(
                        size = fileHeader.uncompressedSize
                    )
                }
            }
        }
    }

    return PackageFileNode("", "", true, 0L, children = rootChildren)
}

// 将文件树扁平化为列表
private fun flattenFileTree(nodes: List<PackageFileNode>): List<PackageFileNode> {
    val result = mutableListOf<PackageFileNode>()

    fun addNode(node: PackageFileNode) {
        result.add(node)
        // 如果是文件夹，递归添加其子节点
        node.children.forEach { child ->
            addNode(child)
        }
    }

    nodes.forEach { addNode(it) }
    return result
}

// 获取所有文件路径
private fun getAllFilePaths(nodes: List<PackageFileNode>): List<String> {
    val result = mutableListOf<String>()

    fun collectPaths(node: PackageFileNode) {
        result.add(node.fullPath)
        node.children.forEach { child ->
            collectPaths(child)
        }
    }

    nodes.forEach { collectPaths(it) }
    return result
}

// 导入选中的文件和文件夹
private suspend fun importSelectedFiles(
    context: android.content.Context,
    packageUri: Uri,
    modPath: String,
    selectedFiles: List<String>,
    tempExtractDir: String,
    gameId: String = "",
    gameName: String = "",
    onResult: (Boolean, String) -> Unit,
) {
    withContext(Dispatchers.IO) {
        try {
            val sourceDir = File(tempExtractDir)
            val targetDir = File(modPath)

            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            Log.d("ModPackagerImport", "开始从临时目录复制文件: ${sourceDir.absolutePath} -> ${targetDir.absolutePath}")
            Log.d("ModPackagerImport", "选中文件数量: ${selectedFiles.size}")

            var copiedCount = 0
            val errorCount = mutableListOf<String>()

            selectedFiles.forEach { relativePath ->
                try {
                    val sourceFile = File(sourceDir, relativePath)
                    val destFile = File(targetDir, relativePath)

                    if (sourceFile.exists()) {
                        Log.d("ModPackagerImport", "复制: ${sourceFile.absolutePath} -> ${destFile.absolutePath}")

                        // 原封不动地复制文件或目录
                        if (sourceFile.isDirectory) {
                            // 如果是目录，创建目标目录
                            if (!destFile.exists()) {
                                destFile.mkdirs()
                            }
                            // 递归复制目录内容
                            sourceFile.copyRecursively(destFile, overwrite = true)
                        } else {
                            // 如果是文件，确保父目录存在
                            val parentDir = destFile.parentFile
                            if (parentDir != null && !parentDir.exists()) {
                                parentDir.mkdirs()
                            }
                            // 复制文件
                            sourceFile.copyTo(destFile, overwrite = true)
                        }

                        copiedCount++
                    } else {
                        Log.w("ModPackagerImport", "源文件不存在: ${sourceFile.absolutePath}")
                        errorCount.add(relativePath)
                    }

                } catch (e: Exception) {
                    Log.e("ModPackagerImport", "复制文件失败: $relativePath", e)
                    errorCount.add(relativePath)
                }
            }

            // 识别导入的模组文件夹并插入数据库
            val importedMods = mutableListOf<String>()
            if (errorCount.isEmpty() && copiedCount > 0) {
                try {
                    // 基于选中的文件识别顶级文件夹
                    val topLevelFolders = selectedFiles.mapNotNull { relativePath ->
                        // 获取顶级文件夹名称
                        val pathParts = relativePath.split("/").filter { it.isNotEmpty() }
                        if (pathParts.isNotEmpty()) pathParts.first() else null
                    }.distinct()

                    Log.d("ModPackagerImport", "识别到顶级模组文件夹: ${topLevelFolders.joinToString(", ")}")

                    topLevelFolders.forEach { folderName ->
                        val modFolder = File(targetDir, folderName)
                        if (modFolder.exists() && modFolder.isDirectory) {
                            val modInfo = ModInfo().apply {
                                isLocal = true
                                name = folderName
                                from = UriUtil.getFileNameFromUri(context, packageUri) ?: "整合包导入"
                                installPath = modPath
                                game_id = gameId
                                game_name = gameName
                            }

                            ModDbUtil.insertMod(modInfo)
                            importedMods.add(folderName)
                            Log.d("ModPackagerImport", "成功插入数据库: $folderName")
                        }
                    }

                } catch (e: Exception) {
                    Log.e("ModPackagerImport", "插入数据库时出错: ${e.message}", e)
                    errorCount.add("数据库插入失败")
                }
            }

            val message = if (errorCount.isEmpty()) {
                if (importedMods.isNotEmpty()) {
                    "成功导入 $copiedCount 个文件，安装了 ${importedMods.size} 个模组: ${importedMods.joinToString(", ")}"
                } else {
                    "成功导入 $copiedCount 个文件"
                }
            } else {
                "成功导入 $copiedCount 个文件，失败 ${errorCount.size} 个文件\n失败文件: ${errorCount.joinToString(", ")}"
            }

            Log.d("ModPackagerImport", "导入完成，准备调用回调函数，success: true")
            // 切换到主线程调用回调函数，确保UI操作正常执行
            withContext(Dispatchers.Main) {
                onResult(true, message)
            }

        } catch (e: Exception) {
            Log.e("ModPackagerImport", "导入文件失败: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onResult(false, "导入失败: ${e.message}")
            }
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