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

import org.benf.cfr.reader.api.CfrDriver
import com.ameermuawiya.decompilers.utils.ktx.cleanMemory
import java.io.File
import java.io.PrintStream

/*
 * Decompiler engine wrapper for CFR supporting modern Java bytecode features up to Java 21+.
 */
class CfrEngine : DecompilerEngine {
    override val id: String = "cfr"
    override val name: String = "CFR"
    override val version: String = "0.152"
    override val description: String = "Fast decompiler supporting modern Java features up to and including Java 21"
    override val isAvailable: Boolean = true
    override val requiresJarInput: Boolean = true

    /*
     * Decompiles JAR bytecode files using CFR driver into Java sources.
     */
    override fun decompile(
        inputDirectory: File,
        outputDirectory: File,
        keepIntermediateFiles: Boolean,
        printStream: PrintStream?,
        onProgress: ((message: String) -> Unit)?
    ): Boolean {
        cleanMemory()
        val jarFiles = inputDirectory.listFiles()?.filter { it.isFile } ?: emptyList()
        if (jarFiles.isEmpty()) return false

        onProgress?.invoke("[STEP] Decompiling ${jarFiles.size} JAR bytecode files with CFR...")
        printStream?.println("[STEP] Decompiling ${jarFiles.size} JAR bytecode files with CFR...")
        val options = mapOf(
            "outputdir" to outputDirectory.canonicalPath,
            "lomem" to "true"
        )
        val cfrDriver = CfrDriver.Builder().withOptions(options).build()
        cfrDriver.analyse(jarFiles.map { it.canonicalPath })
        onProgress?.invoke("[INFO] Finished CFR analysis on bytecode archives")

        return outputDirectory.exists() && (outputDirectory.list()?.isNotEmpty() == true)
    }
}
