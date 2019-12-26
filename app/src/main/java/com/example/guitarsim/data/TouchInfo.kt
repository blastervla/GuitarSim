package com.example.guitarsim.data

import kotlin.math.max

class TouchInfo(
    var x: Float,
    var y: Float,
    var verticalStretching: Float,
    var pressure: Float,
    var size: Float // Check if can add rotation
) {
    val FINGER_HEIGHT = 100

    val xSize
        get() = max(size * 2500, FINGER_HEIGHT + size * 1100)
    val ySize
        get() = (FINGER_HEIGHT + size * 1100)

    val startX
        get() = x - xSize / 2

    val endX
        get() = x + xSize / 2

    val startY
        get() = y - ySize / 2

    val endY
        get() = y + ySize / 2
}