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

package com.ameermuawiya.decompilers.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ameermuawiya.decompilers.Constants
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.decompilers.BaseDecompiler
import com.ameermuawiya.decompilers.utils.UserPreferences
import com.ameermuawiya.decompilers.utils.ktx.getAppStorage
import com.ameermuawiya.decompilers.utils.ktx.sourceDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Manages background decompilation lifecycle, progress state, and persistent task notifications.
 */
object DecompilerTaskManager {

    enum class TaskState {
        IDLE,
        RUNNING,
        COMPLETED,
        STOPPED,
        FAILED
    }

    data class TrackedTask(
        val packageInfo: PackageInfo,
        val engineKey: String,
        val state: TaskState,
        val folderName: String,
        val completionTimestamp: Long = 0L,
        val isViewed: Boolean = false
    )

    @Volatile
    var isStopping = false
        private set

    private val scope = CoroutineScope(Dispatchers.Main)
    private var observeJob: kotlinx.coroutines.Job? = null
    private var appContext: Context? = null
    private var isReceiverRegistered = false

    private val _taskState = MutableStateFlow(TaskState.IDLE)
    val taskState: StateFlow<TaskState> = _taskState.asStateFlow()

    private val _trackedTasks = MutableStateFlow<List<TrackedTask>>(emptyList())
    val trackedTasks: StateFlow<List<TrackedTask>> = _trackedTasks.asStateFlow()

    private val _activePackageInfo = MutableStateFlow<PackageInfo?>(null)
    val activePackageInfo: StateFlow<PackageInfo?> = _activePackageInfo.asStateFlow()

    private val _activeDecompiler = MutableStateFlow("jadx")
    val activeDecompiler: StateFlow<String> = _activeDecompiler.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _memoryUsage = MutableStateFlow("")
    val memoryUsage: StateFlow<String> = _memoryUsage.asStateFlow()

    private val _progressPercentage = MutableStateFlow(0f)
    val progressPercentage: StateFlow<Float> = _progressPercentage.asStateFlow()

    private val _accumulatedLogs = MutableStateFlow("")
    val accumulatedLogs: StateFlow<String> = _accumulatedLogs.asStateFlow()

    private val logLinesList = ArrayList<String>()
    private var logFlushJob: kotlinx.coroutines.Job? = null
    private val logLock = Any()
    private var lastStatusTextTime = 0L

