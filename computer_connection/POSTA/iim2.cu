// rtsine.cpp STK tutorial program
texture<float, 1, cudaReadModeElementType> d_potencial_tex;

#include <signal.h>
#include <assert.h>
#include "RtMidi.h"
#include "RtWvOut.h"
#include "RtWvIn.h"

#include "lib/avancem2.cu"
#include "lib/AOAUsbConnectionManager.h"

using namespace stk;

#define USB_IN 0x81    // In Port
#define USB_OUT 0x07   // Out Port

#define USB_VID 0x18D1 // Google Inc.
#define USB_PID 0x4EE7 // Pixel 3 debug

#define USB_ACCESSORY_PID 0x2D01
#define USB_ACCESSORY_PID_ALT 0x2D00

/* Comentar y descomentar para elegir lo que se usa, MIDI o USB */
// #define USE_MIDI
#define USE_USB

bool debugMode;
bool imprimir;

void initPresses();
void deInitPresses();

template <class T>
void init(T *data, int size, T def) {
  for (int i = 0; i < size; ++i) data[i] = def;
}

void ceroInit(float *data, int size) { init<float>(data, size, 0.); }

void gaussiana(float *data, int nodos, int cuerdas, float valor, float centro,
               float expo, int *nodoscuer) {
  float cosa = 0;
  float maximo;
  for (int i = 0; i < cuerdas; ++i) {
    int sentro = centro * nodoscuer[i];
    maximo = valor - valor * 0.01 * i;
    for (int j = 0; j < nodos; ++j) {
      cosa = -expo * ((j - sentro) * (j - sentro));
      data[j + i * nodos] = maximo * exp(cosa);
    }
  }
}

void una_gaussiana(float *data, int nodos, int cuerda, float valor,
                   float centro, float expo, int *nodos_cuer) {
  float cosa = 0;
  float maximo = valor;
  int i = cuerda;
  int sentro = centro * nodos_cuer[i];
  for (int j = 0; j < nodos_cuer[i]; j++) {
    cosa = -expo * ((j - sentro) * (j - sentro));
    data[j + i * nodos] = maximo * exp(cosa);
  }
}

void friccion_una_punta(StateManager* var, const float *fricc, const int *dedo, int cuerda_tocada) {
  if (debugMode) printf("Dedo  %d  \n", dedo[cuerda_tocada]);
  int j = cuerda_tocada;
  int cantNodos = var->state()->getcuerdas(cuerda_tocada, "nodos");
  int dedo_aca = dedo[cuerda_tocada];
  float orden = var->state()->getcuerdas(cuerda_tocada, "anchoPuntas");

  for (int i = 0; i < cantNodos; ++i) {
    float factor = 0.5 - fricc[j];
    if (i <= dedo[cuerda_tocada]) {
      factor =
        (var->state()->getcuerdas(j, "maxFriccionEnPunta") - fricc[cuerda_tocada]) * (exp(-orden * ((i - dedo_aca) * (i - dedo_aca))) +
                            exp(-orden * (i - cantNodos) * (i - cantNodos)));
    }

    var->state()->setfriccionSinDedo(i + j * var->state()->getcantMaximaNodos(), factor + fricc[j]);

    if (fricc[i + j * var->state()->getcantMaximaNodos()] > 1.)
      printf("Friccion mayor a uno %e  \n",  var->state()->getfriccionSinDedo(i + j * var->state()->getcantMaximaNodos()));
  }
}

void imprimir_parametros(int ncuerdas, int nodos_max, vector<float> const& masaPorNodo, vector<float> const& fricc,
                         float *fuerza, int *mic, int *nodoscuer) {
	if (imprimir) {
		FILE *parametrosout;
		parametrosout = fopen("salida/parmout.dat", "w");

		for (int i = 0; i < ncuerdas; i++) {
		    for (int j = 0; j < nodos_max; j++) {
		      int nodo = j + i * nodos_max;
		      fprintf(parametrosout, "%e %e %e \n", masaPorNodo[nodo], fuerza[nodo],
		              fricc[nodo]);
		    }
		 }
		 fclose(parametrosout);
	}
}

template <class T>
T* cudaMallocAndCopy(const vector<T> &vect) {
  T* cuda_pointer;
  cudaMalloc((void **)&cuda_pointer, vect.size() * sizeof(T));
  cudaMemcpy(cuda_pointer, vect.data(), vect.size() * sizeof(T),
             cudaMemcpyHostToDevice);
  return cuda_pointer;
}

