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

package com.ameermuawiya.decompilers.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.utils.UserPreferences
import com.ameermuawiya.decompilers.utils.ktx.isSystemPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isLoading: Boolean = PackageInfo.cachedUserApps.isEmpty(),
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val selectedTab: Int = 0, // 0 = User Apps, 1 = System Apps
    val userApps: List<PackageInfo> = PackageInfo.cachedUserApps,
    val systemApps: List<PackageInfo> = PackageInfo.cachedSystemApps,
    val statusMessage: String = ""
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /*
     * Asynchronously loads installed applications divided into user and system categories with icon pre-loading.
     */
    fun loadInstalledApps(context: Context, force: Boolean = false, isPullToRefresh: Boolean = false) {
        val hasCache = PackageInfo.cachedUserApps.isNotEmpty() || PackageInfo.cachedSystemApps.isNotEmpty()
        if (!force && hasCache && _uiState.value.userApps.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            if (isPullToRefresh) {
                _uiState.value = _uiState.value.copy(isRefreshing = true)
            } else if (!hasCache) {
                _uiState.value = _uiState.value.copy(isLoading = true, isRefreshing = false)
            }
            val startTime = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val packages = pm.getInstalledPackages(0)
                    val userList = ArrayList<PackageInfo>(packages.size)
                    val systemList = ArrayList<PackageInfo>(packages.size)

                    packages.forEach { pack ->
                        val isSys = isSystemPackage(pack)
                        val pkgInfo = PackageInfo.fromApkPackageInfo(context, pack)
                        
                        try {
                            if (!PackageInfo.iconMemoryCache.containsKey(pack.packageName)) {
                                val icon = pack.applicationInfo?.loadIcon(pm)
                                if (icon != null) {
                                    PackageInfo.iconMemoryCache[pack.packageName] = icon
                                }
                            }
                        } catch (ignored: Exception) {
                        }

                        if (isSys) {
                            systemList.add(pkgInfo)
                        } else {
                            userList.add(pkgInfo)
                        }
                    }

                    val distinctUserList = userList.distinctBy { "${it.name}_${it.filePath}" }.sortedBy { it.label.lowercase() }
                    val distinctSystemList = systemList.distinctBy { "${it.name}_${it.filePath}" }.sortedBy { it.label.lowercase() }

                    PackageInfo.cachedUserApps = distinctUserList
                    PackageInfo.cachedSystemApps = distinctSystemList

                    if (isPullToRefresh) {
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed < 1500L) {
                            kotlinx.coroutines.delay(1500L - elapsed)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            userApps = distinctUserList,
                            systemApps = distinctSystemList
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
                    }
                }
            }
        }
    }

    /*
     * Explicitly refreshes the installed applications list triggered by user pull gesture.
     */
    fun refreshInstalledApps(context: Context) {
        loadInstalledApps(context, force = true, isPullToRefresh = true)
    }

    /*
     * Updates search filter text in the home screen.
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /*
     * Switches between User Apps and System Apps sub-tabs.
     */
    fun onTabSelected(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }
}
