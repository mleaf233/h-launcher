package org.love2d.android.ui.compose.page

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.love2d.android.ui.activity.DirectInstallState
import org.love2d.android.ui.activity.GameManagerViewModel
import org.love2d.android.ui.compose.ConfirmDialog
import org.love2d.android.ui.compose.TopTitleBar

@Composable
fun ModDirectInstallPage(
    navController: NavController,
    viewModel: GameManagerViewModel,
) {
    val context = LocalContext.current
    val gameInfo = viewModel.currentGame.value
    val directInstallState by viewModel.directInstallState.collectAsState()
    var inputUrl by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetDirectInstallState()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopTitleBar(navController = navController, title = "链接安装")
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "当前游戏",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = gameInfo?.name ?: "未选择游戏",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("模组下载链接") },
                            placeholder = { Text("https://example.com/mod.zip") },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = "链接")
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val clipboardText = readClipboardText(context)
                                        if (clipboardText.isNullOrBlank()) {
                                            Toast.makeText(context, "剪贴板中没有可用文本", Toast.LENGTH_SHORT).show()
                                        } else {
                                            inputUrl = clipboardText
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "仅支持可直接下载 ZIP 模组包的链接。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.requestInstallModFromUrl(inputUrl, context)
                            },
                            enabled = gameInfo != null &&
                                directInstallState != DirectInstallState.Downloading &&
                                directInstallState != DirectInstallState.Installing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "开始安装")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始安装")
                        }
                    }
                }

                item {
                    DirectInstallStatusCard(state = directInstallState)
                }
            }
        }
    }

    when (val state = directInstallState) {
        is DirectInstallState.ConfirmReplace -> {
            ConfirmDialog(
                title = "覆盖安装",
                message = "检测到已安装模组“${state.existingModName}”，继续会覆盖原内容。",
                confirmText = "继续安装",
                onConfirm = {
                    viewModel.confirmInstallModFromUrl(state.url, context)
                },
                onDismiss = {
                    viewModel.resetDirectInstallState()
                }
            )
        }

        is DirectInstallState.Success -> {
            LaunchedEffect(state.modName) {
                Toast.makeText(context, "模组安装成功：${state.modName}", Toast.LENGTH_SHORT).show()
                viewModel.resetDirectInstallState()
            }
        }

        is DirectInstallState.Error -> {
            LaunchedEffect(state.message) {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetDirectInstallState()
            }
        }

        else -> Unit
    }
}

@Composable
private fun DirectInstallStatusCard(state: DirectInstallState) {
    val (title, description) = when (state) {
        DirectInstallState.Idle -> "等待安装" to "输入下载链接后即可开始安装。"
        DirectInstallState.Downloading -> "正在下载" to "正在获取模组压缩包。"
        DirectInstallState.Installing -> "正在安装" to "压缩包已下载完成，正在解压并写入模组目录。"
        is DirectInstallState.ConfirmReplace -> "等待确认" to "检测到同名模组，确认后会覆盖安装。"
        is DirectInstallState.Success -> "安装成功" to "已完成安装。"
        is DirectInstallState.Error -> "安装失败" to state.message
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state == DirectInstallState.Downloading || state == DirectInstallState.Installing) {
                CircularProgressIndicator(modifier = Modifier.width(20.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun readClipboardText(context: Context): String? {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    if (!clipboardManager.hasPrimaryClip()) return null
    val description = clipboardManager.primaryClipDescription ?: return null
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
        !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
    ) {
        return null
    }
    return clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}
