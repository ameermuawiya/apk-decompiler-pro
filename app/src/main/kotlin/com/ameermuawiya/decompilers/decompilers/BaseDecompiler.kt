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

package com.ameermuawiya.decompilers.decompilers

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import com.ameermuawiya.decompilers.Constants
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.data.PackageInfo
import com.ameermuawiya.decompilers.data.SourceInfo
import com.ameermuawiya.decompilers.utils.ProcessNotifier
import com.ameermuawiya.decompilers.utils.UserPreferences
import com.ameermuawiya.decompilers.utils.ktx.appStorage
import com.ameermuawiya.decompilers.utils.ktx.cleanMemory
import com.ameermuawiya.decompilers.utils.ktx.getAppStorage
import com.ameermuawiya.decompilers.utils.ktx.getDate
import com.ameermuawiya.decompilers.utils.streams.ProgressStream
import com.ameermuawiya.decompilers.workers.DecompilerWorker
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.io.FileUtils
import timber.log.Timber
import java.io.File
import java.io.PrintStream
import java.util.concurrent.TimeUnit

/**
 * The base decompiler. This reads the input [Data] into easy to use properties of the class.
 * All other components of the decompiler will extend this one.
 */
abstract class BaseDecompiler(val context: Context, val data: Data) {
    var printStream: PrintStream? = null

    private var id = data.getString("id")
    private var processNotifier: ProcessNotifier? = null
    private var runAttemptCount: Int = 0
    protected var outOfMemory = false

    protected val decompiler = data.getString("decompiler")
    protected val type = PackageInfo.Type.values()[data.getInt("type", 0)]
    private val maxAttempts = data.getInt("maxAttempts", UserPreferences.DEFAULTS.MAX_ATTEMPTS)
    private val memoryThreshold = data.getInt("memoryThreshold", 80)

    protected val packageName = data.getString("name").toString()
    protected val packageLabel = data.getString("label").toString()

    protected val isExternalPackage = data.getBoolean("isExternalPackage", false)


    protected val keepIntermediateFiles = data.getBoolean("keepIntermediateFiles", UserPreferences.DEFAULTS.KEEP_INTERMEDIATE_FILES)

    protected val targetFolderName = data.getString("targetFolderName") ?: packageName
    protected val workingDirectory: File = getAppStorage(context).resolve("sources/$targetFolderName/")
    protected val cacheDirectory: File = getAppStorage(context).resolve("sources/.cache/")

    protected val inputPackageFile: File = File(data.getString("inputPackageFile") ?: "")

    protected val outputDexFiles: File = workingDirectory.resolve("dex-files")
    protected val outputJarFiles: File = workingDirectory.resolve("jar-files")
    protected val outputSrcDirectory: File = workingDirectory.resolve("src")
    protected val outputJavaSrcDirectory: File = outputSrcDirectory.resolve("java")

    private val disposables = CompositeDisposable()
    private var onLowMemory: ((Boolean) -> Unit)? = null
    private var memoryThresholdCrossCount = 0
    protected open val maxMemoryAdjustmentFactor = 1.25

    init {
        @Suppress("LeakingThis")
        printStream = PrintStream(ProgressStream(this))
    }

    /**
     * Prepare the required directories.
     * All child classes must call this method on override.
     */
    open fun doWork(): ListenableWorker.Result {
        cleanMemory()
        monitorMemory()
        outputJavaSrcDirectory.mkdirs()

        return ListenableWorker.Result.success()
    }

    /*
     * Sets current retry attempt count and initiates decompiler worker execution.
     */
    fun withAttempt(attempt: Int = 0): ListenableWorker.Result {
        this.runAttemptCount = attempt
        return this.doWork()
    }

    /*
     * Attaches persistent notification manager to decompiler lifecycle.
     */
    fun withNotifier(notifier: ProcessNotifier): BaseDecompiler {
        this.processNotifier = notifier
        return this
    }

    /*
     * Configures consumer callback triggered when memory usage crosses critical threshold.
     */
    fun withLowMemoryCallback(onLowMemory: ((Boolean) -> Unit)): BaseDecompiler {
        this.onLowMemory = onLowMemory
        return this
    }

