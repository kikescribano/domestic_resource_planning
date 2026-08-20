import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { afterEach, describe, expect, it, vi } from 'vitest'

import { humanMessage } from './client'
import { HeicConversionError, isHeic, toUploadable } from './heic'

/**
 * La conversión de HEIC (ADR-014), medida contra **un HEIC de verdad**.
 *
 * El fichero de `fixtures/` no es un JPEG renombrado, y esa distinción es toda
 * la prueba: lo que aquí se comprueba es que la detección mira los bytes, y con
 * un JPEG renombrado los bytes serían los de un JPEG.
 *
 * Lo que **no** se puede medir aquí es la decodificación. `jsdom` no tiene
 * `<canvas>` con contexto 2D, ni `Worker`, ni WebAssembly instanciable, y el
 * decodificador necesita las tres cosas: es un módulo nativo compilado a wasm
 * que corre en un hilo aparte y vuelca los píxeles en un lienzo. Así que aquí se
 * dobla el módulo y **el decodificador de verdad lo ejecuta el recorrido
 * vertical**, en Chromium, que es donde esa comprobación significa algo.
 *
 * El reparto no es una renuncia sino el mismo de siempre: lo que depende del
 * navegador se mide en un navegador.
 */

// Desde la raíz del proyecto y no desde `import.meta.url`: Vite reescribe esa
// URL a una del servidor de desarrollo, y `readFileSync` solo entiende rutas.
const HEIC = readFileSync(resolve(process.cwd(), 'src/test/fixtures/photo-with-gps.heic'))

/** Los primeros bytes de un JPEG: la firma que el `ContentSniffer` del servidor reconoce. */
const JPEG_HEAD = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01])

function fileOf(bytes: Uint8Array | Buffer, name: string, type = ''): File {
  return new File([new Uint8Array(bytes)], name, { type })
}

afterEach(() => {
  vi.resetModules()
  vi.doUnmock('heic-to/csp')
})

describe('detectar un HEIC', () => {
  it('reconoce el fichero de prueba, que es un HEIC de verdad', async () => {
    await expect(isHeic(fileOf(HEIC, 'IMG_0042.HEIC'))).resolves.toBe(true)
  })

  it('lo reconoce aunque se llame .jpg, porque mira los bytes y no el nombre', async () => {
    // Es el caso que más se da: la foto que llega de una carpeta compartida ya
    // renombrada. Fiarse de la extensión lo dejaría subir tal cual, y el `415`
    // volvería con el mismo mensaje de siempre.
    await expect(isHeic(fileOf(HEIC, 'IMG_0042.jpg', 'image/jpeg'))).resolves.toBe(true)
  })

  it('no reconoce un JPEG aunque se llame .heic', async () => {
    await expect(isHeic(fileOf(JPEG_HEAD, 'foto.heic', 'image/heic'))).resolves.toBe(false)
  })

  it('no reconoce un fichero más corto que la cabecera', async () => {
    await expect(isHeic(fileOf(new Uint8Array([0x00, 0x01, 0x02]), 'roto.heic'))).resolves.toBe(false)
  })

  it('no reconoce una marca conocida colocada sin la caja ftyp delante', async () => {
    // `heic` en el sitio correcto pero sin `ftyp` en el 4: es lo que separa
    // mirar el desplazamiento de mirar «si aparece por ahí».
    const impostor = new Uint8Array(16)
    impostor.set(new TextEncoder().encode('heic'), 8)
    await expect(isHeic(fileOf(impostor, 'impostor.heic'))).resolves.toBe(false)
  })
})

describe('convertir antes de subir', () => {
  it('devuelve el mismo fichero, sin tocarlo, si no era un HEIC', async () => {
    const jpeg = fileOf(JPEG_HEAD, 'foto.jpg', 'image/jpeg')
    const onConverting = vi.fn()

    // La identidad importa: si devolviera una copia, el coste de no subir un
    // HEIC pasaría de leer doce bytes a copiar el fichero entero en memoria.
    await expect(toUploadable(jpeg, onConverting)).resolves.toBe(jpeg)
    expect(onConverting).not.toHaveBeenCalled()
  })

  it('convierte el HEIC a JPEG, avisa de que empieza y renombra lo que sale', async () => {
    const heicTo = vi.fn().mockResolvedValue(new Blob([new Uint8Array(JPEG_HEAD)], { type: 'image/jpeg' }))
    vi.doMock('heic-to/csp', () => ({ heicTo }))
    const { toUploadable: convert } = await import('./heic')

    const onConverting = vi.fn()
    const result = await convert(fileOf(HEIC, 'IMG_0042.HEIC'), onConverting)

    expect(onConverting).toHaveBeenCalledOnce()
    expect(result.type).toBe('image/jpeg')
    // El nombre describe lo que se guarda. Dejarlo en `.HEIC` mentiría en el
    // listado del almacenamiento, que enseña el nombre original.
    expect(result.name).toBe('IMG_0042.jpg')
    // Por encima del 0,85 del servidor: la pérdida que decide el resultado es
    // la suya, no la suma de dos.
    expect(heicTo).toHaveBeenCalledWith(expect.objectContaining({ type: 'image/jpeg', quality: 0.9 }))
  })

  it('no sube el original si la conversión falla', async () => {
    const heicTo = vi.fn().mockRejectedValue(new Error('no such image'))
    vi.doMock('heic-to/csp', () => ({ heicTo }))
    const { toUploadable: convert, HeicConversionError: Failure } = await import('./heic')

    // Subirlo igual gastaría la conexión de la persona para acabar en el mismo
    // 415 de antes, y encima enumerando cuatro tipos que no explican nada.
    await expect(convert(fileOf(HEIC, 'IMG_0042.HEIC'))).rejects.toBeInstanceOf(Failure)
    // Que el doble se haya llegado a llamar: sin esto, la prueba pasaría igual
    // el día que el módulo de verdad reventara al cargarse en `jsdom`, que es
    // otro fallo con la misma pinta.
    expect(heicTo).toHaveBeenCalledOnce()
  })
})

describe('el mensaje del fallo', () => {
  it('no se confunde con un fallo de red', () => {
    // Un fallo de conversión no ha llegado a haber petición, así que no trae
    // `code`. Sin este caso caería en el mensaje por defecto y mandaría a
    // comprobar la conexión.
    expect(humanMessage(new HeicConversionError(new Error('boom')))).toContain('convertir')
    expect(humanMessage(new Error('cualquier otra cosa'))).toContain('conectar')
  })
})
