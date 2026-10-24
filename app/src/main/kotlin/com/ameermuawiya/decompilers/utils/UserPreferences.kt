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

import android.content.SharedPreferences

class UserPreferences(private val prefs: SharedPreferences) {

    companion object {
        const val NAME = "user_preferences"
    }

    interface DEFAULTS {
        companion object {
            const val DARK_MODE = false
            const val SHOW_MEMORY_USAGE = true
            const val MEMORY_THRESHOLD = 80
            const val IGNORE_LIBRARIES = true
            const val KEEP_INTERMEDIATE_FILES = false
            const val CHUNK_SIZE = 4000
            const val MAX_ATTEMPTS = 2
            const val MAX_MEMORY_MB = 0
        }
    }

    val ignoreLibraries: Boolean
        get() = prefs.getBoolean("ignoreLibraries", DEFAULTS.IGNORE_LIBRARIES)

    val keepIntermediateFiles: Boolean
        get() = prefs.getBoolean("keepIntermediateFiles", DEFAULTS.KEEP_INTERMEDIATE_FILES)

    val darkMode: Boolean
        get() = prefs.getBoolean("darkMode", DEFAULTS.DARK_MODE)

    val showMemoryUsage: Boolean
        get() = prefs.getBoolean("showMemoryUsage", DEFAULTS.SHOW_MEMORY_USAGE)

    val workingDirectoryPath: String
        get() = prefs.getString("workingDirectoryPath", "") ?: ""

    val workingDirectoryUri: String
        get() = prefs.getString("workingDirectoryUri", "") ?: ""

    val selectedDecompilerIndex: Int
        get() = prefs.getInt("selectedDecompilerIndex", 0)

    val themeMode: String
        get() = prefs.getString("themeMode", if (darkMode) "dark" else "system") ?: "system"

    val dynamicColor: Boolean
        get() = prefs.getBoolean("dynamicColor", true)

    val chunkSize: Int
        get() = try {
            val raw = prefs.getString("chunkSize", DEFAULTS.CHUNK_SIZE.toString())?.trim()?.toInt()
                ?: DEFAULTS.CHUNK_SIZE
            if (raw <= 500) DEFAULTS.CHUNK_SIZE else raw
        } catch (ignored: Exception) {
            DEFAULTS.CHUNK_SIZE
        }

    val maxAttempts: Int
        get() = try {
            prefs.getString("maxAttempts", DEFAULTS.MAX_ATTEMPTS.toString())?.trim()?.toInt()
                    ?: DEFAULTS.MAX_ATTEMPTS
        } catch (ignored: Exception) {
            DEFAULTS.MAX_ATTEMPTS
        }

    val maxMemoryMb: Int
        get() = prefs.getInt("maxMemoryMb", DEFAULTS.MAX_MEMORY_MB)

    val isAutoMemory: Boolean
        get() = maxMemoryMb == 0

    /*
     * Calculates effective initial heap limit in MB based on user setting or live runtime usage.
     */
    fun getEffectiveMaxMemoryMb(packageSizeBytes: Long): Int {
        val configured = maxMemoryMb
        if (configured > 0) return configured
        val runtime = Runtime.getRuntime()
        val usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).coerceAtLeast(0L)
        return computeDynamicMaxMemoryMb(usedMb, 512)
    }

    /*
     * Dynamically scales selected memory ceiling up or down based on live heap usage.
     */
    fun computeDynamicMaxMemoryMb(usedMb: Long, currentMaxMb: Int): Int {
        val configured = maxMemoryMb
        if (configured > 0) {
            if (usedMb >= (configured * 0.82).toLong()) {
                Runtime.getRuntime().gc()
            }
            val baseLimit = maxOf(configured, currentMaxMb)
            return if (usedMb >= baseLimit - 32) {
                (((usedMb + 128).toInt() + 127) / 128) * 128
            } else {
                baseLimit
            }
        }

        var target = if (currentMaxMb >= 512) currentMaxMb else 512
        if (usedMb >= (target * 0.78).toLong() || (target - usedMb) < 96L) {
            val needed = ((usedMb + 192).toInt().coerceAtLeast(target + 256))
            target = ((needed + 255) / 256) * 256
        } else if (target > 512 && usedMb < (target * 0.38).toLong()) {
            val reduced = (((usedMb * 2 + 128).toInt() + 255) / 256) * 256
            target = reduced.coerceAtLeast(512)
        }
        if (usedMb >= target - 32) {
            target = (((usedMb + 192).toInt() + 255) / 256) * 256
        }
        return target.coerceAtLeast(512)
    }

    val customIgnoredLibsString: String
        get() = prefs.getString("customIgnoredList", "") ?: ""

    val customIgnoredLibs: List<String>
        get() {
            val raw = customIgnoredLibsString
            if (raw.isBlank()) return emptyList()
            return raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        }

    /*
     * Saves updated list of custom ignored library prefixes into shared preferences.
     */
    fun setCustomIgnoredList(list: List<String>) {
        prefs.edit().putString("customIgnoredList", list.joinToString("\n")).apply()
    }

    val memoryThreshold: Int
        get() = try {
            prefs.getString(
                "memoryThreshold",
                DEFAULTS.MEMORY_THRESHOLD.toString()
            )?.trim()?.toInt()
                    ?: DEFAULTS.MEMORY_THRESHOLD
        } catch (ignored: Exception) {
            DEFAULTS.MEMORY_THRESHOLD
        }

    val hiddenHistoryPaths: Set<String>
        get() = prefs.getStringSet("hidden_history_paths", emptySet()) ?: emptySet()

    /*
     * Records project path in hidden history preferences so it is excluded from history list.
     */
    fun hideHistoryPath(path: String) {
        val updated = hiddenHistoryPaths.toMutableSet()
        updated.add(path)
        prefs.edit().putStringSet("hidden_history_paths", updated).apply()
    }

    /*
     * Removes project path from hidden history preferences restoring its display visibility.
     */
    fun unhideHistoryPath(path: String) {
        val updated = hiddenHistoryPaths.toMutableSet()
        updated.remove(path)
        prefs.edit().putStringSet("hidden_history_paths", updated).apply()
    }
}
