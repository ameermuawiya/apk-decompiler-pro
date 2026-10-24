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

import android.app.Application
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.WorkManager
import com.ameermuawiya.decompilers.utils.UserPreferences
import com.ameermuawiya.decompilers.utils.logging.ProductionTree
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.UUID

class MainApplication : Application() {

    val disposables = CompositeDisposable()
    lateinit var instanceId: String

    /*
     * Initializes preferences, theme settings, logging, and background cleanup tasks.
     */
    override fun onCreate() {
        super.onCreate()
        instanceId = UUID.randomUUID().toString()

        val rawPrefs = applicationContext.getSharedPreferences(UserPreferences.NAME, MODE_PRIVATE)
        val preferences = UserPreferences(rawPrefs)
        val savedThemeMode = rawPrefs.getString("themeMode", if (preferences.darkMode) "dark" else "system") ?: "system"

        AppCompatDelegate.setDefaultNightMode(
            when (savedThemeMode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        com.ameermuawiya.decompilers.managers.DecompilerTaskManager.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            disposables.add(
                cleanStaleNotifications()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .onErrorReturn {}
                    .subscribe()
            )
        }
    }

    /*
     * Cleans any stale notifications that are no longer linked to active worker tasks.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun cleanStaleNotifications(): Observable<Unit> {
        return Observable.fromCallable {
            try {
                val manager = applicationContext
                    .getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return@fromCallable
                val workManager = WorkManager.getInstance(applicationContext)
                manager.activeNotifications.forEach { notification ->
                    val tag = notification.tag ?: return@forEach
                    val infos = try {
                        workManager.getWorkInfosForUniqueWork(tag).get()
                    } catch (e: Exception) {
                        null
                    }
                    val allFinished = infos.isNullOrEmpty() || infos.all { it.state.isFinished }
                    if (allFinished) {
                        manager.cancel(tag, notification.id)
                    }
                }
            } catch (ignored: Exception) {
            }
        }
    }

    /*
     * Releases resources and clears active rx disposables upon termination.
     */
    override fun onTerminate() {
        super.onTerminate()
        disposables.clear()
    }
}
