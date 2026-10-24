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

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import java.security.MessageDigest
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

private val NON_LATIN = Pattern.compile("[^\\w-]")
private val WHITESPACE = Pattern.compile("[\\s]")

/*
 * Converts dot-separated package name into JVM bytecode class descriptor format.
 */
fun toClassName(packageName: String): String {
    return "L" + packageName.trim().replace(".", "/")
}

/*
 * Converts input string into URL/filename friendly slug format.
 */
fun toSlug(input: String): String {
    val noWhiteSpace = WHITESPACE.matcher(input).replaceAll("-")
    val normalized = Normalizer.normalize(noWhiteSpace, Normalizer.Form.NFD)
    val slug = NON_LATIN.matcher(normalized).replaceAll("")
    return slug.lowercase(Locale.ENGLISH)
}

/*
 * Hashes input string with requested digest algorithm and returns uppercase hex string.
 */
fun hashString(algorithm: String, input: String): String {
    val hexChars = "0123456789ABCDEF"
    val bytes = MessageDigest
        .getInstance(algorithm)
        .digest(input.toByteArray())
    val result = StringBuilder(bytes.size * 2)
    bytes.forEach {
        val i = it.toInt()
        result.append(hexChars[i shr 4 and 0x0f])
        result.append(hexChars[i and 0x0f])
    }
    return result.toString()
}

/*
 * Returns current timestamp formatted as an ISO-8601 UTC date string.
 */
fun getDate(): String {
    val date = Date(System.currentTimeMillis())
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(date)
}

/*
 * Triggers system garbage collection and yields briefly to reclaim unreferenced memory.
 */
fun cleanMemory() {
    Runtime.getRuntime().gc()
    Thread.sleep(500)
}

/*
 * Retrieves human-readable version name or fallback version code for the given package.
 */
fun getVersion(packageInfo: PackageInfo): String {
    return if (packageInfo.versionName != null)
        packageInfo.versionName!!
    else
        getVersionCode(packageInfo).toString()
}

/*
 * Retrieves long version code compatibility across Android platform versions.
 */
fun getVersionCode(packageInfo: PackageInfo): Number {
    return PackageInfoCompat.getLongVersionCode(packageInfo)
}
