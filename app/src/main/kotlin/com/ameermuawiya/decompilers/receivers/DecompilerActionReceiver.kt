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

package com.ameermuawiya.decompilers.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ameermuawiya.decompilers.Constants
import com.ameermuawiya.decompilers.workers.DecompilerWorker
import timber.log.Timber

/*
 * BroadcastReceiver routing notification stop requests to confirmation dialog in DecompilerActivity.
 */
class DecompilerActionReceiver : BroadcastReceiver() {

    /*
     * Receives broadcast events and routes cancellation requests to confirmation UI.
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Constants.WORKER.ACTION.STOP -> {
                val id = intent.getStringExtra("id")
                val pkgName = intent.getStringExtra("packageName") ?: id ?: ""
                Timber.d("[cancel-request] ID: $id")
                context?.let { ctx ->
                    val activityIntent = Intent(ctx, com.ameermuawiya.decompilers.activities.decompiler.DecompilerActivity::class.java).apply {
                        putExtra("packageName", pkgName)
                        putExtra("confirmStop", true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    try {
                        ctx.startActivity(activityIntent)
                    } catch (e: Exception) {
                        if (!id.isNullOrEmpty()) {
                            DecompilerWorker.cancel(ctx, id)
                        }
                    }
                }
            }
            else -> {
                Timber.i("Received an unknown action.")
            }
        }
    }
}