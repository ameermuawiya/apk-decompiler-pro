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

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.value.ResConfig
import com.ameermuawiya.decompilers.utils.Identicon
import com.ameermuawiya.decompilers.utils.ktx.getVersion
import com.ameermuawiya.decompilers.utils.ktx.isSystemPackage
import com.ameermuawiya.decompilers.utils.ktx.jarPackageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * [PackageInfo] holds information about an apk/jar/dex file in preparation for sending it for
 * decompilation. It also providers helpers to auto-generate an instance from a given [File]
 */
class PackageInfo() : Parcelable {
    var label = ""
    var name = ""
    var version = ""
    var filePath = ""
    var file: File = File("")
    var icon: Drawable? = null
    var type = Type.APK
    var isSystemPackage = false
    var isExternalPackage = false
    var isSplitApk = false
    var splitSourceDirs: Array<String>? = null

    constructor(parcel: Parcel) : this() {
        label = parcel.readString() ?: ""
        name = parcel.readString() ?: ""
        version = parcel.readString() ?: ""
        filePath = parcel.readString() ?: ""
        type = Type.values().getOrElse(parcel.readInt()) { Type.APK }
        isSystemPackage = parcel.readInt() == 1
        isExternalPackage = parcel.readInt() == 1
        isSplitApk = parcel.readInt() == 1
        val splitList = parcel.createStringArray()
        splitSourceDirs = splitList
        file = File(filePath)
    }

    constructor(label: String, name: String, version: String, filePath: String, type: Type, isSystemPackage: Boolean = false, isExternalPackage: Boolean = false, isSplitApk: Boolean = false, splitSourceDirs: Array<String>? = null) : this() {
        this.label = label
        this.name = name
        this.version = version
        this.filePath = filePath
        this.type = type
        this.isSystemPackage = isSystemPackage
        this.isExternalPackage = isExternalPackage
        this.isSplitApk = isSplitApk
        this.splitSourceDirs = splitSourceDirs
        file = File(filePath)
    }

    constructor(label: String, name: String) : this() {
        this.label = label
        this.name = name
    }

