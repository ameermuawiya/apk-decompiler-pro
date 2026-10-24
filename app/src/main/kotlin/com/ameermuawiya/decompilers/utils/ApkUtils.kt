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

package com.ameermuawiya.decompilers.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.utils.ktx.getAppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * Utility object for extracting and sharing APK files and split APK bundles.
 */
object ApkUtils {

    /*
     * Generates an auto-incremented file reference so existing extracted packages are never overwritten.
     */
    private fun getUniqueExtractedFile(dir: File, baseName: String, ext: String): File {
        val initial = File(dir, "$baseName.$ext")
        if (!initial.exists()) return initial
        var index = 1
        while (true) {
            val candidate = File(dir, "$baseName ($index).$ext")
            val altCandidate = File(dir, "${baseName}_$index.$ext")
            if (!candidate.exists() && !altCandidate.exists()) {
                return candidate
            }
            index++
        }
    }

    /*
     * Extracts single APK or bundles split APKS into storage directory with auto-numbering.
     */
    suspend fun extractPackage(context: Context, packageInfo: PackageInfo): File? {
        return withContext(Dispatchers.IO) {
            try {
                val targetDir = getAppStorage(context).resolve("extracted")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val safeLabel = packageInfo.label.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
                val safeVersion = packageInfo.version.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
                val baseName = "${safeLabel}_${safeVersion}"

                if (packageInfo.isSplitApk && !packageInfo.splitSourceDirs.isNullOrEmpty()) {
                    val zipFile = getUniqueExtractedFile(targetDir, baseName, "apks")
                    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                        val baseFile = File(packageInfo.filePath)
                        if (baseFile.exists()) {
                            zos.putNextEntry(ZipEntry("base.apk"))
                            FileInputStream(baseFile).use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                        packageInfo.splitSourceDirs?.forEachIndexed { index, splitPath ->
                            val splitFile = File(splitPath)
                            if (splitFile.exists()) {
                                val entryName = if (splitFile.name.endsWith(".apk")) splitFile.name else "split_$index.apk"
                                zos.putNextEntry(ZipEntry(entryName))
                                FileInputStream(splitFile).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                    zipFile
                } else {
                    val srcFile = File(packageInfo.filePath)
                    if (!srcFile.exists()) return@withContext null
                    val ext = if (packageInfo.filePath.endsWith(".apks")) "apks" else "apk"
                    val dstFile = getUniqueExtractedFile(targetDir, baseName, ext)
                    srcFile.copyTo(dstFile, overwrite = false)
                    dstFile
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /*
     * Prepares sharing intents for standard APKs or APKS split bundle archives.
     */
    fun sharePackage(context: Context, packageInfo: PackageInfo) {
        try {
            val baseFile = File(packageInfo.filePath)
            if (!baseFile.exists()) return

            val authority = "${context.packageName}.provider"

            if (packageInfo.isSplitApk && !packageInfo.splitSourceDirs.isNullOrEmpty()) {
                val uris = ArrayList<Uri>()
                val baseUri = FileProvider.getUriForFile(context, authority, baseFile)
                uris.add(baseUri)

                packageInfo.splitSourceDirs?.forEach { splitPath ->
                    val splitFile = File(splitPath)
                    if (splitFile.exists()) {
                        uris.add(FileProvider.getUriForFile(context, authority, splitFile))
                    }
                }

                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/vnd.android.package-archive"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, packageInfo.label))
            } else {
                val fileUri = FileProvider.getUriForFile(context, authority, baseFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (packageInfo.filePath.endsWith(".apks")) "application/zip" else "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, packageInfo.label))
            }
        } catch (ignored: Exception) {
        }
    }
}
