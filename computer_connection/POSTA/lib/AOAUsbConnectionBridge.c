#include "UsbConnectionBridge.h"
#include <stdio.h>
#include <usb.h>
#include <libusb-1.0/libusb.h>
#include <string.h>
#include <unistd.h>
#include <assert.h>
#include <stdbool.h>

#define BUFFER 1024

int in;
int out;

int vid;
int pid;

int accessory_pid;
int accessory_pid_alt;

struct libusb_device_handle* handle;
char stop;
char success = 0;

AOAConnectionListener connectionListener;

int isUsbAccessory ();
int init();
static void error(int code);
int setupAccessory(
  const char* manufacturer,
  const char* modelName,
  const char* description,
  const char* version,
  const char* uri,
  const char* serialNumber);

int start (char* manufacturer,
  char* modelName,
  char* description,
  char* version,
  char* uri,
  char* serialNumber,
  int inPort,
  int outPort,
  int vendorId,
  int productId,
  int accessoryProductId,
  int accessoryProductIdAlt,
  AOAConnectionListener listener) {
    in = inPort;
    out = outPort;
    vid = vendorId;
    pid = productId
    accessory_pid = accessoryProductId;
    accessory_pid_alt = accessoryProductIdAlt;
    
    connectionListener = listener;

  if (isUsbAccessory() < 0) {
    if(init() < 0)
      return -1;
    if(setupAccessory(manufacturer,
                      modelName,
                      description,
                      version,
                      uri,
                      serialNumber) {
      fprintf(stdout, "Error setting up accessory\n");
      close();
      return -1;
    }
  }
  fprintf(stdout, "USB Server correctly, no errors\n");
  return 0;
}

int listenNow(){
  unsigned char buffer[BUFFER];
  int transferred;

  if (libusb_bulk_transfer(handle, in, buffer, BUFFER, &transferred, 0) == 0) {
    connectionListener(buffer, transferred);
    return 0;
  }

  return -1;
}

int isUsbAccessory () {
  // 端末がすでにUSB Accessory Modeかどうかを判定する
  int res;
  libusb_init(NULL);
  if((handle = libusb_open_device_with_vid_pid(NULL, vid,  accessory_pid)) == NULL) {
    fprintf(stdout, "Device is not USB Accessory Mode\n");
    res = -1;
  } else {
    // already usb accessory mode
    fprintf(stdout, "Device is already USB Accessory Mode\n");
    libusb_set_configuration(handle, 1);
    sleep(1);

    libusb_claim_interface(handle, 0);
    res = 0;
  }
  return res;
}

int init(){
  if((handle = libusb_open_device_with_vid_pid(NULL, vid, pid)) == NULL){
    fprintf(stdout, "Problem acquireing handle\n");
    return -1;
  }
  libusb_set_configuration(handle, 1);
  sleep(1);
  libusb_claim_interface(handle, 0);
  return 0;
}

int close(){
  if(handle != NULL)
    libusb_release_interface (handle, 0);
  libusb_exit(NULL);

    for (int i = 0; i < MAX_FINGER_PRESS_AMOUNT; i++) {
        free(presses[i]);
    }

  return 0;
}

int setupAccessory(
  const char* manufacturer,
  const char* modelName,
  const char* description,
  const char* version,
  const char* uri,
  const char* serialNumber){

  unsigned char ioBuffer[2];
  int devVersion;
  int response;
  int tries = 5;

  // DeviceがAndroid accessory protocolをサポートしているか判定する
  response = libusb_control_transfer(
    handle, //handle
    0xC0, //bmRequestType
    51, //bRequest
    0, //wValue
    0, //wIndex
    ioBuffer, //data
    2, //wLength
        0 //timeout
  );

  if(response < 0){error(response);return-1;}

  devVersion = ioBuffer[1] << 8 | ioBuffer[0];
  fprintf(stdout,"Verion Code Device: %d\n", devVersion);
  
  usleep(1000);//sometimes hangs on the next transfer :(

  // Accessory Identificationを送信する
  response = libusb_control_transfer(handle,0x40,52,0,0,(char*)manufacturer,strlen(manufacturer),0);
  if(response < 0){error(response);return -1;}
  response = libusb_control_transfer(handle,0x40,52,0,1,(char*)modelName,strlen(modelName)+1,0);
  if(response < 0){error(response);return -1;}
  response = libusb_control_transfer(handle,0x40,52,0,2,(char*)description,strlen(description)+1,0);
  if(response < 0){error(response);return -1;}
  response = libusb_control_transfer(handle,0x40,52,0,3,(char*)version,strlen(version)+1,0);
  if(response < 0){error(response);return -1;}
  response = libusb_control_transfer(handle,0x40,52,0,4,(char*)uri,strlen(uri)+1,0);
  if(response < 0){error(response);return -1;}
  response = libusb_control_transfer(handle,0x40,52,0,5,(char*)serialNumber,strlen(serialNumber)+1,0);
  if(response < 0){error(response);return -1;}

  fprintf(stdout,"Accessory Identification sent\n", devVersion);

  // DeviceをAccessory modeにする
  response = libusb_control_transfer(handle,0x40,53,0,0,NULL,0,0);
  if(response < 0){error(response);return -1;}

  fprintf(stdout,"Attempted to put device into accessory mode\n", devVersion);

  if(handle != NULL){
    libusb_close(handle);
  }

  fprintf(stdout, "connect to new PID...\n");
  for(;;){ //attempt to connect to new PID, if that doesn't work try ACCESSORY_PID_ALT
    tries--;
    if((handle = libusb_open_device_with_vid_pid(NULL, vid, accessory_pid)) == NULL){
      if(tries < 0){
        return -1;
      }
    }else{
      break;
    }
    sleep(1);
  }
  
  // Interface #0をhandleに紐づける
  fprintf(stdout, "claim usb accessory I/O interface\n");
  libusb_set_configuration(handle, 1);
  sleep(2);
  
  response = libusb_claim_interface(handle, 0);
  if(response < 0){error(response);return -1;}

  fprintf(stdout, "Interface claimed, ready to transfer data\n");
  return 0;
}

static void error(int code){
  fprintf(stdout,"\n");
  switch(code){
  case LIBUSB_ERROR_IO:
    fprintf(stdout,"Error: LIBUSB_ERROR_IO\nInput/output error.\n");
    break;
  case LIBUSB_ERROR_INVALID_PARAM:
    fprintf(stdout,"Error: LIBUSB_ERROR_INVALID_PARAM\nInvalid parameter.\n");
    break;
  case LIBUSB_ERROR_ACCESS:
    fprintf(stdout,"Error: LIBUSB_ERROR_ACCESS\nAccess denied (insufficient permissions).\n");
    break;
  case LIBUSB_ERROR_NO_DEVICE:
    fprintf(stdout,"Error: LIBUSB_ERROR_NO_DEVICE\nNo such device (it may have been disconnected).\n");
    break;
  case LIBUSB_ERROR_NOT_FOUND:
    fprintf(stdout,"Error: LIBUSB_ERROR_NOT_FOUND\nEntity not found.\n");
    break;
  case LIBUSB_ERROR_BUSY:
    fprintf(stdout,"Error: LIBUSB_ERROR_BUSY\nResource busy.\n");
    break;
  case LIBUSB_ERROR_TIMEOUT:
    fprintf(stdout,"Error: LIBUSB_ERROR_TIMEOUT\nOperation timed out.\n");
    break;
  case LIBUSB_ERROR_OVERFLOW:
    fprintf(stdout,"Error: LIBUSB_ERROR_OVERFLOW\nOverflow.\n");
    break;
  case LIBUSB_ERROR_PIPE:
    fprintf(stdout,"Error: LIBUSB_ERROR_PIPE\nPipe error.\n");
    break;
  case LIBUSB_ERROR_INTERRUPTED:
    fprintf(stdout,"Error:LIBUSB_ERROR_INTERRUPTED\nSystem call interrupted (perhaps due to signal).\n");
    break;
  case LIBUSB_ERROR_NO_MEM:
    fprintf(stdout,"Error: LIBUSB_ERROR_NO_MEM\nInsufficient memory.\n");
    break;
  case LIBUSB_ERROR_NOT_SUPPORTED:
    fprintf(stdout,"Error: LIBUSB_ERROR_NOT_SUPPORTED\nOperation not supported or unimplemented on this platform.\n");
    break;
  case LIBUSB_ERROR_OTHER:
    fprintf(stdout,"Error: LIBUSB_ERROR_OTHER\nOther error.\n");
    break;
  default:
    fprintf(stdout, "Error: unkown error\nERROR: %i\n", code);
  }
}