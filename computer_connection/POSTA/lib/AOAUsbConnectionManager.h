#include "ConnectionManager.h"

// To include C files
extern "C" {
  #include "AOAUsbConnectionBridge.h"
}

class UsbConnectionManager: public ConnectionManager {
    public:
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
        )

    private:
        int mPortIn;
        int mPortOut;

        int mVid;
        int mPid;

        int mAccessoryPid;
        int mAccessoryPidAlt;

        char* mManufacturer;
        char* mModelName;
        char* mDescription;
        char* mVersion;
        char* mUri;
        char* mSerialNumber;

        AOAUsbConnectionBridge connectionBridge;
}