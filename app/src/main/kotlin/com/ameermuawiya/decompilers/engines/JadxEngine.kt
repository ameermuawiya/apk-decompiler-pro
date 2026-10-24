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

package com.ameermuawiya.decompilers.engines

import android.os.Build
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import com.ameermuawiya.decompilers.utils.ktx.cleanMemory
import java.io.File
import java.io.PrintStream

/*
 * Decompiler engine wrapper for JaDX optimized for Android DEX bytecode files.
 */
class JadxEngine : DecompilerEngine {
    override val id: String = "jadx"
    override val name: String = "JaDX"
    override val version: String = "1.5.6"
    override val description: String = "Decompiler optimized for working with Android dex and apk files"
    override val isAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    override val requiresJarInput: Boolean = false

    /*
     * Decompiles DEX files using JaDX API into Java sources with per-file progress and stop checks.
     */
    override fun decompile(
        inputDirectory: File,
        outputDirectory: File,
        keepIntermediateFiles: Boolean,
        printStream: PrintStream?,
        onProgress: ((message: String) -> Unit)?
    ): Boolean {
        if (com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) return false
        cleanMemory()
        val args = JadxArgs()
        args.outDirSrc = outputDirectory
        args.isSkipResources = true
        val inputList = inputDirectory.listFiles()?.filter { it.isFile }?.toMutableList() ?: mutableListOf()
        args.inputFiles = inputList
        args.threadsCount = 1

        var jadx: JadxDecompiler? = null
        try {
            jadx = JadxDecompiler(args)
            onProgress?.invoke("[STEP] Loading DEX classes for JaDX decompilation...")
            jadx.load()
            val classes = jadx.classes
            val totalClasses = classes.size
            val stepMsg = "[STEP] Decompiling $totalClasses Java source classes"
            onProgress?.invoke(stepMsg)
            printStream?.println(stepMsg)
            jadx.save(50) { done, total ->
                if (com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping || Thread.currentThread().isInterrupted) {
                    throw RuntimeException("Decompilation stopped by user")
                }
                val doneInt = done.toInt()
                val safeTotal = if (total > 0L) total.toInt() else totalClasses
                val idx = (doneInt - 1).coerceIn(0, (totalClasses - 1).coerceAtLeast(0))
                val clsName = if (classes.isNotEmpty() && idx < classes.size) {
                    "${classes[idx].fullName}.java"
                } else {
                    "Class $doneInt"
                }
                val progressMsg = "[INFO] Decompiling Java [$doneInt/$safeTotal]: $clsName"
                onProgress?.invoke(progressMsg)
                printStream?.println(progressMsg)
            }
        } catch (t: Throwable) {
            if (!com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) {
                val warnMsg = "[WARN] JaDX execution notice: ${t.localizedMessage}"
                onProgress?.invoke(warnMsg)
                printStream?.println(warnMsg)
            }
        } finally {
            try {
                jadx?.close()
            } catch (ignored: Throwable) {
            }
        }

        if (inputDirectory.exists() && inputDirectory.isDirectory && !keepIntermediateFiles) {
            inputDirectory.deleteRecursively()
        }
        return outputDirectory.exists() && (outputDirectory.list()?.isNotEmpty() == true)
    }
}
