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

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.Nullable
import com.ameermuawiya.decompilers.utils.ktx.getCircularBitmap
import java.security.MessageDigest
import kotlin.experimental.and

/*
 * Generates deterministic identicon circular avatar bitmaps from arbitrary seed objects.
 */
object Identicon {

    /*
     * Generates a circular identicon bitmap using an arbitrary seed object's hash code.
     */
    fun createFromObject(seed: Any): Bitmap {
        return create(Integer.toHexString(seed.hashCode()), Options.DEFAULT)
    }

    /*
     * Generates an identicon bitmap using a seed object and custom generation options.
     */
    fun createFromObject(seed: Any, options: Options): Bitmap {
        return create(Integer.toHexString(seed.hashCode()), options)
    }

    /*
     * Generates a circular identicon bitmap from a string seed and configuration options.
     */
    @JvmOverloads
    fun create(seed: String, config: Options = Options.DEFAULT): Bitmap {
        return createBitmap(seed, config.rows, config.size, config.blankColor)
    }

    /*
     * Builds and paints a grid of pixels from mapped hash bits and applies circular masking.
     */
    private fun createBitmap(seed: String, rows: Int, size: Int, blankColor: Int): Bitmap {
        var bmSize = size
        if (bmSize % rows != 0) {
            bmSize += (rows - bmSize % rows)
        }
        val bitmap = Bitmap.createBitmap(bmSize, bmSize, Bitmap.Config.RGB_565)
        val block = bmSize / rows
        val mapping = mapToBit(seed, rows) ?: return bitmap
        val tintColor = getColor(seed)
        val pixels = IntArray(block * block)
        val blankPixels = IntArray(block * block)

        for (p in 0 until block * block) {
            pixels[p] = tintColor
            blankPixels[p] = blankColor
        }

        for (i in 0 until rows) {
            for (j in 0 until rows) {
                if (mapping[i][j]) {
                    bitmap.setPixels(pixels, 0, block, j * block, i * block, block, block)
                } else {
                    bitmap.setPixels(blankPixels, 0, block, j * block, i * block, block, block)
                }
            }
        }
        return bitmap.getCircularBitmap()
    }

    /*
     * Translates MD5 hash bits into a symmetrical 2D boolean grid for the identicon pattern.
     */
    fun mapToBit(seed: String, rows: Int): Array<BooleanArray>? {
        checkRows(rows)
        val mapping = Array(rows) { BooleanArray(rows) }
        val bits = getHash(seed) ?: return null
        val stride = getStride(rows)
        var index = stride
        for (i in 0 until rows) {
            for (j in 0 until (rows + 1) / 2) {
                val c = bits[index]
                val b = c == '1'
                mapping[i][j] = b
                mapping[i][rows - j - 1] = b
                index += stride
            }
        }
        return mapping
    }

    /*
     * Validates that the specified grid rows parameter is an odd integer within valid range.
     */
    private fun checkRows(rows: Int) {
        if (rows and 0x1 == 0) {
            throw IllegalArgumentException("Argument 'rows' must be an odd number")
        } else if (rows < 5 || rows > 11) {
            throw IllegalArgumentException("Argument 'rows' must be between 5 and 11")
        }
    }

    /*
     * Generates a binary string character array representing MD5 hash of seed string.
     */
    @Nullable
    private fun getHash(src: String): CharArray? {
        val md5 = getBinMd5(src)
        return md5?.toCharArray()
    }

    /*
     * Computes binary string representation of MD5 bytes.
     */
    private fun getBinMd5(src: String): String? {
        val encryption = md5(src) ?: return null
        val buffer = StringBuilder()
        for (b in encryption) {
            buffer.append(Integer.toBinaryString((b and 0xFF.toByte()) + 0x100).substring(1))
        }
        return buffer.toString()
    }

    /*
     * Computes bit stride index based on matrix row count.
     */
    private fun getStride(rows: Int): Int {
        return 128 / (rows * ((rows + 1) / 2))
    }

    /*
     * Computes raw MD5 digest bytes for the given source string.
     */
    private fun md5(src: String): ByteArray? {
        try {
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(src.toByteArray(charset("UTF-8")))
            return md5.digest()
        } catch (e: Exception) {
            return null
        }
    }

    /*
     * Computes hexadecimal MD5 representation for the given string.
     */
    private fun getHexMd5(src: String): String? {
        val encryption = md5(src) ?: return null
        val sb = StringBuilder()
        for (i in encryption.indices) {
            if (Integer.toHexString(0xFF and encryption[i].toInt()).length == 1) {
                sb.append("0").append(Integer.toHexString(0xFF and encryption[i].toInt()))
            } else {
                sb.append(Integer.toHexString(0xFF and encryption[i].toInt()))
            }
        }
        return sb.toString()
    }

    /*
     * Derives a color integer from the trailing characters of the MD5 hash.
     */
    private fun getColor(src: String): Int {
        val md5 = getHexMd5(src)
        if (md5 != null) {
            val rgb = md5.substring(md5.length - 6, md5.length)
            return Color.parseColor("#" + rgb.uppercase())
        }

        return Color.BLACK
    }

    class Options private constructor(
        internal var blankColor: Int,
        internal var rows: Int,
        internal var size: Int
    ) {

        class Builder {
            private var bColor = Color.LTGRAY
            private var bRows = 5
            private var bSize = 100

            /*
             * Configures background blank color.
             */
            fun setBlankColor(color: Int): Builder {
                bColor = color
                return this
            }

            /*
             * Configures number of grid rows.
             */
            fun setRows(rows: Int): Builder {
                checkRows(rows)
                bRows = rows
                return this
            }

            /*
             * Configures output square bitmap pixel size.
             */
            fun setSize(size: Int): Builder {
                bSize = size
                return this
            }

            /*
             * Constructs the configured Options instance.
             */
            fun create(): Options {
                return Options(bColor, bRows, bSize)
            }

            /*
             * Validates grid rows parameter constraint.
             */
            private fun checkRows(rows: Int) {
                if (rows and 0x1 == 0) {
                    throw IllegalArgumentException("Argument 'rows' must be an odd number")
                } else if (rows < 5 || rows > 11) {
                    throw IllegalArgumentException("Argument 'rows' must be between 5 and 11")
                }
            }
        }

        companion object {
            val DEFAULT = Options(Color.LTGRAY, 5, 50)
        }
    }
}