#include <stdio.h>
#include <usb.h>
#include <libusb-1.0/libusb.h>
#include <string.h>
#include <unistd.h>
#include <assert.h>
#include <stdbool.h>

#define IN 0x81    // In Port
#define OUT 0x07   // Out Port

#define VID 0x18D1 // Google Inc.
#define PID 0x4EE7 // Pixel 3 debug

#define ACCESSORY_PID 0x2D01
#define ACCESSORY_PID_ALT 0x2D00

#define BUFFER 1024

#define MAX_FINGER_PRESS_AMOUNT 20
uint16_t NODE_AMOUNT = 400;

typedef struct FingerPress {
    bool valid;
    bool isCejilla;
    int id;
    int node;
    int vertStretch;
    int pressure;
} FingerPress;

FingerPress* presses[MAX_FINGER_PRESS_AMOUNT]; // Andá a llegar a 10 taps, te reto...

static int mainPhase();
static int isUsbAccessory (void);
static int init(void);
static int deInit(void);
static void error(int code);
static int setupAccessory(
  const char* manufacturer,
  const char* modelName,
  const char* description,
  const char* version,
  const char* uri,
  const char* serialNumber);

//static
static struct libusb_device_handle* handle;
static char stop;
static char success = 0;

// ===== UTILS ======
void clearScreen()
{
    system("clear");
}
// ==================

void agregarOActualizarPress(FingerPress *press) {
    // Encuentro posición libre en presses
    int positionForPress = -1;
    int freePosition = -1;
    for (uint8_t i = 0; i < MAX_FINGER_PRESS_AMOUNT && positionForPress == -1; i++) {
        if (presses[i]->id == press->id && presses[i]->valid) { // Ya lo tengo
            positionForPress = i;
        } else if (!presses[i]->valid && freePosition == -1) {
            // Me guardo la primera posición libre que encuentre, por si lo tengo que agregar
            freePosition = i;
        }
    }

    if (positionForPress == -1) { // Si no lo tenía
        positionForPress = freePosition; // Lo meto en un slot no válido
    }
    assert(positionForPress != -1);
    
    // Actualizo press
    presses[positionForPress]->valid = true;
    presses[positionForPress]->id = press->id;
    presses[positionForPress]->isCejilla = press->isCejilla;
    presses[positionForPress]->node = press->node;
    presses[positionForPress]->vertStretch = press->vertStretch;
    presses[positionForPress]->pressure = press->pressure;

    free(press);
}

void quitarPress(int id) {
    // Encuentro posición libre en presses
    bool eliminado;
    for (uint8_t i = 0; i < MAX_FINGER_PRESS_AMOUNT && !eliminado; i++) {
        if (presses[i]->id == id) { // Lo elimino
            presses[i]->valid = false;
            eliminado = true;
        }
    }
}

int main (int argc, char *argv[]){
    for (int i = 0; i < MAX_FINGER_PRESS_AMOUNT; i++) {
        presses[i] = malloc(sizeof(FingerPress));
    }

  if (isUsbAccessory() < 0) {
    if(init() < 0)
      return -1;
    if(setupAccessory(
                      "Blastervla",
                      "Guitar Simulator",
                      "Guitar Simulator",
                      "1.0",
                      "http://d.hatena.ne.jp/thorikawa/",
                      "0000000012345678") < 0) {
      fprintf(stdout, "Error setting up accessory\n");
      deInit();
      return -1;
    }
  }
  if(mainPhase() < 0){
    fprintf(stdout, "Error during main phase\n");
    deInit();
    return -1;
  }  
  deInit();
  fprintf(stdout, "Done, no errors\n");
  return 0;
}

