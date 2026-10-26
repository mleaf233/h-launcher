package org.love2d.android.net

import org.love2d.android.SettingConfig
import org.love2d.android.util.MMKVHelper

/**
 * ClassName Net
 * Description
 * Create by hjr
 * Date 2025/9/30 09:46
 */
object Net {
    const val DEFAULT_BASE_URL = "https://mod-api-prod.zhki.org"
    const val DEFAULT_SOURCE_LABEL = "内置源"

    fun getEffectiveBaseUrl(): String {
        val customBaseUrl = MMKVHelper.getString(SettingConfig.MOD_STORE_BASE_URL, "")
        return normalizeBaseUrl(customBaseUrl).ifBlank { "$DEFAULT_BASE_URL/" }
    }

    fun getDisplayBaseUrl(baseUrl: String): String {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        return if (normalizedBaseUrl.isBlank() || normalizedBaseUrl == "$DEFAULT_BASE_URL/") {
            DEFAULT_SOURCE_LABEL
        } else {
            normalizedBaseUrl.removeSuffix("/")
        }
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
