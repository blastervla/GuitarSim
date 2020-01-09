#include "AOAUsbConnectionManager.h"

void setConnectionDetails(
            char* manufacturer,
            char* modelName,
            char* description,
            char* version,
            char* uri,
            char* serialNumber,
            int portIn, 
            int portOut, 
            int vid, 
            int pid, 
            int accessoryPid, 
            int accessoryPidAlt
        ) {
            mManufacturer = manufacturer;
            mModelName = modelName;
            mDescription = description;
            mVersion = version;
            mUri = uri;
            mSerialNumber = serialNumber;
            mIn = in;
            mOut = out;
            mVid = vid;
            mPid = pid;
            mAccessoryPid = accessoryPid;
            mAccessoryPidAlt = accessoryPidAlt;
        }

int start(ConnectionListener connectionListener) {
    return connectionBridge.start( 
        mManufacturer,
        mModelName,
        mDescription,
        mVersion,
        mUri,
        mSerialNumber,
        mIn,
        mOut,
        mVid,
        mPid,
        mAccessoryPid,
        mAccessoryPidAlt,
        connectionListener
    );
}

int listenNow() {
    connectionListener.listenNow();
}

void send(byte[] data) {
    // TODO: Implement
}