//package com.example.guitarsim.connectivity
//
//import android.app.Activity
//import android.app.PendingIntent
//import android.content.Intent
//import android.hardware.usb.UsbAccessory
//import android.hardware.usb.UsbManager
//import android.os.Handler
//import android.os.Message
//import android.util.Log
//import com.example.guitarsim.connectivity.java.UsbConnection
//import com.example.guitarsim.connectivity.java.Utilities
//import java.io.ByteArrayInputStream
//import java.io.IOException
//import java.io.InputStream
//import java.lang.ref.WeakReference
//
//class AOAManager : Runnable, Handler.Callback {
//
//    private val ACTION_USB_PERMISSION = "com.example.guitarsim.USB_PERMISSION"
//
//    var activity: WeakReference<Activity>? = null
//
//    private var mUSBManager: UsbManager? = null
//
//    //    internal var mConnection: Connection? = null
//    internal var mConnection: UsbConnection? = null
//    private var mDeviceHandler: Handler? = null
//
//    internal var mAccessory: UsbAccessory? = null
//
//    private val gLogPackets = false
//
//    private val mQueryBuffer = ByteArray(4)
//    private val mEmptyPayload = ByteArray(0)
//
//    private var mPermissionRequestPending: Boolean = false
//
//    val mPermissionIntent = PendingIntent.getBroadcast(activity?.get(), 0, Intent(ACTION_USB_PERMISSION), 0)
//
//    internal val CMD_GET_PROTO_VERSION: Byte = 1 // () -> (u8 protocolVersion)
//
//    fun connectToAccessory() {
//        // bail out if we're already connected
//        if (mConnection != null)
//            return
//
//        // ============================ BT ============================
//        /*val address = getIntent().getStringExtra(
//                BTDeviceListActivity.EXTRA_DEVICE_ADDRESS
//            )
//            Log.i("GuitarSim", "want to connect to " + address!!)
//            mConnection = BTConnection(address)
//            performPostConnectionTasks()*/
//        // ===========================================================
//
//        // =========================== USB ===========================
//        // assume only one accessory (currently safe assumption)
//        val accessories = mUSBManager?.accessoryList
//        val accessory = if (accessories == null)
//            null
//        else
//            accessories[0]
//        if (accessory != null) {
//            if (mUSBManager?.hasPermission(accessory) == true) {
//                openAccessory(accessory)
//            } else {
//                mConnection?.mUsbReceiver?.let {
//                    synchronized(it) {
//                        if (!mPermissionRequestPending) {
//                            mUSBManager?.requestPermission(
//                                accessory,
//                                mPermissionIntent
//                            )
//                            mPermissionRequestPending = true
//                        }
//                    }
//                }
//            }
//        } else {
//            Log.d("GuitarSim", "mAccessory is null");
//        }
//
//    }
//
//    fun disconnectFromAccessory() {
//        closeAccessory()
//    }
//
//    private fun openAccessory(accessory: UsbAccessory) {
//        mConnection = UsbConnection(activity?.get(), mUSBManager, accessory)
//        performPostConnectionTasks()
//    }
//
//    private fun performPostConnectionTasks() {
//        sendCommand(CMD_GET_PROTO_VERSION.toInt(), CMD_GET_PROTO_VERSION.toInt())
////        sendCommand(CMD_SETTINGS.toInt(), CMD_SETTINGS.toInt())
////        sendCommand(CMD_BT_NAME.toInt(), CMD_BT_NAME.toInt())
////        sendCommand(CMD_ALARM_FILE.toInt(), CMD_ALARM_FILE.toInt())
////        listDirectory(TUNES_FOLDER)
//
//        val thread = Thread(null, this, "ADK 2012")
//        thread.start()
//    }
//
//    fun closeAccessory() {
//        try {
//            mConnection?.close()
//        } catch (e: IOException) {
//        } finally {
//            mConnection = null
//        }
//    }
//
//    override fun run() {
//        var ret = 0
//        val buffer = ByteArray(16384)
//        var bufferUsed = 0
//
//        while (ret >= 0) {
//            try {
//                ret = mConnection?.inputStream?.read(
//                    buffer, bufferUsed,
//                    buffer.size - bufferUsed
//                ) ?: 0
//                bufferUsed += ret
//                val remainder = process(buffer, bufferUsed)
//                bufferUsed = if (remainder > 0) {
//                    System.arraycopy(buffer, remainder, buffer, 0, bufferUsed - remainder)
//                    remainder
//                } else {
//                    0
//                }
//            } catch (e: IOException) {
//                break
//            }
//
//        }
//    }
//
//    fun process(buffer: ByteArray, bufferUsed: Int): Int {
//        if (gLogPackets) {
//            Log.i(
//                "GuitarSim",
//                "read " + bufferUsed + " bytes: "
//                        + Utilities.dumpBytes(buffer, bufferUsed)
//            )
//        }
//        val inputStream = ByteArrayInputStream(
//            buffer, 0,
//            bufferUsed
//        )
//        mDeviceHandler?.let {
//            val ph = ProtocolHandler(it, inputStream)
//            ph.process()
//        }
//        return inputStream.available()
//    }
//
//    /*fun listDirectory(path:String) {
//        mSoundFiles.clear()
//        val payload = ByteArray(path.length + 1)
//        for (i in 0 until path.length)
//        {
//            payload[i] = path[i].toByte()
//        }
//        payload[path.length] = 0
//        sendCommand(CMD_FILE_LIST.toInt(), CMD_FILE_LIST.toInt(), payload)
//    }*/
//
//    fun sendCommand(
//        command: Int, sequence: Int, payload: ByteArray,
//        buffer: ByteArray?
//    ): ByteArray {
//        var buffer = buffer
//        val bufferLength = payload.size + 4
//        if (buffer == null || buffer.size < bufferLength) {
//            Log.i("GuitarSim", "allocating new command buffer of length $bufferLength")
//            buffer = ByteArray(bufferLength)
//        }
//
//        buffer[0] = command.toByte()
//        buffer[1] = sequence.toByte()
//        buffer[2] = (payload.size and 0xff).toByte()
//        buffer[3] = (payload.size and 0xff00 shr 8).toByte()
//        if (payload.isNotEmpty()) {
//            System.arraycopy(payload, 0, buffer, 4, payload.size)
//        }
//        if (mConnection != null && buffer[1].toInt() != -1) {
//            try {
//                if (gLogPackets) {
//                    Log.i(
//                        "GuitarSim",
//                        "sendCommand: " + Utilities
//                            .dumpBytes(buffer, buffer.size)
//                    )
//                }
//                mConnection?.outputStream?.write(buffer)
//            } catch (e: IOException) {
//                Log.e("GuitarSim", "accessory write failed", e)
//            }
//
//        }
//        return buffer
//    }
//
//    fun sendCommand(command: Int, sequence: Int, payload: ByteArray) {
//        sendCommand(command, sequence, payload, null)
//    }
//
//    private fun sendCommand(command: Int, sequence: Int) {
//        sendCommand(command, sequence, mEmptyPayload, mQueryBuffer)
//    }
//
//    override fun handleMessage(msg: Message): Boolean {
//        when (msg.what) {
//            CMD_SETTINGS -> {
//                handleSettingsCommand(msg.obj as ByteArray)
//                return true
//            }
//            CMD_BT_NAME -> {
//                handleBtNameCommand(msg.obj as ByteArray)
//                handleLicenseTextCommand(msg.obj as ByteArray)
//                return true
//            }
//            CMD_GET_LICENSE -> {
//                handleLicenseTextCommand(msg.obj as ByteArray)
//                return true
//            }
//            CMD_FILE_LIST -> {
//                handleFileListCommand(msg.obj as ByteArray)
//                return true
//            }
//            CMD_ALARM_FILE -> {
//                handleAlarmFileCommand(msg.obj as ByteArray)
//                return true
//            }
//            CMD_GET_SENSORS -> {
//                handleGetSensorsCommand(msg.obj as ByteArray)
//                return true
//            }
//            CMD_LOCK -> {
//                handleLockCommand(msg.obj as ByteArray)
//                return true
//            }
//        }
//        return false
//    }
//
//    private inner class ProtocolHandler(internal var mHandler: Handler, internal var mInputStream: InputStream) {
//
//        @Throws(IOException::class)
//        internal fun readByte(): Int {
//            val retVal = mInputStream.read()
//            if (retVal == -1) {
//                throw RuntimeException("End of stream reached.")
//            }
//            return retVal
//        }
//
//        @Throws(IOException::class)
//        internal fun readInt16(): Int {
//            val low = readByte()
//            val high = readByte()
//            if (gLogPackets) {
//                Log.i("GuitarSim", "readInt16 low=$low high=$high")
//            }
//            return low or (high shl 8)
//        }
//
//        @Throws(IOException::class)
//        internal fun readBuffer(bufferSize: Int): ByteArray {
//            val readBuffer = ByteArray(bufferSize)
//            var index = 0
//            var bytesToRead = bufferSize
//            while (bytesToRead > 0) {
//                val amountRead = mInputStream.read(
//                    readBuffer, index,
//                    bytesToRead
//                )
//                if (amountRead == -1) {
//                    throw RuntimeException("End of stream reached.")
//                }
//                bytesToRead -= amountRead
//                index += amountRead
//            }
//            return readBuffer
//        }
//
//        fun process() {
//            mInputStream.mark(0)
//            try {
//                while (mInputStream.available() > 0) {
//                    if (gLogPackets)
//                        Log.i("GuitarSim", "about to read opcode")
//                    val opCode = readByte()
//                    if (gLogPackets)
//                        Log.i("GuitarSim", "opCode = $opCode")
//                    if (isValidOpCode(opCode)) {
//                        val sequence = readByte()
//                        if (gLogPackets)
//                            Log.i("GuitarSim", "sequence = $sequence")
//                        val replySize = readInt16()
//                        if (gLogPackets)
//                            Log.i("GuitarSim", "replySize = $replySize")
//                        val replyBuffer = readBuffer(replySize)
//                        if (gLogPackets) {
//                            Log.i(
//                                "GuitarSim",
//                                "replyBuffer: " + Utilities.dumpBytes(
//                                    replyBuffer,
//                                    replyBuffer.size
//                                )
//                            )
//                        }
//                        processReply(opCode and 0x7f, sequence, replyBuffer)
//                        mInputStream.mark(0)
//                    }
//                }
//                mInputStream.reset()
//            } catch (e: IOException) {
//                Log.i("GuitarSim", "ProtocolHandler error $e")
//            }
//
//        }
//
//        internal fun isValidOpCode(opCodeWithReplyBitSet: Int): Boolean {
//            if ((opCodeWithReplyBitSet and 0x80) != 0) {
//                val opCode = opCodeWithReplyBitSet and 0x7f
//                return ((opCode >= CMD_GET_PROTO_VERSION) && (opCode <= CMD_LOCK))
//            }
//            return false
//        }
//
//        private fun processReply(opCode: Int, sequence: Int, replyBuffer: ByteArray) {
//            val msg = mHandler.obtainMessage(
//                opCode, sequence, 0,
//                replyBuffer
//            )
//            mHandler.sendMessage(msg)
//        }
//    }
//}