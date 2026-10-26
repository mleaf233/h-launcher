package org.love2d.android.util

import android.content.Context
import com.tencent.mmkv.MMKV

object MMKVGetHelper {

    private const val DEFAULT_ID = "default"

    private val defaultMMKV: MMKV
        get() = MMKV.mmkvWithID(DEFAULT_ID, MMKV.MULTI_PROCESS_MODE)

    // 获取
    fun getString(key: String, default: String = ""): String = defaultMMKV.decodeString(key, default) ?: default
    fun getInt(key: String, default: Int = 0): Int = defaultMMKV.decodeInt(key, default)
    fun getBoolean(key: String, default: Boolean = false): Boolean = defaultMMKV.decodeBool(key, default)
    fun getLong(key: String, default: Long = 0L): Long = defaultMMKV.decodeLong(key, default)
    fun getFloat(key: String, default: Float = 0f): Float = defaultMMKV.decodeFloat(key, default)
}
