package com.example.guitarsim

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.guitarsim.utils.ParsingUtils
import com.example.guitarsim.utils.SharedPrefsUtils
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        txtScaleLength.setText(SharedPrefsUtils(this).getScaleLength().toString())
        txtFretAmount.setText(SharedPrefsUtils(this).getFretAmount().toString())
        txtViewportLocation.setText(SharedPrefsUtils(this).getViewportLocation().toString())
    }

    override fun onBackPressed() {
        super.onBackPressed()
        SharedPrefsUtils(this).apply {
            setScaleLength(ParsingUtils.defaultIfEmptyString(txtScaleLength.text.toString(), 650).toFloat())
            setFretAmount(ParsingUtils.defaultIfEmptyString(txtFretAmount.text.toString(), 24).toInt())
            setNodeAmount(ParsingUtils.defaultIfEmptyString(txtNodeAmount.text.toString(), 400).toInt())
            setViewportLocation(ParsingUtils.defaultIfEmptyString(txtViewportLocation.text.toString(), 0).toInt())
        }
    }
}