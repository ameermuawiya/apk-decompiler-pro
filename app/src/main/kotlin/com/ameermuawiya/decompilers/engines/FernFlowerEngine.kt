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

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler
import com.ameermuawiya.decompilers.utils.ZipUtils
import com.ameermuawiya.decompilers.utils.ktx.cleanMemory
import java.io.File
import java.io.PrintStream

/*
 * Decompiler engine wrapper for FernFlower analytical Java decompiler.
 */
class FernFlowerEngine : DecompilerEngine {
    override val id: String = "fernflower"
    override val name: String = "FernFlower"
    override val version: String = "1.0.0"
    override val description: String = "The first working analytical decompiler for Java"
    override val isAvailable: Boolean = true
    override val requiresJarInput: Boolean = true

    /*
     * Decompiles JAR bytecode files using FernFlower console decompiler into Java sources.
     */
    override fun decompile(
        inputDirectory: File,
        outputDirectory: File,
        keepIntermediateFiles: Boolean,
        printStream: PrintStream?,
        onProgress: ((message: String) -> Unit)?
    ): Boolean {
        cleanMemory()
        onProgress?.invoke("[STEP] Starting FernFlower decompiler on bytecode archives...")
        printStream?.println("[STEP] Starting FernFlower decompiler on bytecode archives...")
        ConsoleDecompiler.main(
            arrayOf(
                inputDirectory.canonicalPath, outputDirectory.canonicalPath
            )
        )

        val outputJars = outputDirectory.listFiles() ?: emptyArray()
        onProgress?.invoke("[INFO] Extracting ${outputJars.size} decompiled JAR archives...")
        outputJars.forEach { decompiledJarFile ->
            if (decompiledJarFile.exists() && decompiledJarFile.isFile && decompiledJarFile.extension == "jar") {
                if (printStream != null) {
                    ZipUtils.unzip(decompiledJarFile, outputDirectory, printStream)
                } else {
                    ZipUtils.unzip(decompiledJarFile, outputDirectory)
                }
                decompiledJarFile.delete()
            }
        }
        return outputDirectory.exists() && (outputDirectory.list()?.isNotEmpty() == true)
    }
}
