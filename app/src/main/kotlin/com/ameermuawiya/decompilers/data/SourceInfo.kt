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

package com.ameermuawiya.decompilers.data

import android.os.Parcel
import android.os.Parcelable
import com.ameermuawiya.decompilers.utils.ktx.getDate
import com.ameermuawiya.decompilers.utils.ktx.sourceDir
import org.apache.commons.io.FileUtils
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * [SourceInfo] holds information about a specific decompiled source on the app storage.
 * The source info is persisted to disk as a simple json file at the root of each package's
 * decompiled source folder.
 */
class SourceInfo() : Parcelable {

    var packageLabel: String = ""
    var packageName: String = ""

    var hasJavaSource: Boolean = false
    var hasXmlSource: Boolean = false

    var createdAt: String = getDate()
    var updatedAt: String = getDate()

    var sourceSize: Long = 0

    var sourceDirectory: File = File("")
    var decompilerEngine: String = ""
    var decompilerVersion: String = ""

    constructor(parcel: Parcel) : this() {
        packageLabel = parcel.readString() ?: ""
        packageName = parcel.readString() ?: ""
        hasJavaSource = parcel.readInt() == 1
        hasXmlSource = parcel.readInt() == 1
        createdAt = parcel.readString() ?: getDate()
        updatedAt = parcel.readString() ?: getDate()
        sourceSize = parcel.readLong()
        val dirPath = parcel.readString() ?: ""
        sourceDirectory = if (dirPath.isNotBlank()) File(dirPath) else (if (packageName.isNotBlank()) sourceDir(packageName) else File(""))
        decompilerEngine = parcel.readString() ?: ""
        decompilerVersion = parcel.readString() ?: ""
    }

    constructor(packageLabel: String, packageName: String, hasJavaSource: Boolean, hasXmlSource:Boolean, createdAt: String, updatedAt: String, sourceSize: Long) : this() {
        this.packageLabel = packageLabel
        this.packageName = packageName
        this.hasJavaSource = hasJavaSource
        this.hasXmlSource = hasXmlSource
        this.createdAt = createdAt
        this.updatedAt = updatedAt
        this.sourceSize = sourceSize
        this.sourceDirectory = sourceDir(packageName)
    }