    private var lastBroadcastTime = 0L
    private var dynamicMaxMemoryMb = 512
    @Volatile
    var isStoppedByUser = false

    /*
     * Periodically samples heap memory usage and dynamically scales headroom up or down.
     */
    private fun monitorMemory() {
        val prefs = UserPreferences(context.getSharedPreferences(UserPreferences.NAME, Context.MODE_PRIVATE))
        val packageBytes = if (inputPackageFile.exists()) inputPackageFile.length() else 0L
        val configuredMb = prefs.maxMemoryMb
        dynamicMaxMemoryMb = if (configuredMb > 0) configuredMb else prefs.getEffectiveMaxMemoryMb(packageBytes)

        disposables.add(
            Observable.interval(500, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe {
                    if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) {
                        return@subscribe
                    }
                    val runtime = Runtime.getRuntime()
                    val usedBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
                    val usedMb = (usedBytes / (1024 * 1024)).coerceAtLeast(1L)

                    dynamicMaxMemoryMb = prefs.computeDynamicMaxMemoryMb(usedMb, dynamicMaxMemoryMb)
                    val safeUsedMb = usedMb.coerceAtMost((dynamicMaxMemoryMb - 1).toLong().coerceAtLeast(1L))
                    val usedPercentage = (safeUsedMb.toDouble() / dynamicMaxMemoryMb.toDouble()) * 100.0

                    if (prefs.showMemoryUsage) {
                        broadcastStatus("memory", "$safeUsedMb MB / $dynamicMaxMemoryMb MB", "memory")
                    }

                    if (!prefs.isAutoMemory && usedPercentage > memoryThreshold) {
                        if (memoryThresholdCrossCount > 6) {
                            onLowMemory?.invoke(true)
                        } else {
                            memoryThresholdCrossCount++
                        }
                    } else {
                        memoryThresholdCrossCount = 0
                    }
                }
        )
    }

