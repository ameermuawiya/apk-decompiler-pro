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

package com.ameermuawiya.decompilers.utils.logging

import android.util.Log
import timber.log.Timber

class ProductionTree : Timber.Tree() {

    /*
     * Filters verbose and debug logs while writing warnings and errors to system logs.
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == Log.VERBOSE || priority == Log.DEBUG || priority == Log.INFO) {
            return
        }

        val logTag = tag ?: "ApkDecompilerPro"
        if (t != null) {
            Log.println(priority, logTag, "$message\n${Log.getStackTraceString(t)}")
        } else {
            Log.println(priority, logTag, message)
        }
    }
}