template <class T>
T* cudaFreeMallocAndCopy(const vector<T> &vect, T* cuda_pointer) {
  cudaFree(cuda_pointer);
  return cudaMallocAndCopy<T>(vect);
}

void iim2(StateManager *var) {
  debugMode = var->state()->getdebugMode();
  imprimir = var->state()->getimprimir();
  // Set the global sample rate before creating class instances.
  // Attempt to instantiate MIDI output class.

  /*************************
   *  Inicializo variables *
   *************************/

  float volumen = 1.0 / 1700.0;
  int cuerda_tocada = 0;
  bool de_a_una = 0;
  float palanca = 1;
  bool retoque = 0;
  bool pedal = true;
  float intensidad = 1e-4;
  float expo = 2.6;

  unsigned int size_A = var->state()->getcantMaximaNodos() * var->state()->getcantCuerdas();
  unsigned int mem_size_A = sizeof(float) * size_A;

  unsigned int buffer_salida =
      sizeof(float) * var->state()->getnbufferii() * var->state()->getcantCuerdas() * 2;
  unsigned int buffer_entrada =
      sizeof(float) * var->state()->getnbufferii() * var->state()->getcanalesEntrada() * 3;

  unsigned int bcur = sizeof(bool) * var->state()->getcantCuerdas();

  // Imprimo informacion en archivo (en caso de debug activo)
  FILE *xyz;
  FILE *out;
  if (imprimir) {
    xyz = fopen("salida/coord.xyz", "w");
    out = fopen("salida/salida.ascii", "w");

    fprintf(out, " ## 'rate'= %d\n", var->state()->getsamplerate());
    fprintf(out, " ## 'tracks'= 2\n");
    fprintf(out, "## 'bits'= 24\n");
    fprintf(out, "## 'length'= %d\n", var->state()->getnbufferii() * var->state()->getnFrames());
    fprintf(out, "## 'Date'='2013-11-09'\n");
    fprintf(out, "## 'Software'='nanosampler'\n");
  }

  // Reservo memoria local necesaria
  float *X = (float *)malloc(mem_size_A);
  float *V = (float *)malloc(mem_size_A);
  float *Fext = (float *)malloc(mem_size_A);
  float *salida = (float *)malloc(buffer_salida);
  float *entrada = (float *)malloc(buffer_entrada);
  bool *tococ = (bool *)malloc(bcur);
  bool *activa = (bool *)malloc(bcur);
  bool *freno = (bool *)malloc(bcur);
  int *caca = (int *)malloc(var->state()->getcantCuerdas() * sizeof(int));
  int *dedo = (int *)malloc(var->state()->getcantCuerdas() * sizeof(int));
  int *dedov = (int *)malloc(var->state()->getcantCuerdas() * sizeof(int));
  int *mic = (int *)malloc(2 * var->state()->getcantCuerdas() * sizeof(int));
  int *nodoscuer = (int *)malloc(var->state()->getcantCuerdas() * sizeof(int));
  int *escribe = (int *)malloc(var->state()->getcantCuerdas() * sizeof(int));
  int *fuerzavieja = (int *)malloc(var->state()->getcantCuerdas() * sizeof(int));
  float *fricc = (float *)malloc(var->state()->getcantCuerdas() * sizeof(float));
  float *paneo = (float *)malloc(var->state()->getcantCuerdas() * sizeof(float));
  float *volcuer = (float *)malloc(var->state()->getcantCuerdas() * sizeof(float));
  float *pertu = (float *)malloc(512 * sizeof(float));
  float *pertup = (float *)malloc(512 * sizeof(float));
  float *pertud = (float *)malloc(512 * sizeof(float));
  float *potencial = (float *)malloc(var->state()->getnpot() * sizeof(float));
  float *d_X;
  float *d_V;
  float *d_Fr;
  float *d_Frd;
  float *d_M;
  float *d_Fext;
  float *d_xmin;
  float *d_salida;
  float *d_entrada;
  bool *d_tococ;
  bool *d_activa;
  bool *d_freno;
  int *d_dedo;
  int *d_dedov;
  int *d_mic;
  int *d_nodoscuer;
  int *d_escribe;
  float *d_pertu;
  float *d_pertup;
  float *d_pertud;
  float *d_potencial;

  // 8. allocate device memory
  cudaMalloc((void **)&d_X, mem_size_A);
  cudaMalloc((void **)&d_V, mem_size_A);
  cudaMalloc((void **)&d_Fext, mem_size_A);
  cudaMalloc((void **)&d_salida, buffer_salida);
  cudaMalloc((void **)&d_entrada, buffer_entrada);
  cudaMalloc((void **)&d_tococ, bcur);
  cudaMalloc((void **)&d_activa, bcur);
  cudaMalloc((void **)&d_freno, bcur);
  cudaMalloc((void **)&d_dedo, var->state()->getcantCuerdas() * sizeof(int));
  cudaMalloc((void **)&d_dedov, var->state()->getcantCuerdas() * sizeof(int));
  cudaMalloc((void **)&d_mic, 2 * var->state()->getcantCuerdas() * sizeof(int));
  cudaMalloc((void **)&d_nodoscuer, var->state()->getcantCuerdas() * sizeof(int));
  cudaMalloc((void **)&d_escribe, var->state()->getcantCuerdas() * sizeof(int));
  cudaMalloc((void **)&d_pertud, 512 * sizeof(float));
  cudaMalloc((void **)&d_pertup, 512 * sizeof(float));
  cudaMalloc((void **)&d_potencial, var->state()->getnpot() * sizeof(float));

  cudaArray *tex;
  cudaChannelFormatDesc channel = cudaCreateChannelDesc<float>();
  cudaMallocArray(&tex, &channel, var->state()->getnpot(), 1, cudaArrayDefault);

  // 2. initialize host memory
  ceroInit(X, size_A);
  ceroInit(V, size_A);
  ceroInit(salida, var->state()->getnbufferii() * var->state()->getcantCuerdas() * 2);

  int nodosreales = 0;
  for (int ii = 0; ii < var->state()->getcantCuerdas(); ++ii) {
    caca[ii] = 0;
    fuerzavieja[ii] = 0;
    dedo[ii] = var->state()->getcantMaximaNodos();
    dedov[ii] = 0;
    fricc[ii] = var->state()->getcuerdas(ii, "friccion");
    nodoscuer[ii] = var->state()->getcuerdas(ii, "nodos");
    nodosreales += nodoscuer[ii];
    activa[ii] = 0;
    mic[2 * ii] = int((float)nodoscuer[ii] * 0.1);
    mic[2 * ii + 1] = int((float)nodoscuer[ii] * 0.3);
    paneo[ii] = 0.5;
    volcuer[ii] = 3.0f;
  }

  for (int ii = 0; ii < var->state()->getnpot(); ++ii) {
    float iii = (float)ii - ((float)var->state()->getnpot()) / 2.0f;

    float deltax = iii / 10.0f;
    float dx2 = deltax * deltax;
    float d2tot = dx2 + var->state()->getdistanciaEntreNodos();
    float d12 = sqrt(d2tot);
    potencial[ii] =
        (d12 - var->state()->getdistanciaEquilibrioResorte()) / (d12)*deltax -
        deltax;  // << Esta es la cuenta rememorando algo 2D con delta Y siempre
                 // igual
  }

  cudaMemcpyToArray(tex, 0, 0, potencial, 2048 * sizeof(float),
                    cudaMemcpyHostToDevice);
  cudaBindTextureToArray(d_potencial_tex, tex, channel);

  d_potencial_tex.normalized = false;
  d_potencial_tex.filterMode = cudaFilterModeLinear;
  d_potencial_tex.addressMode[0] = cudaAddressModeBorder;

  for (int ii = 0; ii < var->state()->getnpot() && debugMode && imprimir; ++ii) {
    printf(" NN %e   \n", potencial[ii]);
  }

  gaussiana(Fext, var->state()->getcantMaximaNodos(), var->state()->getcantCuerdas(), 2.E-2,
            var->state()->getcentro(), 0.2, nodoscuer);

  d_M = cudaMallocAndCopy<float>(var->state()->getmasaPorNodo());
  d_Fr = cudaMallocAndCopy<float>(var->state()->getfriccionSinDedo());
  d_Frd = cudaMallocAndCopy<float>(var->state()->getfriccionConDedo());
  d_xmin = cudaMallocAndCopy<float>(var->state()->getminimosYtrastes());

  if (debugMode && imprimir) {
    for (int nn = 0; nn < var->state()->getcantCuerdas() && debugMode; nn++) {
      for (int nj = 0; nj < var->state()->getcantMaximaNodos(); nj++) {
        printf("El nodo %d de la cuerda %d tiene minimo %e \n", nj, nn,
        		var->state()->getminimosYtrastes(nj + var->state()->getcantMaximaNodos() * nn));
      }
    }
  }

  cudaMemcpy(d_X, X, mem_size_A, cudaMemcpyHostToDevice);
  cudaMemcpy(d_V, V, mem_size_A, cudaMemcpyHostToDevice);
  cudaMemcpy(d_salida, salida, buffer_salida, cudaMemcpyHostToDevice);

  cudaMemcpy(d_Fext, Fext, mem_size_A, cudaMemcpyHostToDevice);
  dim3 threads(var->state()->getcantMaximaNodos(), 1, 1);
  dim3 grid(var->state()->getcantCuerdas(), 1, 1);
  dim3 threads_caja(1024);
  dim3 grid_caja(var->state()->getnbufferii());

  // levanto el input de perturvación

  FILE *pertp;
  FILE *pertd;
  const char *mode = "r";

  pertd = fopen("entrada/dedo.dat", mode);
  pertp = fopen("entrada/pua.dat", mode);

  if (pertd == NULL || pertp == NULL) {
    fprintf(stderr, "Can't open input file in.list!\n");
    exit(1);
  }

  for (int i = 0; i < 512; i++) {
    pertud[i] = 0.;
    pertup[i] = 0.;
  }

  int nn = 0;
  while (fscanf(pertp, "%d %E", &nn, &pertup[nn]) != EOF);
  while (fscanf(pertd, "%d %E", &nn, &pertud[nn]) != EOF);

  cudaMemcpy(d_pertud, pertud, 512 * sizeof(float), cudaMemcpyHostToDevice);
  cudaMemcpy(d_pertup, pertup, 512 * sizeof(float), cudaMemcpyHostToDevice);
  d_pertu = d_pertud;
  float random;

  cudaMemcpy(d_dedo, dedo, var->state()->getcantCuerdas() * sizeof(int),
             cudaMemcpyHostToDevice);
  cudaMemcpy(d_dedov, dedov, var->state()->getcantCuerdas() * sizeof(int),
             cudaMemcpyHostToDevice);
  cudaMemcpy(d_mic, mic, 2 * var->state()->getcantCuerdas() * sizeof(int),
             cudaMemcpyHostToDevice);
  cudaMemcpy(d_nodoscuer, nodoscuer, var->state()->getcantCuerdas() * sizeof(int),
             cudaMemcpyHostToDevice);
  cudaMemcpy(d_activa, activa, bcur, cudaMemcpyHostToDevice);

  init<bool>(freno, var->state()->getcantCuerdas(), false);

  imprimir_parametros(var->state()->getcantCuerdas(), var->state()->getcantMaximaNodos(),
                      var->state()->getmasaPorNodo(), var->state()->getfriccionSinDedo(), Fext,
                      mic, nodoscuer);

  init<bool>(tococ, var->state()->getcantCuerdas(), false);

  for (int cuerda_t = 0; cuerda_t < var->state()->getcantCuerdas();
       cuerda_t++) {
    intensidad = var->state()->getescalaIntensidad() * 600;
    una_gaussiana(Fext, var->state()->getcantMaximaNodos(), cuerda_t, intensidad,
                  var->state()->getcentro(), expo, nodoscuer);
  }

  cudaMemcpy(d_Fext, Fext, mem_size_A, cudaMemcpyHostToDevice);

  float *valor_anterior = (float *)malloc(6 * sizeof(float));

  #ifdef USE_MIDI
  /********************
   *  Inicializo MIDI *
   ********************/

  // Set the global sample rate before creating class instances.
  // Attempt to instantiate MIDI output class.
  RtMidiIn *midiin;
  try {
    midiin = new RtMidiIn();
  } catch (RtMidiError &error) {
    error.printMessage();
    exit(0);
  }

  midiin->openVirtualPort();
  vector<unsigned char> message(5);

  double stamp = midiin->getMessage(&message);
  int nbytes = message.size();

  Stk::setSampleRate(var->state()->getsamplerate());
  Stk::showWarnings(true);

  //  SineWave sine;
  RtWvOut *dac;
  try {
    // Define and open the default realtime output device for one-channel
    // playback
    dac = new RtWvOut(2);
  } catch (StkError &) {
    exit(1);
  }

  #endif

  #ifdef USE_USB
  /*********************************
  //        Configurar USB         *
  //********************************/
  AOAUsbConnectionManager connectionManager;
  connectionManager.setConnectionDetails(
    "Blastervla",
    "Guitar Simulator",
    "Guitar Simulator",
    "1.0",
    "http://d.hatena.ne.jp/thorikawa/",
    "0000000012345678",
    USB_IN, 
    USB_OUT, 
    USB_VID, 
    USB_PID, 
    USB_ACCESSORY_PID, 
    USB_ACCESSORY_PID_ALT
  );
  connectionManager.start(usbConnectionListener);
  #endif

  /*********************************
  // ACA EMPIEZA EL LOOP PRINCIPAL *
  //********************************/

  for (int i = 10; i < var->state()->getnFrames(); i++) {

    for (int ii = 0; ii < var->state()->getcantCuerdas(); ++ii) {
      tococ[ii] = 0;
    }

    #ifdef USE_MIDI
    nbytes = 1;
    while (nbytes > 0) {
      stamp = midiin->getMessage(&message);
      nbytes = message.size();

      if (nbytes > 0) {
        int tipoycanal = message[0];  // whatever;
        int tipo;                     //
        tipo =
            tipoycanal / 16;  // (message[0] & (1 << 2 | 1 << 3 | 1 << 4)) >> 2;
        int canal =
            tipoycanal %
            16;  //(message[0] & (1 << 5 | 1 << 6 | 1 << 7 | 1 << 8)) >> 2;

        if (debugMode) {
          printf(" Recibo el mensaje midi \n");
          printf(" # nbytes: %i \n", nbytes);
          printf(" # tipo: %i \n",tipo);
          printf(" # canal: %i \n", canal);
          printf(" # message 0 (tipoycanal): %i \n", tipoycanal);
          printf(" # message 1: %i \n", message[1]);
          printf(" # message 2: %i \n", message[2]);
          printf(" # message 3: %i \n", message[3]);
        }

        if (message[1] == 84 && tipo == 11) {
          retoque = true;
          printf("estoy retocando\n");
        }

        if (tipo == 9 && message[1] == 24 && debugMode) {
          imprimir_parametros(var->state()->getcantCuerdas(), var->state()->getcantMaximaNodos(),
                              var->state()->getmasaPorNodo(), var->state()->getfriccionSinDedo(),
                              Fext, mic, nodoscuer);
        }

        if (tipo == 9) {
          int cur = 5 - canal;     // 149-tipoycanal;
          int toque = message[2];  //-30;

          if (message[2] > 16 ) {
            caca[cur] = 1;
            freno[cur] = 0;
            if (!retoque){
		    tococ[cur] = 1;
                    intensidad = var->state()->getescalaIntensidad() * toque * toque * toque /
                         (60.0f + 120.0 * cur);
                    if (debugMode)
                   printf(
                     "Toco la cuerda, %i con la fuerza %i en %e , retoque = %i \n",
                         cur, message[2], var->state()->getcentro(), retoque);
                          una_gaussiana(Fext, var->state()->getcantMaximaNodos(), cur, intensidad,
                          var->state()->getcentro(), expo, nodoscuer);
                    cudaMemcpy(d_Fext, Fext, mem_size_A, cudaMemcpyHostToDevice);
                    fuerzavieja[cur] = message[2];
	    }
          } else if (message[2] == 0 || message[2] == 16) {
            tococ[cur] = 0;
            if (message[2] == 0 && !retoque) freno[cur] = 1;
             retoque = 0;
            if (debugMode) printf("Freno la cuerda %i \n", cur);
          }

          if (message[2] > 0 && cur >= 0 && cur <= 5) {
            double fdedo = 1 / 1.059463094;

            int value = 40;
            switch (cur) {
              case 0:
                value = 40;
                break;
              case 1:
                value = 45;
                break;
              case 2:
                value = 50;
                break;
              case 3:
                value = 55;
                break;
              case 4:
                value = 59;
                break;
              case 5:
                value = 64;
                break;
            }
            int fret = message[1] - value;
            dedo[cur] = (int)((float)nodoscuer[cur]) * pow(fdedo, fret);
          }
        }

        if (tipo == 14) {
          palanca = 1.0f + 0.002f * (message[2] - 64);
          if (debugMode) printf("Usa palanca \n ");
        }
        if (tipo == 11) {
          if (debugMode) printf("Canal controladores \n");

          if (message[1] == 74 || message[1] == 1) {
            var->state()->setmaxp(0.007 * message[2]);

            if (debugMode)
              printf(
                  " El maximo de la gaussiana de fricciones %e en la cuerda "
                  "%i\n",
                  var->state()->getmaxp(), cuerda_tocada);

            if (de_a_una) {
            	friccion_una_punta(var, fricc, dedo, cuerda_tocada);
            }
          }

          if (message[1] == 71 || message[1] == 2) {
            var->state()->setexpp(0.8f - 0.0060 * message[2]);

            if (debugMode)
              printf(" El exponente de la gaussiana de fricciones %e \n",
                     var->state()->getexpp());

            if (de_a_una) {
              friccion_una_punta(var, fricc, dedo, cuerda_tocada);
            }
          }

          if (message[1] == 91 || message[1] == 4) {
            for (int jk = 0; jk < var->state()->getcantCuerdas(); ++jk) {
              fricc[jk] = var->state()->getcuerdas(jk, "friccion") * message[2] *
                          0.03;  // 0.00000001;
            }

            if (debugMode) printf(" La friccion base es %e \n", fricc[0]);

            if (de_a_una) {
              friccion_una_punta(var, fricc, dedo, cuerda_tocada);
            }
          }

          if (message[1] == 93 || message[1] == 5) {
            expo = 0.00005 * (message[2] * message[2]) + 0.0005;
            if (debugMode)
              printf("El exponente para la fuerza es  %e \n", expo);

            gaussiana(Fext, var->state()->getcantMaximaNodos(), var->state()->getcantCuerdas(),
                      intensidad, var->state()->getcentro(), expo, nodoscuer);
            cudaMemcpy(d_Fext, Fext, mem_size_A, cudaMemcpyHostToDevice);
          }

          if (message[1] == 73 || message[1] == 8) {
            var->state()->setcentro(0.5 - ((float)message[2]) / 270.0);
            if (debugMode)
              printf("La fuerza se aplica en   %e \n", var->state()->getcentro());
            gaussiana(Fext, var->state()->getcantMaximaNodos(), var->state()->getcantCuerdas(),
                      intensidad, var->state()->getcentro(), expo, nodoscuer);
            cudaMemcpy(d_Fext, Fext, mem_size_A, cudaMemcpyHostToDevice);
          }

          if (message[1] == 72 || message[1] == 10) {
            var->state()->setescalaIntensidad(0.005 * message[2]);
            if (debugMode)
              printf("intbase es ahora   %e \n", var->state()->getescalaIntensidad());
            intensidad = var->state()->getescalaIntensidad() * 2000;
            gaussiana(Fext, var->state()->getcantMaximaNodos(), var->state()->getcantCuerdas(),
                      intensidad, var->state()->getcentro(), expo, nodoscuer);
            cudaMemcpy(d_Fext, Fext, mem_size_A, cudaMemcpyHostToDevice);
          }

          if (message[1] == 7) {
            volumen =
                (float)message[2] /
                ((float)30000.0);  // intensidad*(10-0.1)/(3.e-4 - 1.e-6) +
            if (debugMode) printf("El volumen es %e \n", volumen);
          }
        }

        if (canal == 48) {
          if (message[1] == 8) {
            cerr << " ************ Panico! reseteando todo ************" << endl;

            ceroInit(X, size_A);
            ceroInit(V, size_A);

            cudaMemcpy(d_X, X, mem_size_A, cudaMemcpyHostToDevice);
            cudaMemcpy(d_V, V, mem_size_A, cudaMemcpyHostToDevice);
          }

          if (message[1] == 7) {
          	imprimir = !imprimir;
            var->state()->setimprimir(imprimir);
            printf("¿estoy imprimiendo?  %s",
                   imprimir ? "Si!!!" : "NO!!!");
          }

          if (message[1] == 0) {
            d_pertu = d_pertup;
            printf("Ahora toco con pua \n");
          }

          if (message[1] == 1) {
            d_pertu = d_pertud;
            printf("Ahora toco con dedo \n");
          }

          if (message[1] == 3) {
            de_a_una = 0;
          }

          if (message[1] == 4 && debugMode) {
            imprimir_parametros(
                var->state()->getcantCuerdas(), var->state()->getcantMaximaNodos(),
                var->state()->getmasaPorNodo(), var->state()->getfriccionSinDedo(), Fext, mic,
                nodoscuer);
          }
        }

        if (message[1] == 64 && canal == 40) {
          pedal = message[2] == 127 ? 0 : (message[2] == 0 ? 1 : pedal);
          if (debugMode) cout << "Pedal " << (pedal ? "ON" : "OFF") << endl;
        }
      }
    }
    #endif

    #ifdef USE_USB
    initPresses();

    int response = 0;
    do {
        response = connectionManager.listenNow();
    } while (response == 0);

    deInitPresses();
    #endif

    cudaMemcpy(d_tococ, tococ, bcur, cudaMemcpyHostToDevice);
    cudaMemcpy(d_freno, freno, bcur, cudaMemcpyHostToDevice);
    cudaMemcpy(d_dedo, dedo, var->state()->getcantCuerdas() * sizeof(int),
               cudaMemcpyHostToDevice);

    if (var->state()->isChanged) {
      if (var->state()->isChangedmasaPorNodo()) {
          d_M = cudaFreeMallocAndCopy<float>(var->state()->getmasaPorNodo(), d_M);
          var->state()->setChangedmasaPorNodo();
      }

      if (var->state()->isChangedfriccionSinDedo()) {
        d_Fr = cudaFreeMallocAndCopy<float>(var->state()->getfriccionSinDedo(), d_Fr);
        var->state()->setChangedfriccionSinDedo();
      }

      if (var->state()->isChangedfriccionConDedo()) {
        d_Frd = cudaFreeMallocAndCopy<float>(var->state()->getfriccionConDedo(), d_Frd);
        var->state()->setChangedfriccionConDedo();
      }

      // Esperariamos que esto no cambie mucho (soft-realtime)
      if (var->state()->isChangedcuerdas() && (i % var->state()->getsoftrealtimeRefresh() == 0)) {
        for (int c=0; c < var->state()->getcantCuerdas(); ++c) {
            fricc[c] = var->state()->getcuerdas(c, "friccion");
        }
        var->state()->setChangedcuerdas();
      } 
      var->state()->isChanged = false;
    }

    // Imprimo variables de entrada

    avance<<<grid, threads>>>(
        d_X, d_V, d_Fext, d_salida, var->state()->getcantMaximaNodos(), d_M, d_Fr, d_Frd,
        var->state()->getdedoSize(), d_tococ, d_activa, d_freno, random, var->state()->getnbufferii(),
        d_dedo, d_dedov, d_pertu, d_mic, d_nodoscuer, pedal, var->state()->getcentro(),
        d_entrada, d_xmin, palanca);

    for (int kk = 0; kk < var->state()->getcantCuerdas(); kk++) {
      dedov[kk] = dedo[kk];
    }

    cudaMemcpy(salida, d_salida, buffer_salida, cudaMemcpyDeviceToHost);
    if (imprimir && i % 23 == 0) {
      cudaMemcpy(X, d_X, mem_size_A, cudaMemcpyDeviceToHost);
      cudaMemcpy(dedo, d_dedo, var->state()->getcantCuerdas() * sizeof(int),
                 cudaMemcpyDeviceToHost);
      fprintf(xyz, " %d \n", nodosreales + 2 * var->state()->getcantCuerdas());
      fprintf(xyz, "  \n");
      for (int ji = 0; ji < var->state()->getcantCuerdas(); ji++) {
        for (int jj = 0; jj < nodoscuer[ji]; jj++) {
          fprintf(xyz, " %d %E %E %E \n", ji + 6,
                  0.5 * X[jj + (ji)*var->state()->getcantMaximaNodos()], 1.2 * jj,
                  20.0 * ji);
        }
        fprintf(xyz, " %d %E %E %E \n", 26, 0.0, 1.2 * dedo[ji], 20.0 * ji);
        if (caca[ji] == 1) {
          caca[ji] = 0;
          fprintf(xyz, " %d %E %E %E \n", 1, 0.,
                  1.2 * var->state()->getcentro() * nodoscuer[ji], 20.0 * ji);

        } else {
          fprintf(xyz, " %d %E %E %E \n", 1, 50.,
                  1.2 * var->state()->getcentro() * nodoscuer[ji], 20.0 * ji);
        }
      }
    }
    StkFloat sal;
    StkFloat sal2;
    StkFrames frames(var->state()->getnbufferii(), 2);
    StkFloat *samples = &frames[0];

    unsigned int hop = frames.channels();

    for (uint jk = 0; jk < var->state()->getnbufferii(); jk++) {
      sal = 0;
      sal2 = 0;
      for (uint iii = 0; iii < var->state()->getcantCuerdas(); iii++) {
        sal2 += salida[jk + (2 * iii) * var->state()->getnbufferii()] * paneo[iii] *
                volcuer[iii];
        sal += salida[jk + (2 * iii + 1) * var->state()->getnbufferii()] *
               (1.0f - paneo[iii]) * volcuer[iii];
      }

      if (imprimir) {
        int sali = 20000 * sal2;
        int sali2 = 20000 * sal;
        fprintf(out, "  %d , %d \n", sali, sali2);
      }

      if (sal2 != sal2 || sal != sal) {
        ceroInit(X, size_A);
        ceroInit(V, size_A);

        cudaMemcpy(d_X, X, mem_size_A, cudaMemcpyHostToDevice);
        cudaMemcpy(d_V, V, mem_size_A, cudaMemcpyHostToDevice);

        sal2 = 0.;
        sal = 0.;
      }

      *samples = sal2 * volumen;
      samples += 1;
      *samples = sal * volumen;
      samples += 1;
    }

    try {
      dac->tick(frames);
    } catch (StkError &) {
      goto cleanup;
    }
  }
  cudaUnbindTexture(d_potencial_tex);
cleanup:

  return;
}

