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

package com.ameermuawiya.decompilers.decompilers

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import com.googlecode.d2j.reader.DexFileReader
import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.dex.DexExceptionHandler
import com.googlecode.d2j.Method
import com.googlecode.d2j.node.DexMethodNode
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.utils.UserPreferences
import com.ameermuawiya.decompilers.utils.ktx.cleanMemory
import com.ameermuawiya.decompilers.utils.ktx.toClassName
import org.apache.commons.io.FilenameUtils
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.dexbacked.DexBackedOdexFile
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.immutable.ImmutableDexFile
import org.objectweb.asm.MethodVisitor
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class JarExtractionWorker(context: Context, data: Data) : BaseDecompiler(context, data) {

    private val shouldIgnoreLibs = data.getBoolean("shouldIgnoreLibs", true)
    private var ignoredLibs: ArrayList<String> = ArrayList()

    override val maxMemoryAdjustmentFactor = 1.5

    /*
     * Loads library package patterns to skip when the ignore libraries option is enabled.
     */
    private fun loadIgnoredLibs() {
        ignoredLibs.clear()
        if (!shouldIgnoreLibs) return
        try {
            val customFile = context.filesDir.resolve("ignored_packages.txt")
            val customRaw = data.getString("customIgnoredList") ?: ""
            val rawLines = when {
                customFile.exists() -> customFile.readLines()
                customRaw.isNotBlank() -> customRaw.split("\n")
                else -> context.assets.open("ignored.list").bufferedReader().use { it.readLines() }
            }
            val parsed = rawLines
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.removePrefix("L").removeSuffix(";").replace('/', '.').trim('.') }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedByDescending { it.length }
            ignoredLibs.addAll(parsed)
            if (ignoredLibs.isNotEmpty()) {
                sendStatus(context.getString(R.string.jar_worker_log_ignore_enabled, ignoredLibs.size), true)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading ignored libraries list")
        }
    }

    /*
     * Matches a bytecode class descriptor against active ignored package rules.
     */
    private fun findMatchedIgnoreRule(classDescriptor: String): String? {
        if (!shouldIgnoreLibs || ignoredLibs.isEmpty()) return null
        val normalized = classDescriptor.removePrefix("L").removeSuffix(";").replace('/', '.')
        return ignoredLibs.firstOrNull { rule ->
            normalized == rule || normalized.startsWith("$rule.")
        }
    }

    /*
     * Extracts dex files from the input package, logs skipped library packages, and writes DEX chunks.
     */
    @Throws(Exception::class)
    private fun convertApkToDex() {
        cleanMemory()
        Timber.i("Starting APK to DEX Conversion")
        sendStatus(context.getString(R.string.optimizing))

        val classes = ArrayList<ClassDef>()
        val skippedPackagesCount = LinkedHashMap<String, Int>()

        /*
         * Parses DEX input stream and populates active class list while filtering ignored packages.
         */
        fun addClassesFromStream(inputStream: InputStream, ext: String, filterIgnored: Boolean) {
            try {
                val dexFile = if (ext == "dex") {
                    DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(inputStream))
                } else {
                    DexBackedOdexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(inputStream))
                }
                for (classDef in dexFile.classes) {
                    val matchedRule = if (filterIgnored && shouldIgnoreLibs) {
                        findMatchedIgnoreRule(classDef.type)
                    } else {
                        null
                    }
                    if (matchedRule != null) {
                        skippedPackagesCount[matchedRule] = (skippedPackagesCount[matchedRule] ?: 0) + 1
                    } else {
                        classes.add(classDef)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse dex file stream")
            }
        }

        /*
         * Extracts DEX bytecode streams from APK, APKS bundles, or nested APK entries.
         */
        fun processInputFile(file: File, filterIgnored: Boolean) {
            val ext = file.extension.lowercase()
            if (ext == "apk" || ext == "apks" || ext == "zip" || type == PackageInfo.Type.APK) {
                try {
                    val zipFile = ZipFile(file)
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory) {
                            val entryExt = FilenameUtils.getExtension(entry.name).lowercase()
                            if (entryExt == "dex" || entryExt == "odex") {
                                addClassesFromStream(zipFile.getInputStream(entry), entryExt, filterIgnored)
                            } else if (entryExt == "apk") {
                                try {
                                    val innerZip = ZipInputStream(zipFile.getInputStream(entry))
                                    var innerEntry = innerZip.nextEntry
                                    while (innerEntry != null) {
                                        if (!innerEntry.isDirectory) {
                                            val innerExt = FilenameUtils.getExtension(innerEntry.name).lowercase()
                                            if (innerExt == "dex" || innerExt == "odex") {
                                                addClassesFromStream(innerZip, innerExt, filterIgnored)
                                            }
                                        }
                                        innerZip.closeEntry()
                                        innerEntry = innerZip.nextEntry
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Error reading inner APK entry: ${entry.name}")
                                }
                            }
                        }
                    }
                    zipFile.close()
                } catch (e: Exception) {
                    Timber.e(e, "Error opening ZipFile for $file")
                    sendStatus("[WARN] Error reading archive for ${file.name}: ${e.localizedMessage}")
                }
            } else if (ext == "dex" || ext == "odex" || type == PackageInfo.Type.DEX) {
                addClassesFromStream(file.inputStream(), ext, filterIgnored)
            }
        }

        processInputFile(inputPackageFile, filterIgnored = shouldIgnoreLibs)

        val splits = data.getString("splitSourceDirs")
        val splitFiles = if (!splits.isNullOrBlank()) {
            splits.split(";").filter { it.isNotBlank() }.map { File(it) }.filter { it.exists() && it.canRead() }
        } else {
            emptyList()
        }

        if (splitFiles.isNotEmpty()) {
            sendStatus("[INFO] Discovered ${splitFiles.size} split APK packages to process", true)
            splitFiles.forEachIndexed { splitIdx, splitFile ->
                sendStatus("[INFO] Processing split APK [${splitIdx + 1}/${splitFiles.size}]: ${splitFile.name}", true)
                processInputFile(splitFile, filterIgnored = shouldIgnoreLibs)
            }
        }

        if (shouldIgnoreLibs && skippedPackagesCount.isNotEmpty()) {
            for ((pkgPattern, count) in skippedPackagesCount) {
                sendStatus(context.getString(R.string.jar_worker_log_skipped_package_count, pkgPattern, count), true)
            }
            val totalSkipped = skippedPackagesCount.values.sum()
            sendStatus(context.getString(R.string.jar_worker_log_ignore_summary, totalSkipped, skippedPackagesCount.size), true)
        }

        if (classes.isEmpty() && shouldIgnoreLibs && skippedPackagesCount.isNotEmpty()) {
            val totalSkipped = skippedPackagesCount.values.sum()
            Timber.w("All $totalSkipped classes matched ignore list; retrying without ignore filter.")
            sendStatus(context.getString(R.string.jar_worker_log_all_classes_ignored_fallback, totalSkipped), true)
            skippedPackagesCount.clear()
            processInputFile(inputPackageFile, filterIgnored = false)
            splitFiles.forEach { splitFile ->
                processInputFile(splitFile, filterIgnored = false)
            }
        }

        if (classes.isEmpty()) {
            sendStatus(context.getString(R.string.jar_worker_log_no_dex_classes, inputPackageFile.name), true)
            return
        }

        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) return

        setStep(context.getString(R.string.writingDexFile))
        val configuredChunkSize = data.getInt("chunkSize", UserPreferences.DEFAULTS.CHUNK_SIZE)
        val chunkSize = if (configuredChunkSize <= 500) 4000 else configuredChunkSize
        val chunkedClasses = classes.chunked(chunkSize)

        if (shouldIgnoreLibs && skippedPackagesCount.isNotEmpty()) {
            sendStatus(context.getString(R.string.jar_worker_log_preparing_filtered_classes, classes.size), true)
        } else {
            sendStatus(context.getString(R.string.jar_worker_log_preparing_all_classes, classes.size), true)
        }

        chunkedClasses.forEachIndexed { index, list ->
            if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping || Thread.currentThread().isInterrupted) {
                return
            }
            if (list.isNotEmpty()) {
                val dexFile = ImmutableDexFile(Opcodes.getDefault(), list)
                DexFileFactory.writeDexFile(
                    outputDexFiles.resolve("$index.dex").canonicalPath,
                    dexFile
                )
            }
        }
    }

    /*
     * Copies input bytecode JAR directly to the extraction output directory.
     */
    @Throws(Exception::class)
    private fun convertJarToDex() {
        cleanMemory()
        inputPackageFile.copyTo(outputDexFiles.resolve("input.jar"), overwrite = true)
    }

    /*
     * Converts DEX chunks to JAR files for decompilers requiring bytecode JARs.
     */
    @Throws(Exception::class)
    private fun convertDexToJar() {
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) return
        cleanMemory()
        setStep(context.getString(R.string.startingDexToJar))

        if (outputDexFiles.exists() && outputDexFiles.isDirectory) {
            setStep(context.getString(R.string.writingJarFile))
            outputDexFiles.listFiles()?.forEachIndexed { index, outputDexFile ->
                if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping || Thread.currentThread().isInterrupted) {
                    return
                }
                if (outputDexFile.exists() && outputDexFile.isFile) {
                    val dexExceptionHandlerMod = DexExceptionHandlerMod()
                    try {
                        val reader = DexFileReader(outputDexFile)
                        val dex2jar = Dex2jar.from(reader)
                            .reUseReg(false)
                            .topoLogicalSort(false)
                            .skipDebug(true)
                            .optimizeSynchronized(true)
                            .printIR(false)

                        dex2jar.exceptionHandler = dexExceptionHandlerMod
                        dex2jar.to(outputJarFiles.resolve("$index.jar"))
                    } catch (e: Exception) {
                        Timber.e(e, "Error converting dex to jar for $outputDexFile")
                    }
                    if (!keepIntermediateFiles) {
                        outputDexFile.delete()
                    }
                }
            }
        }
    }

    /*
     * Handles exceptions produced during dex2jar translation without throwing.
     */
    private inner class DexExceptionHandlerMod : DexExceptionHandler {
        /*
         * Logs Dex2Jar file processing exceptions to internal debug logs.
         */
        override fun handleFileException(e: Exception) {
            Timber.d("Dex2Jar Exception $e")
        }

        /*
         * Logs bytecode method translation exceptions encountered by Dex2Jar.
         */
        override fun handleMethodTranslateException(
            method: Method?,
            dexMethodNode: DexMethodNode?,
            methodVisitor: MethodVisitor?,
            e: java.lang.Exception?
        ) {
            Timber.d("Dex2Jar Exception $e")
        }
    }

    /*
     * Executes the jar extraction step and checks for output file generation.
     */
    override fun doWork(): ListenableWorker.Result {
        Timber.tag("JarExtraction")
        context.getString(R.string.extractingJar).let {
            buildNotification(it)
            setStep(it)
        }

        super.doWork()

        outputDexFiles.mkdirs()
        outputJarFiles.mkdirs()

        try {
            when (type) {
                PackageInfo.Type.APK, PackageInfo.Type.DEX -> {
                    loadIgnoredLibs()
                    convertApkToDex()
                    if (decompiler != "jadx") {
                        convertDexToJar()
                    }
                }
                PackageInfo.Type.JAR -> {
                    if (decompiler == "jadx") {
                        convertJarToDex()
                    } else {
                        inputPackageFile.copyTo(
                            outputJarFiles.resolve("0.jar"),
                            true
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return exit(e)
        }

        val hasDex = outputDexFiles.listFiles()?.isNotEmpty() == true
        val hasJar = outputJarFiles.listFiles()?.isNotEmpty() == true
        val hasWorkingFiles = workingDirectory.listFiles()?.isNotEmpty() == true

        if (!hasDex && !hasJar && !hasWorkingFiles) {
            return exit(Exception("No DEX bytecode or JAR files could be extracted from ${inputPackageFile.name} (system app may be ODEX/VDEX stripped)"))
        }

        return ListenableWorker.Result.success()
    }
}