    /*
     * Serializes package metadata properties into destination parcel.
     */
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(label)
        parcel.writeString(name)
        parcel.writeString(version)
        parcel.writeString(filePath)
        parcel.writeInt(type.ordinal)
        parcel.writeInt(if (isSystemPackage) 1 else 0)
        parcel.writeInt(if (isExternalPackage) 1 else 0)
        parcel.writeInt(if (isSplitApk) 1 else 0)
        parcel.writeStringArray(splitSourceDirs)
    }

    /*
     * Retrieves application icon drawable using system package manager cache or archive inspection.
     */
    fun loadIcon(context: Context): Drawable? {
        val cached = iconMemoryCache[name]
        if (cached != null) {
            return cached
        }

        val loaded = when (type) {
            Type.APK -> {
                var retval: Drawable? = null
                if (!isExternalPackage) {
                    try {
                        retval = context.packageManager.getApplicationIcon(name)
                    } catch (ignored: Exception) {
                    }
                }
                if (retval == null) {
                    retval = context.packageManager.getPackageArchiveInfo(filePath, 0)
                        ?.applicationInfo?.loadIcon(context.packageManager)
                }
                if (retval == null) {
                    try {
                        val apkModule = ApkModule.loadApkFile(File(filePath))
                        val inp = apkModule.listResFiles(
                            apkModule.androidManifest.iconResourceId,
                            ResConfig.parse(ResConfig.Density.HDPI.toString())
                        ).firstOrNull()?.inputSource?.openStream()

                        if (inp != null) {
                            retval = BitmapDrawable(context.resources, BitmapFactory.decodeStream(inp))
                        }
                    } catch (ignored: Exception) {
                    }
                }
                retval
            }

            Type.JAR, Type.DEX ->
                BitmapDrawable(
                    context.resources,
                    Identicon.createFromObject(this.name + this.label)
                )
        }

        if (loaded != null) {
            iconMemoryCache[name] = loaded
        }
        return loaded
    }

    /*
     * Returns parcelable bitmask specification flags.
     */
    override fun describeContents(): Int {
        return 0
    }

    /*
     * Formats string description showing path of source package file.
     */
    override fun toString(): String {
        return String.format("filePath: %s", filePath)
    }

    enum class Type {
        APK, JAR, DEX
    }

    companion object CREATOR : Parcelable.Creator<PackageInfo> {

        val iconMemoryCache = java.util.concurrent.ConcurrentHashMap<String, Drawable>()
        var cachedUserApps: List<PackageInfo> = emptyList()
        var cachedSystemApps: List<PackageInfo> = emptyList()

        /**
         * Get [PackageInfo] for an apk using the [context] and [android.content.pm.PackageInfo] instance.
         */
        fun fromApkPackageInfo(context: Context, pack: android.content.pm.PackageInfo): PackageInfo {
            val appInfo = pack.applicationInfo
            val splits = appInfo?.splitPublicSourceDirs ?: appInfo?.splitSourceDirs
            val hasSplits = splits != null && splits.isNotEmpty()
            return PackageInfo(
                appInfo?.loadLabel(context.packageManager)?.toString() ?: pack.packageName,
                pack.packageName,
                getVersion(pack),
                appInfo?.publicSourceDir ?: appInfo?.sourceDir ?: "",
                Type.APK,
                isSystemPackage(pack),
                isExternalPackage = false,
                isSplitApk = hasSplits,
                splitSourceDirs = splits
            )
        }

        /*
         * Extracts package metadata from an APK file using ARSCLib and PackageManager fallback.
         */
        private fun fromApk(context: Context, file: File, isExternalPackage: Boolean = false): PackageInfo? {
            return try {
                val apkmodule = ApkModule.loadApkFile(file)
                val manifest = apkmodule.androidManifest

                var packageBlock = manifest?.packageBlock
                if (packageBlock == null && manifest != null && apkmodule.tableBlock != null) {
                    val packageId: Int = manifest.guessCurrentPackageId()
                    val tableBlock: TableBlock = apkmodule.tableBlock
                    packageBlock = tableBlock.pickOne(packageId) ?: tableBlock.pickOne()
                }

                var sysPack = false
                val pack = try {
                    context.packageManager.getPackageArchiveInfo(file.canonicalPath, 0)
                } catch (ignored: Exception) {
                    null
                }
                if (!isExternalPackage && pack != null) {
                    sysPack = isSystemPackage(pack)
                }

                val labelFromArsc = try {
                    val ref = manifest?.applicationLabelReference
                    if (packageBlock != null && ref != null) {
                        val entries = packageBlock.getEntries(ref, true)
                        if (entries != null && entries.hasNext()) {
                            entries.next()?.resValue?.valueAsString
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } catch (ignored: Exception) {
                    null
                }

                val resolvedPkgName = apkmodule.packageName
                    ?: pack?.packageName
                    ?: file.nameWithoutExtension.ifBlank { "unknown.package" }
                val resolvedLabel = labelFromArsc?.takeIf { it.isNotBlank() }
                    ?: pack?.applicationInfo?.let { appInfo ->
                        appInfo.sourceDir = file.canonicalPath
                        appInfo.publicSourceDir = file.canonicalPath
                        appInfo.loadLabel(context.packageManager)?.toString()
                    }?.takeIf { it.isNotBlank() }
                    ?: manifest?.applicationLabelString?.takeIf { it.isNotBlank() }
                    ?: resolvedPkgName
                val resolvedVersion = manifest?.versionName
                    ?: pack?.let { getVersion(it) }
                    ?: "1.0"

                PackageInfo(
                    resolvedLabel,
                    resolvedPkgName,
                    resolvedVersion,
                    file.canonicalPath,
                    Type.APK,
                    sysPack,
                    isExternalPackage
                )
            } catch (e: Exception) {
                try {
                    val pack = context.packageManager.getPackageArchiveInfo(file.canonicalPath, 0) ?: return null
                    pack.applicationInfo?.apply {
                        sourceDir = file.canonicalPath
                        publicSourceDir = file.canonicalPath
                    }
                    fromApkPackageInfo(context, pack).apply {
                        this.filePath = file.canonicalPath
                        this.file = file
                        this.isExternalPackage = isExternalPackage
                    }
                } catch (ignored: Exception) {
                    null
                }
            }
        }

        /*
         * Constructs PackageInfo metadata for a JAR archive file.
         */
        private fun fromJar(file: File, type: Type = Type.JAR, isExternalPackage: Boolean = false): PackageInfo? {
            return PackageInfo(
                file.name,
                jarPackageName(file.name),
                (System.currentTimeMillis() / 1000).toString(),
                file.canonicalPath,
                type,
                isExternalPackage = isExternalPackage
            )
        }

        /*
         * Constructs PackageInfo metadata for a DEX or ODEX file.
         */
        private fun fromDex(file: File, isExternalPackage: Boolean = false): PackageInfo? {
            return fromJar(file, Type.DEX, isExternalPackage)
        }

        /*
         * Resolves a local file into a PackageInfo instance based on its extension.
         */
        fun fromFile(context: Context, file: File): PackageInfo? {
            return try {
                when (file.extension.lowercase()) {
                    "apk" -> fromApk(context, file, true)
                    "jar" -> fromJar(file)
                    "dex", "odex" -> fromDex(file)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

        /*
         * Resolves content or file URI to extract a temporary package info file.
         */
        suspend fun fromUri(context: Context, uri: Uri): PackageInfo? {
            var fileName = "imported_app.apk"
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val name = File(path).name
                    if (name.isNotEmpty()) fileName = name
                }
            } else if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrEmpty()) fileName = name
                        }
                    }
                } catch (ignored: Exception) {}
            }
            return fromUri(context, uri, fileName)
        }

        /*
         * Copies URI stream into cache and parses PackageInfo according to file extension.
         */
        suspend fun fromUri(context: Context, uri: Uri, fileName: String): PackageInfo? {
            return try {
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri).use { inputStream ->
                    if (inputStream != null) {
                        copy(inputStream, tempFile)
                    }
                    tempFile
                }
                when (fileName.substringAfterLast('.', "").lowercase()) {
                    "apk" -> fromApk(context, tempFile, true)
                    "jar" -> fromJar(tempFile, isExternalPackage = true)
                    "dex", "odex" -> fromDex(tempFile, true)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
        /*
         * Streams byte chunks from input stream to target file on disk.
         */
        @Throws(IOException::class)
        suspend fun copy(src: InputStream, dst: File) {
            withContext(Dispatchers.IO) {
                FileOutputStream(dst).use { output ->

                    val buffer = ByteArray(10240)
                    var len: Int = src.read(buffer)
                    var readBytes = len
                    while (len > 0) {
                        yield()
                        output.write(buffer, 0, len)
                        len = src.read(buffer)
                        readBytes += len
                    }
                }
            }
        }
        /*
         * Recreates PackageInfo instance from serialized parcel data.
         */
        override fun createFromParcel(parcel: Parcel): PackageInfo {
            return PackageInfo(parcel)
        }

        /*
         * Allocates new array of PackageInfo parcelable items.
         */
        override fun newArray(size: Int): Array<PackageInfo?> {
            return arrayOfNulls(size)
        }
    }
}
