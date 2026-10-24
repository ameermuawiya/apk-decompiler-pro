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

//import net.dongliu.apk.parser.AbstractApkFile
//import net.dongliu.apk.parser.ApkFile
//import net.dongliu.apk.parser.exception.ParserException
//import net.dongliu.apk.parser.struct.resource.ResourcePackage
//import net.dongliu.apk.parser.struct.resource.ResourceTable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.work.Data
import androidx.work.ListenableWorker
import com.reandroid.apk.APKLogger
import com.reandroid.apk.ApkModule
import com.reandroid.apk.ApkModuleXmlDecoder
import com.reandroid.arsc.value.ResConfig
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.data.SourceInfo
import com.ameermuawiya.decompilers.utils.ktx.cleanMemory
import com.ameermuawiya.decompilers.utils.ktx.toFile
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import timber.log.Timber
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter


class ResourcesExtractionWorker(context: Context, data: Data) : BaseDecompiler(context, data) {

    private lateinit var apkModule: ApkModule
    private val images = listOf("jpg", "png", "gif", "jpeg", "webp", "tiff", "bmp")

    /**
     * Extract xml & image resources using JaDX. JaDX has a better resources decompiler compared
     * to others. But sadly, JaDX also uses javax.imageio classes that are not available in android
     * at present. So, we don't use it.
     *
     * TODO Figure out how to use it only for XML though.
     *
     * @experimental
     */
    @Throws(Exception::class)
    private fun extractResourcesWithJadx() {
        cleanMemory()
        val resDir = outputSrcDirectory
        val args = JadxArgs()
        args.outDirRes = resDir
        args.inputFiles = mutableListOf(inputPackageFile)
        val jadx = JadxDecompiler(args)
        jadx.load()
        jadx.saveResources()
    }

