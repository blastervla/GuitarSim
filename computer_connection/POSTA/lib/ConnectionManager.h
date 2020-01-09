/**
 * Los parámetros van a ser usados para buffer de datos y size del buffer.
 */
typedef void (*ConnectionListener)(unsigned char*&, int);

class ConnectionManager {
    public:
        virtual int start(ConnectionListener listener);
        virtual int listenNow();
        virtual bool send(byte[] data);
}