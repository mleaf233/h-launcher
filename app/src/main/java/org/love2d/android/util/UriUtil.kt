package org.love2d.android.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

/**
 * Uri 相关的工具类
 */
object UriUtil {

    /**
     * 从 Uri 获取文件名
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }

    /**
     * 检查 Uri 指向的是否是 .love 文件
     */
    fun isLoveFile(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        val fileName = getFileNameFromUri(context, uri)
        Log.d("UriUtil", "Selected file: $fileName")
        return fileName?.endsWith(".love", ignoreCase = true) == true
    }

    /**
     * 检查 Uri 指向的是否是 .zip 文件
     */
    fun isZipFile(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        val fileName = getFileNameFromUri(context, uri)
        Log.d("UriUtil", "Selected file: $fileName")
        return fileName?.endsWith(".zip", ignoreCase = true) == true
    }
}