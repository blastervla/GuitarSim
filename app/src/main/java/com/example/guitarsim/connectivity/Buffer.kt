package com.example.guitarsim.connectivity

import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import com.example.guitarsim.BuildConfig
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer


class Buffer {
    private val TAG = Buffer::class.java.simpleName
    private var mSizeBytes: ByteArray = ByteArray(2)
    var buffer: ByteBuffer = ByteBuffer.allocate(0xffff)
    var size: Int = 0

    fun reset() {
        buffer.clear()
        size = 0
    }

    override fun toString(): String {
        return String(buffer.array(), 0, size)
    }

    @Throws(IOException::class)
    fun read(inputStream: FileInputStream): Boolean {
        if (size <= 0) {
            val bytesRead: Int
            try {
                bytesRead = inputStream.read(mSizeBytes)
            } catch (exception: IOException) {
                if (ioExceptionIsNoSuchDevice(exception)) {
                    throw exception
                }
                Log.d(TAG, "IOException while reading size bytes", exception)
                return false
            }

            if (bytesRead != mSizeBytes.size) {
                Log.d(
                    TAG, "Incorrect number of bytes read while reading size bytes:"
                            + " actual=" + bytesRead + " expected=" + mSizeBytes.size
                )
                return false
            }
            size = readSizeBytes()
        }
        val bytesRead: Int
        try {
            bytesRead = inputStream.read(buffer.array(), 0, size)
        } catch (exception: IOException) {
            if (ioExceptionIsNoSuchDevice(exception)) {
                throw exception
            }
            Log.d(TAG, "IOException while reading data bytes", exception)
            return false
        }

        if (bytesRead != size) {
            Log.d(
                TAG, "Incorrect number of bytes read while reading data bytes:"
                        + " actual=" + bytesRead + " expected=" + size
            )
            return false
        }
        return true
    }

    @Throws(IOException::class)
    fun write(outputStream: FileOutputStream, data: ByteArray): Boolean {
        writeSizeBytes(data.size)
        return try {
            outputStream.write(mSizeBytes)
            outputStream.write(buffer.array(), 0, data.size)
            outputStream.flush()
            true
        } catch (exception: IOException) {
            if (ioExceptionIsNoSuchDevice(exception)) {
                throw exception
            }
            Log.d(TAG, "IOException while writing size+data bytes", exception)
            false
        }

    }

    private fun readSizeBytes(): Int {
        return ((mSizeBytes[0].toInt() and 0xff) shl 8) or (mSizeBytes[1].toInt() and 0xff)
    }

    private fun writeSizeBytes(value: Int) {
        if (BuildConfig.DEBUG && (value <= 0 || value > 0xffff)) {
            throw AssertionError("Size value out of bounds: $value")
        }
        mSizeBytes[0] = (value and 0xff00 shr 8).toByte()
        mSizeBytes[1] = (value and 0x00ff).toByte()
    }

    private fun ioExceptionIsNoSuchDevice(ioException: IOException): Boolean {
        val cause = ioException.cause
        if (cause is ErrnoException) {
            return cause.errno == OsConstants.ENODEV
        }
        return false
    }
}