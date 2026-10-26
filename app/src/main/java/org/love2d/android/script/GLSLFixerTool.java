package org.love2d.android.script;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class GLSLFixerTool {

    /**
     * 每次调用自动清空
     */
    private static final List<String> modificationLogs = new ArrayList<>();

    /**
     * 修复指定 .fs / .frag / .glsl 文件
     */
    public static void fixShaderFile(File inputFile, boolean overwriteOriginal) throws IOException {
        modificationLogs.clear();

        List<String> lines = Files.readAllLines(inputFile.toPath());
        List<String> fixedLines = new ArrayList<>();

        // 正则匹配完整单词 int，避免替换变量名等
        Pattern intPattern = Pattern.compile("\\bint\\b");

        // 修改后的正则，支持负数整数，排除浮点数、已经带点的数字
        Pattern numberPattern = Pattern.compile("(?<=[\\s\\(\\+\\-\\*/=,])(-?\\b\\d+\\b)(?!\\.\\d|f)");

        for (int i = 0; i < lines.size(); i++) {
            String originalLine = lines.get(i);
            String trimmed = originalLine.trim();

            // 跳过注释和预处理指令
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
                fixedLines.add(originalLine);
                continue;
            }

            // 1. 替换 int -> float（单词匹配）
            String lineAfterIntFix = intPattern.matcher(originalLine).replaceAll("float");

            // 2. 替换整数数字加 .0
            String fixedLine = fixLine(numberPattern, lineAfterIntFix);

            if (!fixedLine.equals(originalLine)) {
                modificationLogs.add("第 " + (i + 1) + " 行：\"" + originalLine.trim() + "\" → \"" + fixedLine.trim() + "\"");
            }

            fixedLines.add(fixedLine);
        }

        // 插入日志头
        fixedLines.add(0, generateLogHeader(inputFile.getName()));

        // 写入文件
        String outputPath;
        if (overwriteOriginal) {
            // 直接覆盖原文件
            outputPath = inputFile.getAbsolutePath();
        } else {
            // 生成新文件，带 _fixed 后缀
            outputPath = inputFile.getAbsolutePath().replaceAll("\\.(fs|frag|glsl)$", "") + "_fixed.fs";
        }

        Files.write(Paths.get(outputPath), fixedLines);
        System.out.println("✅ 修复完成，输出文件： " + outputPath);
    }

    /**
     * 修复一行：将整数变为浮点形式（加.0）
     */
    private static String fixLine(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String num = matcher.group(1);

            int start = matcher.start(1);
            int end = matcher.end(1);

            // 数字前后是否有点，避免修改 .123 或 1.
            boolean dotBefore = start > 0 && line.charAt(start - 1) == '.';
            boolean dotAfter = end < line.length() && line.charAt(end) == '.';

            if (dotBefore || dotAfter) {
                matcher.appendReplacement(sb, num);
            } else {
                matcher.appendReplacement(sb, num + ".0");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 生成日志头注释
     */
    private static String generateLogHeader(String fileName) {
        StringBuilder builder = new StringBuilder();
        builder.append("// === GLSL Fix Log ===\n");
        builder.append("// 修复时间: ").append(new Date()).append("\n");
        builder.append("// 文件来源: ").append(fileName).append("\n");
        builder.append("// 共修改 ").append(modificationLogs.size()).append(" 行\n");
        for (String log : modificationLogs) {
            builder.append("// ").append(log).append("\n");
        }
        builder.append("// =====================\n");
        return builder.toString();
    }
}
