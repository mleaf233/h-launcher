package org.love2d.android.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale

/**
 * ClassName PermissionUtil
 * Description
 * Create by hjr
 * Date 2025/6/20 13:49
 */
object PermissionUtil {

    val MANAGE_EXTERNAL_STORAGE = listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
    val LEGACY_EXTERNAL_STORAGE = listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun getStoragePermissions(context: Context, needsLegacyMigration: Boolean): List<String> {
        if (!needsLegacyMigration) {
            return emptyList()
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                MANAGE_EXTERNAL_STORAGE
            } else {
                emptyList()
            }
        } else {
            LEGACY_EXTERNAL_STORAGE
        }
    }

    /**
     * 判断是否为永久拒绝：权限未授权，且 shouldShowRationale == false
     */
    @OptIn(ExperimentalPermissionsApi::class)
    fun isPermanentlyDenied(permissionState: PermissionState): Boolean {
        return !permissionState.status.isGranted &&
                !permissionState.status.shouldShowRationale
    }

    /**
     * 跳转到系统设置 -> 当前 App 的详情页
     */
    fun openAppSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    }
}
