package com.example.guitarsim.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment

class SharedPrefsUtils(val context: Context) {

    companion object {
        private const val SHARED_PREFS_FILE = "com.blastervla.guitarsim.1281998"
        private const val VIEWPORT_LOCATION_KEY = "VIEWPORT_LOCATION"
        private const val SCALE_LENGTH_KEY = "SCALE_SIZE"
        private const val FRET_AMOUNT_KEY = "FRET_AMOUNT"

        fun isStoragePermissionGranted(fragment: Fragment): Boolean {
            return if (ActivityCompat.checkSelfPermission(
                    fragment.context!!,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                true
            } else {
                fragment.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
                false
            }
        }
    }

    fun setViewportLocation(location: Int) {
        context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE).edit().apply {
            putInt(VIEWPORT_LOCATION_KEY, location)
            apply()
        }
    }

    fun getViewportLocation(): Int = context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
        .getInt(VIEWPORT_LOCATION_KEY, 0)

    fun setScaleLength(scaleSize: Float) {
        context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE).edit().apply {
            putFloat(SCALE_LENGTH_KEY, scaleSize)
            apply()
        }
    }

    fun getScaleLength(): Float = context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
        .getFloat(SCALE_LENGTH_KEY, 650f)

    fun setFretAmount(fretAmount: Int) {
        context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE).edit().apply {
            putInt(FRET_AMOUNT_KEY, fretAmount)
            apply()
        }
    }

    fun getFretAmount(): Int = context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
        .getInt(FRET_AMOUNT_KEY, 24)
}