    /*
     * Normalizes line endings across XML files to ensure standard formatting.
     */
    fun normalizeFile(f: File) {
        var temp: File? = null
        var bufferIn: BufferedReader? = null
        var bufferOut: BufferedWriter? = null
        try {
            if (f.exists()) {
                // Create a new temp file to write to
                temp = File(f.absolutePath + ".normalized")
                temp.createNewFile()

                // Get a stream to read from the file un-normalized file
                val fileIn = FileInputStream(f)
                val dataIn = DataInputStream(fileIn)
                bufferIn = BufferedReader(InputStreamReader(dataIn))

                // Get a stream to write to the normalized file
                val fileOut = FileOutputStream(temp)
                val dataOut = DataOutputStream(fileOut)
                bufferOut = BufferedWriter(OutputStreamWriter(dataOut))

                // For each line in the un-normalized file
                var line: String?
                while (bufferIn.readLine().also { line = it } != null) {
                    // Write the original line plus the operating-system dependent newline
                    bufferOut.write(line)
                    bufferOut.newLine()
                }
                bufferIn.close()
                bufferOut.close()

                // Remove the original file
                f.delete()

                // And rename the original file to the new one
                temp.renameTo(f)
            } else {
                // If the file doesn't exist...
                Timber.w("Could not find file to open: %s", f.absolutePath)
            }
        } catch (e: java.lang.Exception) {
            Timber.w(e)
        } finally {
            // Clean up, temp should never exist
            FileUtils.deleteQuietly(temp)
            IOUtils.closeQuietly(bufferIn)
            IOUtils.closeQuietly(bufferOut)
        }
    }
    /**
     * Read the APK as zip, and extract XML resources using the apk-parser and image/other resources
     * by just extracting it from the zip.
     */
    @Throws(Exception::class)
    private fun extractResourcesWithParser() {
        cleanMemory()
//        writeManifest()
//        val zipFile = ZipFile(inputPackageFile)
//        val entries = zipFile.entries()
//        while (entries.hasMoreElements()) {
//            val zipEntry = entries.nextElement()
//
//            try {
//                if (!zipEntry.isDirectory && zipEntry.name != "AndroidManifest.xml") {
//                    sendStatus(zipEntry.name)
//                    if (FilenameUtils.isExtension(zipEntry.name, "xml") && !zipEntry.name.startsWith("assets")) {
//                        writeXML(zipEntry.name)
//                    } else if (FilenameUtils.isExtension(zipEntry.name, images) || zipEntry.name.startsWith("assets")) {
//                        writeFile(zipFile.getInputStream(zipEntry), zipEntry.name)
//                    }
//                }
//            } catch (e: java.lang.Exception) {
//                sendStatus("Skipped ${zipEntry.name}")
//            }
//        }
//        zipFile.close()

        val xmlDecoder = ApkModuleXmlDecoder(apkModule)
        var lastLogTimestamp = 0L
        xmlDecoder.setApkLogger(object : APKLogger {
            /*
             * Emits throttled information messages from the APK logger into the decompiler stream.
             */
            override fun logMessage(p0: String?) {
                if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping || Thread.currentThread().isInterrupted) {
                    throw RuntimeException("Decompilation stopped by user")
                }
                if (!p0.isNullOrBlank()) {
                    val now = System.currentTimeMillis()
                    if (now - lastLogTimestamp >= 50L) {
                        sendStatus("[INFO] $p0", true)
                        lastLogTimestamp = now
                    }
                }
            }

            /*
             * Routes error messages from the APK logger directly to the status callback.
             */
            override fun logError(p0: String?, p1: Throwable?) {
                if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping || Thread.currentThread().isInterrupted) {
                    throw RuntimeException("Decompilation stopped by user")
                }
                if (!p0.isNullOrBlank()) {
                    sendStatus("[WARN] $p0", true)
                }
            }

            /*
             * Handles verbose debug messages with timestamp rate-limiting.
             */
            override fun logVerbose(p0: String?) {
                if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping || Thread.currentThread().isInterrupted) {
                    throw RuntimeException("Decompilation stopped by user")
                }
                if (!p0.isNullOrBlank()) {
                    val now = System.currentTimeMillis()
                    if (now - lastLogTimestamp >= 50L) {
                        sendStatus("[INFO] $p0", true)
                        lastLogTimestamp = now
                    }
                }
            }
        })
        xmlDecoder.setKeepResPath(false)
        sendStatus("[STEP] Decoding AndroidManifest.xml", true)
        xmlDecoder.decodeAndroidManifest(outputSrcDirectory.parentFile)
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) return
        sendStatus("[STEP] Decoding resource table and XML layouts", true)
        xmlDecoder.decodeResourceTable(outputSrcDirectory)
        outputSrcDirectory.parentFile?.let { normalizeFile(it.resolve("AndroidManifest.xml")) }
    }

    /**
     * Currently the extracted XML resources, refer to the resource used within them via their
     * numeric ID. This is an experiment to parse the resource table in the APK and get the human
     * readable names from the numeric ID.
     *
     * @experimental
     */
//    @Suppress("UNCHECKED_CAST")
//    @RequiresApi(Build.VERSION_CODES.N)
//    @Throws(Exception::class)
//    private fun loadResourcesTable() {
//        cleanMemory()
//        val resourceTableField = AbstractApkFile::class.java.getDeclaredField("resourceTable")
//        resourceTableField.isAccessible = true
//        val resourceTable = resourceTableField.get(parsedInputApkFile) as ResourceTable
//        val packageMapField = resourceTable.javaClass.getDeclaredField("packageMap")
//        packageMapField.isAccessible = true
//        val packageMap = packageMapField.get(resourceTable) as Map<Short, ResourcePackage>
//        packageMap.forEach { _, u ->
//            Timber.d("[res] ID: ${u.id} Name: ${u.name}")
//            u.typesMap.forEach { _, iu ->
//                iu.forEach {
//                    Timber.d("[res] Inner ID: ${it.id} Inner Name: ${it.name}")
//                }
//            }
//        }
//    }

    /**
     * Write a file at the appropriate output path within the source directory
     */
    @Throws(Exception::class)
    private fun writeFile(fileStream: InputStream, path: String) {
        val fileFolderPath =
            outputSrcDirectory.canonicalPath + "/" + path.replace(
                FilenameUtils.getName(
                    path
                ), ""
            )
        val fileFolder = File(fileFolderPath)
        if (!fileFolder.exists() || !fileFolder.isDirectory) {
            fileFolder.mkdirs()
        }
        fileStream.toFile(File(fileFolderPath, FilenameUtils.getName(path)))
    }

    /**
     * Read and decompile an XML resource from the APK and write it to the source directory.
     */
