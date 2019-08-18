package com.example.guitarsim

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt


class ShakeDetector(val mSensorManager: SensorManager? = null, val onShake: (() -> Unit)) {
    var mAccel: Float = 0.toFloat() // acceleration apart from gravity
    private var mAccelCurrent: Float = 0.toFloat() // current acceleration including gravity
    private var mAccelLast: Float = 0.toFloat() // last acceleration including gravity

    private val mSensorListener = object : SensorEventListener {
        override fun onSensorChanged(se: SensorEvent) {
            val x = se.values[0]
            val y = se.values[1]
            val z = se.values[2]
            mAccelLast = mAccelCurrent
            mAccelCurrent = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = mAccelCurrent - mAccelLast
            mAccel = mAccel * 0.9f + delta // perform low-cut filter

            if (mAccel > 10) {
                onShake()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    init {
        mSensorManager?.registerListener(
            mSensorListener,
            mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        );
        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;
    }

    fun resume() {
        mSensorManager?.registerListener(
            mSensorListener,
            mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    fun pause() {
        mSensorManager?.unregisterListener(mSensorListener)
    }
}