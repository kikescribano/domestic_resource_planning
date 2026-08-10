# 5.8 Almacenamiento de ficheros

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Almacenamiento de ficheros en el servidor |
| Última revisión | 2026-08-10 |

> Trasladado desde la sección 5.8 del [`README principal`](../../../README.md) al iniciar la Fase 1. **Los números de sección se conservan**: hay más de cien referencias cruzadas del tipo «ver 4.1.1» repartidas por el repositorio, y renumerarlas las rompería todas.

Los ficheros de 4.1.1 se guardan **en el disco del propio servidor**, no en un servicio externo. La base de datos guarda la fila y el disco guarda los bytes: son dos sistemas distintos, y casi todo lo que sigue existe para que no acaben contradiciéndose.

## 5.8.1 Dónde viven los bytes

Los ficheros ocupan un **volumen separado** del sistema operativo y de PostgreSQL, montado con `noexec,nodev,nosuid` y fuera de cualquier árbol que sirva el servidor web. La separación no es cosmética: sin ella, un disco lleno de fotos no degrada las subidas, sino que tumba la base de datos y con ella la aplicación entera. Con ella, llenarlo solo impide subir más.

La ruta se deriva del identificador del fichero, nunca de nada que haya enviado el cliente:

```
<raíz>/<householdId>/<2 primeros caracteres del fileId>/<fileId>
```

Sin extensión —nada debe poder interpretarse por su nombre— y con el nombre original guardado solo como dato en la fila. El troceado por dos caracteres evita directorios de decenas de miles de entradas.

Que el `householdId` aparezca en la ruta no es lo que aísla. **Lo que aísla es que para construir la ruta hay que haber leído antes la fila**, y esa lectura ya pasó por la política de RLS. De ahí sale una consecuencia que parece una optimización desaprovechada y no lo es: **no se deduplica contenido entre hogares**. Dos hogares con el mismo manual guardan dos copias, porque compartir una ruta rompería justo esa herencia.


## 5.8.3 El camino de subida

1. **Tope duro antes de leer el cuerpo**, en la configuración del contenedor: 25 MB. Una petición mayor se corta sin llegar al caso de uso.
2. **Reserva de cuota**, en una transacción corta: se bloquea la fila del hogar (`SELECT … FOR UPDATE`), se suma lo vivo más el tamaño declarado y, si cabe, se inserta la fila del fichero con `uploadedAt` a nulo. Confirmar y soltar.
3. **Escritura a un fichero temporal**, ya **sin ningún bloqueo**, contando bytes y abortando si se supera lo reservado.
4. **Detección del tipo real** inspeccionando el contenido, no la extensión ni el `Content-Type` declarado. Si el tipo real no está en la lista blanca, se rechaza.
5. **Recodificación de las imágenes**, con tope de dimensiones comprobado *antes* de decodificar —un PNG de 50 000 × 50 000 revienta la memoria al abrirlo, no al leerlo— y generación de la miniatura.
6. **Movimiento al destino definitivo y cierre de la fila:** tamaño real, `checksum`, tipo detectado y `uploadedAt`. Los bytes se escriben antes de cerrar la fila, de modo que en cualquier instante el disco contiene todo lo que la base de datos da por bueno.

**Por qué la reserva, y no comprobar la cuota y ya.** Si el bloqueo se tomara antes de transmitir y se soltara al confirmar, duraría **toda la subida**: un fichero de 25 MB por una conexión mala dejaría al hogar entero sin poder subir nada durante un minuto. Reservando primero, el bloqueo dura milisegundos y la transmisión no bloquea a nadie. El precio es una fila a medias mientras dura la subida, que es exactamente lo que `uploadedAt` a nulo significa — ocupa cuota, no se puede adjuntar, y si la subida se corta la recoge el proceso diario.

El tamaño real solo puede ser **menor** que el reservado, porque recodificar encoge y porque la transmisión aborta si lo supera. Así que cerrar la fila nunca aumenta lo consumido: solo devuelve lo que sobraba.

El `Content-Length` sirve para reservar y para rechazar antes de recibir, nunca para creerse el tamaño: quien lo escribe es el cliente, y el paso 3 lo comprueba contando.

**Recodificar es sobre todo por el EXIF.** Una foto hecha con el móvil dentro de casa lleva incrustadas las coordenadas GPS de la casa. Es el dato más sensible que va a atravesar este mecanismo y nadie lo introduce a sabiendas: quitar los metadatos al recodificar no es un ahorro de bytes, es la razón principal para hacerlo. De paso destruye cualquier carga útil escondida en el fichero.

