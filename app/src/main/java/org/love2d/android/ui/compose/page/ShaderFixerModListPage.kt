package org.love2d.android.ui.compose.page

import android.app.Activity
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.love2d.android.room.mod.ModInfo
import org.love2d.android.ui.activity.GameManagerViewModel
import org.love2d.android.ui.activity.Screen
import org.love2d.android.ui.compose.EmptyLottie
import org.love2d.android.ui.compose.TopTitleBar
import java.io.File
import java.net.URLEncoder

@Composable
fun ShaderFixerModListPage(
    navController: NavController,
    localActivity: Activity,
    modPath: String,
    viewModel: GameManagerViewModel,
) {
    val modsFlow = remember(modPath) { viewModel.getPathMods(modPath) }
    val modList by modsFlow.collectAsState(initial = emptyList())
    val shaderMods = remember(modList) {
        modList.filter { mod ->
            resolveShadersFolder(mod).isDirectory
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
            TopTitleBar(navController = navController, title = "着色器修复")
            Box(modifier = Modifier.fillMaxSize()) {
                if (shaderMods.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyLottie(emptyText = "没有可修复的模组", isShowAdd = false)
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
                        items(shaderMods) { modInfo ->
                            ShaderFixerModCard(
                                modInfo = modInfo,
                                onClick = { mod ->
                                    val shadersFolder = resolveShadersFolder(mod)
                                    if (shadersFolder.exists() && shadersFolder.isDirectory) {
                                        val encodedPath =
                                            URLEncoder.encode(shadersFolder.absolutePath, "UTF-8")
                                        navController.navigate(Screen.SHADERS_FIXER.name + "/$encodedPath")
                                    } else {
                                        Toast.makeText(
                                            localActivity,
                                            "未找到着色器文件",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShaderFixerModCard(
    modInfo: ModInfo,
    onClick: (ModInfo) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
            .clickable { onClick(modInfo) }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
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
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "作者: ${modInfo.author}",
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
            text = "点击查看该模组的着色器文件",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun resolveShadersFolder(mod: ModInfo): File {
    val fileName = if (mod.isLocal) {
        mod.name
    } else {
        mod.resultName
    }
    val modFolder = File(mod.installPath, fileName)
    return File(File(modFolder, "assets"), "shaders")
}