    private val progressReceiver = object : BroadcastReceiver() {
        /*
         * Dispatches broadcast events into reactive state flows for UI consumption.
         */
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isStopping || _taskState.value != TaskState.RUNNING) return
            if (intent != null) {
                val title = intent.getStringExtra(Constants.WORKER.STATUS_TITLE)
                val msg = intent.getStringExtra(Constants.WORKER.STATUS_MESSAGE)
                val type = intent.getStringExtra(Constants.WORKER.STATUS_TYPE)

                if (type == "memory" || title.equals("memory", ignoreCase = true)) {
                    if (!msg.isNullOrEmpty()) {
                        _memoryUsage.value = msg
                    }
                } else {
                    if (!msg.isNullOrEmpty()) {
                        val formatted = if (!title.isNullOrEmpty()) "$title: $msg" else msg
                        val now = System.currentTimeMillis()
                        val isPriority = formatted.startsWith("[STEP]") || formatted.startsWith("[ERROR]") || formatted.startsWith("[WARN]")
                        if (isPriority || (now - lastStatusTextTime >= 100L)) {
                            _statusText.value = formatted
                            lastStatusTextTime = now
                        }
                        appendLog(formatted)
                    }
                }
            }
        }
    }

    /*
     * Initializes global manager state, restores active task references, and hooks into WorkManager.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        getAppStorage(context.applicationContext)
        restoreFromPreferences(context)
    }

    /*
     * Restores previous active task metadata from SharedPreferences if workers are still active.
     */
    private fun restoreFromPreferences(context: Context) {
        val prefs = context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
        val savedPkgName = prefs.getString("active_task_pkg_name", null) ?: return
        val savedLabel = prefs.getString("active_task_label", savedPkgName) ?: savedPkgName
        val savedFilePath = prefs.getString("active_task_file_path", "") ?: ""
        val savedEngine = prefs.getString("active_task_engine", "jadx") ?: "jadx"
        val savedState = prefs.getString("active_task_state", TaskState.IDLE.name) ?: TaskState.IDLE.name

        val info = PackageInfo(savedLabel, savedPkgName)
        info.filePath = savedFilePath
        if (savedFilePath.isNotEmpty()) {
            info.file = File(savedFilePath)
        }
        _activePackageInfo.value = info
        _activeDecompiler.value = savedEngine

        val activeFolder = prefs.getString("active_task_folder", savedPkgName) ?: savedPkgName
        scope.launch {
            try {
                val workManager = WorkManager.getInstance(context)
                val workInfos = workManager.getWorkInfosByTag(savedPkgName).get()
                if (!workInfos.isNullOrEmpty()) {
                    val anyRunning = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                    if (anyRunning) {
                        _taskState.value = TaskState.RUNNING
                        _statusText.value = context.getString(R.string.gettingReady)
                        registerReceiverForPackage(context, savedPkgName)
                        observeWork(context, savedPkgName, activeFolder)
                    } else {
                        _taskState.value = TaskState.IDLE
                        saveToPreferences(context, TaskState.IDLE)
                    }
                } else {
                    _taskState.value = TaskState.IDLE
                    saveToPreferences(context, TaskState.IDLE)
                }
            } catch (ignored: Exception) {
                _taskState.value = TaskState.IDLE
            }
        }
    }

    /*
     * Persists active task state into SharedPreferences for cross-process survival.
     */
    private fun saveToPreferences(context: Context, state: TaskState) {
        val prefs = context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
        val info = _activePackageInfo.value
        if (info != null && state != TaskState.IDLE) {
            prefs.edit()
                .putString("active_task_pkg_name", info.name)
                .putString("active_task_label", info.label)
                .putString("active_task_file_path", info.filePath)
                .putString("active_task_engine", _activeDecompiler.value)
                .putString("active_task_state", state.name)
                .apply()
        } else {
            prefs.edit()
                .remove("active_task_pkg_name")
                .remove("active_task_label")
                .remove("active_task_file_path")
                .remove("active_task_engine")
                .remove("active_task_state")
                .apply()
        }
    }

    /*
     * Registers application-level broadcast receiver to capture worker progress updates.
     */
    private fun registerReceiverForPackage(context: Context, packageName: String) {
        val targetContext = context.applicationContext ?: context
        appContext = targetContext
        unregisterReceiver()
        val statusIntentFilter = IntentFilter(Constants.WORKER.ACTION.BROADCAST + packageName)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                targetContext.registerReceiver(progressReceiver, statusIntentFilter, Context.RECEIVER_EXPORTED)
            } else {
                targetContext.registerReceiver(progressReceiver, statusIntentFilter)
            }
            isReceiverRegistered = true
        } catch (ignored: Exception) {
            isReceiverRegistered = false
        }
    }

    /*
     * Safely unregisters progress broadcast receiver from application context.
     */
    fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                appContext?.unregisterReceiver(progressReceiver)
            } catch (ignored: Exception) {
            }
            isReceiverRegistered = false
        }
    }

    private var lastAppendedLine = ""
    private val _activeFolderName = MutableStateFlow("")
    val activeFolderName: StateFlow<String> = _activeFolderName.asStateFlow()

    /*
     * Appends a log line to accumulated logs with debounced dispatch to avoid UI thread thrashing.
     */
    fun appendLog(message: String) {
        val lower = message.lowercase()
        if (lower.startsWith("memory") || lower.contains("[mem]") || lower.contains("mb /")) {
            return
        }
        val formattedMsg = when {
            message.startsWith("[ERROR]") || message.startsWith("[FAIL]") -> message
            message.startsWith("[WARN]") || message.startsWith("WARNING") -> message
            message.startsWith("[STEP]") || message.startsWith("[INFO]") -> message
            message.contains("error", ignoreCase = true) || message.contains("fail", ignoreCase = true) || message.contains("exception", ignoreCase = true) -> "[ERROR] $message"
            message.contains("warn", ignoreCase = true) -> "[WARN] $message"
            message.contains("doWork", ignoreCase = true) -> "[STEP] Decompilation step in progress"
            message.contains("Getting Ready", ignoreCase = true) -> "[STEP] Preparing engine environment"
            else -> "[INFO] $message"
        }
        if (formattedMsg == lastAppendedLine) return
        lastAppendedLine = formattedMsg

        synchronized(logLock) {
            logLinesList.add(formattedMsg)
            if (logLinesList.size > 800) {
                val sub = ArrayList(logLinesList.takeLast(600))
                logLinesList.clear()
                logLinesList.addAll(sub)
            }
        }
        scheduleLogFlush()
    }

    /*
     * Flushes buffered log lines to the accumulated logs StateFlow on a throttled interval.
     */
    private fun scheduleLogFlush() {
        if (logFlushJob?.isActive == true) return
        logFlushJob = scope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(120L)
            val text = synchronized(logLock) {
                logLinesList.joinToString("\n")
            }
            withContext(Dispatchers.Main) {
                _accumulatedLogs.value = text
            }
        }
    }

    /*
     * Initiates the background decompilation pipeline with WorkManager and updates active states.
     */
    fun startDecompilation(
        context: Context,
        packageInfo: PackageInfo,
        decompilerKey: String,
        decompilerIndex: Int
    ) {
        isStopping = false
        observeJob?.cancel()
        _activePackageInfo.value = packageInfo
        _activeDecompiler.value = decompilerKey
        _taskState.value = TaskState.RUNNING
        _progressPercentage.value = 0f
        _memoryUsage.value = ""
        _statusText.value = context.getString(R.string.gettingReady)

        val engineVersion = com.ameermuawiya.decompilers.engines.DecompilerRegistry.getEngine(decompilerKey)?.version ?: "1.0"
        val targetFolder = com.ameermuawiya.decompilers.utils.ktx.generateSourceFolderName(packageInfo.label, decompilerKey, engineVersion)
        _activeFolderName.value = targetFolder

        val initialLog = "[STEP 1/3] Initializing decompilation engine: $decompilerKey"
        synchronized(logLock) {
            logLinesList.clear()
            if (packageInfo.isSystemPackage) {
                logLinesList.add("[WARN] " + context.getString(R.string.systemAppWarning))
            }
            logLinesList.add(initialLog)
            _accumulatedLogs.value = logLinesList.joinToString("\n")
        }
        lastAppendedLine = initialLog

        val tracked = TrackedTask(
            packageInfo = packageInfo,
            engineKey = decompilerKey,
            state = TaskState.RUNNING,
            folderName = targetFolder
        )
        _trackedTasks.value = listOf(tracked) + _trackedTasks.value.filter { it.packageInfo.name != packageInfo.name }

        saveToPreferences(context, TaskState.RUNNING)
        val prefs = context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("active_task_folder", targetFolder).apply()

        registerReceiverForPackage(context, packageInfo.name)

        val targetFile = prepareTargetFile(context, packageInfo)
        val userPreferences = UserPreferences(prefs)
        val effectiveMemory = userPreferences.getEffectiveMaxMemoryMb(targetFile.length())
        if (userPreferences.showMemoryUsage) {
            val memLog = if (userPreferences.isAutoMemory) {
                context.getString(R.string.decompiler_manager_log_memory_auto, effectiveMemory)
            } else {
                context.getString(R.string.decompiler_manager_log_memory_fixed, effectiveMemory)
            }
            appendLog(memLog)
        }
        val inputMap = hashMapOf<String, Any>(
            "shouldIgnoreLibs" to userPreferences.ignoreLibraries,
            "customIgnoredList" to userPreferences.customIgnoredLibsString,
            "showMemoryUsage" to userPreferences.showMemoryUsage,
            "maxMemoryMb" to effectiveMemory,
            "isAutoMemory" to userPreferences.isAutoMemory,
            "maxAttempts" to userPreferences.maxAttempts,
            "chunkSize" to userPreferences.chunkSize,
            "memoryThreshold" to userPreferences.memoryThreshold,
            "keepIntermediateFiles" to userPreferences.keepIntermediateFiles,
            "decompiler" to decompilerKey,
            "name" to packageInfo.name,
            "label" to packageInfo.label,
            "targetFolderName" to targetFolder,
            "inputPackageFile" to targetFile.absolutePath,
            "type" to packageInfo.type.ordinal,
            "isExternalPackage" to packageInfo.isExternalPackage
        )

        packageInfo.splitSourceDirs?.let { splits ->
            if (splits.isNotEmpty()) {
                inputMap["splitSourceDirs"] = splits.joinToString(";")
            }
        }

        BaseDecompiler.start(inputMap, context)
        observeWork(context, packageInfo.name, targetFolder)
    }

    /*
     * Observes background WorkManager execution tasks and updates terminal success or error states.
     */
    private fun observeWork(context: Context, packageName: String, targetFolder: String = "") {
        observeJob?.cancel()
        val workManager = WorkManager.getInstance(context)
        observeJob = scope.launch {
            workManager.getWorkInfosByTagFlow(packageName).collect { workInfos ->
                if (isStopping || _taskState.value == TaskState.STOPPED) return@collect
                if (!workInfos.isNullOrEmpty()) {
                    val isFinished = workInfos.all { it.state.isFinished }
                    val hasCancelled = workInfos.any { it.state == WorkInfo.State.CANCELLED }
                    val hasFailed = workInfos.any { it.state == WorkInfo.State.FAILED }
                    val allSucceeded = workInfos.all { it.state == WorkInfo.State.SUCCEEDED }

                    if (isFinished) {
                        unregisterReceiver()
                        when {
                            hasCancelled || isStopping || _taskState.value == TaskState.STOPPED -> {
                                _taskState.value = TaskState.STOPPED
                                _statusText.value = context.getString(R.string.decompiler_status_stopped)
                                saveToPreferences(context, TaskState.IDLE)
                                _trackedTasks.value = _trackedTasks.value.map {
                                    if (it.packageInfo.name == packageName) it.copy(state = TaskState.STOPPED, completionTimestamp = System.currentTimeMillis()) else it
                                }
                            }
                            hasFailed -> {
                                _taskState.value = TaskState.FAILED
                                _statusText.value = context.getString(R.string.decompiler_status_failed)
                                appendLog("[ERROR] Decompilation process encountered an error and halted.")
                                saveToPreferences(context, TaskState.IDLE)
                                _trackedTasks.value = _trackedTasks.value.map {
                                    if (it.packageInfo.name == packageName) it.copy(state = TaskState.FAILED, completionTimestamp = System.currentTimeMillis()) else it
                                }
                            }
                            allSucceeded -> {
                                _taskState.value = TaskState.COMPLETED
                                _statusText.value = context.getString(R.string.decompiler_status_completed)
                                val folder = if (targetFolder.isNotBlank()) targetFolder else _activeFolderName.value
                                val targetDir = if (folder.isNotBlank()) {
                                    getAppStorage(context).resolve("sources/$folder")
                                } else {
                                    sourceDir(context, packageName)
                                }
                                appendLog("[INFO] Decompilation process completed successfully.")
                                appendLog("[INFO] Output directory: ${targetDir.absolutePath}")
                                saveToPreferences(context, TaskState.IDLE)
                                try {
                                    val srcInfo = com.ameermuawiya.decompilers.data.SourceInfo()
                                    srcInfo.sourceDirectory = targetDir
                                    srcInfo.packageLabel = _activePackageInfo.value?.label ?: packageName
                                    srcInfo.packageName = packageName
                                    srcInfo.decompilerEngine = _activeDecompiler.value
                                    srcInfo.hasJavaSource = targetDir.resolve("src/java").exists() || targetDir.resolve("java").exists() || targetDir.resolve("src").exists()
                                    srcInfo.hasXmlSource = targetDir.resolve("src/resources").exists() || targetDir.resolve("resources").exists()
                                    srcInfo.persist()
                                } catch (ignored: Exception) {
                                }
                                _trackedTasks.value = _trackedTasks.value.map {
                                    if (it.packageInfo.name == packageName) it.copy(state = TaskState.COMPLETED, completionTimestamp = System.currentTimeMillis()) else it
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * Forcibly halts background workers, preserves generated files, and sets stopped state.
     */
    fun stopActiveTask(context: Context) {
        isStopping = true
        observeJob?.cancel()
        observeJob = null
        unregisterReceiver()

        val info = _activePackageInfo.value
        val folder = _activeFolderName.value
        val targetDir = if (folder.isNotBlank()) {
            getAppStorage(context).resolve("sources/$folder")
        } else if (info != null) {
            sourceDir(context, info.name)
        } else {
            null
        }

        if (info != null) {
            com.ameermuawiya.decompilers.workers.DecompilerWorker.cancel(context, info.name)
            WorkManager.getInstance(context).cancelAllWorkByTag(info.name)
            com.ameermuawiya.decompilers.utils.ProcessNotifier(context, info.name).cancel()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            try {
                manager.cancel(info.name, Constants.WORKER.PROGRESS_NOTIFICATION_ID)
                manager.cancel(info.name, Constants.WORKER.COMPLETED_NOTIFICATION_ID)
            } catch (ignored: Exception) {
            }
            _trackedTasks.value = _trackedTasks.value.map {
                if (it.packageInfo.name == info.name) it.copy(state = TaskState.STOPPED, completionTimestamp = System.currentTimeMillis()) else it
            }
        }

        val prefs = UserPreferences(context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE))
        if (targetDir != null && targetDir.exists()) {
            try {
                if (!prefs.keepIntermediateFiles) {
                    targetDir.resolve("dex-files").deleteRecursively()
                    targetDir.resolve("jar-files").deleteRecursively()
                }
                val hasGeneratedSource = targetDir.resolve("src").exists() || targetDir.resolve("AndroidManifest.xml").exists()
                if (hasGeneratedSource && info != null) {
                    val engineKey = _activeDecompiler.value
                    val engineVer = com.ameermuawiya.decompilers.engines.DecompilerRegistry.getEngine(engineKey)?.version ?: "1.0"
                    val sourceInfo = com.ameermuawiya.decompilers.data.SourceInfo.from(targetDir)
                        .setPackageLabel(info.label)
                        .setPackageName(info.name)
                        .setJavaSourcePresence(targetDir.resolve("src/java").exists())
                        .setXmlSourcePresence(targetDir.resolve("AndroidManifest.xml").exists())
                    sourceInfo.decompilerEngine = engineKey
                    sourceInfo.decompilerVersion = engineVer
                    sourceInfo.persist()
                }
            } catch (ignored: Exception) {
            }
        }

        _taskState.value = TaskState.STOPPED
        _memoryUsage.value = ""
        _statusText.value = context.getString(R.string.decompiler_status_stopped)
        appendLog("[WARN] Decompilation process stopped by user.")
        if (targetDir != null && targetDir.exists() && (targetDir.list()?.isNotEmpty() == true)) {
            appendLog("[WARN] Partial files saved to: ${targetDir.absolutePath}")
        }
        appendLog("[WARN] Warning: Because the process was halted early, some source code or resources may be incomplete or missing.")
        saveToPreferences(context, TaskState.IDLE)
    }

    /*
     * Dismisses a completed task from the recent operations list after being inspected.
     */
    fun markTaskAsViewed(packageName: String) {
        _trackedTasks.value = _trackedTasks.value.filter { it.packageInfo.name != packageName || it.state == TaskState.RUNNING }
    }

    /*
     * Cancels active WorkManager tasks and cleans up running progress notifications.
     */
    fun cancelActiveTask(context: Context) {
        stopActiveTask(context)
    }

    /*
     * Clears active completed task from memory and resets manager state.
     */
    fun clearActiveTask(context: Context) {
        val activeName = _activePackageInfo.value?.name
        if (activeName != null) {
            markTaskAsViewed(activeName)
        }
        isStopping = false
        observeJob?.cancel()
        observeJob = null
        unregisterReceiver()
        _taskState.value = TaskState.IDLE
        _activePackageInfo.value = null
        _statusText.value = ""
        _memoryUsage.value = ""
        _accumulatedLogs.value = ""
        saveToPreferences(context, TaskState.IDLE)
    }

    /*
     * Safely copies target application package file to local cache when handling system packages.
     */
    private fun prepareTargetFile(context: Context, info: PackageInfo): File {
        val dest = File(context.cacheDir, "system_target_${info.name}.apk")
        val directFile = File(info.filePath)
        if (!info.isSystemPackage && directFile.exists() && directFile.canRead()) {
            return directFile
        }

        try {
            val appInfo = context.packageManager.getApplicationInfo(info.name, 0)
            val candidatePaths = listOfNotNull(appInfo.publicSourceDir, appInfo.sourceDir, info.filePath).distinct()
            for (path in candidatePaths) {
                val candidateFile = File(path)
                if (candidateFile.exists() && candidateFile.canRead()) {
                    candidateFile.inputStream().use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (dest.exists() && dest.length() > 0) {
                        return dest
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        try {
            val pkgContext = context.createPackageContext(info.name, Context.CONTEXT_IGNORE_SECURITY)
            val codePath = pkgContext.packageResourcePath ?: pkgContext.packageCodePath
            if (!codePath.isNullOrBlank()) {
                val codeFile = File(codePath)
                if (codeFile.exists() && codeFile.canRead()) {
                    codeFile.inputStream().use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (dest.exists() && dest.length() > 0) {
                        return dest
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        return if (directFile.exists()) directFile else dest
    }
}
