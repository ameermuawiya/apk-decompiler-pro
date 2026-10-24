/*
 * Apk Decompiler Pro
 * Copyright (C) 2026 Ameer Muawiya
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.ameermuawiya.decompilers.activities.explorer.viewer

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/*
 * Provides multi-language syntax highlighting for XML, JSON, Smali, and configuration files.
 */
class AppCodeLanguage(private val fileExtension: String) : EmptyLanguage() {

    private val analyzeManager = CodeAnalyzeManager(fileExtension)

    /*
     * Returns the analysis manager for syntax highlighting.
     */
    override fun getAnalyzeManager(): AnalyzeManager {
        return analyzeManager
    }

    private class CodeAnalyzeManager(private val ext: String) : SimpleAnalyzeManager<Any?>() {

        /*
         * Scans the full document text and generates syntax spans.
         */
        override fun analyze(text: StringBuilder, delegate: Delegate<Any?>): Styles {
            val raw = text.toString()
            val lines = raw.split("\n")
            val builder = MappedSpans.Builder(lines.size)

            val normalStyle = TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
            val keywordStyle = TextStyle.makeStyle(EditorColorScheme.KEYWORD)
            val commentStyle = TextStyle.makeStyle(EditorColorScheme.COMMENT)
            val literalStyle = TextStyle.makeStyle(EditorColorScheme.LITERAL)
            val tagStyle = TextStyle.makeStyle(EditorColorScheme.HTML_TAG)
            val attrNameStyle = TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_NAME)
            val attrValueStyle = TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_VALUE)
            val operatorStyle = TextStyle.makeStyle(EditorColorScheme.OPERATOR)
            val funcStyle = TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)

            for (i in lines.indices) {
                val line = lines[i]
                when (ext) {
                    "xml", "html" -> {
                        tokenizeXmlLine(line, i, builder, normalStyle, tagStyle, attrNameStyle, attrValueStyle, commentStyle)
                    }
                    "json" -> {
                        tokenizeJsonLine(line, i, builder, normalStyle, attrNameStyle, literalStyle, keywordStyle, operatorStyle)
                    }
                    "smali" -> {
                        tokenizeSmaliLine(line, i, builder, normalStyle, keywordStyle, funcStyle, commentStyle, literalStyle)
                    }
                    "log" -> {
                        tokenizeLogLine(line, i, builder, normalStyle, keywordStyle, literalStyle, tagStyle, attrNameStyle, funcStyle)
                    }
                    else -> {
                        tokenizeGenericLine(line, i, builder, normalStyle, keywordStyle, commentStyle, literalStyle)
                    }
                }
            }
            builder.determine(lines.size)
            return Styles(builder.build())
        }

        /*
         * Tokenizes a line of XML text into tag, attribute, and comment spans.
         */
        private fun tokenizeXmlLine(
            line: String,
            lineIndex: Int,
            builder: MappedSpans.Builder,
            normal: Long,
            tag: Long,
            attrName: Long,
            attrVal: Long,
            comment: Long
        ) {
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            if (trimmed.startsWith("<!--")) {
                builder.addIfNeeded(lineIndex, indent, comment)
                return
            }

            var inTag = false
            var inString = false
            var quoteChar = '"'
            var col = 0

            builder.addIfNeeded(lineIndex, 0, normal)

            while (col < line.length) {
                val c = line[col]
                if (inString) {
                    if (c == quoteChar) {
                        inString = false
                        builder.addIfNeeded(lineIndex, col + 1, if (inTag) attrName else normal)
                    }
                } else if (c == '"' || c == '\'') {
                    inString = true
                    quoteChar = c
                    builder.addIfNeeded(lineIndex, col, attrVal)
                } else if (c == '<' || c == '>') {
                    inTag = (c == '<')
                    builder.addIfNeeded(lineIndex, col, tag)
                    if (c == '>') {
                        builder.addIfNeeded(lineIndex, col + 1, normal)
                    }
                } else if (inTag && (c == ' ' || c == '\t')) {
                    builder.addIfNeeded(lineIndex, col, normal)
                } else if (inTag && (c.isLetter() || c == ':' || c == '-')) {
                    builder.addIfNeeded(lineIndex, col, attrName)
                }
                col++
            }
        }

        /*
         * Tokenizes a line of JSON text into key, value, number, and operator spans.
         */
        private fun tokenizeJsonLine(
            line: String,
            lineIndex: Int,
            builder: MappedSpans.Builder,
            normal: Long,
            keyStyle: Long,
            literal: Long,
            keyword: Long,
            operator: Long
        ) {
            builder.addIfNeeded(lineIndex, 0, normal)
            var col = 0
            var inString = false
            var isKey = true

            while (col < line.length) {
                val c = line[col]
                if (inString) {
                    if (c == '"' && (col == 0 || line[col - 1] != '\\')) {
                        inString = false
                        builder.addIfNeeded(lineIndex, col + 1, normal)
                    }
                } else if (c == '"') {
                    inString = true
                    val style = if (isKey && line.indexOf(':', col) > col) keyStyle else literal
                    builder.addIfNeeded(lineIndex, col, style)
                } else if (c == ':') {
                    isKey = false
                    builder.addIfNeeded(lineIndex, col, operator)
                    builder.addIfNeeded(lineIndex, col + 1, normal)
                } else if (c == '{' || c == '}' || c == '[' || c == ']' || c == ',') {
                    if (c == ',') isKey = true
                    builder.addIfNeeded(lineIndex, col, operator)
                    builder.addIfNeeded(lineIndex, col + 1, normal)
                } else if (line.startsWith("true", col) || line.startsWith("false", col) || line.startsWith("null", col)) {
                    builder.addIfNeeded(lineIndex, col, keyword)
                } else if (c.isDigit() || c == '-') {
                    builder.addIfNeeded(lineIndex, col, literal)
                }
                col++
            }
        }

        /*
         * Tokenizes a line of Smali assembly text into directive, opcode, and literal spans.
         */
        private fun tokenizeSmaliLine(
            line: String,
            lineIndex: Int,
            builder: MappedSpans.Builder,
            normal: Long,
            keyword: Long,
            func: Long,
            comment: Long,
            literal: Long
        ) {
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            builder.addIfNeeded(lineIndex, 0, normal)

            if (trimmed.startsWith("#")) {
                builder.addIfNeeded(lineIndex, indent, comment)
                return
            }
            if (trimmed.startsWith(".")) {
                builder.addIfNeeded(lineIndex, indent, keyword)
                val spaceIndex = line.indexOf(' ', indent)
                if (spaceIndex != -1) {
                    builder.addIfNeeded(lineIndex, spaceIndex, normal)
                }
                return
            }
            if (trimmed.startsWith("invoke-") || trimmed.startsWith("return") || trimmed.startsWith("const") || trimmed.startsWith("move")) {
                builder.addIfNeeded(lineIndex, indent, func)
                val spaceIndex = line.indexOf(' ', indent)
                if (spaceIndex != -1) {
                    builder.addIfNeeded(lineIndex, spaceIndex, normal)
                }
            }
        }

        /*
         * Tokenizes generic configuration or script files into comment and string spans.
         */
        private fun tokenizeGenericLine(
            line: String,
            lineIndex: Int,
            builder: MappedSpans.Builder,
            normal: Long,
            keyword: Long,
            comment: Long,
            literal: Long
        ) {
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            builder.addIfNeeded(lineIndex, 0, normal)

            if (trimmed.startsWith("#") || trimmed.startsWith("//")) {
                builder.addIfNeeded(lineIndex, indent, comment)
                return
            }
            if (trimmed.startsWith("import") || trimmed.startsWith("package") || trimmed.startsWith("fun") || trimmed.startsWith("val")) {
                builder.addIfNeeded(lineIndex, indent, keyword)
                val spaceIndex = line.indexOf(' ', indent)
                if (spaceIndex != -1) {
                    builder.addIfNeeded(lineIndex, spaceIndex, normal)
                }
            }
        }

        /*
         * Tokenizes execution log lines with distinct styling for bracketed level prefixes.
         */
        private fun tokenizeLogLine(
            line: String,
            lineIndex: Int,
            builder: MappedSpans.Builder,
            normal: Long,
            stepStyle: Long,
            infoStyle: Long,
            warnStyle: Long,
            errorStyle: Long,
            funcStyle: Long
        ) {
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            builder.addIfNeeded(lineIndex, 0, normal)

            val closeBracket = line.indexOf(']', indent)
            when {
                trimmed.startsWith("[STEP") -> {
                    val boldStep = TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)
                    builder.addIfNeeded(lineIndex, indent, boldStep)
                    if (closeBracket != -1 && closeBracket + 1 < line.length) {
                        builder.addIfNeeded(lineIndex, closeBracket + 1, funcStyle)
                    }
                }
                trimmed.startsWith("[ERROR]") || trimmed.startsWith("[FAIL]") || trimmed.startsWith("Exception") -> {
                    val boldError = TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_NAME, 0, true, false, false)
                    builder.addIfNeeded(lineIndex, indent, boldError)
                    if (closeBracket != -1 && closeBracket + 1 < line.length) {
                        builder.addIfNeeded(lineIndex, closeBracket + 1, errorStyle)
                    }
                }
                trimmed.startsWith("[WARN]") || trimmed.startsWith("WARNING") -> {
                    val boldWarn = TextStyle.makeStyle(EditorColorScheme.HTML_TAG, 0, true, false, false)
                    builder.addIfNeeded(lineIndex, indent, boldWarn)
                    if (closeBracket != -1 && closeBracket + 1 < line.length) {
                        builder.addIfNeeded(lineIndex, closeBracket + 1, warnStyle)
                    }
                }
                trimmed.startsWith("[INFO]") -> {
                    val boldInfo = TextStyle.makeStyle(EditorColorScheme.LITERAL, 0, true, false, false)
                    builder.addIfNeeded(lineIndex, indent, boldInfo)
                    if (closeBracket != -1 && closeBracket + 1 < line.length) {
                        builder.addIfNeeded(lineIndex, closeBracket + 1, normal)
                    }
                }
                trimmed.startsWith("Decompiling") || trimmed.startsWith("Extracting") || trimmed.startsWith("Decoding") -> {
                    builder.addIfNeeded(lineIndex, indent, stepStyle)
                }
            }
        }
    }
}

