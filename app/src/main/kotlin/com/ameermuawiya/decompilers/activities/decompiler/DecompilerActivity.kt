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

package com.ameermuawiya.decompilers.activities.decompiler

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.decompilers.BaseDecompiler.Companion.isAvailable
import com.ameermuawiya.decompilers.managers.DecompilerTaskManager
import com.ameermuawiya.decompilers.ui.screens.decompiler.DecompilerScreen
import com.ameermuawiya.decompilers.ui.theme.JadecTheme
import com.ameermuawiya.decompilers.utils.UserPreferences
import com.ameermuawiya.decompilers.utils.ktx.sourceDir
import java.io.File
import java.net.URI

class DecompilerActivity : ComponentActivity() {

    private lateinit var packageInfo: PackageInfo
    private var startedCompilation = false
    private val requestStopConfirmation = kotlinx.coroutines.flow.MutableStateFlow(false)

    /*
     * Handles incoming intents when the activity is already in the foreground.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("confirmStop", false) || intent.action == com.ameermuawiya.decompilers.Constants.WORKER.ACTION.STOP) {
            requestStopConfirmation.value = true
            intent.removeExtra("confirmStop")
        }
    }

    /*
     * Initializes decompiler selection compose UI and loads input package information.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        val fromNotification = intent.getBooleanExtra("fromNotification", false)
        if (intent.getBooleanExtra("confirmStop", false) || intent.action == com.ameermuawiya.decompilers.Constants.WORKER.ACTION.STOP) {
            requestStopConfirmation.value = true
            intent.removeExtra("confirmStop")
        }
        loadPackageInfoFromIntent()

        if (!::packageInfo.isInitialized) {
            Toast.makeText(this, R.string.cannotDecompileFile, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            /*
             * Handles back button presses by notifying user of background execution or finishing activity.
             */
            override fun handleOnBackPressed() {
                val activeTaskState = DecompilerTaskManager.taskState.value
                val isProcessing = activeTaskState == DecompilerTaskManager.TaskState.RUNNING && DecompilerTaskManager.activePackageInfo.value?.name == packageInfo.name
                if (isProcessing) {
                    Toast.makeText(
                        this@DecompilerActivity,
                        R.string.decompiler_activity_toast_background,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                if (activeTaskState == DecompilerTaskManager.TaskState.COMPLETED || activeTaskState == DecompilerTaskManager.TaskState.STOPPED || fromNotification) {
                    DecompilerTaskManager.clearActiveTask(applicationContext)
                }
                finish()
            }
        })

        val rawPrefs = getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
        val initialDarkMode = rawPrefs.getBoolean("darkMode", false)
        val initialDynamic = rawPrefs.getBoolean("dynamicColor", true)
        val themeModeSetting = rawPrefs.getString("themeMode", if (initialDarkMode) "dark" else "system") ?: "system"