    /*
     * Serializes source package metadata into destination parcel.
     */
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageLabel)
        parcel.writeString(packageName)
        parcel.writeInt(if (hasJavaSource) 1 else 0)
        parcel.writeInt(if (hasXmlSource) 1 else 0)
        parcel.writeString(createdAt)
        parcel.writeString(updatedAt)
        parcel.writeLong(sourceSize)
        parcel.writeString(sourceDirectory.absolutePath)
        parcel.writeString(decompilerEngine)
        parcel.writeString(decompilerVersion)
    }

    /*
     * Returns parcelable bitmask specification flags.
     */
    override fun describeContents(): Int {
        return 0
    }

    /*
     * Updates package display label and returns instance for chaining.
     */
    fun setPackageLabel(packageLabel: String): SourceInfo {
        this.packageLabel = packageLabel
        return this
    }

    /*
     * Updates package identifier name and resolves associated source directory.
     */
    fun setPackageName(packageName: String): SourceInfo {
        this.packageName = packageName
        this.sourceDirectory = sourceDir(packageName)
        return this
    }

    /*
     * Sets boolean flag indicating whether Java source files were produced.
     */
    fun setJavaSourcePresence(hasJavaSource: Boolean): SourceInfo {
        this.hasJavaSource = hasJavaSource
        return this
    }

    /*
     * Sets boolean flag indicating whether extracted XML resources exist.
     */
    fun setXmlSourcePresence(hasXmlSource: Boolean): SourceInfo {
        this.hasXmlSource = hasXmlSource
        return this
    }

    /*
     * Configures the total size in bytes of the decompiled source tree.
     */
    fun setSourceSize(sourceSize: Long): SourceInfo {
        this.sourceSize = sourceSize
        return this
    }

    /*
     * Writes source metadata to disk as structured JSON.
     */
    fun persist(): SourceInfo {
        synchronized(this) {
            updatedAt = getDate()
            try {
                if (sourceDirectory.exists() && sourceDirectory.isDirectory) {
                    val json = JSONObject()
                    json.put("package_label", packageLabel)
                    json.put("package_name", packageName)
                    json.put("has_java_sources", hasJavaSource)
                    json.put("has_xml_sources", hasXmlSource)
                    json.put("created_at", createdAt)
                    json.put("updated_at", updatedAt)
                    json.put("source_size", sourceSize)
                    json.put("decompiler_engine", decompilerEngine)
                    json.put("decompiler_version", decompilerVersion)
                    json.put("source_directory", sourceDirectory.absolutePath)
                    val content = json.toString(2)
                    FileUtils.writeStringToFile(sourceDirectory.resolve("info.json"), content, "UTF-8")
                    val oldMeta = sourceDirectory.resolve(".project_meta.json")
                    if (oldMeta.exists()) {
                        oldMeta.delete()
                    }
                }
            } catch (e: IOException) {
                Timber.e(e)
            } catch (e: JSONException) {
                Timber.e(e)
            }
            return this
        }
    }

    /*
     * Computes directory size asynchronously and persists the updated metadata to disk.
     */
    fun computeAndCacheSize(): Long {
        val size = try {
            if (sourceDirectory.exists() && sourceDirectory.isDirectory) {
                calculateDirectorySize(sourceDirectory)
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
        sourceSize = size
        persist()
        return size
    }

    /*
     * Checks if decompiled sources exist for this item.
     */
    fun exists(): Boolean {
        return hasJavaSource || hasXmlSource
    }

    /*
     * Formats timestamp and engine name into a concise readable history tag.
     */
    fun getFormattedDisplayTag(): String {
        val engine = decompilerEngine.ifBlank {
            val bracketMatch = Regex("[\\[\\(]([a-zA-Z0-9_\\-]+)[\\]\\)]").find(sourceDirectory.name)
            bracketMatch?.groupValues?.getOrElse(1) { "jadx" } ?: "jadx"
        }
        val dateFormatted = try {
            val dateStr = if (updatedAt.isNotBlank()) updatedAt else createdAt
            if (dateStr.contains("T")) {
                val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US)
                parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val d = parser.parse(dateStr)
                if (d != null) java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.US).format(d) else null
            } else {
                null
            }
        } catch (ignored: Exception) {
            null
        } ?: try {
            val lastMod = if (sourceDirectory.exists()) sourceDirectory.lastModified() else System.currentTimeMillis()
            java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.US).format(java.util.Date(lastMod))
        } catch (ignored: Exception) {
            "Unknown"
        }
        return "$dateFormatted ($engine)"
    }

    companion object CREATOR : Parcelable.Creator<SourceInfo> {
        /*
         * Calculates directory total byte size recursively with depth safeguard.
         */
        fun calculateDirectorySize(dir: File): Long {
            if (!dir.exists()) return 0L
            if (dir.isFile) return dir.length()
            var total = 0L
            try {
                dir.walkBottomUp()
                    .maxDepth(15)
                    .forEach { file ->
                        try {
                            if (file.isFile) {
                                total += file.length()
                            }
                        } catch (ignored: Exception) {
                        }
                    }
            } catch (ignored: Exception) {
            }
            return total
        }

        /*
         * Resolves the primary metadata file residing inside the given source directory.
         */
        private fun getInfoFile(source: File): File {
            if (!source.exists() || source.isFile) {
                return source
            }
            return source.resolve("info.json")
        }

        /*
         * Quickly checks if a decompiled project folder is valid without directory traversal.
         */
        fun exists(sourceDir: File): Boolean {
            if (sourceDir.exists() && sourceDir.isDirectory) {
                val infoFile = sourceDir.resolve("info.json")
                if (infoFile.exists()) {
                    return true
                }
                val hasSrc = sourceDir.resolve("src").exists() || sourceDir.resolve("java").exists() || sourceDir.resolve("AndroidManifest.xml").exists()
                if (hasSrc && (sourceDir.list()?.isNotEmpty() == true)) {
                    return true
                }
            }
            return false
        }

        /*
         * Parses metadata from disk or infers package properties from folder structure.
         */
        fun from(source: File): SourceInfo {
            val info = SourceInfo()
            info.sourceDirectory = source
            val infoFile = getInfoFile(source)

            if (infoFile.exists()) {
                try {
                    val json = JSONObject(FileUtils.readFileToString(infoFile, "UTF-8"))
                    info.packageLabel = json.optString("package_label", source.name)
                    info.packageName = json.optString("package_name", source.name)
                    info.hasJavaSource = json.optBoolean("has_java_sources", true)
                    info.hasXmlSource = json.optBoolean("has_xml_sources", false)
                    info.createdAt = json.optString("created_at", getDate())
                    info.updatedAt = json.optString("updated_at", getDate())
                    info.sourceSize = json.optLong("source_size", 0L)
                    info.decompilerEngine = json.optString("decompiler_engine", "")
                    info.decompilerVersion = json.optString("decompiler_version", "")
                    return info
                } catch (ignored: Exception) {
                }
            }

            val folderName = source.name
            val engineRegex = Regex("[\\[\\(]([a-zA-Z0-9_\\-]+)_?([0-9.]*)[\\]\\)]")
            val match = engineRegex.find(folderName)
            if (match != null) {
                info.decompilerEngine = match.groupValues.getOrElse(1) { "" }
                info.decompilerVersion = match.groupValues.getOrElse(2) { "" }
            }
            val cleanName = folderName.substringBeforeLast("_[").substringBeforeLast("_(").substringBeforeLast("_20")
            info.packageLabel = cleanName.replace("_", " ").ifBlank { folderName }
            info.packageName = cleanName
            info.hasJavaSource = source.resolve("src/java").exists() || source.resolve("java").exists() || source.resolve("src").exists()
            info.hasXmlSource = source.resolve("src/resources").exists() || source.resolve("resources").exists()
            info.sourceSize = 0L
            return info
        }

        /*
         * Recreates SourceInfo instance from serialized parcel data.
         */
        override fun createFromParcel(parcel: Parcel): SourceInfo {
            return SourceInfo(parcel)
        }
        /*
         * Allocates new array of SourceInfo parcelable instances.
         */
        override fun newArray(size: Int): Array<SourceInfo?> {
            return arrayOfNulls(size)
        }
    }
}