    /*
     * Updates persistent progress notification and dispatches broadcast update.
     */
    protected fun sendStatus(title: String, message: String, forceSet: Boolean = false) {
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) return
        processNotifier?.updateTitleText(title, message, forceSet)
        this.broadcastStatus(title, message)
    }

    /*
     * Dispatches simple progress message to notification and UI listeners.
     */
    fun sendStatus(message: String, forceSet: Boolean = false) {
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) return
        processNotifier?.updateText(message, forceSet)
        this.broadcastStatus(null, message)
    }

    /*
     * Sets current major decompilation step title and notifies listeners.
     */
    fun setStep(title: String) {
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) return
        sendStatus(title, context.getString(R.string.initializing), true)
    }

    /*
     * Clears running states and exits worker with failure result and error logs.
     */
    protected fun exit(exception: Exception?): ListenableWorker.Result {
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) {
            onStopped()
            disposables.clear()
            return ListenableWorker.Result.failure()
        }
        val errorMsg = exception?.localizedMessage ?: exception?.javaClass?.simpleName ?: "Unknown error"
        Timber.e(exception)
        sendStatus("[ERROR] Decompilation error: $errorMsg", true)
        exception?.cause?.let { cause ->
            if (!cause.localizedMessage.isNullOrBlank()) {
                sendStatus("[ERROR] Cause: ${cause.localizedMessage}", false)
            }
        }
        exception?.stackTraceToString()?.lineSequence()?.take(6)?.forEach { line ->
            if (line.isNotBlank()) sendStatus("[ERROR] $line", false)
        }
        onStopped()
        disposables.clear()
        return ListenableWorker.Result.failure()
    }

    /*
     * Validates condition and returns success or terminates with failure.
     */
    protected fun successIf(condition: Boolean): ListenableWorker.Result {
        disposables.clear()
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) {
            return ListenableWorker.Result.failure()
        }
        return if (condition)
            ListenableWorker.Result.success()
        else
            exit(Exception("Success condition failed"))
    }

    /*
     * Broadcasts throttled decompilation progress or memory updates to registered UI receivers.
     */
    private fun broadcastStatus(title: String?, message: String, type: String = "progress") {
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) return
        val now = System.currentTimeMillis()
        val isCritical = type != "progress" || title != null || message.startsWith("[") || message.startsWith("Starting")
        if (!isCritical && (now - lastBroadcastTime < 150L)) {
            return
        }
        lastBroadcastTime = now
        try {
            context.sendBroadcast(
                Intent(Constants.WORKER.ACTION.BROADCAST + packageName)
                    .putExtra(Constants.WORKER.STATUS_TITLE, title)
                    .putExtra(Constants.WORKER.STATUS_MESSAGE, message)
                    .putExtra(Constants.WORKER.STATUS_TYPE, type)
                    .setPackage(context.packageName)
            )
        } catch (ignored: Exception) {
        }
    }

    /*
     * Creates and configures foreground progress notification for this task.
     */
    protected fun buildNotification(title: String) {
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) return
        if (processNotifier == null) {
            processNotifier = ProcessNotifier(context, id)
        }
        processNotifier!!.buildFor(
            title,
            packageName,
            packageLabel,
            inputPackageFile,
            context.resources.getStringArray(R.array.decompilersValues).indexOf(decompiler)
        )
    }

    /*
     * Persists run metadata and broadcasts completion event to notify listeners.
     */
    fun onCompleted() {
        if (isStoppedByUser || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) return
        if (isExternalPackage) {
            val tempFile = File(context.cacheDir, inputPackageFile.toString().substring(inputPackageFile.toString().lastIndexOf("/") + 1))
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        val sourceInfo = SourceInfo(packageLabel, packageName, true, true, getDate(), getDate(), 0L)
        sourceInfo.sourceDirectory = workingDirectory
        sourceInfo.decompilerEngine = decompiler ?: ""
        sourceInfo.decompilerVersion = com.ameermuawiya.decompilers.engines.DecompilerRegistry.getEngine(decompiler ?: "")?.version ?: "1.0"
        sourceInfo.sourceSize = try { org.apache.commons.io.FileUtils.sizeOfDirectory(workingDirectory) } catch (e: Exception) { 0L }
        sourceInfo.persist()

        processNotifier?.success()
        broadcastStatus(
            context.getString(R.string.appHasBeenDecompiled, packageLabel),
            workingDirectory.absolutePath,
            "completed"
        )
    }

    /*
     * Cancels active subscriptions and notifications when the worker is stopped.
     */
    open fun onStopped() {
        isStoppedByUser = true
        Timber.d("[cancel-request] cancelled")
        disposables.clear()
        processNotifier?.cancel()
    }

    companion object {

        /**
         * Check if the specified decompiler is available on the device based on the android version
         */
        fun isAvailable(decompiler: String): Boolean {
            return com.ameermuawiya.decompilers.engines.DecompilerRegistry.isAvailable(decompiler)
        }

        /**
         * For the WorkManager compatible Data object from the given map
         */
        fun formData(dataMap: Map<String, Any>): Data {
            val id = dataMap["name"] as String
            return Data.Builder()
                .putAll(dataMap)
                .putString("id", id)
                .build()
        }

        /*
         * Enqueues the three-phase decompilation worker chain into WorkManager.
         */
        fun start(dataMap: Map<String, Any>, targetContext: Context): String {
            val data = formData(dataMap)
            val id = data.getString("id")!!

            /*
             * Constructs a tagged OneTimeWorkRequest for the decompiler task.
             */
            fun buildWorkRequest(type: String): OneTimeWorkRequest {
                return OneTimeWorkRequestBuilder<DecompilerWorker>()
                    .addTag("decompile")
                    .addTag(type)
                    .addTag(id)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 0, TimeUnit.SECONDS)
                    .setInputData(data)
                    .build()
            }

            val workManager = WorkManager.getInstance(targetContext)

            workManager
                .beginUniqueWork(
                    id,
                    ExistingWorkPolicy.REPLACE,
                    buildWorkRequest("jar-extraction")
                )
                .then(buildWorkRequest("java-extraction"))
                .then(buildWorkRequest("resources-extraction"))
                .enqueue()
            return id
        }
    }

    class OutOfMemoryError: Exception()
}