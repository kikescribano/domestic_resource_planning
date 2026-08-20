# ADR-014: Conversión de HEIC

- Estado: accepted
- Fecha: 2026-08-20
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

**La foto que hace un iPhone con los ajustes de fábrica no se puede subir a DRP.**
Sale en HEIC, HEIC está fuera de la lista blanca de
[5.8.3](../../../backend/architecture/file-storage.md) y el servidor responde un
`415` que enumera cuatro tipos que quien hizo la foto no eligió. Para una
aplicación cuyo gesto central es fotografiar lo que hay en casa, es la carencia
más visible que dejaron las dos primeras fases, y le toca al usuario más
probable.

No es un olvido. La [ADR-005](ADR-005-local-file-storage.md) y 5.8.3 excluyeron
HEIC con su motivo escrito —«la JVM no lo decodifica sin librerías nativas»— y
asignaron la conversión a otro sitio:

> **HEIC queda fuera por ahora** [...]: la JVM no lo decodifica sin librerías
> nativas. Lo convierte el frontend antes de subirlo.

Lo que quedó abierto es que **el frontend nunca tuvo con qué**. La frase describe
un reparto de trabajo que no llegó a existir, y lleva desde el Hito 3 de la Fase 1
en la ficha de [`upload-field`](../../../frontend/design-system/components/upload-field.md)
como lo primero que se iba a notar en un móvil de verdad.

**El plan del cierre de huecos prohibió expresamente decidir esto por gusto.** Su
condición era dos medidas delante —el peso real del decodificador wasm sobre el
bundle vuelto a medir, y el coste en servidor con los números del runner— y tres
salidas evaluadas, incluida la de no convertir. Esta ADR se escribe después de
tomarlas, y las trae dentro.

Dos cosas vienen dadas y esta ADR no las revisa:

1. **Recodificar es sobre todo por el EXIF, no por los bytes.** Una foto hecha
   dentro de casa lleva incrustadas las coordenadas de la casa, y quitarlas es la
   razón principal del paso 5 de 5.8.3 — que además lo consigue **por
   construcción**: se decodifica a píxeles, se pinta un lienzo nuevo y lo que no
   son píxeles no llega al escritor. Cualquier camino que admita HEIC tiene que
   seguir pasando por ahí.
2. **La lista blanca es cerrada por decisión y no por casualidad.** Vive en tres
   sitios —el enumerado `StoredContentType`, el `CHECK` de `files.content_type` y
   el contrato— y ampliarla exige una migración. Esa fricción es deliberada.

## Las dos medidas

Están enteras, con su método, en
[`capacity-measurements.md`](../../../backend/operations/capacity-measurements.md),
que es donde viven los números del proyecto. Aquí va lo que decide.

### 1. El decodificador en el cliente

Construcción de producción, que es la única que dice la verdad. **El bundle de
referencia se vuelve a medir y no son los 402 kB que citaba el plan: son 407,28
kB** (119,74 comprimido) — entre planificar y ejecutar habían entrado la baja de
hogar y el cierre de cuenta con sus pantallas.

| Decodificador | Peso propio | Comprimido | Sobre la primera carga |
|---|---|---|---|
| `heic-to` 1.5.2 (libheif al día) | 2 995,46 kB | 734,16 kB | **+2,49 kB** |
| `heic2any` 0.0.4 (libheif de 2023) | 1 352,97 kB | 341,25 kB | +2,49 kB |

**La columna que decide es la última, y no está donde el plan esperaba.** El
decodificador pesa siete veces la aplicación entera, pero solo si se descarga: en
un `import()` dinámico sale en su propio fragmento y lo pide únicamente el
navegador que se encuentra un HEIC. Sobre lo que descarga quien abre DRP, el coste
medido es de **2,49 kB, un 0,61 %**.

El plan advertía que «un megabyte encima de eso no es un incremento, es un cambio
de categoría», y tenía razón sobre el supuesto que manejaba —el decodificador
dentro del bundle—. Medirlo enseñó que ese supuesto era evitable, que es
exactamente la diferencia entre medir y opinar.

Y el coste que sí se paga, medido en Chromium sobre un escritorio con una foto de
12 MP: **913 ms** con `heic-to` y **1 628 ms** con `heic2any`. En un móvil es
varias veces más.

### 2. La conversión en el servidor

Aquí la viabilidad decide antes que el milisegundo, y por eso va primero:

