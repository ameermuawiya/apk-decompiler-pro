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

package com.ameermuawiya.decompilers.activities.explorer.navigator

import android.content.Context
import com.ameermuawiya.decompilers.data.FileItem
import com.ameermuawiya.decompilers.utils.ZipUtils
import io.reactivex.Observable
import org.apache.commons.io.FileUtils
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlin.collections.ArrayList
import kotlin.collections.forEach
import kotlin.collections.sortBy

class NavigatorHandler(private var context: Context) {

    /*
     * Loads and sorts all directories and files residing in the target directory.
     */
    fun loadFiles(currentDirectory: File): Observable<ArrayList<FileItem>> {
        return Observable.fromCallable {
            val directories = ArrayList<FileItem>()
            val files = ArrayList<FileItem>()
            val items = currentDirectory.listFiles()
            if (items.isNullOrEmpty()) {
                return@fromCallable directories
            }
            val dateFormat = DateFormat.getDateTimeInstance()
            items.forEach { file ->
                val lastModDate = dateFormat.format(Date(file.lastModified()))
                if (file.isDirectory) {
                    val noOfChildren = file.list()?.size ?: 0
                    val fileSize = "$noOfChildren ${if (noOfChildren == 1) "item" else "items"}"
                    directories.add(FileItem(file, fileSize, lastModDate))
                } else {
                    val fileSize = FileUtils.byteCountToDisplaySize(file.length())
                    files.add(FileItem(file, fileSize, lastModDate))
                }
            }
            directories.sortBy { it.name?.lowercase() }
            files.sortBy { it.name?.lowercase() }
            directories.addAll(files)
            directories
        }
    }

    /*
     * Compresses the specified source directory into a zip archive file.
     */
    fun archiveDirectory(sourceDirectory: File, packageName: String): Observable<File> {
        return Observable.fromCallable {
            ZipUtils.zipDir(sourceDirectory, packageName)
        }
    }

    /*
     * Recursively deletes the specified source directory from the filesystem.
     */
    fun deleteDirectory(sourceDirectory: File): Observable<Unit> {
        return Observable.fromCallable {
            try {
                if (sourceDirectory.exists()) {
                    sourceDirectory.deleteRecursively()
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }
}