// ==================== Handling con USB ====================

typedef struct FingerPress {
    bool valid;
    bool isCejilla;
    int id;
    int node;
    int chord;
    int vertStretch;
    int pressure;
} FingerPress;

FingerPress* presses[MAX_FINGER_PRESS_AMOUNT]; // Andá a llegar a 10 taps, te reto...

void initPresses() {
    for (int i = 0; i < MAX_FINGER_PRESS_AMOUNT; i++) {
        presses[i] = malloc(sizeof(FingerPress));
    }
}

void deInitPresses() {
    for (int i = 0; i < MAX_FINGER_PRESS_AMOUNT; i++) {
        free(presses[i]);
    }
}

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
    presses[positionForPress]->chord = press->chord;
    presses[positionForPress]->vertStretch = press->vertStretch;
    presses[positionForPress]->pressure = press->pressure;

    free(press);
}

void clearPresses() {
    for (uint8_t i = 0; i < MAX_FINGER_PRESS_AMOUNT; i++) {
        presses[i]->valid = false;
    }
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

// ===== UTILS ======
void clearScreen()
{
    system("clear");
}
// ==================

void logPressess() {
    if (!debugMode) {
        clearScreen();
    }

    printf("Se están apretando los nodos: ============================\n");
    for (uint8_t i = 0; i < MAX_FINGER_PRESS_AMOUNT; i++) {
        if (presses[i]->valid) { // Lo elimino
            if (presses[i]->isCejilla) {
                printf("[ C ]");
            } else {
                printf("[ ");
                switch (presses[i]->chord) {
                    case 0:
                        printf("e ]");
                        break;
                    case 1:
                        printf("b ]");
                        break;
                    case 2:
                        printf("g ]");
                        break;
                    case 3:
                        printf("d ]");
                        break;
                    case 4:
                        printf("a ]");
                        break;
                    case 5:
                        printf("E ]");
                        break;
                }
            }
            printf("Node:  %i  |  VertStretch:  %i  |  Pressure:  %i\n", presses[i]->node, presses[i]->vertStretch, presses[i]->pressure);
        }
    }
    printf("==========================================================\n\n\n\n");
}

void usbConnectionListener(unsigned char* &dataBuff, int size) {
    clearPresses(); // Comentar si vamos por la alternativa de delete no implícito

    if (debugMode) {
        printf(" Se recibió mensaje USB \n");
        printf(" # Bytes: %i \n", size);
    }

    int intOffset = 0;
    // Nos movemos de a 5 bytes, que es el mínimo tamaño del paquete.
    for (offset = 0; offset < size; offset += 8) {
        int *buff = (int *) dataBuff;
        int op = buff[intOffset];

        if (debugMode) {
            printf(" # OP: %i\n", op);
        }
        
        switch (op) {
            case 0x2: { // Finger press
                FingerPress *press = malloc(sizeof(FingerPress));
                press->isCejilla = buff[intOffset + 1];
                press->node = buff[intOffset + 2];
                press->chord = buff[intOffset + 3];
                press->id = buff[intOffset + 4];
                press->vertStretch = buff[intOffset + 5];
                press->pressure = buff[intOffset + 6];

                agregarOActualizarPress(press);

                // Este es un paquete más grande de lo común
                // Lo aumentamos en la diferencia con el paquete más pequeño
                // 28 - 8 = 20
                intOffset += 7;
                offset += 20;

                break;
            }
            // Variante con finger release explícito
            /* 
            case 0x3: { // Finger release
                int id = buff[intOffset + 1];


                quitarPress(id);
                intOffset += 2;

                break;
            }
            */
            default: { // assume 8 byte packet
                intOffset += 2;
                break;
            }
        }
    }

    logPressess();
    actualizarEstado();
}

void actualizarEstado() {
    if (debugMode) {
        printf(" Recibo el mensaje midi \n");
        printf(" # nbytes: %i \n", nbytes);
        printf(" # tipo: %i \n",tipo);
        printf(" # canal: %i \n", canal);
        printf(" # message 0 (tipoycanal): %i \n", tipoycanal);
        printf(" # message 1: %i \n", message[1]);
        printf(" # message 2: %i \n", message[2]);
        printf(" # message 3: %i \n", message[3]);
    }

    // TODO: Pasar data con cada press!!
    for (uint8_t i = 0; i < MAX_FINGER_PRESS_AMOUNT; i++) {
        if (presses[i]->valid) {
            // Actualizo estado del sistema con la data de este press!
        }
    }
}