//    @Throws(ParserException::class)
//    private fun writeXML(path: String) {
//        val xml = parsedInputApkFile.transBinaryXml(path)
//        val fileFolderPath =
//            outputSrcDirectory.canonicalPath + "/" + path.replace(
//                FilenameUtils.getName(
//                    path
//                ), ""
//            )
//        val fileFolder = File(fileFolderPath)
//        if (!fileFolder.exists() || !fileFolder.isDirectory) {
//            fileFolder.mkdirs()
//        }
//        FileUtils.writeStringToFile(
//            File(fileFolderPath + FilenameUtils.getName(path)),
//            xml,
//            Charset.defaultCharset()
//        )
//    }

    /**
     * Write the AndroidManifest file to the source directory
     */
//    @Throws(Exception::class)
//    private fun writeManifest() {
//        val manifestXml = parsedInputApkFile.manifestXml
//        FileUtils.writeStringToFile(
//            workingDirectory.resolve("AndroidManifest.xml"),
//            manifestXml,
//            Charset.defaultCharset()
//        )
//    }

    /**
     * Get bitmap from drawable. This is used to read the app icon and save to the source directory.
     */
    // Borrowed from from https://stackoverflow.com/a/52453231/1562480
    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    /*
     * Saves the application icon into the decompiled working directory.
     */
    private fun saveIcon() {
        try {
            var drawable: Drawable? = null
            try {
                drawable = context.packageManager.getApplicationIcon(packageName)
            } catch (ignored: Exception) {
            }
            if (drawable == null) {
                val packageInfo = context.packageManager.getPackageArchiveInfo(inputPackageFile.canonicalPath, 0)
                drawable = packageInfo?.applicationInfo?.loadIcon(context.packageManager)
            }

            val bitmap = if (drawable != null) {
                getBitmapFromDrawable(drawable)
            } else {
                val resFiles = apkModule.listResFiles(
                    apkModule.androidManifest.iconResourceId,
                    ResConfig.parse(ResConfig.Density.HDPI.toString())
                )
                if (resFiles.isNotEmpty()) {
                    val inp = resFiles[0].inputSource.openStream()
                    BitmapFactory.decodeStream(inp)
                } else null
            }
            if (bitmap != null) {
                FileOutputStream(workingDirectory.resolve("icon.png")).use { iconOutput ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, iconOutput)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not extract or save icon")
        }
    }

    /*
     * Executes resource extraction worker lifecycle for manifests, assets, and app resources.
     */
    override fun doWork(): ListenableWorker.Result {
        Timber.tag("ResourcesExtraction")
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) {
            return ListenableWorker.Result.failure()
        }

        if (type == PackageInfo.Type.APK) {
            context.getString(R.string.extractingResources).let {
                buildNotification(it)
                setStep(it)
            }
        }

        super.doWork()

        val sourceInfo = SourceInfo.from(workingDirectory)
            .setPackageLabel(packageLabel)
            .setPackageName(packageName)

        if (type == PackageInfo.Type.APK) {
            try {
                apkModule = ApkModule.loadApkFile(inputPackageFile)
                extractResourcesWithParser()
                if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) {
                    return ListenableWorker.Result.failure()
                }
                saveIcon()
                sourceInfo
                    .setXmlSourcePresence(true)
                    .persist()
            } catch (e: Exception) {
                if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) {
                    return ListenableWorker.Result.failure()
                }
                sendStatus("[WARN] Resources extraction notice: ${e.localizedMessage}")
                Timber.w(e, "Resources extraction error")
            }
        }

        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) {
            return ListenableWorker.Result.failure()
        }

        sourceInfo
            .setSourceSize(FileUtils.sizeOfDirectory(workingDirectory))
            .persist()

        onCompleted()

        return ListenableWorker.Result.success()
    }
}
