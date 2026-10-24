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

package com.ameermuawiya.decompilers.utils.streams

import androidx.annotation.NonNull
import com.ameermuawiya.decompilers.decompilers.BaseDecompiler
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/*
 * Custom output stream that captures decompiler process outputs and routes progress messages.
 */
class ProgressStream(val decompiler: BaseDecompiler) : OutputStream() {

    private val lineBuffer = ByteArrayOutputStream()

    /*
     * Filters internal debugger noise while preserving meaningful decompiler progress messages.
     */
    private fun shouldIgnore(string: String): Boolean {
        if (string.startsWith("[ignored]")) return true
        if (string.startsWith("[") && (string.contains("]") || string.contains(":"))) return false

        for (part in arrayOf(
            "TRYBLOCK", "stack info", "Produces", "ASTORE", "targets",
            "WARN jadx", "thread-1", "ERROR jadx", "JadxRuntimeException"
        )) {
            if (string.contains(part, true)) {
                return true
            }
        }
        return false
    }

    /*
     * Dispatches a single completed line of output to the decompiler status listener.
     */
    private fun processLine(rawLine: String) {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return
        if (shouldIgnore(trimmed)) return

        val cleanLine = if (trimmed.startsWith("[stdout]")) trimmed.removePrefix("[stdout] ").trim() else trimmed
        if (cleanLine.isNotEmpty()) {
            Timber.d("[stdout] %s", cleanLine)
            decompiler.sendStatus(cleanLine, true)
        }
    }

    /*
     * Writes specified byte subarray into the internal output buffer.
     */
    override fun write(@NonNull data: ByteArray, offset: Int, length: Int) {
        for (i in offset until (offset + length)) {
            write(data[i].toInt())
        }
    }

    /*
     * Writes a single byte and flushes completed line upon encountering newline characters.
     */
    override fun write(byte: Int) {
        if (byte == '\n'.code || byte == '\r'.code) {
            if (lineBuffer.size() > 0) {
                val line = lineBuffer.toString(StandardCharsets.UTF_8.name())
                lineBuffer.reset()
                processLine(line)
            }
        } else {
            lineBuffer.write(byte)
            if (lineBuffer.size() > 4096) {
                val line = lineBuffer.toString(StandardCharsets.UTF_8.name())
                lineBuffer.reset()
                processLine(line)
            }
        }
    }
}