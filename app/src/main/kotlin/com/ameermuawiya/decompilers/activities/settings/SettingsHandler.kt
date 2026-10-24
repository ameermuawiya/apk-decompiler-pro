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

package com.ameermuawiya.decompilers.activities.settings

import android.content.Context
import com.ameermuawiya.decompilers.utils.ktx.appStorage
import io.reactivex.Observable

class SettingsHandler(private var context: Context) {

    /*
     * Deletes all decompiled source files recursively from app storage and cache directories.
     */
    fun deleteHistory(): Observable<Any> {
        return Observable.fromCallable {
            appStorage.resolve("sources").deleteRecursively()
            context.cacheDir.resolve("sources").deleteRecursively()
            com.ameermuawiya.decompilers.data.PackageInfo.iconMemoryCache.clear()
            true
        }
    }
}