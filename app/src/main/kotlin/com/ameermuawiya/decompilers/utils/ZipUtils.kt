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

import com.ameermuawiya.decompilers.utils.ktx.appStorage
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * Utility object for compressing directories into ZIP archives and extracting ZIP files to disk.
 */
object ZipUtils {

    /*
     * Compresses the specified directory into a ZIP archive saved in the app's archives directory.
     */
    fun zipDir(dir: File, packageId: String): File {
        val zipIntoDir = appStorage.resolve("archives")
        if (!zipIntoDir.exists() || !zipIntoDir.isDirectory) {
            zipIntoDir.mkdirs()
        }
        val zipFile = File(zipIntoDir, "$packageId.zip")
        if (zipFile.exists()) {
            zipFile.delete()
        }
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                addFolderToZip("", dir.toString(), zip)
                zip.flush()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating zip archive for $packageId")
        }

        return zipFile
    }

    /*
     * Recursively writes a file or directory entry into an open ZipOutputStream.
     */
    @Throws(Exception::class)
    private fun addFileToZip(path: String, srcFile: String, zip: ZipOutputStream) {
        val folder = File(srcFile)
        if (folder.isDirectory) {
            addFolderToZip(path, srcFile, zip)
        } else {
            FileInputStream(folder).use { input ->
                zip.putNextEntry(ZipEntry(path + "/" + folder.name))
                input.copyTo(zip)
                zip.closeEntry()
            }
        }
    }

    /*
     * Iterates child files and folders in a directory and adds each to the zip stream.
     */
    @Throws(Exception::class)
    private fun addFolderToZip(path: String, srcFolder: String, zip: ZipOutputStream) {
        val folder = File(srcFolder)
        for (fileName in folder.list().orEmpty()) {
            if (path.isEmpty()) {
                addFileToZip(folder.name, "$srcFolder/$fileName", zip)
            } else {
                addFileToZip(path + "/" + folder.name, "$srcFolder/$fileName", zip)
            }
        }
    }

    /*
     * Decompresses an input ZIP file into a destination directory with optional stream logging.
     */
    @Throws(IOException::class)
    @JvmOverloads
    fun unzip(zipFile: File, targetDirectory: File, printStream: PrintStream = System.out) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            while (true) {
                val zipEntry = zis.nextEntry ?: break
                val file = File(targetDirectory, zipEntry.name)
                printStream.println(zipEntry.name)
                val dir = if (zipEntry.isDirectory) file else file.parentFile
                if (!dir.isDirectory && !dir.mkdirs()) {
                    throw FileNotFoundException("Failed to ensure directory: " + dir.canonicalPath)
                }

                if (zipEntry.isDirectory) {
                    continue
                }

                FileOutputStream(file).use { out ->
                    zis.copyTo(out)
                }
                val time = zipEntry.time
                if (time > 0) {
                    file.setLastModified(time)
                }
            }
        }
    }
}