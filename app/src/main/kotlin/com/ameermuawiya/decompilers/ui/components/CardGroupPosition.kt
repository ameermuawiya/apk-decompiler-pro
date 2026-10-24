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

package com.ameermuawiya.decompilers.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CardGroupPosition {
    SINGLE,
    FIRST,
    MIDDLE,
    LAST
}

/*
 * Calculates rounded corner shapes for grouped cards matching APK Signer Pro layout specs.
 */
fun getGroupedCardShape(
    position: CardGroupPosition,
    outerRadius: Dp = 16.dp,
    innerRadius: Dp = 2.dp
): Shape {
    return when (position) {
        CardGroupPosition.SINGLE -> RoundedCornerShape(outerRadius)
        CardGroupPosition.FIRST -> RoundedCornerShape(
            topStart = outerRadius,
            topEnd = outerRadius,
            bottomStart = innerRadius,
            bottomEnd = innerRadius
        )
        CardGroupPosition.MIDDLE -> RoundedCornerShape(innerRadius)
        CardGroupPosition.LAST -> RoundedCornerShape(
            topStart = innerRadius,
            topEnd = innerRadius,
            bottomStart = outerRadius,
            bottomEnd = outerRadius
        )
    }
}
