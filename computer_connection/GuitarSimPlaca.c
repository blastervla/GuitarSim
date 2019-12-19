#include "ADK.c"
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <assert.h>
#include <string.h> 

// ===== UTILS ======
void clearScreen()
{
    system("@cls||clear");
}
// ==================

// ADK1 usb accessory strings
#define ACCESSORY_STRING_VENDOR "Google, Inc."
#define ACCESSORY_STRING_NAME   "DemoKit"
#define ACCESSORY_STRING_LONGNAME "DemoKit Arduino Board"
#define ACCESSORY_STRING_VERSION  "2.0"
#define ACCESSORY_STRING_URL    "http://www.android.com"
#define ACCESSORY_STRING_SERIAL "0000000012345678"

#define MAX_FINGER_PRESS_AMOUNT 20
uint16_t NODE_AMOUNT = 400;

static int deInit(void);

// void adkPutchar(char c){ 
    // Serial.write(c); 
// }

typedef struct SendBuf {
    uint8_t buf[128];
    int pos;
} SendBuf;

void SendBuf_Reset(SendBuf *buf) { buf->pos = 0; memset(buf->buf, 0, sizeof(buf->buf)); }
void SendBuf_AppendInt(SendBuf *buf, int val) { buf->buf[buf->pos++] = val; }
void SendBuf_AppendUInt8(SendBuf *buf, uint8_t val) { buf->buf[buf->pos++] = val; }
void SendBuf_AppendUInt16(SendBuf *buf, uint16_t val) { buf->buf[buf->pos++] = val >> 8; buf->buf[buf->pos++] = val; }
void SendBuf_AppendUInt32(SendBuf *buf, uint32_t val) { buf->buf[buf->pos++] = val >> 24; buf->buf[buf->pos++] = val >> 16; buf->buf[buf->pos++] = val >> 8; buf->buf[buf->pos++] = val; }

int SendBuf_Send(SendBuf *buf) { return ADK_accessorySend(buf->buf, buf->pos); }

typedef struct FingerPress {
    bool valid;
    bool isCejilla;
    int id;
    uint16_t node;
    int vertStretch;
    uint32_t pressure;
} FingerPress;

FingerPress* presses[MAX_FINGER_PRESS_AMOUNT]; // Andá a llegar a 10 taps, te reto...

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

int main(void) {

    // L.adkSetPutchar(adkPutchar);
    if (ADK_adkInit() < 0) {
        printf("No se pudo abrir la conexión usb :(");
        return -1;
    } 
    
    if (ADK_setupAccessory(
		ACCESSORY_STRING_VENDOR,
		ACCESSORY_STRING_NAME,
		ACCESSORY_STRING_LONGNAME,
		ACCESSORY_STRING_VERSION,
		ACCESSORY_STRING_URL,
		ACCESSORY_STRING_SERIAL) < 0) {
            printf("No se pudo establecer conexión con dispositivo");
            deInit();
            return -1;
        }

    bool noResponse = true;

    for (int i = 0; i < MAX_FINGER_PRESS_AMOUNT; i++) {
        presses[i] = malloc(sizeof(FingerPress));
    }

    while (true) {
        SendBuf *buf = malloc(sizeof(SendBuf));
        SendBuf_Reset(buf);

        // node_amount
        SendBuf_AppendUInt8(buf, 0x01);
        SendBuf_AppendUInt16(buf, NODE_AMOUNT);

        SendBuf_Send(buf);

        // read from phone
        {
            uint8_t buf[64];

            int res = ADK_accessoryReceive(buf, sizeof(buf));
        
            noResponse = res == 0;
        }
    }

    while (true) {
        // read from phone
        uint8_t buf[64];

        int res = ADK_accessoryReceive(buf, sizeof(buf));
    
        int pos = 0;
        while (pos < res) {
            uint8_t op = buf[pos++];
        
            switch (op) {
                case 0x2: { // Finger press    
                    FingerPress *press = malloc(sizeof(FingerPress));
                    press->id = buf[pos];
                    press->node = buf[pos+4];
                    press->vertStretch = buf[pos+6];
                    press->pressure = buf[pos+10];
                    press->isCejilla = buf[pos+11];

                    agregarOActualizarPress(press);

                    pos += 14; // 15 byte packets
                    break;
                }
                case 0x3: { // Finger release
                    uint32_t id = buf[pos];

                    quitarPress(id);

                    pos += 3; // 4 byte packet
                    break;
                }
                default: // assume 4 byte packet
                printf("No entendí :(");
                pos += 3;
                break;
            }

            // Logging
            clearScreen();
            printf("Se están apretando los nodos: ==============\n");
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
        }
    }

    return 0;
}

int deInit(){
	//TODO free all transfers individually...
	//if(ctrlTransfer != NULL)
	//	libusb_free_transfer(ctrlTransfer);
	if(handle != NULL)
		libusb_release_interface (handle, 0);
	libusb_exit(NULL);
	return 0;
}