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

package com.ameermuawiya.decompilers.workers

import android.content.Context
import androidx.work.Data
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.ameermuawiya.decompilers.decompilers.BaseDecompiler
import com.ameermuawiya.decompilers.decompilers.JarExtractionWorker
import com.ameermuawiya.decompilers.decompilers.JavaExtractionWorker
import com.ameermuawiya.decompilers.decompilers.ResourcesExtractionWorker
import com.ameermuawiya.decompilers.utils.ProcessNotifier
import com.ameermuawiya.decompilers.utils.UserPreferences
import timber.log.Timber
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DecompilerWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {

    private var worker: BaseDecompiler? = null
    private lateinit var step: String
    private val maxAttempts = params.inputData.getInt("maxAttempts", UserPreferences.DEFAULTS.MAX_ATTEMPTS)

    private val id: String = params.inputData.getString("id").toString()
    private val packageName: String = params.inputData.getString("name").toString()
    private val packageLabel: String = params.inputData.getString("label").toString()
    private val decompiler: String = params.inputData.getString("decompiler").toString()
    private val chunkSize: Int = params.inputData.getInt("chunkSize", UserPreferences.DEFAULTS.CHUNK_SIZE)
    private val memoryThreshold: Int = params.inputData.getInt("memoryThreshold", UserPreferences.DEFAULTS.MEMORY_THRESHOLD)
    private val inputPackageFile: File = File(params.inputData.getString("inputPackageFile") ?: "")

    private val decompilerExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        if (tags.contains("jar-extraction")) {
            step = "jar-extraction"
            worker = JarExtractionWorker(context, params.inputData)
        }
        if (tags.contains("java-extraction")) {
            step = "java-extraction"
            worker = JavaExtractionWorker(context, params.inputData)
        }
        if (tags.contains("resources-extraction")) {
            step = "resources-extraction"
            worker = ResourcesExtractionWorker(context, params.inputData)
        }
    }

    /*
     * Executes the assigned extraction step with retry logic and memory threshold monitoring.
     */
    override fun doWork(): Result {
        if (isStopped || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping) {
            return Result.failure()
        }
        var result = if (runAttemptCount >= (maxAttempts - 1)) Result.failure() else Result.retry()
        var ranOutOfMemory = false
        val notifier = ProcessNotifier(context, id)
            .withPackageInfo(packageName, packageLabel, inputPackageFile)

        var outputData = Data.Builder().build()

        worker?.let {
            try {
                result = it.withNotifier(notifier)
                    .withLowMemoryCallback { isLowMemory ->
                        ranOutOfMemory = isLowMemory
                        outputData = Data.Builder()
                            .putBoolean("ranOutOfMemory", isLowMemory)
                            .build()
                    }
                    .withAttempt(runAttemptCount)
            } catch (e: Exception) {
                Timber.e(e)
                result = Result.failure()
            }
        }

        if (isStopped || com.ameermuawiya.decompilers.managers.DecompilerTaskManager.isStopping || worker?.isStoppedByUser == true) {
            notifier.cancel()
            return Result.failure()
        }

        if (ranOutOfMemory) {
            result = Result.failure(outputData)
        }

        if (result == Result.failure()) {
            try {
                if (ranOutOfMemory) {
                    notifier.lowMemory(decompiler)
                } else {
                    notifier.error()
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        return result
    }

    /*
     * Handles cleanup and resource release when the worker task is cancelled or finished.
     */
    override fun onStopped() {
        super.onStopped()
        worker?.onStopped()
        decompilerExecutor.shutdownNow()
    }

    companion object {
        /*
         * Cancels an active decompilation background task by package identifier.
         */
        fun cancel(context: Context, id: String) {
            ProcessNotifier(context, id).cancel()
            WorkManager.getInstance(context).cancelUniqueWork(id)
        }
    }
}