        setContent {
            val activeTaskState by DecompilerTaskManager.taskState.collectAsState()
            val activePackage by DecompilerTaskManager.activePackageInfo.collectAsState()
            val activeFolder by DecompilerTaskManager.activeFolderName.collectAsState()
            val taskLogs by DecompilerTaskManager.accumulatedLogs.collectAsState()
            val taskStatusText by DecompilerTaskManager.statusText.collectAsState()
            val taskMemoryUsage by DecompilerTaskManager.memoryUsage.collectAsState()
            val taskProgress by DecompilerTaskManager.progressPercentage.collectAsState()
            val showStopDialog by requestStopConfirmation.collectAsState()

            val isThisPackageActive = activePackage?.name == packageInfo.name
            val isProcessing = if (isThisPackageActive) {
                activeTaskState == DecompilerTaskManager.TaskState.RUNNING
            } else {
                false
            }
            val isCompleted = if (isThisPackageActive) {
                activeTaskState == DecompilerTaskManager.TaskState.COMPLETED
            } else {
                fromNotification
            }
            val isStopped = isThisPackageActive && activeTaskState == DecompilerTaskManager.TaskState.STOPPED
            val statusText = if (isThisPackageActive && taskStatusText.isNotEmpty()) {
                taskStatusText
            } else {
                ""
            }
            val memoryUsage = if (isThisPackageActive && taskMemoryUsage.isNotEmpty()) {
                taskMemoryUsage
            } else {
                ""
            }
            val logs = if (isThisPackageActive) taskLogs else ""

            JadecTheme(
                themeMode = themeModeSetting,
                dynamicColor = initialDynamic
            ) {
                DecompilerScreen(
                    packageInfo = packageInfo,
                    isProcessing = isProcessing,
                    progressPercentage = taskProgress,
                    statusText = statusText,
                    memoryUsage = memoryUsage,
                    isCompleted = isCompleted,
                    isStopped = isStopped,
                    targetFolderName = if (isThisPackageActive) activeFolder else "",
                    logs = logs,
                    showStopConfirmation = showStopDialog,
                    onRequestStopConfirmation = { requestStopConfirmation.value = true },
                    onDismissStopConfirmation = { requestStopConfirmation.value = false },
                    onStopDecompileClick = {
                        requestStopConfirmation.value = false
                        DecompilerTaskManager.stopActiveTask(applicationContext)
                    },
                    onBackClick = {
                        if (isProcessing) {
                            Toast.makeText(
                                this@DecompilerActivity,
                                R.string.decompiler_activity_toast_background,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        if (activeTaskState == DecompilerTaskManager.TaskState.COMPLETED || activeTaskState == DecompilerTaskManager.TaskState.STOPPED || fromNotification) {
                            DecompilerTaskManager.clearActiveTask(applicationContext)
                        }
                        finish()
                    },
                    onStartDecompileClick = { decompilerKey, index ->
                        startProcess(decompilerKey, index)
                    }
                )
            }
        }
    }

    /*
     * Reads target application package information from intent extras or data URI.
     */
    private fun loadPackageInfoFromIntent() {
        val streamUri = if (intent.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
        } else {
            intent.data
        }

        if (streamUri != null) {
            val resolved = kotlinx.coroutines.runBlocking {
                PackageInfo.fromUri(this@DecompilerActivity, streamUri)
            }
            if (resolved != null) {
                packageInfo = resolved
                return
            }
        }

        if (intent.dataString.isNullOrEmpty()) {
            if (intent.hasExtra("packageInfo")) {
                val passed = IntentCompat.getParcelableExtra(intent, "packageInfo", PackageInfo::class.java)
                if (passed != null) {
                    val active = DecompilerTaskManager.activePackageInfo.value
                    if (active != null && active.name == passed.name && active.filePath.isNotEmpty()) {
                        packageInfo = active
                    } else if (passed.filePath.isNotEmpty()) {
                        packageInfo = passed
                    } else {
                        try {
                            val pm = packageManager
                            val pi = pm.getPackageInfo(passed.name, 0)
                            packageInfo = PackageInfo.fromApkPackageInfo(this, pi)
                        } catch (e: Exception) {
                            packageInfo = passed
                        }
                    }
                }
            } else {
                val active = DecompilerTaskManager.activePackageInfo.value
                if (active != null) {
                    packageInfo = active
                } else {
                    Toast.makeText(this, R.string.errorLoadingInputFile, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } else {
            val info = try {
                val dataStr = intent.dataString
                if (!dataStr.isNullOrBlank()) {
                    PackageInfo.fromFile(this, File(URI.create(dataStr)).canonicalFile)
                } else {
                    null
                }
            } catch (e: Exception) {
                intent.data?.let { uri ->
                    kotlinx.coroutines.runBlocking {
                        PackageInfo.fromUri(this@DecompilerActivity, uri)
                    }
                }
            }
            if (info != null) {
                packageInfo = info
            }
        }
    }

    /*
     * Cleans up receiver registration and cached temporary files on activity destruction.
     */
    override fun onDestroy() {
        if (::packageInfo.isInitialized) {
            val active = DecompilerTaskManager.activePackageInfo.value
            val isStillRunning = DecompilerTaskManager.taskState.value == DecompilerTaskManager.TaskState.RUNNING && active?.name == packageInfo.name
            if (packageInfo.isExternalPackage && !startedCompilation && !isStillRunning) {
                val tempFile = File(cacheDir, packageInfo.filePath.substring(packageInfo.filePath.lastIndexOf("/") + 1))
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
        super.onDestroy()
    }

    /*
     * Prepares configuration parameters and launches the background decompiler worker.
     */
    private fun startProcess(decompiler: String, decompilerIndex: Int) {
        if (!isAvailable(decompiler)) {
            Toast.makeText(this, R.string.decompilerUnavailableExplanation, Toast.LENGTH_LONG).show()
            return
        }
        startedCompilation = true
        DecompilerTaskManager.startDecompilation(applicationContext, packageInfo, decompiler, decompilerIndex)
    }
}
