/**
 * La conversión de HEIC a JPEG antes de subir
 * ([ADR-014](../../../docs/common/architecture/decisions/ADR-014-heic-conversion.md)).
 *
 * Es lo que 5.8.3 lleva escrito desde la Fase 1 —«lo convierte el frontend antes
 * de subirlo»— y no existía. Hasta hoy, la foto que hace un iPhone con los
 * ajustes de fábrica se rechazaba con un `415` que enumeraba tipos que el
 * usuario no eligió.
 *
 * **Vive en el cliente de la API y no en el campo de subida**, y esa es la parte
 * que conviene no deshacer. Hay dos sitios desde los que se sube —`UploadField`
 * y el avatar, que tiene su propio `<input>`— y va a haber más. Poniéndola en la
 * única puerta por la que pasan todos, no hay forma de olvidarla en la tercera.
 *
 * **El import es dinámico, y esa es la mitad de la decisión.** El decodificador
 * pesa 2 995 kB minificado (734 kB comprimido) y vive en un fragmento aparte:
 * quien no elige un HEIC no descarga ni un byte de él, y sobre el bundle inicial
 * esto cuesta **2,49 kB**. Sustituirlo por un import estático no cambiaría nada
 * visible en desarrollo y multiplicaría por ocho la primera carga de todo el
 * mundo.
 *
 * Lo que **no** hace, y no es un olvido: no valida nada. Igual que el `accept`,
 * esto es una comodidad. Quien decide qué es un fichero sigue siendo el
 * servidor, inspeccionando el contenido, y quien borra el EXIF con las
 * coordenadas de la casa sigue siendo la recodificación de 5.8.3 —el lienzo
 * nuevo del que salen los píxeles sin metadatos—. Lo de aquí solo consigue que
 * lleguen unos bytes que ese camino sepa abrir.
 */

/**
 * Lo que se le pide al codificador al convertir, **por encima del 0,85 que usa
 * el servidor**.
 *
 * Recodificar dos veces suma las dos pérdidas. Convirtiendo con más calidad de
 * la que el servidor va a aplicar después, la que decide el resultado es la
 * suya y no la de este paso, que es donde está escrito el criterio.
 */
const QUALITY = 0.9

/** Con menos de esto no se puede decidir nada, y ningún fichero útil es tan corto. */
const HEAD_BYTES = 12

/**
 * Las marcas de contenedor que significan HEIF, en el orden de la especificación
 * ISO/IEC 23008-12.
 *
 * `heic` y `heix` son las de una foto de iPhone; `mif1` es la genérica del
 * contenedor y la emiten varios codificadores; las de secuencia entran porque un
 * *Live Photo* llega así y su primer fotograma es exactamente la foto que la
 * persona cree estar subiendo.
 */
const HEIF_BRANDS = ['heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'hevm', 'hevs', 'mif1', 'msf1']

/**
 * La conversión ha fallado con un fichero que **sí** era HEIC.
 *
 * Tiene clase propia porque `humanMessage` no puede tratarlo como los demás: no
 * lleva `code` —no ha llegado a haber petición— y tampoco es un fallo de red, así
 * que caer en el mensaje por defecto mandaría a comprobar la conexión, que es
 * mirar donde no es.
 */
export class HeicConversionError extends Error {
  constructor(readonly cause: unknown) {
    super('No se ha podido convertir la foto HEIC')
    this.name = 'HeicConversionError'
  }
}

/**
 * Si los **bytes** dicen HEIF: la caja `ftyp` en el desplazamiento 4 y una marca
 * conocida justo detrás.
 *
 * Se miran los dos y no solo la marca, con el mismo criterio que el
 * `ContentSniffer` del servidor: ser estricto en el desplazamiento es lo que
 * impide que baste con colocar cuatro letras en el sitio aproximado.
 *
 * Y se miran los bytes y **no la extensión**, que es lo que hace que funcione el
 * caso que más se da en la práctica: el fichero que llega de una carpeta
 * compartida llamándose `IMG_0042.JPG` y siendo HEIC por dentro.
 */
export async function isHeic(file: Blob): Promise<boolean> {
  if (file.size < HEAD_BYTES) return false

  const head = new Uint8Array(await file.slice(0, HEAD_BYTES).arrayBuffer())
  const ascii = (from: number, to: number) => String.fromCharCode(...head.slice(from, to))

  return ascii(4, 8) === 'ftyp' && HEIF_BRANDS.includes(ascii(8, 12).replace('\0', ' ').trim())
}

/**
 * El fichero tal cual si el servidor ya sabe abrirlo, y su conversión a JPEG si
 * era HEIC.
 *
 * [onConverting] avisa de que empieza la parte lenta, que en una foto de 12 MP
 * es del orden de un segundo en un escritorio y de varios en un móvil. Sin ese
 * aviso la interfaz se queda quieta sin decir nada, que es justo lo que el
 * estado «Procesando» de la subida existe para evitar.
 *
 * **Un fallo aquí no sube el original.** Sería gastar la conexión de la persona
 * para acabar en el mismo `415` de antes, y encima con el mensaje equivocado:
 * enumeraría los cuatro tipos admitidos cuando el problema es otro.
 */
export async function toUploadable(file: File, onConverting?: () => void): Promise<File> {
  if (!(await isHeic(file))) return file

  onConverting?.()

  try {
    // La variante `csp` en lugar de la de por defecto: hace lo mismo sin
    // `eval` ni `new Function`, así que poner una Content-Security-Policy sin
    // `unsafe-eval` delante de la aplicación sigue siendo posible el día que
    // toque. La otra lo cerraría, y por un kilobyte de diferencia.
    const { heicTo } = await import('heic-to/csp')
    const converted = await heicTo({ blob: file, type: 'image/jpeg', quality: QUALITY })

    // El nombre pasa a `.jpg` porque es lo que se guarda y lo que se va a
    // enseñar en el listado del almacenamiento. Dejarlo en `.heic` describiría
    // unos bytes que ya no existen.
    return new File([converted], asJpegName(file.name), {
      type: 'image/jpeg',
      lastModified: file.lastModified,
    })
  } catch (failure) {
    throw new HeicConversionError(failure)
  }
}

function asJpegName(name: string): string {
  const dot = name.lastIndexOf('.')
  return `${dot > 0 ? name.slice(0, dot) : name}.jpg`
}
