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
import java.io.File

class FileItem() : Parcelable {

    var file: File = File("/")
    var fileSize: String = ""
    var metaInfo: String? = ""

    val name: String?
        get() = if (metaInfo == "parent") ".." else file.name

    constructor(parcel: Parcel) : this() {
        file = File(parcel.readString() ?: "/")
        fileSize = parcel.readString() ?: ""
        metaInfo = parcel.readString()
    }

    constructor(file: File, fileSize: String, metaInfo: String) : this() {
        this.file = file
        this.fileSize = fileSize
        this.metaInfo = metaInfo
    }

    /*
     * Writes file item properties to target parcel for IPC transport.
     */
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(file.canonicalPath)
        parcel.writeString(fileSize)
        parcel.writeString(metaInfo)
    }

    /*
     * Returns parcelable bitmask descriptors for file items.
     */
    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<FileItem> {
        /*
         * Recreates a FileItem instance from incoming parcel data.
         */
        override fun createFromParcel(parcel: Parcel): FileItem {
            return FileItem(parcel)
        }

        /*
         * Instantiates an empty array for parcelable FileItem collections.
         */
        override fun newArray(size: Int): Array<FileItem?> {
            return arrayOfNulls(size)
        }
    }
}