static int mainPhase(){
  unsigned char buffer[BUFFER];
  int response = 0;
  static int transferred;

  int i;
  int index=1;
  fprintf(stdout, "start main Phase\n");

  int read;
  do {
      read = libusb_bulk_transfer(handle, IN, buffer, BUFFER, &transferred, 0);
      fprintf(stdout, "Trying to connect... received: %i\n", read);
      sleep(1);
  } while (libusb_bulk_transfer(handle, IN, buffer, BUFFER, &transferred, 0) != 0);

  fprintf(stdout, "Connected!\n");
  while (libusb_bulk_transfer(handle, IN, buffer, BUFFER, &transferred, 0) == 0) {
    int offset;
    clearScreen();

    int intOffset = 0;
    // Nos movemos de a 5 bytes, que es el mínimo tamaño del paquete.
    for (offset = 0; offset < transferred; offset += 8) {
        sleep(0.1);

        // printf("Received: %i, ", (char) buffer[offset]);
        // printf("%i, ", (char) buffer[offset + 1]);
        // printf("%i, ", (char) buffer[offset + 2]);
        // printf("%i, ", (char) buffer[offset + 3]);
        // printf("%i, ", (char) buffer[offset + 4]);
        // printf("%i, ", (char) buffer[offset + 5]);
        // printf("%i, ", (char) buffer[offset + 6]);
        // printf("%i, ", (char) buffer[offset + 7]);
        // printf("%i, ", (char) buffer[offset + 8]);
        // printf("%i, ", (char) buffer[offset + 9]);
        // printf("%i, ", (char) buffer[offset + 10]);
        // printf("%i, ", (char) buffer[offset + 11]);
        // printf("%i, ", (char) buffer[offset + 12]);
        // printf("%i, ", (char) buffer[offset + 13]);
        // printf("%i, ", (char) buffer[offset + 14]);
        // printf("%i, ", (char) buffer[offset + 15]);
        // printf("%i, ", (char) buffer[offset + 16]);
        // printf("%i, ", (char) buffer[offset + 17]);
        // printf("%i, ", (char) buffer[offset + 18]);
        // printf("%i, ", (char) buffer[offset + 19]);
        // printf("%i, ", (char) buffer[offset + 20]);
        // printf("%i, ", (char) buffer[offset + 21]);
        // printf("%i, ", (char) buffer[offset + 22]);
        // printf("%i\n", (char) buffer[offset + 23]);

        // printf("Test %i\n", (uint8_t) buffer[offset + 8]);
        // printf("Test2 %i\n", (uint8_t) buffer[offset + 9] << 8);

        int *buff = (int *) buffer;
        int op = buff[intOffset];
        // printf("OP: %i\n", op);
        
        switch (op) {
            case 0x2: { // Finger press
                FingerPress *press = malloc(sizeof(FingerPress));
                press->isCejilla = buff[intOffset + 1];     // opcode is 1 Byte
                press->node = buff[intOffset + 2];          // isCejilla is 1 Byte
                press->id = buff[intOffset + 3];            // node is 2 Bytes
                press->vertStretch = buff[intOffset + 4];   // id is 4 Bytes
                press->pressure = buff[intOffset + 5];     // vertStretch is 4 Bytes

                // printf("id: %i\n", press->id);
                // printf("node: %i\n", press->node);
                // printf("vertStretch: %i\n", press->vertStretch);
                // printf("pressure: %i\n", press->pressure);
                // printf("isCejilla: %s\n", press->isCejilla ? "true" : "false");

                agregarOActualizarPress(press);

                // Este es un paquete más grande de lo común
                // Lo aumentamos en la diferencia con el paquete más pequeño
                // 24 - 8 = 16
                intOffset += 6;
                offset += 16;

                break;
            }
            case 0x3: { // Finger release
                int id = buff[intOffset + 1];

                printf("Quito press %i\n", id);

                quitarPress(id);
                intOffset += 2;

                break;
            }
            default: // assume 8 byte packet
                printf("No entendí :(\n\n");
                intOffset += 2;
            break;
        }
    }

    // Logging
    printf("Se están apretando los nodos: ============================\n");
    for (uint8_t i = 0; i < MAX_FINGER_PRESS_AMOUNT; i++) {
        if (presses[i]->valid) { // Lo elimino
            if (presses[i]->isCejilla) {
                printf("[ C ]");
            } else {
                printf("     ");
            }
            printf("Node:  %i  |  VertStretch:  %i  |  Pressure:  %i\n", presses[i]->node, presses[i]->vertStretch, presses[i]->pressure);
        }
    }
    printf("==========================================================\n");
  }
  error(response);
  return -1;
}

static int isUsbAccessory () {
  // 端末がすでにUSB Accessory Modeかどうかを判定する
  int res;
  libusb_init(NULL);
  if((handle = libusb_open_device_with_vid_pid(NULL, VID,  ACCESSORY_PID)) == NULL) {
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

static int init(){
  if((handle = libusb_open_device_with_vid_pid(NULL, VID, PID)) == NULL){
    fprintf(stdout, "Problem acquireing handle\n");
    return -1;
  }
  libusb_set_configuration(handle, 1);
  sleep(1);
  libusb_claim_interface(handle, 0);
  return 0;
}

static int deInit(){
  if(handle != NULL)
    libusb_release_interface (handle, 0);
  libusb_exit(NULL);

    for (int i = 0; i < MAX_FINGER_PRESS_AMOUNT; i++) {
        free(presses[i]);
    }

  return 0;
}

static int setupAccessory(
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
    if((handle = libusb_open_device_with_vid_pid(NULL, VID, ACCESSORY_PID)) == NULL){
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