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

package com.ameermuawiya.decompilers.ui.screens.history

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.data.SourceInfo
import com.ameermuawiya.decompilers.utils.UserPreferences
import com.ameermuawiya.decompilers.utils.ktx.appStorage
import com.ameermuawiya.decompilers.utils.ktx.getAppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StagedDelete(
    val item: SourceInfo,
    val index: Int,
    val deleteFromDisk: Boolean,
    val canonicalPath: String
)

data class HistoryUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val historyItems: List<SourceInfo> = emptyList(),
    val computedSizes: Map<String, Long> = emptyMap()
)

class HistoryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var pendingDelete: StagedDelete? = null
    private var pendingDeleteJob: Job? = null

    /*
     * Loads previously decompiled application source history from device storage.
     */
    fun loadHistory(context: Context, force: Boolean = false, isPullToRefresh: Boolean = false) {
        if (!force && _uiState.value.historyItems.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            if (isPullToRefresh) {
                _uiState.value = _uiState.value.copy(isRefreshing = true)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, isRefreshing = false)
            }
            withContext(Dispatchers.IO) {
                val list = ArrayList<SourceInfo>()
                try {
                    val userPrefs = UserPreferences(context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE))
                    val hiddenPaths = userPrefs.hiddenHistoryPaths

                    val dirsToCheck = listOfNotNull(
                        getAppStorage(context).resolve("sources").takeIf { it.exists() },
                        appStorage.resolve("sources").takeIf { it.exists() },
                        context.cacheDir.resolve("sources").takeIf { it.exists() }
                    ).distinctBy { try { it.canonicalPath } catch (e: Exception) { it.absolutePath } }

                    val seenDirs = mutableSetOf<String>()
                    dirsToCheck.forEach { sourcesDir ->
                        val files = sourcesDir.listFiles()
                        files?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }?.forEach { file ->
                            val canPath = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
                            if (hiddenPaths.contains(canPath) || hiddenPaths.contains(file.absolutePath)) {
                                return@forEach
                            }
                            if (SourceInfo.exists(file) && seenDirs.add(canPath)) {
                                val item = SourceInfo.from(file)
                                list.add(item)
                                try {
                                    val iconKey = item.packageName.ifBlank { item.packageLabel }
                                    if (!PackageInfo.iconMemoryCache.containsKey(iconKey)) {
                                        val iconFile = item.sourceDirectory.resolve("icon.png")
                                        if (iconFile.exists()) {
                                            val bmp = BitmapFactory.decodeFile(iconFile.canonicalPath)
                                            if (bmp != null) {
                                                PackageInfo.iconMemoryCache[iconKey] = BitmapDrawable(context.resources, bmp)
                                            }
                                        }
                                    }
                                } catch (ignored: Exception) {
                                }
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
                list.sortByDescending { it.sourceDirectory.lastModified() }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        historyItems = list
                    )
                }

                list.filter { it.sourceSize <= 0L }.forEach { item ->
                    launch(Dispatchers.IO) {
                        val size = item.computeAndCacheSize()
                        val canonPath = try { item.sourceDirectory.canonicalPath } catch (e: Exception) { item.sourceDirectory.absolutePath }
                        val absPath = item.sourceDirectory.absolutePath
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                computedSizes = _uiState.value.computedSizes + (canonPath to size) + (absPath to size)
                            )
                        }
                    }
                }
            }
        }
    }

    /*
     * Explicitly refreshes history items when triggered by a pull gesture.
     */
    fun refreshHistory(context: Context) {
        loadHistory(context, force = true, isPullToRefresh = true)
    }

    /*
     * Stages history item for deletion and immediately removes it from the UI list.
     */
    fun stageDeleteItem(context: Context, sourceInfo: SourceInfo, deleteFromDisk: Boolean) {
        commitPendingDelete(context)

        val currentItems = _uiState.value.historyItems
        val targetDir = sourceInfo.sourceDirectory
        val targetCanonical = try { targetDir.canonicalPath } catch (e: Exception) { targetDir.absolutePath }
        val targetAbsolute = targetDir.absolutePath
        val index = currentItems.indexOfFirst {
            val itCanon = try { it.sourceDirectory.canonicalPath } catch (e: Exception) { it.sourceDirectory.absolutePath }
            itCanon == targetCanonical || it.sourceDirectory.absolutePath == targetAbsolute
        }.coerceAtLeast(0)

        pendingDelete = StagedDelete(
            item = sourceInfo,
            index = index,
            deleteFromDisk = deleteFromDisk,
            canonicalPath = targetCanonical
        )

        _uiState.value = _uiState.value.copy(
            historyItems = currentItems.filter {
                val itCanon = try { it.sourceDirectory.canonicalPath } catch (e: Exception) { it.sourceDirectory.absolutePath }
                itCanon != targetCanonical && it.sourceDirectory.absolutePath != targetAbsolute
            }
        )

        pendingDeleteJob = viewModelScope.launch {
            delay(5000L)
            commitPendingDelete(context)
        }
    }

    /*
     * Restores staged deletion item back into visible history list at its original index.
     */
    fun undoDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val staged = pendingDelete ?: return
        pendingDelete = null

        val currentItems = _uiState.value.historyItems.toMutableList()
        val insertIndex = staged.index.coerceIn(0, currentItems.size)
        currentItems.add(insertIndex, staged.item)
        _uiState.value = _uiState.value.copy(historyItems = currentItems)
    }

    /*
     * Finalizes pending deletion by deleting files from disk or recording in hidden preferences.
     */
    fun commitPendingDelete(context: Context) {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val staged = pendingDelete ?: return
        pendingDelete = null

        val targetDir = staged.item.sourceDirectory
        val iconKey = staged.item.packageName.ifBlank { staged.item.packageLabel }
        PackageInfo.iconMemoryCache.remove(iconKey)

        if (staged.deleteFromDisk) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (targetDir.exists()) {
                        targetDir.deleteRecursively()
                    }
                    val folderName = targetDir.name
                    val appStorageDir = getAppStorage(context).resolve("sources").resolve(folderName)
                    if (appStorageDir.exists()) {
                        appStorageDir.deleteRecursively()
                    }
                    val cacheDir = context.cacheDir.resolve("sources").resolve(folderName)
                    if (cacheDir.exists()) {
                        cacheDir.deleteRecursively()
                    }
                } catch (ignored: Exception) {
                }
            }
        } else {
            val userPrefs = UserPreferences(context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE))
            userPrefs.hideHistoryPath(staged.canonicalPath)
            userPrefs.hideHistoryPath(staged.item.sourceDirectory.absolutePath)
        }
    }

    /*
     * Removes decompiled source code files immediately bypassing undo queue.
     */
    fun deleteHistoryItem(context: Context, sourceInfo: SourceInfo) {
        stageDeleteItem(context, sourceInfo, deleteFromDisk = true)
        commitPendingDelete(context)
    }
}
