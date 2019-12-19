package com.example.guitarsim.connectivity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Message
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException


class AOAManager2(context: Context, intent: Intent) {

    private val MESSAGE_LED = 1

    private val TAG = "HelloADK"

    private val ACTION_USB_PERMISSION = "com.example.guitarsim.USB_PERMISSION"

    private val mUsbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var mAccessory: UsbAccessory? = null

    var mFileDescriptor: ParcelFileDescriptor? = null

    var mInputStream: FileInputStream? = null
    var mOutputStream: FileOutputStream? = null

    val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)

    private val mUsbReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val accessories = mUsbManager.accessoryList

                    accessories.firstOrNull()?.let {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            openAccesory(it)
                        } else {
                            val permissionIntent = permissionIntent
                            mUsbManager.requestPermission(it, permissionIntent)
                            Log.d(TAG, "Permission denied for accessory $it")
                        }
                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED == action) {
                (intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY) as UsbAccessory?)?.apply {
                    // call your method that cleans up and closes communication with the accessory
                    if (this == mAccessory) {
                        closeConnection()
                    }
                }
            }
        }
    }

    fun openAccesory(accessory: UsbAccessory) {
        mFileDescriptor = mUsbManager.openAccessory(accessory)

        if (mFileDescriptor != null) {
            mAccessory = accessory
            val fd = mFileDescriptor!!.fileDescriptor

            mInputStream = FileInputStream(fd)
            mOutputStream = FileOutputStream(fd)

            // communication thread start
            val thread = Thread(null, AOAManagerReader(), "DemoKit")
            thread.start()
            Log.d(TAG, "accessory opened")

//            enableControls(true)
        } else {
            Log.d(TAG, "accessory open fail")
        }

    }

    fun closeConnection() {
        try {
            mFileDescriptor?.close()
        } catch (e: IOException) {
        } finally {
            mFileDescriptor = null
            mAccessory = null
        }
    }

    fun startListening() {
//        val thread = Thread(null, AOAManagerReader(), "DemoKit")
//        thread.start()
    }

    inner class AOAManagerReader: Runnable {
        override fun run() {
            var ret = 0
            val buffer = ByteArray(16384)
            var i: Int

            // Accessory -> Android
            while (ret >= 0) {
                try {
                    ret = mInputStream!!.read(buffer)
                } catch (e: IOException) {
                    e.printStackTrace()
                    break
                }

                if (ret > 0) {
                    Log.d(TAG, "$ret bytes message received.")
                }
                i = 0
                while (i < ret) {
                    val len = ret - i

                    when (buffer[i]) {
                        0x1.toByte() -> if (len >= 2) {
                            val m = Message.obtain(mHandler, MESSAGE_LED)
                            m.arg1 = buffer[i + 1].toInt()
                            mHandler.sendMessage(m)
                            i += 2
                        }

                        else -> {
                            Log.d(TAG, "unknown msg: " + buffer[i])
                            i = len
                        }
                    }
                }
            }
        }
    }

    private val mHandler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_LED -> if (msg.arg1 == 0) {
                    Toast.makeText(context, "Llegó algo", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Llegó otra cosa", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}