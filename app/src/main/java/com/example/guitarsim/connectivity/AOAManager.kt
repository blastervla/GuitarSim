package com.example.guitarsim.connectivity

import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class AOAManager(
    private var mUsbManager: UsbManager,
    private var mUsbAccessory: UsbAccessory? = null
) : Runnable {

    private var shouldRun = false
    private var dataToWrite: ByteArray = ByteArray(0)

    fun connect() {
        if (mUsbAccessory == null) {
            val accessoryList = mUsbManager.accessoryList ?: return
            mUsbAccessory = accessoryList[0]
        }
        shouldRun = true
        val thread = Thread(null, this, "AccessoryThread")
        thread.start()
    }

    fun disconnect() {
        shouldRun = false
    }

    fun write(data: ByteArray) {
        dataToWrite = data
    }

    override fun run() {
        val pfd = mUsbManager.openAccessory(mUsbAccessory)
        val fd = pfd.fileDescriptor
        val fis = FileInputStream(fd)
        val fos = FileOutputStream(fd)
        try {
            while (this.shouldRun) {
                if (dataToWrite.isEmpty()) continue

                fos.write(dataToWrite)
                Log.v("ASDF", "Length of transfer: ${dataToWrite.size}")
                dataToWrite = ByteArray(0)
            }
            pfd.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}