- **En Maven Central hay un solo plugin de ImageIO para HEIF**,
  `com.github.gotson.nightmonkeys:imageio-heif` 1.0.0. Su implementación vive en
  `META-INF/versions/22`: **exige JDK 22 o superior** —usa la FFM API— y **no trae
  binarios**, sino que enlaza con el `libheif` que haya instalado en la máquina.
- El proyecto está en `jvmToolchain(17)`, y el plugin de WebP que ya usa
  (`org.sejda.imageio:webp-imageio`) **sí trae los nativos dentro del jar**, que es
  justamente lo que hace que hoy no haya ninguna dependencia del sistema
  operativo. TwelveMonkeys 3.12.0, la otra colección de plugins de ImageIO, no
  tiene HEIF.

Es decir: el camino del servidor no empieza por escribir código, sino por **subir
el toolchain de la JVM y meter una librería nativa en la imagen de despliegue y en
el runner de la CI**.

Y el coste de CPU, medido con `libheif` limitado a **2 vCPU** para parecerse al
runner, sobre 4032 × 3024 —12 MP, la misma talla que mide el proyecto—:

| Origen, 12 MP | Decodificar y recodificar a JPEG |
|---|---|
| JPEG típico | 233 ms |
| **HEIC típico** | **790 ms** (×3,4) |
| JPEG con mucho detalle | 333 ms |
| **HEIC con mucho detalle** | **1 865 ms** (×5,6) |

Llevado a la cifra del runner —775,9 ms de mediana para recodificar una foto de
12 MP— admitir HEIC en el servidor dejaría esa operación en **2,6 a 4,3 s de
núcleo por foto**. Era ya la operación cara por un orden de magnitud, y las
mismas 2 vCPU atienden los logins con sus 19 MiB de Argon2id cada uno.

## Decisión

### 1. **Convierte el cliente**, que es lo que 5.8.3 ya decía

Y por tanto **5.8.3 no se enmienda**: la frase «lo convierte el frontend antes de
subirlo» pasa de describir un reparto que no existía a describir uno que existe.
Lo que se cierra es la nota de la ficha de `upload-field`, no la sección.

Las tres razones, en orden de peso:

1. **El coste va a quien lo necesita.** Los 2 995 kB los descarga el navegador que
   se encuentra un HEIC, una vez y luego de su caché. El servidor no paga nada, y
   quien sube JPEG tampoco.
2. **El camino del servidor no es una dependencia más, es un cambio de plataforma.**
   JDK 22 y `libheif` del sistema afectan al despliegue, a la CI y a cualquiera que
   construya el proyecto, para resolver un caso que el cliente resuelve dentro de
   su propio proceso.
3. **El decodificador acaba donde menos daño puede hacer.** Es un parser de
   formato de imagen escrito en C, es decir la clase de componente donde
   históricamente aparecen los desbordamientos, y aquí corre **en la caja de arena
   del navegador de la propia persona, sobre un fichero que ella misma acaba de
   elegir**. En el servidor correría como proceso de la aplicación sobre bytes que
   manda cualquiera.

### 2. **`heic-to` 1.5.2**, y no el que pesa la mitad

`heic2any` cuesta 1 642 kB menos y pierde igualmente, por cuatro cosas medidas:

| | `heic-to` 1.5.2 | `heic2any` 0.0.4 |
|---|---|---|
| Última publicación | 2026-05-26 | **2023-03-29** |
| 12 MP en Chromium | **913 ms** | 1 628 ms |
| Decodifica fuera del hilo principal | **Sí**, en un Worker que monta él solo | No |
| Variante sin `eval` | **Sí** (`heic-to/csp`) | No |

Las dos últimas filas son las que no se compensan con bytes. Que decodifique en un
Worker **es la diferencia entre una interfaz que espera y una que parece colgada**:
en un móvil la decodificación son varios segundos, y en el hilo principal no se
repinta ni la barra que dice que algo está pasando. Y la variante sin `eval` deja
abierta la puerta de poner una Content-Security-Policy sin `unsafe-eval` delante de
la aplicación el día que toque; la otra la cerraría por medio kilobyte.

Se usa por tanto `heic-to/csp`, no la entrada por defecto.

**La licencia no distingue entre las dos y conviene decirlo**: `heic-to` es
LGPL-3.0 y `heic2any` es MIT, pero **los dos empaquetan `libheif`, que es LGPL**.
Es una dependencia enlazada dinámicamente y sin modificar, que sale en un fichero
propio y sustituible, así que el proyecto —MIT— sigue siendo MIT. Un camino de
servidor tampoco habría escapado: `libheif` es la única implementación práctica que
hay.

