package com.example.guitarsim.utils

import android.app.Activity
import android.content.res.Resources
import android.util.DisplayMetrics


object ViewUtils {
    const val DP_TO_MM_CONSTANT = 0.159

    fun getScreenHeight(activity: Activity): Int {
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels + getNavigationBarHeight(activity)
        val width = displayMetrics.widthPixels

        return height
    }

    private fun getNavigationBarHeight(activity: Activity): Int {
        val metrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(metrics)
        val usableHeight = metrics.heightPixels
        activity.windowManager.defaultDisplay.getRealMetrics(metrics)
        val realHeight = metrics.heightPixels
        return if (realHeight > usableHeight)
            realHeight - usableHeight
        else
            0
    }

    fun dpToPx(dp: Int): Int = (dp * Resources.getSystem().displayMetrics.density).toInt()

    fun pxToDp(px: Int): Int = (px / Resources.getSystem().displayMetrics.density).toInt()

    fun dpToMm(dp: Int): Double = (dp * DP_TO_MM_CONSTANT)

    fun pxToMm(px: Int): Double = dpToMm(pxToDp(px))

    fun mmToDp(mm: Double): Int = (mm / DP_TO_MM_CONSTANT).toInt()

    fun mmToPx(mm: Double): Int = dpToPx(mmToDp(mm))
}