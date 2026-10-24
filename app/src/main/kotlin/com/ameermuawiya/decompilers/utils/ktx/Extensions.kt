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

package com.ameermuawiya.decompilers.utils.ktx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import java.io.File
import java.io.InputStream

/*
 * Copies bytes from input stream directly into destination file on disk.
 */
fun InputStream.toFile(file: File) {
    file.outputStream().use { this.copyTo(it) }
}

/*
 * Crops and renders source bitmap into an anti-aliased circular bitmap shape.
 */
fun Bitmap.getCircularBitmap(config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
    val circlePaint = Paint().apply { isAntiAlias = true }
    val circleRadius = Math.max(width, height) / 2f
    val outputBitmapPaint = Paint(circlePaint).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
    val outputBounds = Rect(0, 0, width, height)
    val output = Bitmap.createBitmap(width, height, config)

    return Canvas(output).run {
        drawCircle(circleRadius, circleRadius, circleRadius, circlePaint)
        drawBitmap(this@getCircularBitmap, outputBounds, outputBounds, outputBitmapPaint)
        output
    }
}
