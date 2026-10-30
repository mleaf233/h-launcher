package org.love2d.android.util

import android.util.Log
import org.json.JSONObject
import org.love2d.android.room.mod.ModInfo
import java.io.File

/**
 * 读写模组的 metadata JSON，供「去除版本限制」工具使用。
 *
 * Steamodded 的依赖字符串格式是 `"模组名 (>=1.0.0)"`，多个候选之间用 `|` 分隔，
 * 括号里是版本约束（运算符有 `<<` `>>` `<=` `>=` `==`）。
 * 把括号部分去掉后，Steamodded 只检查该模组是否存在，不再比较版本
 * （见 smods/src/preflight/loader.lua 里 dependencies 的解析与校验）。
 *
 * 这里刻意采用「按原文替换字符串字面量」而不是重新序列化 JSON，
 * 这样能保留用户文件里的键顺序、缩进和其余字段的原始写法。
 */
object ModMetadataUtil {

    private const val TAG = "ModMetadataUtil"

    data class Dependency(
        /** 文件里原始的那一条，例如 `Steamodded (>=1.0.0~ALPHA-1423h)` */
        val raw: String,
        /** 去掉版本约束后的模组名，多候选用 `|` 连接，例如 `Steamodded` */
        val id: String,
        /** 括号里的版本约束，例如 `[">=1.0.0~ALPHA-1423h"]` */
        val constraints: List<String>,
    ) {
        val hasVersionConstraint: Boolean get() = constraints.isNotEmpty()
    }

    data class Metadata(
        val file: File,
        val modName: String,
        val dependencies: List<Dependency>,
    )

    /** 从数据库记录定位模组目录 */
    fun resolveModDir(mod: ModInfo): File {
        val folder = if (mod.isLocal) mod.name else mod.resultName
        return File(mod.installPath, folder)
    }

    /**
     * 找模组的 metadata 文件：优先 `metadata.json`，否则在模组根目录里找第一个
     * 含 `dependencies` 的 json（Steamodded 接受任意 json 作为 metadata）。
     */
    fun findMetadataFile(modDir: File): File? {
        if (!modDir.isDirectory) return null

        val preferred = File(modDir, "metadata.json")
        if (preferred.isFile) return preferred

        val candidates = modDir.listFiles { f -> f.isFile && f.name.endsWith(".json", true) }
            ?: return null
        return candidates.firstOrNull { f ->
            runCatching { JSONObject(f.readText()).has("dependencies") }.getOrDefault(false)
        }
    }

    fun read(modDir: File): Metadata? {
        val file = findMetadataFile(modDir) ?: return null
        return try {
            val json = JSONObject(file.readText())
            val array = json.optJSONArray("dependencies")
            val deps = mutableListOf<Dependency>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val raw = array.optString(i, "")
                    if (raw.isNotBlank()) deps.add(parseDependency(raw))
                }
            }
            val name = json.optString("name").ifBlank { modDir.name }
            Metadata(file, name, deps)
        } catch (e: Exception) {
            Log.w(TAG, "读取 metadata 失败: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * 去掉某一条依赖的版本约束，只保留模组名。
     * @return true 表示确实写入了改动
     */
    fun stripVersion(modDir: File, dependency: Dependency): Boolean {
        if (!dependency.hasVersionConstraint) return false
        val file = findMetadataFile(modDir) ?: return false
        val newRaw = stripConstraints(dependency.raw)
        if (newRaw == dependency.raw) return false

        return try {
            var text = file.readText()
            var changed = false
            for (key in listOf("dependencies", "optional_dependencies")) {
                val result = replaceInArray(text, key, dependency.raw, newRaw)
                if (result.second) {
                    text = result.first
                    changed = true
                }
            }
            if (changed) {
                file.writeText(text)
                Log.d(TAG, "已去除版本限制: ${dependency.raw} -> $newRaw (${file.name})")
            }
            changed
        } catch (e: Exception) {
            Log.e(TAG, "写入 metadata 失败: ${file.absolutePath}", e)
            false
        }
    }

    /** `A (>=1)|B (>=2)` -> `A|B` */
    fun stripConstraints(raw: String): String =
        raw.split('|')
            .mapNotNull { part ->
                part.trim().substringBefore('(').trim().ifEmpty { null }
            }
            .joinToString("|")

    private fun parseDependency(raw: String): Dependency {
        val ids = mutableListOf<String>()
        val constraints = mutableListOf<String>()
        for (part in raw.split('|')) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val id = trimmed.substringBefore('(').trim()
            if (id.isNotEmpty()) ids.add(id)
            Regex("\\(([^)]*)\\)").findAll(trimmed).forEach {
                val c = it.groupValues[1].trim()
                if (c.isNotEmpty()) constraints.add(c)
            }
        }
        return Dependency(raw = raw, id = ids.joinToString("|"), constraints = constraints)
    }

    /**
     * 在指定 key 的数组范围内，把 oldRaw 的字面量替换成 newRaw。
     * 只替换该数组内部的字符串，避免误伤文件里别处的同名内容。
     */
    private fun replaceInArray(
        text: String,
        key: String,
        oldRaw: String,
        newRaw: String,
    ): Pair<String, Boolean> {
        val keyToken = "\"$key\""
        val keyIndex = text.indexOf(keyToken)
        if (keyIndex < 0) return text to false

        val colon = text.indexOf(':', keyIndex + keyToken.length)
        if (colon < 0) return text to false

        var i = colon + 1
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '[') return text to false
        val arrayStart = i

        var depth = 0
        var inString = false
        var escaped = false
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
            }
            i++
        }
        if (depth != 0) return text to false
        val arrayEnd = i

        val slice = text.substring(arrayStart, arrayEnd + 1)
        val oldLiteral = "\"${jsonEscape(oldRaw)}\""
        if (!slice.contains(oldLiteral)) return text to false

        val newLiteral = "\"${jsonEscape(newRaw)}\""
        val newSlice = slice.replace(oldLiteral, newLiteral)
        return (text.substring(0, arrayStart) + newSlice + text.substring(arrayEnd + 1)) to true
    }

    private fun jsonEscape(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }
}
