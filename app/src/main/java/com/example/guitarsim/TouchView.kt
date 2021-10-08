package com.example.guitarsim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.guitarsim.data.TouchInfo


class TouchView(context: Context, attributes: AttributeSet? = null) : View(context, attributes) {

    /**
     * Map containing each touch Id with its respective touch information
     */
    val touches = hashMapOf<Int, TouchInfo>()
    val removedTouches = arrayListOf<Int>()
    val tapPaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, android.R.color.holo_blue_light)
        alpha = 140
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        touches.values.forEach {
            canvas.drawOval(it.startX, it.startY, it.endX, it.endY, tapPaint)
        }
    }

    fun removeTouch(id: Int) {
        if (!touches.containsKey(id)) return

        touches.remove(id)
        removedTouches.add(id)
    }

    fun removeAllTouches() {
        touches.keys.forEach {
            removedTouches.add(it)
        }
        touches.clear()
    }
}