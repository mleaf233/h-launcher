package org.love2d.android.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 负责处理与 Android 文件选择器相关的交互逻辑
 */
object FilePickerHelper {

    const val REQUEST_GAME_CODE_FILE = 1002
    const val REQUEST_MOD_CODE_FILE = 10025

    /**
     * 打开系统文件选择器
     * @param context Activity 上下文
     * @param requestCode 请求码
     */
    fun openFilePicker(context: Activity, requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        context.startActivityForResult(intent, requestCode)
    }

    /**
     * 处理 .love 文件的选择结果
     * @param context Context
     * @param data onActivityResult 返回的 Intent
     * @param onFileSelected 验证成功后的回调
     * @return Boolean 是否成功处理
     */
    fun handleLoveFileResult(context: Context, data: Intent?, onFileSelected: (Uri) -> Unit): Boolean {
        val uri = data?.data
        if (uri == null) {
            Toast.makeText(context, "没有选择文件", Toast.LENGTH_SHORT).show()
            return false
        }

        return if (UriUtil.isLoveFile(context, uri)) {
            onFileSelected(uri)
            true
        } else {
            Toast.makeText(context, "不是 .love 文件", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * 处理 .zip 文件的选择结果
     * @param context Context
     * @param data onActivityResult 返回的 Intent
     * @param onFileSelected 验证成功后的回调
     * @return Boolean 是否成功处理
     */
    fun handleZipFileResult(context: Context, data: Intent?, onFileSelected: (Uri) -> Unit): Boolean {
        val uri = data?.data
        if (uri == null) {
            Toast.makeText(context, "没有选择文件", Toast.LENGTH_SHORT).show()
            return false
        }

        return if (UriUtil.isZipFile(context, uri)) {
            onFileSelected(uri)
            true
        } else {
            Toast.makeText(context, "不是 .zip 文件", Toast.LENGTH_SHORT).show()
            false
        }
    }
}