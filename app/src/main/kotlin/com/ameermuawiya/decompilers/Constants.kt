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

/*
 * Constants used across decompiler tasks, notifications, and background workers.
 */
object Constants {

    interface WORKER {

        companion object {
            const val STATUS_TYPE = "com.ameermuawiya.decompilers.worker.STATUS_TYPE"
            const val STATUS_TITLE = "com.ameermuawiya.decompilers.worker.STATUS_TITLE"
            const val STATUS_MESSAGE = "com.ameermuawiya.decompilers.worker.STATUS_MESSAGE"

            const val PROGRESS_NOTIFICATION_CHANNEL = "com.ameermuawiya.decompilers.worker.notification.progress"
            const val COMPLETION_NOTIFICATION_CHANNEL = "com.ameermuawiya.decompilers.worker.notification.completion"
            const val PROGRESS_NOTIFICATION_ID = 1094
            const val COMPLETED_NOTIFICATION_ID = 1095
        }

        interface ACTION {
            companion object {
                const val BROADCAST = "com.ameermuawiya.decompilers.worker.action.BROADCAST"
                const val STOP = "com.ameermuawiya.decompilers.worker.action.STOP"
            }
        }
    }
}