### 3. Vive en el cliente de la API, **no en el campo de subida**

`toUploadable` se invoca dentro de `uploadFile`, que es la única puerta por la que
salen los ficheros. Hay dos vías de subida —`UploadField` y el avatar, que tiene su
propio `<input>` porque sustituye en vez de acumular— y va a haber más. Puesta en
el campo, la tercera vía que alguien escriba nace sin conversión y nadie lo nota
hasta que un iPhone la usa.

El coste para quien no sube un HEIC es **leer doce bytes**.

### 4. Se decide por los bytes, nunca por la extensión

La detección busca la caja `ftyp` en el desplazamiento 4 y una marca HEIF conocida
justo detrás, con el mismo criterio estricto que el `ContentSniffer` del servidor.
Mirar el nombre habría dejado fuera el caso que más se da: la foto que llega de una
carpeta compartida llamándose `IMG_0042.JPG` y siendo HEIC por dentro.

### 5. Sale un JPEG con calidad 0,90, y se renombra

**0,90 y no 0,85**, que es la del servidor: recodificar dos veces suma las dos
pérdidas, y con el primer paso por encima del segundo, la que decide el resultado
es la del servidor —que es donde el criterio está escrito— y no la suma.

El fichero pasa a llamarse `.jpg` porque **es lo que se guarda**, y el nombre
original es lo que enseña el listado del almacenamiento. Dejarlo en `.heic`
describiría unos bytes que ya no existen en ninguna parte.

### 6. Si la conversión falla, **no se sube el original**

Sería gastar la conexión de la persona para acabar en el mismo `415` de siempre, y
encima con el mensaje equivocado: enumeraría cuatro tipos admitidos cuando el
problema es otro. El fallo tiene clase propia (`HeicConversionError`) por la misma
razón que hace falta distinguirlo: no lleva `code` —no ha llegado a haber
petición— y tampoco es un fallo de red, así que sin tratarlo caería en «comprueba
la conexión», que manda a mirar donde no es.

### 7. **La lista blanca del servidor no cambia. Ni el contrato, ni la tabla**

Es la comprobación que el plan pedía hacer antes de dar por hecha la migración o
por hecha su ausencia, y el resultado es que **no hace falta ninguna**: lo que se
guarda en `files.content_type` es el tipo **detectado tras recodificar**, y por el
camino elegido el servidor no llega a ver un HEIC nunca. Sigue habiendo cuatro
tipos en `StoredContentType`, cuatro en el `CHECK` de la tabla y cuatro en
`openapi.yaml`. El recorrido vertical lo afirma leyendo el `contentType` de lo
guardado.

Lo que sí cambia es el `accept` del selector, que gana `image/heic`, `image/heif`
y las dos extensiones. **No es la lista blanca**: es la lista de lo que se puede
elegir. Dejar el HEIC en gris en el diálogo sería el mismo muro que el `415` con
otra cara.

Van los tipos **y** las extensiones porque `image/heic` no está registrado en
todos los sistemas, y donde no lo está el diálogo solo casa por el final del
nombre.

### 8. El mensaje del `415` se queda como está

Sigue enumerando los tipos admitidos, porque sigue siendo la respuesta correcta
para todo lo demás —un GIF, un ZIP, un SVG—. Lo que ya no es es la respuesta que
recibe una foto de iPhone.

## Alternativas consideradas

### Convertir en el servidor

**Descartada**, y no por la CPU sino por lo que hay que mover para llegar a poder
medirla: JDK 22 en un proyecto que está en 17, y `libheif` instalado en la imagen
de despliegue y en el runner. Lo que hoy se despliega es un jar que no depende de
ninguna librería del sistema para tratar imágenes, y esa propiedad se pierde
entera.

La CPU la remata: **×3,4 a ×5,6 sobre la operación que ya era la más cara**, lo
que deja subir una foto en 2,6–4,3 s de núcleo sobre 2 vCPU compartidas con el
login.

Tenía a favor una cosa real, y conviene dejarla escrita porque es la que la
devolvería a la mesa: **funciona con cualquier cliente**, incluido el que no
ejecuta JavaScript y el que llegue por la API sin pasar por el navegador. Hoy no
hay ninguno.

