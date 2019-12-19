#include <libusb-1.0/libusb.h>

#define IN 0x85
#define OUT 0x07

#define VID 0x18D1
#define PID 0x4EE7

#define ACCESSORY_PID 0x2D01
#define ACCESSORY_PID_ALT 0x2D00

#define LEN 2

static void error(int code);
static void status(int code);

static struct libusb_device_handle* handle;
static char stop;
static char success = 0;

int ADK_adkInit() {
    libusb_init(NULL);
	if((handle = libusb_open_device_with_vid_pid(NULL, VID, PID)) == NULL){
		printf("Problem acquiring handle\n");
		return -1;
	}
	libusb_claim_interface(handle, 0);
	return 0;
}

int ADK_accessoryReceive(unsigned char *buffer, unsigned int len) {
    static int transferred;
    int response = libusb_bulk_transfer(handle,IN,buffer,len, &transferred,0);
	if (response < 0) {
        error(response);
    }
    return response;
}

int ADK_accessorySend(const unsigned char *buffer, unsigned int len) {
    static int transferred;
    int response = libusb_bulk_transfer(handle,OUT,buffer,len, &transferred,0);
	if (response < 0) {
        error(response);
    }
    return response;
}


int ADK_setupAccessory(
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
	printf("Verion Code Device: %d\n", devVersion);
	
	usleep(1000);//sometimes hangs on the next transfer :(

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

	printf("Accessory Identification sent\n", devVersion);

	response = libusb_control_transfer(handle,0x40,53,0,0,NULL,0,0);
	if(response < 0){error(response);return -1;}

	printf("Attempted to put device into accessory mode\n", devVersion);

	if(handle != NULL) {
        printf("Release\n", devVersion);
        libusb_release_interface (handle, 0);
    }

	for(;;){//attempt to connect to new PID, if that doesn't work try ACCESSORY_PID_ALT
		tries--;
        printf("Won't open\n", devVersion);
		if((handle = libusb_open_device_with_vid_pid(NULL, VID, ACCESSORY_PID)) == NULL){
			if(tries < 0){
				return -1;
			}
		}else{
			break;
		}
		sleep(1);
	}
    printf("Claiming\n", devVersion);
	libusb_claim_interface(handle, 0);
	printf("Interface claimed, ready to transfer data\n");
	return 0;
}

void error(int code){
	printf("\n");
	switch(code){
	case LIBUSB_ERROR_IO:
		printf("Error: LIBUSB_ERROR_IO\nInput/output error.\n");
		break;
	case LIBUSB_ERROR_INVALID_PARAM:
		printf("Error: LIBUSB_ERROR_INVALID_PARAM\nInvalid parameter.\n");
		break;
	case LIBUSB_ERROR_ACCESS:
		printf("Error: LIBUSB_ERROR_ACCESS\nAccess denied (insufficient permissions).\n");
		break;
	case LIBUSB_ERROR_NO_DEVICE:
		printf("Error: LIBUSB_ERROR_NO_DEVICE\nNo such device (it may have been disconnected).\n");
		break;
	case LIBUSB_ERROR_NOT_FOUND:
		printf("Error: LIBUSB_ERROR_NOT_FOUND\nEntity not found.\n");
		break;
	case LIBUSB_ERROR_BUSY:
		printf("Error: LIBUSB_ERROR_BUSY\nResource busy.\n");
		break;
	case LIBUSB_ERROR_TIMEOUT:
		printf("Error: LIBUSB_ERROR_TIMEOUT\nOperation timed out.\n");
		break;
	case LIBUSB_ERROR_OVERFLOW:
		printf("Error: LIBUSB_ERROR_OVERFLOW\nOverflow.\n");
		break;
	case LIBUSB_ERROR_PIPE:
		printf("Error: LIBUSB_ERROR_PIPE\nPipe error.\n");
		break;
	case LIBUSB_ERROR_INTERRUPTED:
		printf("Error:LIBUSB_ERROR_INTERRUPTED\nSystem call interrupted (perhaps due to signal).\n");
		break;
	case LIBUSB_ERROR_NO_MEM:
		printf("Error: LIBUSB_ERROR_NO_MEM\nInsufficient memory.\n");
		break;
	case LIBUSB_ERROR_NOT_SUPPORTED:
		printf("Error: LIBUSB_ERROR_NOT_SUPPORTED\nOperation not supported or unimplemented on this platform.\n");
		break;
	case LIBUSB_ERROR_OTHER:
		printf("Error: LIBUSB_ERROR_OTHER\nOther error.\n");
		break;
	default:
		printf("Error: unkown error\n");
	}
}

void status(int code){
	printf("\n");
	switch(code){
		case LIBUSB_TRANSFER_COMPLETED:
			printf("Success: LIBUSB_TRANSFER_COMPLETED\nTransfer completed.\n");
			break;
		case LIBUSB_TRANSFER_ERROR:
			printf("Error: LIBUSB_TRANSFER_ERROR\nTransfer failed.\n");
			break;
		case LIBUSB_TRANSFER_TIMED_OUT:
			printf("Error: LIBUSB_TRANSFER_TIMED_OUT\nTransfer timed out.\n");
			break;
		case LIBUSB_TRANSFER_CANCELLED:
			printf("Error: LIBUSB_TRANSFER_CANCELLED\nTransfer was cancelled.\n");
			break;
		case LIBUSB_TRANSFER_STALL:
			printf("Error: LIBUSB_TRANSFER_STALL\nFor bulk/interrupt endpoints: halt condition detected (endpoint stalled).\nFor control endpoints: control request not supported.\n");
			break;
		case LIBUSB_TRANSFER_NO_DEVICE:
			printf("Error: LIBUSB_TRANSFER_NO_DEVICE\nDevice was disconnected.\n");
			break;
		case LIBUSB_TRANSFER_OVERFLOW:
			printf("Error: LIBUSB_TRANSFER_OVERFLOW\nDevice sent more data than requested.\n");
			break;
		default:
			printf("Error: unknown error\nTry again(?)\n");
			break;
	}
}