**La miniatura sale del mismo paso.** 320 px en el lado largo y en WebP, que es de sobra para una rejilla en un móvil de 375 px. Se guarda junto al original, bajo la misma clave y otro prefijo, y **no cuenta en la cuota del hogar** (ver 4.1.1): la decide el sistema, no el usuario. Sí ocupa disco, así que el dimensionado del volumen cuenta con un margen sobre la suma de las cuotas — del orden de un 5 %.

**Lista blanca de tipos:** `image/jpeg`, `image/png`, `image/webp` y `application/pdf`. Nada más, con dos exclusiones deliberadas:

- **SVG queda fuera.** Es XML con scripts dentro, y nadie fotografía una caldera en SVG.
- **HEIC queda fuera por ahora**, aunque sea lo que produce un iPhone por defecto: la JVM no lo decodifica sin librerías nativas. Lo convierte el frontend antes de subirlo.

El PDF sí entra —los manuales y las facturas son PDF— pero nunca se muestra incrustado dentro de la aplicación.

## 5.8.4 El camino de descarga

Hay **dos caminos**, y lo que los separa no es la técnica sino el riesgo de lo que transportan.

**Imágenes: URL firmada de vida corta.** Un `<img src>` no puede enviar la cabecera `Authorization` — el HTML no ofrece ninguna forma de adjuntar cabeceras a la carga de un subrecurso. Autenticar la imagen por cabecera obligaría al frontend a descargarla con JavaScript y a renunciar a `loading="lazy"`, a `srcset`, a la caché del navegador y al renderizado progresivo, justo en la pantalla donde más se nota: una rejilla de existencias en un móvil. Por eso la aplicación **emite la URL ya firmada** al devolver la entidad:

```
https://files.drp.example/f/thumb/3f2a55c1-…?e=1786400000&s=<HMAC>
```

nginx verifica la firma con el módulo `secure_link` y sirve **sin preguntar a la aplicación**. El HMAC cubre la ruta **y** la caducidad, así que alargarla editando el parámetro invalida la firma; y la ruta es un UUID v4, que no se enumera.

**Caduca con el access token que la generó**, unos quince minutos (ver 5.4.1). La simetría no es estética: cuando el frontend renueva el token vuelve a leer las entidades y recibe URL frescas, así que no hay dos relojes que cuadrar. De ahí una consecuencia para el contrato — **un `photoUrl` no se guarda**: vale para pintar ahora, no para almacenar en el estado del cliente ni para compartir.

**Documentos y descargas explícitas: el endpoint autorizado.** `GET /api/v1/files/{id}/content` comprueba que el fichero pertenece al hogar del token y responde con `X-Accel-Redirect` a una ruta interna que resuelve nginx. Descargar una factura es un clic, y un clic ya es JavaScript: ahí no hay ninguna razón para renunciar a comprobar el hogar en cada petición. En desarrollo, sin nginx delante, el mismo endpoint transmite los bytes directamente.

**El reparto es de proporción.** Lo que pierde la comprobación por petición durante quince minutos es una foto de un estante, ya recodificada, sin EXIF y con nombre no adivinable. Lo que la conserva es la factura con nombre y dirección y la garantía con el número de serie.

> **La condición sin la cual esto no sería aceptable: no registrar la cadena de consulta.** nginx escribe la URL completa en el log de acceso por defecto, firma incluida. Con la retención habitual de un log, eso convierte el propio registro en un almacén de credenciales vivas — y de las que nadie vigila. En el `location` de ficheros se registra la ruta **sin parámetros**, y la aplicación tampoco los traza. Se completa con `Referrer-Policy: no-referrer`, para que la firma no viaje en la cabecera `Referer` de ninguna navegación posterior.

**Lo que se acepta a cambio.** La autorización de una imagen se comprueba **al emitir la URL**, no al descargarla, así que sacar a alguien del hogar no invalida las que ya tuviera en pantalla: siguen sirviendo hasta que caduquen. No es una clase de exposición nueva — el access token de esa misma persona tampoco se puede revocar antes de expirar (ver 5.4.1)—, pero conviene que esté escrito y no descubierto.

Tres cabeceras que no son opcionales en ninguno de los dos caminos: `Content-Disposition: attachment` con el nombre original codificado según RFC 6266, `X-Content-Type-Options: nosniff` y `Content-Security-Policy: default-src 'none'; sandbox`. El `attachment` **no impide** que un `<img>` pinte la imagen: el navegador solo lo honra en navegaciones y descargas, no en subrecursos. Es decir, se puede exigir siempre sin romper nada.

Y los ficheros **se sirven desde otro dominio**, no desde una ruta de la aplicación. Es lo que impide que un fichero que se cuele pese a todo lo anterior comparta origen con la sesión, el token y los datos del hogar.
