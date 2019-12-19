package com.example.guitarsim.connectivity

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import com.example.guitarsim.BuildConfig
import com.example.guitarsim.connectivity.AOABridge.AOABridgeHandler.Companion.MAYBE_READ
import com.example.guitarsim.connectivity.AOABridge.AOABridgeHandler.Companion.STOP_THREAD
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import android.content.IntentFilter




class AOABridge(val context: Context?, listener: Listener?) {
    companion object {
        private val TAG = AOABridge::class.java.simpleName
        private const val CONNECT_COOLDOWN_MS: Long = 100
        private const val READ_COOLDOWN_MS: Long = 100
        private val ACTION_USB_PERMISSION = "com.example.guitarsim.USB_PERMISSION"
    }

    private var mListener: Listener? = null
    private var mUsbManager: UsbManager? = null
    private var mReadBuffer: Buffer? = null
    private var mInternalThread: InternalThread? = null
    private var mIsShutdown: Boolean = false
    private var mIsAttached: Boolean = false
    private var mOutputStream: FileOutputStream? = null
    private var mInputStream: FileInputStream? = null
    private var mParcelFileDescriptor: ParcelFileDescriptor? = null

    val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)

    init {
        if (BuildConfig.DEBUG && (context == null || listener == null)) {
            throw AssertionError("Arguments context and listener must not be null")
        }
        mListener = listener
        mUsbManager = context!!.getSystemService(Context.USB_SERVICE) as UsbManager
        mReadBuffer = Buffer()
        mInternalThread = InternalThread()
        mInternalThread?.start()
    }

    @Synchronized
    fun write(bufferHolder: Buffer, data: ByteArray): Boolean {
        if (BuildConfig.DEBUG && (mIsShutdown || mOutputStream == null)) {
            throw AssertionError("Can't write if shutdown or output stream is null")
        }
        return try {
            mOutputStream?.let { bufferHolder.write(it, data) } ?: false
        } catch (exception: IOException) {
            mInternalThread!!.terminate()
            false
        }

    }

    class AOABridgeHandler(
        private val parent: WeakReference<AOABridge>,
        private val thread: WeakReference<InternalThread>
    ) : Handler() {
        companion object {
            const val STOP_THREAD = 1
            const val MAYBE_READ = 2
        }

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                STOP_THREAD -> Looper.myLooper()!!.quit()
                MAYBE_READ -> {
                    parent.get()?.apply {
                        val readResult: Boolean
                        try {
                            mInputStream?.let {
                                readResult = mReadBuffer!!.read(it)
                                Toast.makeText(context, "Recibí algo", Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "Recibí algo :shrug:")

                                if (readResult) {
                                    if (mReadBuffer!!.size == 0) {
                                        thread.get()?.mHandler?.sendEmptyMessage(STOP_THREAD)
                                    } else {
                                        mListener!!.onAoabRead(mReadBuffer)
                                        mReadBuffer!!.reset()
                                        thread.get()?.mHandler?.sendEmptyMessage(MAYBE_READ)
                                    }
                                } else {
                                    thread.get()?.mHandler?.sendEmptyMessageDelayed(MAYBE_READ, READ_COOLDOWN_MS)
                                }
                            }
                        } catch (exception: IOException) {
                            thread.get()?.terminate()
                            return
                        }
                    }
                }
            }
        }
    }

    inner class InternalThread {

        var mHandler: Handler? = null

        private val mUsbReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action

                Log.d("GuitarSim", action)
//                Toast.makeText(context, action, Toast.LENGTH_SHORT).show()
                if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED == action) {
                    Toast.makeText(context, "Yada", Toast.LENGTH_SHORT).show()
                    synchronized(this) {
                        val accessories = mUsbManager?.accessoryList

                        accessories?.firstOrNull()?.let {
                            maybeAttachAccessory(it)
                        }
                    }
                } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED == action) {
                    (intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY) as UsbAccessory?)?.apply {
                        // call your method that cleans up and closes communication with the accessory
                        detachAccessory()
                        Toast.makeText(context, "Detacheado", Toast.LENGTH_SHORT).show()
                        mListener!!.onAoabShutdown()
                    }
                }
            }
        }

        /*override fun run() {
            Looper.prepare()
            mHandler = AOABridgeHandler(WeakReference(this@AOABridge), WeakReference(this))
            *//*detectAccessory()
            Looper.loop()
            detachAccessory()
            mIsShutdown = true
            mListener!!.onAoabShutdown()

            // Clean stuff up
            mHandler = null
            mListener = null
            mUsbManager = null
            mReadBuffer = null
            mInternalThread = null*//*
        }*/

        internal fun terminate() {
            mHandler!!.sendEmptyMessage(STOP_THREAD)
        }

        /*private fun detectAccessory() {
            while (!mIsAttached) {
                if (mIsShutdown) {
                    mHandler!!.sendEmptyMessage(STOP_THREAD)
                    return
                }
                try {
                    sleep(CONNECT_COOLDOWN_MS)
                } catch (exception: InterruptedException) {
                    // pass
                }

                val accessoryList = mUsbManager!!.accessoryList
                if (accessoryList == null || accessoryList.isEmpty()) {
                    continue
                }
                if (accessoryList.size > 1) {
                    Log.w(TAG, "Multiple accessories attached!? Using first one...")
                }
                maybeAttachAccessory(accessoryList[0])
            }
        }*/

        private fun maybeAttachAccessory(accessory: UsbAccessory) {
            if (mUsbManager?.hasPermission(accessory) == true) {
                mUsbManager?.openAccessory(accessory)

                val parcelFileDescriptor = mUsbManager!!.openAccessory(accessory)
                if (parcelFileDescriptor != null) {
                    val fileDescriptor = parcelFileDescriptor.fileDescriptor
                    mIsAttached = true
                    mOutputStream = FileOutputStream(fileDescriptor)
                    mInputStream = FileInputStream(fileDescriptor)
                    mParcelFileDescriptor = parcelFileDescriptor
                    mHandler!!.sendEmptyMessage(MAYBE_READ)
                    Toast.makeText(context, "Toy adentro boló", Toast.LENGTH_SHORT).show()
                }
            } else {
                val permissionIntent = permissionIntent
                mUsbManager?.requestPermission(accessory, permissionIntent)
                Toast.makeText(context, "Permission denied for accessory $accessory", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Permission denied for accessory $accessory")
            }
        }

        private fun detachAccessory() {
            if (mIsAttached) {
                mIsAttached = false
            }
            if (mInputStream != null) {
                closeQuietly(mInputStream!!)
                mInputStream = null
            }
            if (mOutputStream != null) {
                closeQuietly(mOutputStream!!)
                mOutputStream = null
            }
            if (mParcelFileDescriptor != null) {
                closeQuietly(mParcelFileDescriptor!!)
                mParcelFileDescriptor = null
            }
        }

        private fun closeQuietly(closable: Closeable) {
            try {
                closable.close()
            } catch (exception: IOException) {
                // pass
            }

        }

        fun start() {
            // Register Intent myPermission and remove accessory
            val filter = IntentFilter()
            filter.addAction(ACTION_USB_PERMISSION)
            filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
            context?.registerReceiver(mUsbReceiver, filter)
        }

    }

    interface Listener {
        fun onAoabRead(bufferHolder: Buffer?)
        fun onAoabShutdown()
    }
}