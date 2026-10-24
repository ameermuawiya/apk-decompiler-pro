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

package com.ameermuawiya.decompilers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ameermuawiya.decompilers.activities.decompiler.DecompilerActivity
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.managers.ReleaseInfo
import com.ameermuawiya.decompilers.managers.UpdateBottomSheet
import com.ameermuawiya.decompilers.managers.UpdateManager
import com.ameermuawiya.decompilers.ui.screens.history.HistoryScreen
import com.ameermuawiya.decompilers.ui.screens.history.HistoryViewModel
import com.ameermuawiya.decompilers.ui.screens.home.HomeScreen
import com.ameermuawiya.decompilers.ui.screens.home.HomeViewModel
import com.ameermuawiya.decompilers.ui.screens.settings.SettingsScreen
import com.ameermuawiya.decompilers.ui.theme.JadecTheme
import com.ameermuawiya.decompilers.utils.UserPreferences
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var startActivityResultLauncher: ActivityResultLauncher<Intent>
    private var isCopyingFile by mutableStateOf(false)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    /*
     * Initializes notification permissions, file pickers, theme states, and update check.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val rawPrefs = getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
        val initialDarkMode = rawPrefs.getBoolean("darkMode", false)
        val initialDynamic = rawPrefs.getBoolean("dynamicColor", true)

        startActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                handlePickedFileUri(uri)
            } else {
                Toast.makeText(this, getString(R.string.landing_activity_toast_no_file_chosen), Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            val savedThemeMode = rawPrefs.getString("themeMode", if (initialDarkMode) "dark" else "system") ?: "system"
            var themeMode by remember { mutableStateOf(savedThemeMode) }
            var dynamicColor by remember { mutableStateOf(initialDynamic) }
            var launchUpdateInfo by remember { mutableStateOf<ReleaseInfo?>(null) }

            LaunchedEffect(Unit) {
                UpdateManager.checkForUpdates(
                    context = this@MainActivity,
                    scope = lifecycleScope,
                    isManual = false,
                    onUpdateAvailable = { releaseInfo ->
                        launchUpdateInfo = releaseInfo
                    }
                )
            }

            JadecTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                MainAppContent(
                    onPickFile = { pickFileFromStorage() },
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        rawPrefs.edit().putString("themeMode", it).putBoolean("darkMode", it == "dark").apply()
                    },
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = {
                        dynamicColor = it
                        rawPrefs.edit().putBoolean("dynamicColor", it).apply()
                    }
                )

                launchUpdateInfo?.let { releaseInfo ->
                    UpdateBottomSheet(
                        releaseInfo = releaseInfo,
                        onDismiss = { launchUpdateInfo = null }
                    )
                }

                if (isCopyingFile) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text(stringResource(R.string.landing_activity_dialog_title_please_wait)) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(stringResource(R.string.landing_activity_dialog_message_copying))
                            }
                        },
                        confirmButton = {}
                    )
                }
            }
        }
    }

    /*
     * Launches the system file chooser for selecting APK, APKS, DEX, or JAR files.
     */
    private fun pickFileFromStorage() {
        var intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent = Intent.createChooser(intent, getString(R.string.landing_activity_chooser_title))
        startActivityResultLauncher.launch(intent)
    }

    /*
     * Copies selected file from URI to cache and opens decompiler options.
     */
    private fun handlePickedFileUri(uri: Uri) {
        var fileName: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        fileName = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (fileName == null) {
            val path = uri.path
            val mark = path?.lastIndexOf("/") ?: -1
            if (mark != -1 && path != null) {
                fileName = path.substring(mark + 1)
            }
        }
        val resolvedName = fileName ?: "unknown.apk"

        isCopyingFile = true
        lifecycleScope.launch {
            val pkgInfo = PackageInfo.fromUri(this@MainActivity, uri, resolvedName)
            isCopyingFile = false
            if (pkgInfo != null) {
                val intent = Intent(this@MainActivity, DecompilerActivity::class.java)
                intent.putExtra("packageInfo", pkgInfo)
                startActivity(intent)
            } else {
                Toast.makeText(this@MainActivity, getString(R.string.cannotDecompileFile), Toast.LENGTH_LONG).show()
            }
        }
    }
}

/*
 * Renders main scaffold without outer double toolbar, hosting bottom navigation bar and active screens.
 */
@Composable
fun MainAppContent(
    onPickFile: () -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val homeViewModel: HomeViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = stringResource(R.string.nav_home)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_home)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            if (selectedTab == 1) Icons.Filled.History else Icons.Outlined.History,
                            contentDescription = stringResource(R.string.nav_history)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_history)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel = homeViewModel, onPickFileClick = onPickFile)
                1 -> HistoryScreen(viewModel = historyViewModel)
                2 -> SettingsScreen(
                    currentThemeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = onDynamicColorChange
                )
            }
        }
    }
}
