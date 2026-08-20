# Ficheros de prueba

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Los binarios que las pruebas del frontend necesitan y no pueden fabricar |
| Última revisión | 2026-08-20 |

Este directorio existe por una sola razón: **nada de lo que hay en el repositorio
sabe escribir un HEIC**. Las pruebas del backend generan sus imágenes al vuelo
porque ImageIO escribe JPEG, PNG y —con el plugin— WebP; ninguna de las dos
cadenas de construcción, ni la de Kotlin ni la de TypeScript, tiene con qué
producir un HEIC. Así que este es el único fichero versionado del proyecto que no
se puede regenerar sin una herramienta de fuera.

De ahí la regla de admisión: **aquí solo entra lo que no se puede fabricar en una
prueba**, y cada cosa que entre trae escrito de dónde salió y cómo se rehace.

## `photo-with-gps.heic`

Lo que la [ADR-014](../../../../docs/common/architecture/decisions/ADR-014-heic-conversion.md)
necesita demostrar, y lo que el roadmap pedía con esas palabras: **una foto HEIC
de verdad, no un JPEG renombrado**.

| | |
|---|---|
| Formato | HEIC, marca principal `heic` (la de una foto de iPhone) |
| Dimensiones | 1280 × 960 |
| Tamaño | 95 532 B |
| Metadatos | **352 B de EXIF**, con `Make=Apple`, `Model=iPhone 15`, fecha de captura y **coordenadas GPS** |

**Las coordenadas están puestas a propósito y son las que hacen útil el fichero.**
Recodificar en el servidor no es un ahorro de bytes sino la defensa que borra la
posición de la casa (5.8.3), y el camino de HEIC añade un paso más delante de esa
defensa. Con una foto sin EXIF, una prueba que suba esto no distinguiría entre
«se borró» y «nunca hubo nada que borrar».

Son las de la Puerta del Sol —40,4168 N; 3,7038 O—, que es un sitio público: el
fichero tiene que llevar unas coordenadas creíbles, no las de nadie.

**La imagen no es una fotografía**: es un degradado con tres formas encima,
generado con Pillow. Basta para lo que se mide —que se decodifica, que se
recodifica y que los metadatos no sobreviven— y evita meter en el repositorio una
imagen de la que habría que responder por su licencia.

### Cómo se rehace

Con `libheif` y `exiftool`, que en el equipo de desarrollo salen más fácil de un
contenedor que de una instalación:

```bash
docker run --rm -v "$PWD:/w" -w /w debian:12-slim bash -lc '
  apt-get update -qq && apt-get install -y -qq libheif-examples python3-pil libimage-exiftool-perl
  python3 -c "
from PIL import Image, ImageDraw, ImageFilter
import math
w, h = 1280, 960
img = Image.new(\"RGB\", (w, h))
px = img.load()
for y in range(h):
    for x in range(w):
        px[x, y] = (int(90 + 80 * math.sin(x / 180.0)), int(100 + 70 * math.cos(y / 150.0)), int(120 + 60 * math.sin((x + y) / 240.0)))
d = ImageDraw.Draw(img)
d.ellipse([220, 180, 700, 640], fill=(210, 170, 90))
d.rectangle([760, 300, 1180, 780], fill=(70, 110, 160))
d.ellipse([300, 540, 560, 800], fill=(160, 70, 70))
img.filter(ImageFilter.GaussianBlur(1.2)).save(\"/w/base.png\")"
  heif-enc -q 78 /w/base.png -o /w/photo-with-gps.heic
  exiftool -overwrite_original \
    -GPSLatitude=40.4168 -GPSLatitudeRef=N -GPSLongitude=-3.7038 -GPSLongitudeRef=W \
    -Make=Apple -Model="iPhone 15" -DateTimeOriginal="2026:08:20 12:34:56" \
    /w/photo-with-gps.heic
'
```

El fichero que sale no es byte a byte el que está aquí —`heif-enc` no es
determinista entre versiones de x265— y no hace falta que lo sea: lo que las
pruebas exigen es la marca `heic`, unas dimensiones conocidas y el EXIF dentro.

### Quién lo usa

- [`heic.test.ts`](../../api/heic.test.ts): que la detección mira los bytes, que
  la conversión produce un JPEG y que un fallo se distingue de un fallo de red.
- [`vertical-journey.spec.ts`](../../../e2e/vertical-journey.spec.ts): el
  recorrido entero en un navegador de verdad, hasta verlo en la galería y en la
  ficha del asset.
