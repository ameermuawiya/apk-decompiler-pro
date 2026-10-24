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

import com.strobel.assembler.InputTypeLoader
import com.strobel.assembler.metadata.CompositeTypeLoader
import com.strobel.assembler.metadata.DeobfuscationUtilities
import com.strobel.assembler.metadata.IMetadataResolver
import com.strobel.assembler.metadata.ITypeLoader
import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.assembler.metadata.MetadataParser
import com.strobel.assembler.metadata.MetadataSystem
import com.strobel.assembler.metadata.TypeDefinition
import com.strobel.core.StringUtilities
import com.strobel.core.VerifyArgument
import com.strobel.decompiler.DecompilationOptions
import com.strobel.decompiler.DecompilerSettings
import com.strobel.decompiler.PlainTextOutput
import com.strobel.decompiler.languages.java.JavaFormattingOptions
import com.ameermuawiya.decompilers.utils.ktx.cleanMemory
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.util.jar.JarFile

/*
 * Decompiler engine wrapper for Procyon accurate Java decompiler.
 */
class ProcyonEngine : DecompilerEngine {
    override val id: String = "procyon"
    override val name: String = "Procyon"
    override val version: String = "0.6.0"
    override val description: String = "Highly accurate analytical decompiler with clean output for complex Java classes"
    override val isAvailable: Boolean = true
    override val requiresJarInput: Boolean = true

    private class NoRetryMetadataSystem : MetadataSystem {
        private val failedTypes: MutableSet<String> = HashSet()

        constructor() : super()
        constructor(typeLoader: ITypeLoader?) : super(typeLoader)

        /*
         * Resolves JVM type definition with internal caching to avoid duplicate lookup overhead.
         */
        override fun resolveType(descriptor: String, mightBePrimitive: Boolean): TypeDefinition? {
            if (failedTypes.contains(descriptor)) return null
            val result = super.resolveType(descriptor, mightBePrimitive)
            if (result == null) failedTypes.add(descriptor)
            return result
        }
    }

    /*
     * Decompiles JAR bytecode files using Procyon AST decompiler into Java sources.
     */
    override fun decompile(
        inputDirectory: File,
        outputDirectory: File,
        keepIntermediateFiles: Boolean,
        printStream: PrintStream?,
        onProgress: ((message: String) -> Unit)?
    ): Boolean {
        cleanMemory()
        val jarFiles = inputDirectory.listFiles()?.filter { it.isFile && it.extension == "jar" } ?: emptyList()
        if (jarFiles.isEmpty()) return false

        jarFiles.forEach { inJar ->
            try {
                JarFile(inJar).use { jfile ->
                    val settings = DecompilerSettings.javaDefaults()
                    settings.typeLoader = CompositeTypeLoader(JarTypeLoader(jfile), InputTypeLoader())
                    settings.formattingOptions = JavaFormattingOptions.createDefault()
                    settings.forceExplicitImports = true
                    settings.showSyntheticMembers = false
                    val entries = jfile.entries()

                    var classesDecompiled = 0
                    var metadataSystem = NoRetryMetadataSystem(settings.typeLoader)
                    metadataSystem.isEagerMethodLoadingEnabled = true

                    while (entries.hasMoreElements()) {
                        if (com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping || Thread.currentThread().isInterrupted) {
                            return false
                        }
                        if (++classesDecompiled % 100 == 0) {
                            metadataSystem = NoRetryMetadataSystem(settings.typeLoader)
                        }
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (!name.endsWith(".class")) continue

                        val internalName = StringUtilities.removeRight(name, ".class")
                        val outf = outputDirectory.resolve("$internalName.java")
                        outf.parentFile?.mkdirs()
                        outf.createNewFile()

                        FileOutputStream(outf).use { stream ->
                            OutputStreamWriter(stream).use { writer ->
                                val output = PlainTextOutput(writer)
                                VerifyArgument.notNull(internalName, "internalName")
                                VerifyArgument.notNull(settings, "settings")

                                val type: Any? = if (internalName.length == 1) {
                                    val parser = MetadataParser(IMetadataResolver.EMPTY)
                                    val reference = parser.parseTypeDescriptor(internalName)
                                    metadataSystem.resolve(reference)
                                } else {
                                    metadataSystem.lookupType(internalName)
                                }

                                if (type != null) {
                                    val resolvedType = (type as? com.strobel.assembler.metadata.TypeReference)?.resolve()
                                    if (resolvedType != null) {
                                        DeobfuscationUtilities.processType(resolvedType)
                                        val options = DecompilationOptions()
                                        options.settings = settings
                                        options.isFullDecompilation = true

                                        if (!(resolvedType.isAnonymous || resolvedType.isSynthetic || resolvedType.isNested)) {
                                            val logLine = "[INFO] Decompiling Java: ${internalName.replace('/', '.')}.java"
                                            onProgress?.invoke(logLine)
                                            printStream?.println(logLine)
                                            settings.language.decompileType(resolvedType, output, options)
                                        } else {
                                            outf.delete()
                                        }
                                    } else {
                                        outf.delete()
                                    }
                                } else {
                                    output.writeLine("!!! ERROR: Failed to load class %s.", internalName)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (!com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) {
                    printStream?.println("[WARN] Procyon processing exception: ${e.message}")
                }
            }
        }
        return outputDirectory.exists() && (outputDirectory.list()?.isNotEmpty() == true)
    }
}
