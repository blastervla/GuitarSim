/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.example.guitarsim.connectivity.java;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.example.guitarsim.MainActivity;

import java.io.*;

public class UsbConnection extends Connection {
    Activity mActivity;
    FileInputStream mInputStream;
    FileOutputStream mOutputStream;
    ParcelFileDescriptor mFileDescriptor;
    UsbAccessory mAccessory;
    UsbManager mUsbManager;

    private static final String ACTION_USB_PERMISSION = "com.example.guitarsim.USB_PERMISSION";

    public final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory[] accessories = mUsbManager.getAccessoryList();
                if (accessories.length > 0) {
                    UsbAccessory accessory = accessories[0];
                    if (accessory != null && accessory.equals(mAccessory)) {
                        Log.i("GuitarSim", "closing accessory");
                        Intent connectIntent = new Intent(mActivity,
                                MainActivity.class);
                        connectIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        mActivity.startActivity(connectIntent);
                    }
                }
            }
        }
    };

    public UsbConnection(Activity activity, UsbManager usbManager,
                         UsbAccessory accessory) {
        mActivity = activity;
        mFileDescriptor = usbManager.openAccessory(accessory);
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
        }
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        mUsbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        mActivity.registerReceiver(mUsbReceiver, filter);
    }

    @Override
    public InputStream getInputStream() {
        return mInputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return mOutputStream;
    }

    public void close() throws IOException {
        if (mFileDescriptor != null) {
            mFileDescriptor.close();
        }
        mActivity.unregisterReceiver(mUsbReceiver);
    }

}
