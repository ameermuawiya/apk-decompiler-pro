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

package com.ameermuawiya.decompilers.utils.ktx

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.ameermuawiya.decompilers.utils.UserPreferences
import timber.log.Timber
import java.io.File

val defaultAppStorage: File
    get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "APK Decompiler Pro")

@Volatile
var cachedWorkingDirectoryPath: String? = null

/*
 * Resolves the active working storage directory based on user preferences or default location.
 */
fun getAppStorage(context: Context? = null): File {
    if (context != null) {
        try {
            val rawPrefs = context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
            val customPath = rawPrefs.getString("workingDirectoryPath", "")
            cachedWorkingDirectoryPath = customPath
            if (!customPath.isNullOrBlank()) {
                val customDir = File(customPath)
                if (!customDir.exists()) {
                    customDir.mkdirs()
                }
                if (customDir.exists() && customDir.canWrite()) {
                    return customDir
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading custom working directory path")
        }
    } else if (!cachedWorkingDirectoryPath.isNullOrBlank()) {
        val customDir = File(cachedWorkingDirectoryPath!!)
        if (!customDir.exists()) {
            try {
                customDir.mkdirs()
            } catch (ignored: Exception) {
            }
        }
        if (customDir.exists() && customDir.canWrite()) {
            return customDir
        }
    }

    val dir = defaultAppStorage
    if (!dir.exists()) {
        try {
            dir.mkdirs()
        } catch (ignored: Exception) {
        }
    }
    if (dir.exists() && dir.canWrite()) {
        return dir
    }

    if (context != null) {
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.resolve("APK Decompiler Pro")
        if (extDir != null) {
            if (!extDir.exists()) extDir.mkdirs()
            return extDir
        }
    }

    return defaultAppStorage
}

val appStorage: File
    get() = getAppStorage(null)

/*
 * Determines if the specified package belongs to the Android system image.
 */
fun isSystemPackage(pkgInfo: PackageInfo): Boolean {
    return pkgInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM != 0
}

/*
 * Generates a unique sanitized folder name with app label, short timestamp, and decompiler bracket.
 */
fun generateSourceFolderName(appLabel: String, decompilerEngine: String, engineVersion: String): String {
    val cleanLabel = appLabel.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(30).trim('_').ifEmpty { "app" }
    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
    val cleanEngine = decompilerEngine.replace(Regex("[^a-zA-Z0-9_\\-]"), "").ifEmpty { "decompiler" }
    return "${cleanLabel}_${timestamp}_[${cleanEngine}]"
}

/*
 * Finds the latest decompiled directory for a package name across timestamped runs.
 */
fun findLatestSourceDirForPackage(context: Context, packageName: String): File? {
    val sourcesParent = getAppStorage(context).resolve("sources")
    if (!sourcesParent.exists() || !sourcesParent.isDirectory) return null

    val directDir = sourcesParent.resolve(packageName)
    var latestDir: File? = if (directDir.exists() && directDir.isDirectory && directDir.list()?.isNotEmpty() == true) directDir else null
    var latestTime = latestDir?.lastModified() ?: 0L

    sourcesParent.listFiles()?.forEach { dir ->
        if (dir.isDirectory && dir != directDir) {
            val infoFile = dir.resolve("info.json")
            var matches = false
            if (infoFile.exists()) {
                try {
                    val json = org.json.JSONObject(org.apache.commons.io.FileUtils.readFileToString(infoFile, "UTF-8"))
                    if (json.optString("package_name") == packageName) matches = true
                } catch (ignored: Exception) {
                }
            }
            if (!matches && (dir.name.contains(packageName) || dir.name.startsWith(packageName))) {
                matches = true
            }
            if (matches && dir.lastModified() > latestTime) {
                latestTime = dir.lastModified()
                latestDir = dir
            }
        }
    }
    return latestDir
}

/*
 * Checks if any completed decompiled source directory exists for a given package name.
 */
fun hasDecompiledSourcesForPackage(context: Context, packageName: String): Boolean {
    return findLatestSourceDirForPackage(context, packageName) != null
}

/*
 * Retrieves the latest existing or new destination source directory for a given package.
 */
fun sourceDir(context: Context, packageName: String): File {
    return findLatestSourceDirForPackage(context, packageName) ?: getAppStorage(context).resolve("sources/$packageName")
}

/*
 * Retrieves default source directory for a given package using global storage.
 */
fun sourceDir(packageName: String): File {
    return appStorage.resolve("sources/$packageName")
}

/*
 * Converts SAF Tree Uri to a filesystem absolute path.
 */
fun getPathFromTreeUri(context: Context, uri: Uri): String? {
    try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        if (parts.size >= 2) {
            val type = parts[0]
            val relativePath = parts[1]
            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
            } else {
                return "/storage/" + type + "/" + relativePath
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Error resolving path from tree URI")
    }
    return uri.path
}

/*
 * Generates a URL-safe package identifier from an arbitrary JAR filename.
 */
fun jarPackageName(jarFileName: String): String {
    val slug = toSlug(jarFileName)
    return "$slug-${hashString("SHA-1", slug).slice(0..7)}".lowercase()
}
