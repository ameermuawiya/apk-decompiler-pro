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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ameermuawiya.decompilers.Constants
import com.ameermuawiya.decompilers.R
import com.ameermuawiya.decompilers.activities.decompiler.DecompilerActivity
import com.ameermuawiya.decompilers.data.PackageInfo
import java.io.File

/*
 * Manages foreground and background progress, completion, and error notifications during decompilation.
 */
class ProcessNotifier(
    private val context: Context,
    private val notificationTag: String?,
    private val notificationId: Int = Constants.WORKER.PROGRESS_NOTIFICATION_ID
) {
    private var time: Long = 0
    private var isCancelled: Boolean = false

    private var manager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private lateinit var builder: NotificationCompat.Builder
    private var packageName: String = notificationTag ?: ""
    private var packageLabel: String = notificationTag ?: ""
    private var packageFile: File = File("")

    /*
     * Binds target package metadata to this notification helper.
     */
    fun withPackageInfo(packageName: String, packageLabel: String, packageFile: File): ProcessNotifier {
        this.packageName = packageName
        this.packageFile = packageFile
        this.packageLabel = packageLabel
        return this
    }

    /*
     * Builds ongoing progress notification with stop action and registers notification channels.
     */
    fun buildFor(title: String, packageName: String, packageLabel: String, packageFile: File, decompilerIndex: Int): ProcessNotifier {
        this.packageName = packageName
        this.packageFile = packageFile
        this.packageLabel = packageLabel

        val stopIntent = Intent(context, DecompilerActivity::class.java).apply {
            action = Constants.WORKER.ACTION.STOP
            putExtra("id", packageName)
            putExtra("packageInfo", PackageInfo(packageLabel, packageName))
            putExtra("confirmStop", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntentForStop = PendingIntent.getActivity(
            context,
            101,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val viewIntent = Intent(context, DecompilerActivity::class.java).apply {
            putExtra("packageInfo", PackageInfo(packageLabel, packageName))
            putExtra("decompilerIndex", decompilerIndex)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentForView = PendingIntent.getActivity(
            context,
            0,
            viewIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.WORKER.PROGRESS_NOTIFICATION_CHANNEL,
                context.getString(R.string.decompiler_activity_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setSound(null, null)
            channel.enableVibration(false)
            manager.createNotificationChannel(channel)
        }

        val actionIcon = android.R.drawable.ic_menu_close_clear_cancel

        builder = NotificationCompat.Builder(context, Constants.WORKER.PROGRESS_NOTIFICATION_CHANNEL)
            .setContentTitle(packageLabel)
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntentForView)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .addAction(actionIcon, context.getString(R.string.notification_stop_action), pendingIntentForStop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        manager.notify(
            notificationTag,
            Constants.WORKER.PROGRESS_NOTIFICATION_ID,
            silence(builder.build())
        )
        return this
    }

    /*
     * Ensures consistent silent notification delivery across OS versions.
     */
    private fun silence(notification: Notification): Notification {
        return notification
    }

    /*
     * Updates notification title with rate throttling.
     */
    fun updateTitle(title: String, forceSet: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (!isCancelled && (currentTime - time >= 400 || forceSet) && ::builder.isInitialized) {
            builder.setContentTitle(title)
            try {
                manager.notify(notificationTag, notificationId, silence(builder.build()))
            } catch (ignored: Exception) {
            }
            time = currentTime
        }
    }

    /*
     * Updates notification body text and expanded big text style with latest log line.
     */
    fun updateText(text: String, forceSet: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (!isCancelled && (currentTime - time >= 400 || forceSet) && ::builder.isInitialized) {
            builder.setContentText(text)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText(packageLabel))
            try {
                manager.notify(notificationTag, notificationId, silence(builder.build()))
            } catch (ignored: Exception) {
            }
            time = currentTime
        }
    }

    /*
     * Updates both notification title and body text simultaneously.
     */
    fun updateTitleText(title: String, text: String, forceSet: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (!isCancelled && (currentTime - time >= 400 || forceSet) && ::builder.isInitialized) {
            builder.setContentTitle(title)
            builder.setContentText(text)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText(packageLabel))
            try {
                manager.notify(notificationTag, notificationId, silence(builder.build()))
            } catch (ignored: Exception) {
            }
            time = currentTime
        }
    }

    /*
     * Cancels active persistent notification and marks state as cancelled.
     */
    fun cancel() {
        isCancelled = true
        try {
            manager.cancel(notificationTag, notificationId)
        } catch (ignored: Exception) {
        }
    }

    /*
     * Dispatches completed or error status notification after canceling progress notification.
     */
    private fun complete(intent: Intent, title: String, text: String, icon: Int) {
        isCancelled = true
        try {
            manager.cancel(notificationTag, Constants.WORKER.PROGRESS_NOTIFICATION_ID)
            manager.cancel(notificationTag, notificationId)
            manager.cancel(packageName, Constants.WORKER.PROGRESS_NOTIFICATION_ID)
        } catch (ignored: Exception) {
        }
        val resultPendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.WORKER.COMPLETION_NOTIFICATION_CHANNEL,
                context.getString(R.string.decompiler_activity_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.decompiler_activity_notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, Constants.WORKER.COMPLETION_NOTIFICATION_CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText(packageLabel))
            .setSmallIcon(icon)
            .setContentIntent(resultPendingIntent)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setOngoing(false)
            .setAutoCancel(true)
        try {
            manager.notify(
                packageName,
                Constants.WORKER.COMPLETED_NOTIFICATION_ID,
                builder.build()
            )
        } catch (ignored: Exception) {
        }
    }

    /*
     * Dispatches an error notification when decompilation fails.
     */
    fun error() {
        val intent = Intent(context, DecompilerActivity::class.java).apply {
            putExtra("packageInfo", PackageInfo.fromFile(context, packageFile))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        complete(
            intent,
            context.getString(R.string.errorDecompilingApp, packageLabel),
            context.getString(R.string.tapToRetry),
            R.drawable.ic_notification
        )
    }

    /*
     * Dispatches an alert notification when worker runs low on memory.
     */
    fun lowMemory(decompiler: String) {
        val intent = Intent(context, DecompilerActivity::class.java).apply {
            val packageInfo = PackageInfo.fromFile(context, packageFile)
            putExtra("packageInfo", packageInfo)
            putExtra("decompiler", decompiler)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        complete(
            intent,
            context.getString(R.string.errorDecompilingApp, packageLabel),
            context.getString(R.string.lowMemoryStatusInfo),
            R.drawable.ic_notification
        )
    }

    /*
     * Dispatches a success notification linking to the completed decompiler screen.
     */
    fun success() {
        val intent = Intent(context, DecompilerActivity::class.java).apply {
            putExtra("packageInfo", PackageInfo(packageLabel, packageName))
            putExtra("fromNotification", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        complete(
            intent,
            context.getString(R.string.appHasBeenDecompiled, packageLabel),
            context.getString(R.string.tapToViewSource),
            R.drawable.ic_notification
        )
    }
}