package org.love2d.android.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import org.love2d.android.room.mod.ModInfo
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/**
 * 安全关闭流，忽略异常
 */
private fun BufferedInputStream?.closeQuietly() {
    try {
        this?.close()
    } catch (e: Exception) {
        Log.w("ZipManager", "关闭输入流时发生异常", e)
    }
}

/**
 * 安全关闭流，忽略异常
 */
private fun BufferedOutputStream?.closeQuietly() {
    try {
        this?.close()
    } catch (e: Exception) {
        Log.w("ZipManager", "关闭输出流时发生异常", e)
    }
}

/**
 * 封装所有与 ZIP 文件相关的操作，使用 Zip4j 库实现。
 * 功能包括压缩、解压和 Mod 安装。
 */
object ZipManager {

    // 仅用于 Base64 存档导入的兜底上限，模组安装不再做大小限制
    private const val MAX_FILE_SIZE = 100 * 1024 * 1024L // 100MB

    /**
     * 安全删除文件，并记录日志
     */
    private fun safeDeleteFile(file: File, description: String): Boolean {
        return try {
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d("ZipManager", "成功删除 $description: ${file.absolutePath}")
                } else {
                    Log.w("ZipManager", "删除失败 $description: ${file.absolutePath}")
                }
                deleted
            } else {
                Log.d("ZipManager", "$description 不存在，无需删除: ${file.absolutePath}")
                true
            }
        } catch (e: Exception) {
            Log.e("ZipManager", "删除 $description 时发生异常: ${e.message}", e)
            false
        }
    }

    /**
     * 从 Uri 安装一个 Mod。
     * 由于 zip4j 需要文件句柄，我们会先将 Uri 内容复制到缓存文件中再处理。
     */
    fun installModFromUri(context: Context, modPath: String, zipUri: Uri, gameId: String = "", gameName: String = "") {
        val fileNameWithExt = UriUtil.getFileNameFromUri(context, zipUri) ?: "temp.zip"

        // 验证文件名，防止路径遍历攻击
        if (fileNameWithExt.contains("..") || fileNameWithExt.contains("/") || fileNameWithExt.contains("\\")) {
            Log.e("ZipManager", "不安全的文件名: $fileNameWithExt")
            Toast.makeText(context, "文件名不安全", Toast.LENGTH_SHORT).show()
            return
        }

        // 创建一个临时文件来存放 Uri 的内容。
        // 模组可能有几百 MB，优先用外部缓存目录（容量通常远大于内部 cache）。
        val tempDir = context.externalCacheDir ?: context.cacheDir
        val tempZipFile = File(tempDir, "${System.currentTimeMillis()}_$fileNameWithExt")

        CoroutineScope(Dispatchers.IO).launch {
            var inputStream: BufferedInputStream? = null
            var outputStream: BufferedOutputStream? = null

            try {
                // 1. 将 Uri 的内容复制到临时文件，使用缓冲流提高性能
                inputStream = context.contentResolver.openInputStream(zipUri)?.let {
                    BufferedInputStream(it)
                } ?: run {
                    Log.e("ZipManager", "无法打开输入流: $zipUri")
                    return@launch
                }

                // 不再限制安装包大小：部分模组本身就有几百 MB。
                // 注意不能用 inputStream.available() 判断大小——对 content:// 的 Uri
                // 它只返回"当前可无阻塞读取的字节数"，和真实文件大小无关。
                outputStream = BufferedOutputStream(FileOutputStream(tempZipFile))
                inputStream.copyTo(outputStream)
                outputStream.flush()

                // 2. 调用基于 File 的安装方法
                val resultName = installModFromFile(context, modPath, tempZipFile, isUpdate = false)

                // 3. 安装成功后插入数据库
                if (resultName.isNotBlank()) {
                    val modInfo = ModInfo().apply {
                        isLocal = true
                        name = resultName
                        from = fileNameWithExt
                        installPath = modPath
                        game_id = gameId  // 关联到指定游戏
                        game_name = gameName  // 设置游戏名称
                    }
                    ModDbUtil.insertMod(modInfo)
                }

            } catch (e: Exception) {
                Log.e("ZipManager", "Mod安装失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Mod 安装失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // 4. 安全关闭流和删除临时文件
                inputStream?.closeQuietly()
                outputStream?.closeQuietly()
                safeDeleteFile(tempZipFile, "临时ZIP文件")
            }
        }
    }

    /**
     * 从 Assets 目录安装一个 Mod。
     * 同样，先复制到缓存文件再处理。
     */
    fun installModFromAssets(context: Context, modPath: String, assetZipName: String, gameId: String = "", gameName: String = "") {
        val tempZipFile = File(context.cacheDir, assetZipName)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 复制 Assets 文件
                context.assets.open(assetZipName).use { inputStream ->
                    FileOutputStream(tempZipFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 2. 调用安装
                val resultName = installModFromFile(context, modPath, tempZipFile, isUpdate = false)

                // 3. 插入数据库
                if (resultName.isNotBlank()) {
                    val modInfo = ModInfo().apply {
                        isLocal = true
                        name = resultName
                        from = assetZipName
                        installPath = modPath
                        game_id = gameId  // 关联到指定游戏
                        game_name = gameName  // 设置游戏名称
                    }
                    ModDbUtil.insertMod(modInfo)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                tempZipFile.delete()
            }
        }
    }

    /**
     * 预测 ZIP 安装后的目标目录名，用于安装前冲突检查。
     */
    fun predictInstallResultName(zipFile: File): String? {
        if (!zipFile.exists()) return null

        var zip: ZipFile? = null
        return try {
            zip = ZipFile(zipFile)
            resolveInstallResultName(zip, zipFile)
        } catch (e: Exception) {
            Log.e("ZipManager", "预测安装目录失败: ${e.message}", e)
            null
        } finally {
            try {
                zip?.close()
            } catch (e: Exception) {
                Log.w("ZipManager", "关闭ZipFile时发生异常", e)
            }
        }
    }

    /**
     * 从 File 安装一个 Mod，不插入数据库记录。这是核心实现。
     * @return 返回解压后的根文件夹名，如果操作被跳过或失败，则返回空字符串。
     */
    suspend fun installModFromFile(
        context: Context,
        modPath: String,
        zipFile: File,
        isUpdate: Boolean = false
    ): String {
        if (!zipFile.exists()) return ""

        val zip = ZipFile(zipFile)
        val modDir = File(modPath).apply { mkdirs() }
        val resultName = resolveInstallResultName(zip, zipFile)
        if (!isSafeChildFolderName(resultName)) {
            Log.e("ZipManager", "安装目标目录名无效: $resultName")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "模组安装失败: 无效的模组目录名", Toast.LENGTH_SHORT).show()
            }
            return ""
        }

        val targetFolder = File(modDir, resultName)
        if (!isDirectChildOf(modDir, targetFolder)) {
            Log.e("ZipManager", "安装目标目录越界: ${targetFolder.absolutePath}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "模组安装失败: 目标目录无效", Toast.LENGTH_SHORT).show()
            }
            return ""
        }

        if (!isUpdate && targetFolder.exists()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "已存在同名文件夹：${targetFolder.name}，跳过解压", Toast.LENGTH_SHORT).show()
            }
            return ""
        }

        val tempParent = File(modDir, ".install_tmp").apply { mkdirs() }
        val tempExtractDir = File(tempParent, "extract_${System.currentTimeMillis()}").apply { mkdirs() }

        // 使用 zip4j 解压
        try {
            Log.d("ZipManager", "开始解压 ${zipFile.name} 到临时目录 ${tempExtractDir.absolutePath}")
            zip.extractAll(tempExtractDir.absolutePath)

            val extractedRoot = resolveExtractedRootDirectory(tempExtractDir, resultName)
            if (extractedRoot == null || !extractedRoot.exists() || !extractedRoot.isDirectory) {
                throw IllegalStateException("未找到可安装的模组目录")
            }
            if (!isDirectChildOf(tempExtractDir, extractedRoot)) {
                throw IllegalStateException("临时解压目录结构异常")
            }

            if (isUpdate && targetFolder.exists()) {
                Log.d("ZipManager", "更新操作：删除旧文件夹 ${targetFolder.absolutePath}")
                AppFileUtils.deleteRecursively(targetFolder)
            }

            if (extractedRoot.canonicalPath != targetFolder.canonicalPath) {
                if (targetFolder.exists()) {
                    throw IllegalStateException("目标目录已存在")
                }
                if (!extractedRoot.renameTo(targetFolder)) {
                    throw IllegalStateException("移动安装目录失败")
                }
            }

            Log.d("ZipManager", "解压成功")
            return resultName
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "解压失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return "" // 解压失败
        } finally {
            if (tempExtractDir.exists()) {
                AppFileUtils.deleteRecursively(tempExtractDir)
            }
        }
    }

    /**
     * 将 Base64 字符串解码为 ZIP 文件并解压到特定目录。
     */
    fun decodeBase64AndUnzip(context: Context, base64: String) {
        val outputZip = File(context.cacheDir, "${System.currentTimeMillis()}_wifiSave-LOVE.zip")
        var zipFile: ZipFile? = null

        try {
            // 1. 验证和Base64解码
            if (base64.isBlank()) {
                Log.e("ZipManager", "Base64字符串为空")
                return
            }

            val bytes = Base64.decode(base64, Base64.DEFAULT)

            // 检查解码后的数据大小
            if (bytes.size > MAX_FILE_SIZE) {
                Log.e("ZipManager", "Base64解码后数据过大: ${bytes.size} bytes")
                return
            }

            // 2. 写入临时 ZIP 文件
            outputZip.writeBytes(bytes)
            Log.d("ZipManager", "Base64 解码并写入到: ${outputZip.absolutePath}")

            // 3. 验证ZIP文件有效性
            if (!outputZip.exists() || outputZip.length() == 0L) {
                Log.e("ZipManager", "ZIP文件创建失败或为空")
                return
            }

            // 4. 准备目标目录
            val outputDir = File(context.getExternalFilesDir(null), "save/love/wifi-save-LOVE")
            if (outputDir.exists()) {
                AppFileUtils.deleteRecursively(outputDir)
            }
            outputDir.mkdirs()

            // 5. 使用 zip4j 解压
            zipFile = ZipFile(outputZip)
            zipFile.extractAll(outputDir.absolutePath)
            Log.d("ZipManager", "成功解压到: ${outputDir.absolutePath}")

        } catch (e: IllegalArgumentException) {
            Log.e("ZipManager", "Base64解码失败: ${e.message}")
        } catch (e: IOException) {
            Log.e("ZipManager", "文件操作失败: ${e.message}")
        } catch (e: Exception) {
            Log.e("ZipManager", "解码或解压 Base64 失败: ${e.message}", e)
        } finally {
            // 6. 清理资源
            zipFile?.let {
                try {
                    it.close()
                } catch (e: Exception) {
                    Log.w("ZipManager", "关闭ZipFile时发生异常", e)
                }
            }
            safeDeleteFile(outputZip, "Base64解码临时ZIP文件")
        }
    }

    /**
     * 将指定目录压缩成 ZIP 文件。
     */
    fun zipDirectory(sourceDir: File, outputZip: File) {
        var zipFile: ZipFile? = null

        try {
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                Log.e("ZipManager", "源文件夹不存在或不是一个目录: ${sourceDir.absolutePath}")
                return
            }

            // 检查源目录是否为空
            if (sourceDir.listFiles()?.isEmpty() == true) {
                Log.w("ZipManager", "源文件夹为空: ${sourceDir.absolutePath}")
                return
            }

            Log.d("ZipManager", "开始压缩目录 ${sourceDir.name} 到 ${outputZip.absolutePath}")

            // 如果目标文件已存在，先删除
            if (outputZip.exists()) {
                safeDeleteFile(outputZip, "已存在的ZIP文件")
            }

            // 创建父目录
            outputZip.parentFile?.mkdirs()

            zipFile = ZipFile(outputZip)
            zipFile.addFolder(sourceDir)

            // 验证压缩结果
            if (outputZip.exists() && outputZip.length() > 0) {
                Log.d("ZipManager", "压缩成功，文件大小: ${outputZip.length()} bytes")
            } else {
                Log.e("ZipManager", "压缩失败：输出文件无效")
            }

        } catch (e: SecurityException) {
            Log.e("ZipManager", "安全权限异常: ${e.message}")
        } catch (e: IOException) {
            Log.e("ZipManager", "IO异常: ${e.message}")
        } catch (e: Exception) {
            Log.e("ZipManager", "压缩失败: ${e.message}", e)
        } finally {
            // 清理资源
            zipFile?.let {
                try {
                    it.close()
                } catch (e: Exception) {
                    Log.w("ZipManager", "关闭ZipFile时发生异常", e)
                }
            }
        }
    }

    // --- 私有辅助方法 ---

    private data class ZipStructureInfo(
        val needsNewRootFolder: Boolean,
        val singleRootFolderName: String?
    )

    private fun resolveInstallResultName(zipFile: ZipFile, sourceFile: File): String {
        val fileNameFromUri = sourceFile.nameWithoutExtension
        val zipStructure = analyzeZipStructure(zipFile)
        return if (zipStructure.needsNewRootFolder) {
            fileNameFromUri
        } else {
            zipStructure.singleRootFolderName ?: fileNameFromUri
        }
    }

    /**
     * 分析 ZIP 文件结构，判断解压时是否需要创建新的根目录。
     * @return ZipStructureInfo 包含判断结果和单根目录的名称（如果存在）。
     */
    private fun analyzeZipStructure(zipFile: ZipFile): ZipStructureInfo {
        val fileHeaders: List<FileHeader> = zipFile.fileHeaders
        if (fileHeaders.isEmpty()) {
            return ZipStructureInfo(true, null) // 空zip，创建一个新目录
        }

        // 获取所有顶级条目（文件或文件夹）
        val topLevelEntries = fileHeaders.map {
            it.fileName.substringBefore('/')
        }.distinct()

        return if (topLevelEntries.size > 1) {
            // 根级别有多个文件/文件夹，需要创建新目录
            ZipStructureInfo(true, null)
        } else {
            val singleEntryName = topLevelEntries.first()
            // 检查这唯一的顶级条目是否是文件夹
            val isSingleFolder = fileHeaders.all { it.fileName.startsWith("$singleEntryName/") }
            if (isSingleFolder) {
                // 只有一个顶级文件夹，不需要创建新目录
                ZipStructureInfo(false, singleEntryName)
            } else {
                // 根级别只有一个文件，需要创建新目录
                ZipStructureInfo(true, null)
            }
        }
    }

    private fun resolveExtractedRootDirectory(tempExtractDir: File, resultName: String): File? {
        val children = tempExtractDir.listFiles().orEmpty()
            .filter { !it.name.equals("__MACOSX", ignoreCase = true) }

        if (children.isEmpty()) return null

        if (children.size == 1 && children.first().isDirectory) {
            return children.first()
        }

        val normalizedRoot = File(tempExtractDir, resultName)
        if (!normalizedRoot.exists() && !normalizedRoot.mkdirs()) {
            return null
        }

        children.forEach { child ->
            val target = File(normalizedRoot, child.name)
            if (target.exists()) {
                throw IllegalStateException("模组目录中存在重复文件: ${child.name}")
            }
            if (!child.renameTo(target)) {
                throw IllegalStateException("整理模组目录失败: ${child.name}")
            }
        }
        return normalizedRoot
    }

    private fun isSafeChildFolderName(name: String): Boolean {
        return name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            !name.contains('/') &&
            !name.contains('\\')
    }

    private fun isDirectChildOf(parent: File, child: File): Boolean {
        return try {
            child.canonicalFile.parentFile == parent.canonicalFile
        } catch (e: Exception) {
            Log.w("ZipManager", "校验路径失败: ${child.absolutePath}", e)
            false
        }
    }
}