### No convertir: pedirle al usuario que cambie el ajuste de la cámara

**Descartada**, y se evalúa en serio porque es lo que ocurre hoy de hecho, en
forma de `415`. Tiene el único coste que ninguna de las otras tiene: **cero**. No
hay dependencia, ni bytes, ni código, ni licencia que revisar.

Pierde por tres razones:

1. **Traslada al usuario un problema que es del sistema.** «Ajustes → Cámara →
   Formatos → Más compatible» es una ruta de cuatro pasos dentro de otra
   aplicación, y hay que recorrerla **antes** de hacer la foto: quien ya la tiene
   hecha no tiene arreglo.
2. **No resuelve el caso que más se da a medio plazo.** El HEIC no llega solo
   desde la cámara del propio teléfono: llega de un álbum compartido, de un
   mensaje, de una carpeta del ordenador. Cambiar un ajuste no toca ninguno de
   esos.
3. **Empeora la foto para conseguirlo.** «Más compatible» hace que el teléfono
   guarde JPEG, que a igual calidad ocupa cerca del doble — y el hogar tiene un
   gigabyte de cuota. La salida barata se paga en la cuota del usuario.

Lo que sí se conserva de ella es el **plan de repliegue**: si el decodificador
falla, el mensaje dice que se puede subir en JPEG, que es esta salida convertida
en lo que debería haber sido siempre —un remedio, no la política—.

### `heic2any` en lugar de `heic-to`

**Descartada** con la tabla de la decisión 2 delante. Los 1 642 kB que ahorra
están en un fragmento que la mayoría no descarga nunca y que quien lo descarga
guarda en su caché; a cambio pide el hilo principal durante segundos en un móvil,
obliga a `unsafe-eval` y su última publicación es de hace tres años en la versión
0.0.4.

### Un Worker propio alrededor del decodificador

**Descartada por innecesaria**: `heic-to` ya monta el suyo y decodifica fuera del
hilo principal sin que haya que escribir nada. Un Worker propio habría añadido un
módulo, un protocolo de mensajes y una pieza que `jsdom` no sabe ejecutar, para
conseguir lo que la librería ya hace.

### Usar el decodificador del propio navegador cuando lo haya

**Descartada por no comprobable.** Safari sobre plataformas de Apple decodifica
HEIC con el códec del sistema, así que `createImageBitmap` sobre el fichero
costaría **cero bytes** precisamente en el caso más probable. Es tentador y se
descarta por lo que costaría demostrarlo: **la CI no tiene ese navegador**, y un
camino que ninguna prueba recorre es un camino que se pudre. Si algún día el
recorrido vertical corre en WebKit sobre macOS, esto vuelve a la mesa como
optimización de un camino que ya funciona, no como sustituto.

### Reducir además el tamaño de la imagen en el cliente

**Fuera de alcance a propósito.** Es una de las mejoras que la ficha de
`upload-field` lista junto a HEIC y que el plan del cierre de huecos deja
expresamente fuera: se parecen porque están en la misma pantalla, pero recomprimir
a 2000 px no cierra ningún hueco — nadie ha decidido hacerlo.

## Consecuencias

**A favor:**

- **La foto de un iPhone se sube y se ve**, que es lo que esta ADR existe para
  conseguir.
- **El servidor no cambia**: ni un caso de uso, ni una tabla, ni una operación del
  contrato, ni un milisegundo de CPU. Es la consecuencia más valiosa y la menos
  visible.
- **La defensa del EXIF se refuerza sin tocarla.** El HEIC pasa ahora por **dos**
  lienzos —el del navegador al convertir y el del servidor al recodificar—, y
  ninguno de los dos sabe escribir metadatos.
- **La detección por bytes cubre el HEIC mal nombrado**, que hasta hoy era un
  `415` sin explicación posible.

**En contra, y medido:**

- **2,49 kB sobre la primera carga** de todo el mundo, convierta o no.
- **2 995 kB** para quien convierte, una vez.
- **Segundos de espera** antes de que empiece la subida: 913 ms en un escritorio
  con una foto de 12 MP, varias veces más en un móvil. La interfaz lo dice —
  «Convirtiendo la foto…»— y no se puede cancelar, porque el decodificador no
  ofrece por dónde.
- **La orientación EXIF se pierde**, como ya se perdía con cualquier JPEG que
  pasara por 5.8.3. En HEIC la rotación suele viajar como transformación del
  contenedor y `libheif` la aplica al decodificar, así que el caso normal sale
  derecho; el que la lleve solo en EXIF, no. No es una regresión: es la regla que
  ya había, con un sujeto más.
