package org.love2d.android.ui.compose.page

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.love2d.android.script.GLSLFixerScript
import org.love2d.android.ui.compose.TopTitleBar
import java.io.File

@Composable
fun ShadersFilePage(
    navController: NavController,
    context: Context,
    shadersFolderPath: String,
) {
    val localContext = LocalContext.current
    val backupStates = remember { mutableStateMapOf<File, Boolean>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(shadersFolderPath) {
        // 1. 在这里增加过滤条件，排除备份文件
        val files = File(shadersFolderPath).listFiles()
            ?.filter { it.isFile && !it.name.contains("_shaders_bak.") } // <-- 修改点
            ?: emptyList()

        files.forEach { file ->
            val backupFile =
                File(file.parent, file.nameWithoutExtension + "_shaders_bak." + file.extension)
            backupStates[file] = backupFile.exists()
        }
    }

    val shaderFiles = backupStates.keys.sortedBy { it.name }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            TopTitleBar(navController = navController, title = "着色器文件")

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (shaderFiles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "着色器文件夹为空或不存在")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(shaderFiles) { file ->
                            ShaderFileCard(
                                file = file,
                                backupExists = backupStates[file] ?: false,
                                onFixClick = { clickedFile ->
                                    try {
                                        GLSLFixerScript.fixShaderFile(clickedFile, overwriteOriginal = false)
                                        backupStates[clickedFile] = true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                onRestoreClick = { clickedFile ->
                                    try {
                                        GLSLFixerScript.restoreShaderFile(clickedFile)
                                        backupStates[clickedFile] = false
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )
                        }
                    }

                    // 1. 将 FloatingActionButton 改为 ExtendedFloatingActionButton
                    ExtendedFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                val filesToFix = backupStates.filterValues { !it }.keys
                                Log.e("HJR", "filesToFix: $filesToFix")
                                if (filesToFix.isEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(localContext, "所有文件均已修复", Toast.LENGTH_SHORT).show()
                                    }
                                    return@launch
                                }

                                withContext(Dispatchers.IO) {
                                    filesToFix.forEach { file ->
                                        try {
                                            GLSLFixerScript.fixShaderFile(file, overwriteOriginal = false)
                                            withContext(Dispatchers.Main) {
                                                backupStates[file] = true
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(localContext, "全部修复完成", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        // 2. 分别提供 icon 和 text
                        icon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = "修复全部") },
                        text = { Text(text = "修复全部") }
                    )
                }
            }
        }
    }
}


@Composable
fun ShaderFileCard(
    file: File,
    backupExists: Boolean,
    onFixClick: (File) -> Unit,
    onRestoreClick: (File) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (backupExists) {
                OutlinedButton(onClick = { onRestoreClick(file) }) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = "还原",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text("还原")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Button(onClick = { onFixClick(file) }) {
                Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = "修复",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text("修复")
            }
        }
    }
}