package org.love2d.android.script

import java.io.File

/**
 * ClassName GLSLFixerScript
 * Description
 * Create by hjr
 * Date 2025/9/30 13:27
 */
object GLSLFixerScript {
    @JvmStatic
    fun fixShaderFile(inputFile: File, overwriteOriginal: Boolean) {
        val modificationLogs = mutableListOf<String>()  // 局部变量避免并发问题
        val lines = inputFile.readLines()
        val fixedLines = mutableListOf<String>()

        val intPattern = Regex("\\bint\\b")
        val numberPattern = Regex("(?<=[\\s\\(\\+\\-\\*/=,])(-?\\b\\d+\\b)(?!\\.\\d|f)")

        for ((index, originalLine) in lines.withIndex()) {
            val trimmed = originalLine.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
                fixedLines.add(originalLine)
                continue
            }

            // 1. int -> float
            val lineAfterIntFix = originalLine.replace(intPattern, "float")

            // 2. 整数 -> .0
            val fixedLine = fixLine(numberPattern, lineAfterIntFix)

            if (fixedLine != originalLine) {
                modificationLogs.add("第 ${index + 1} 行：\"${originalLine.trim()}\" → \"${fixedLine.trim()}\"")
            }
            fixedLines.add(fixedLine)
        }

        // 插入日志头
        fixedLines.add(0, generateLogHeader(inputFile.name, modificationLogs))

        val outputPath: String

        if (overwriteOriginal) {
            // 直接覆盖
            outputPath = inputFile.absolutePath
            File(outputPath).writeText(fixedLines.joinToString("\n"))
        } else {
            // 先备份一份
            val backupPath = inputFile.absolutePath.replace(Regex("\\.(fs|frag|glsl)$"), "") + "_shaders_bak.fs"
            inputFile.copyTo(File(backupPath), overwrite = true)

            // 然后替换源文件
            outputPath = inputFile.absolutePath
            File(outputPath).writeText(fixedLines.joinToString("\n"))
        }

        println("✅ 修复完成，输出文件： $outputPath")
    }

    @JvmStatic
    fun restoreShaderFile(inputFile: File) {
        val backupPath = inputFile.absolutePath.replace(Regex("\\.(fs|frag|glsl)$"), "") + "_shaders_bak.fs"
        val backupFile = File(backupPath)

        if (!backupFile.exists()) {
            println("⚠️ 没有找到备份文件：$backupPath")
            return
        }

        // 把备份写回源文件
        inputFile.writeText(backupFile.readText())

        // 删除备份
        if (backupFile.delete()) {
            println("✅ 已还原并删除备份文件：$backupPath")
        } else {
            println("⚠️ 还原完成，但删除备份文件失败：$backupPath")
        }
    }

    // inline 优化
    private inline fun fixLine(pattern: Regex, line: String): String {
        return pattern.replace(line) { mr ->
            val num = mr.groupValues[1]
            val start = mr.range.first
            val end = mr.range.last + 1

            val dotBefore = start > 0 && line[start - 1] == '.'
            val dotAfter = end < line.length && line[end] == '.'

            if (dotBefore || dotAfter) num else "$num.0"
        }
    }

    private fun generateLogHeader(fileName: String, modificationLogs: List<String>): String {
        return buildString {
            appendLine("// === GLSL Fix Log ===")
            appendLine("// 修复时间: ${java.util.Date()}")
            appendLine("// 文件来源: $fileName")
            appendLine("// 共修改 ${modificationLogs.size} 行")
            modificationLogs.forEach { appendLine("// $it") }
            appendLine("// =====================")
        }
    }
}