- **`@types/node` entra en el frontend**, porque la prueba de la conversión lee de
  disco un HEIC de verdad. El precio es que `process` y `Buffer` pasan a estar
  declarados también para el código de la aplicación, donde no existen. Queda
  escrito en el propio `tsconfig.json`.
- **Un fichero binario versionado**, `photo-with-gps.heic`, que nada de este
  repositorio sabe regenerar. Lleva al lado su procedencia y el comando que lo
  rehace.

**Lo que no cambia, y conviene que se lea:**

- La lista blanca sigue teniendo **cuatro tipos**, en los tres sitios.
- El `415` sigue diciendo lo mismo, porque sigue siendo cierto para todo lo demás.
- **Quien decide qué es un fichero sigue siendo el servidor**, inspeccionando el
  contenido. La conversión del cliente es una comodidad, igual que el `accept`, y
  no valida nada: un HEIC que se cuele sin convertir se rechaza como siempre.

## Validación o reversión

Se considera validada cuando:

1. **Una foto tomada por un iPhone con los ajustes de fábrica se sube y se ve**,
   que es el criterio literal que el plan del cierre de huecos fijó para esta ADR.
   Lo demuestra el recorrido vertical con **un HEIC de verdad** —marca `heic`,
   1280 × 960, no un JPEG renombrado— hasta verlo en la galería del hogar y en la
   ficha del asset.
2. **Lo guardado es `image/jpeg`**, leído del listado por la API. Es lo que afirma
   que HEIC no llega nunca a `files.content_type` y que por tanto no hacía falta
   migración.
3. **Los bytes servidos no llevan metadatos.** La foto de prueba entra con 352 B
   de EXIF y coordenadas GPS dentro, y lo que sale no contiene ni `Exif` ni `GPS`.
   Con una foto sin metadatos esta comprobación no distinguiría entre haberlos
   borrado y no haber tenido ninguno.
4. **La detección mira los bytes**: el mismo fichero llamado `.jpg` se convierte
   igual, y un JPEG llamado `.heic` no se toca.
5. **El decodificador no está en el bundle inicial**, comprobable en la salida de
   `npm run build`: sale como fragmento propio y la primera carga sube 2,49 kB.
6. **Un fallo de conversión no sube el original** y produce un mensaje que no
   habla de la conexión.

Revisar cuando ocurra cualquiera de estas cosas:

- **Aparece un cliente que no es este navegador** —una aplicación nativa, una
  integración por API—. Entonces la conversión deja de estar en el único sitio por
  el que pasan todos, y lo que vuelve a la mesa es el camino del servidor con sus
  dos costes ya medidos aquí.
- **El proyecto sube a JDK 22 o superior por otro motivo.** Desaparece la mitad
  más cara del camino del servidor y la comparación cambia; la otra mitad
  —`libheif` en la imagen— y los 2,6–4,3 s por foto siguen en pie.
- **`libheif` publica un aviso de seguridad.** Aquí corre en la caja de arena del
  navegador y sobre un fichero que eligió su propio dueño, así que la urgencia es
  otra que en el servidor — pero la versión se sube igual.
- **Los navegadores decodifican HEIC de serie.** El día que `createImageBitmap`
  lo abra en los navegadores que la CI puede ejecutar, el fragmento de 2 995 kB
  sobra para la mayoría y esto pasa a ser un repliegue.
- **Alguien propone reducir el tamaño en el cliente.** Comparte camino con esto
  —el mismo lienzo, la misma función— pero es otra decisión, y sigue sin tener
  quien la pida.

Revertir es barato y esa es una propiedad de la decisión, no un accidente: quitar
la dependencia, la llamada dentro de `uploadFile` y los cuatro valores del
`accept` devuelve el sistema exactamente a donde estaba, sin migración que
deshacer ni dato que convertir. Es lo que se gana no tocando el servidor.

## Relación con la ADR-005

**La [ADR-005](ADR-005-local-file-storage.md) no se reescribe** —una ADR aceptada
no se reescribe nunca— y **no queda desfasada**: todo lo que decidió sigue en pie,
la lista blanca de cuatro tipos incluida. Esta ADR **completa** una frase que
aquella dejó apuntando hacia adelante, y por eso se enlaza desde allí en lugar de
tocar su cuerpo.
