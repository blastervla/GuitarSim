typedef void (*AOAConnectionListener)(unsigned char*&, int);

/**
 * Starts the USB connection with the Android device.
 * @return 0 on successful setup, -1 otherwise.
 */
int start( 
  char* manufacturer,
  char* modelName,
  char* description,
  char* version,
  char* uri,
  char* serialNumber,
  int in,
  int out,
  int vid,
  int pid,
  int accessory_pid,
  int accessory_pid_alt,
  AOAConnectionListener listener
);
int listenNow();

int close();