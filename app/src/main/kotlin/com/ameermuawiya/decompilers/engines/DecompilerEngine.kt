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

import java.io.File
import java.io.PrintStream

/*
 * Interface defining the contract for all pluggable decompiler engines in the application.
 */
interface DecompilerEngine {
    val id: String
    val name: String
    val version: String
    val description: String
    val isAvailable: Boolean
    val requiresJarInput: Boolean

    /*
     * Decompiles bytecode from the input directory into Java source files in the output directory.
     */
    @Throws(Exception::class)
    fun decompile(
        inputDirectory: File,
        outputDirectory: File,
        keepIntermediateFiles: Boolean = false,
        printStream: PrintStream? = null,
        onProgress: ((message: String) -> Unit)? = null
    ): Boolean
}
