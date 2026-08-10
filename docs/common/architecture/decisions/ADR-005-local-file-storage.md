# ADR-005: Almacenamiento local de ficheros en el servidor

- Estado: accepted
- Fecha: 2026-08-10
- Responsables: Equipo DRP
- Ámbito: common
- Sustituye: Ninguna

## Contexto

Hasta ahora el core no guardaba binarios. La documentación asociada era una
entidad `Document` con una `url` obligatoria, y las fotos de asset, artículo y
ubicación eran también enlaces. La decisión, registrada en la sección 4.1.7 del
[`README principal`](../../../README.md), dejó explícitamente anotada la subida
de ficheros como evolución posterior y no como alternativa descartada.

Esa evolución tiene ahora dos motivos y un destino. Los motivos: depender de un
servicio ajeno —Dropbox o equivalente— para el manual escaneado deja una
instalación doméstica en manos de un tercero, y todo lo que llega en papel (la
garantía del sobre, la factura de la tienda de barrio) no tiene URL ninguna, así
que sencillamente no cabía en el modelo. El destino: la solución se despliega en
un VPS con almacenamiento propio, lo que hace que el disco pase de no existir a
ser un recurso disponible y compartido entre todos los hogares.

Compartido es la palabra que condiciona el resto. La misma máquina aloja varios
hogares, y el almacenamiento sin límite se lo queda entero el primero que suba
los vídeos de una reforma.

## Decisión

Los ficheros se guardan **en el sistema de ficheros del propio servidor**, en un
volumen separado del sistema operativo y de PostgreSQL, con los metadatos en una
tabla `files` sujeta a Row-Level Security como cualquier otra tabla del core.

Cuatro elementos completan la decisión:

1. **Cuota de 1 GB por hogar**, calculada como la suma de los ficheros vivos y
   validada en el caso de uso con la fila del hogar bloqueada. No es un `CHECK`
   porque no es una propiedad de una fila.
2. **El enlace externo convive con el fichero.** Un documento apunta a `url` o a
   `fileId`, exactamente a uno; una foto, a uno de los dos o a ninguno.
3. **El almacén se usa a través de un puerto `FileStorage`** de la capa de
   aplicación, con un adaptador de sistema de ficheros. La portabilidad se
   compra donde no cuesta nada.
4. **Los controles de la [File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)
   de OWASP** se aplican desde el primer día, detallados en la sección 5.8 del
   README: lista blanca por contenido real, renombrado en disco, volumen
   `noexec` fuera del árbol web, recodificación de imágenes y entrega como
   adjunto desde un dominio distinto.
5. **La entrega se parte en dos según lo que transporta.** Una **imagen** se
   muestra con una URL firmada de vida corta que verifica nginx sin preguntar a
   la aplicación, porque un `<img src>` no puede enviar la cabecera
   `Authorization` y autenticarla por cabecera obligaría a renunciar a toda la
   tubería nativa de imágenes del navegador. Un **documento** —factura,
   garantía— sigue bajando por el endpoint autorizado, que comprueba el hogar en
   cada petición. La firma cubre ruta y caducidad, y esta se iguala a la del
   access token, unos quince minutos.

El avatar de una identidad queda **fuera** de este mecanismo. Una `Identity` no
pertenece a ningún hogar, así que no tiene cuota a la que sumar ni política de
RLS que la cubra: es un único fichero sustituible en columnas de `identities`,
con tope propio de 1 MB.

## Alternativas consideradas

- **Guardar los binarios en PostgreSQL (`bytea` o *large objects*):** es la única
  alternativa en la que RLS cubre también los bytes, y la que deja una sola copia
  de seguridad y una sola transacción. Se descarta porque infla los volcados a
  decenas de gigabytes con 1 GB por hogar, convierte cada restauración en un
  evento largo y castiga la memoria del servidor por servir lo que no es más que
  un fichero estático.
- **S3 autoalojado en la misma máquina (MinIO, Garage):** da la API de objetos
  desde el primer día, y con ella la posibilidad de salir del VPS cambiando un
  endpoint. Se descarta porque paga otro proceso residente, otro juego de
  credenciales y otra superficie de ataque para acabar escribiendo en el mismo
  disco. La portabilidad que aportaba se conserva con el puerto `FileStorage`,
  sin el coste operativo.
- **Sustituir el enlace externo en lugar de convivir con él:** dejaría un solo
  camino y un modelo más simple, pero obligaría a descargar y volver a subir
  facturas que ya viven en un correo, y a perder los manuales que el fabricante
  publica y mantiene.
- **Mantener el estado actual, solo enlaces:** no cuesta nada y no resuelve nada.
  Deja fuera del sistema todo lo que existe únicamente en papel.

Para la entrega de imágenes se consideraron además:

- **Descargar con `fetch()` autenticado y pintar desde un blob:** conserva la
  autorización por petición y no pone ninguna credencial en una URL, que es lo
  más limpio en el papel. Se descarta por tres costes que se pagan en cada
  pantalla: un preflight CORS **por cada URL** —la caché de preflight se indexa
  por URL, así que un listado de doscientas miniaturas son doscientos—, cada
  imagen retenida en memoria hasta revocarla a mano, y la pérdida de
  `loading="lazy"`, `srcset`, caché y renderizado progresivo. Además el `blob:`
  hereda el origen del documento que lo crea, de modo que los bytes que se
  habían apartado a otro dominio vuelven al de la aplicación.
- **Cookie acotada al dominio de ficheros:** funciona con `<img>` nativo y
  permite revocación inmediata si se valida contra la aplicación. Se descarta
  porque exige `SameSite=None` —restringido por los navegadores desde hace
  años—, lo que en la práctica obliga a que ficheros y aplicación compartan
  dominio registrable y degrada el aislamiento a solo separación de origen;
  porque reintroduce superficie de CSRF en un sistema hoy inmune por
  construcción al ser JWT en cabecera; y porque crea un segundo ciclo de sesión
  que hay que cerrar en otro dominio al hacer logout.

## Consecuencias

### Positivas

- El hogar deja de depender de un servicio externo para su documentación.
- La recodificación de imágenes **elimina los metadatos EXIF**, y con ellos las
  coordenadas GPS que un móvil incrusta en cada foto hecha dentro de casa. Es una
  ganancia de privacidad que el modelo de enlaces no ofrecía.
- Los metadatos en base de datos hacen la cuota calculable de un solo sitio y
  auditables las subidas, con `createdBy` como el resto del core.
- El puerto `FileStorage` deja abierta la migración a almacenamiento de objetos
  sin tocar el dominio ni los casos de uso.

### Costes y riesgos

- **El sistema de ficheros no tiene Row-Level Security.** La segunda capa que
  exige la [ADR-003](ADR-003-row-level-security.md) protege la fila, no el byte.
  La mitigación es que la ruta se deriva siempre de una fila ya filtrada por la
  política, nunca de un dato del cliente; cualquier atajo que construya rutas con
  entrada del usuario desactiva esa herencia sin producir ningún error visible.
- **No se deduplica contenido entre hogares**, precisamente para no compartir
  rutas y no romper esa herencia. Dos hogares con el mismo manual guardan dos
  copias.
- **Base de datos y disco pueden divergir.** Se acota escribiendo siempre los
  bytes antes que la fila, marcando el borrado en lugar de ejecutarlo, y dejando
  el desenlace a un proceso diario (`PurgeUnusedFiles`).
- **La cuota por hogar no protege el servidor.** Con el alta en autoservicio
  abierto, el número de hogares no está acotado y la suma de las cuotas supera
  cualquier disco razonable. Exige dos controles de operación —volumen propio y
  techo global sobre la ocupación— descritos en 5.8.2 del README.
- **Las copias de seguridad dejan de ser una sola cosa.** Hay que respaldar el
  volcado y el árbol de ficheros, en ese orden, y una restauración que los mezcle
  de momentos distintos deja referencias rotas.
- **La autorización de una imagen se comprueba al emitir la URL, no al
  descargarla.** Sacar a alguien de un hogar no invalida las que ya tuviera en
  pantalla: sirven hasta caducar. No es una clase de exposición nueva —su access
  token tampoco se revoca antes de expirar— pero sí un punto donde la
  comprobación por petición deja de aplicarse.
- **La firma viaja en la URL, y las URL se registran.** nginx escribe la cadena
  de consulta en el log de acceso por defecto, así que **desactivarlo en el
  `location` de ficheros es condición de la decisión, no una mejora**: sin eso,
  el propio log pasa a contener credenciales válidas con retención larga. Se
  completa con `Referrer-Policy: no-referrer`.
- **Sin análisis antivirus** en esta primera versión, anotado como pendiente con
  motivo en 4.1.7 del README.

## Validación o reversión

Se considera validada cuando el recorrido vertical de la
[ADR-001](ADR-001-solution-architecture-baseline.md) incluya subir una foto desde
el frontend, verla adjunta a un asset y descargarla, y cuando pasen las tres
pruebas que caracterizan la decisión: que un fichero que miente sobre su tipo se
rechace por su contenido real, que una imagen con EXIF se almacene sin él, y que
dos reservas simultáneas contra el final de la cuota dejen pasar una sola.

La entrega firmada añade dos comprobaciones propias: que una URL con la
caducidad manipulada se rechace, y que el log de acceso no contenga ninguna
cadena de consulta.

Revertir a enlaces externos es viable mientras `url` siga admitiéndose, que es
siempre. Cambiar el almacén por S3 u otro backend no requiere una ADR nueva si se
respeta el puerto `FileStorage`; sí la requiere volver a meter los binarios en la
base de datos, porque cambia el modelo de copia de seguridad y el de aislamiento.
