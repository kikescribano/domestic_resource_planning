# 5.8.5 Controles OWASP aplicados

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Controles sobre los ficheros subidos |
| Última revisión | 2026-08-10 |

> Trasladado desde la sección 5.8.5 del [`README principal`](../../../README.md) al iniciar la Fase 1. **Los números de sección se conservan**: hay más de cien referencias cruzadas del tipo «ver 4.1.1» repartidas por el repositorio, y renumerarlas las rompería todas.

Las medidas anteriores no son invención propia: son la [File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html) de OWASP y el capítulo V12 de ASVS, aterrizados en este diseño.

| Control | Dónde se aplica aquí |
|---|---|
| Lista blanca de tipos, nunca lista negra | 5.8.3, paso 4. Cuatro tipos admitidos; todo lo demás se rechaza |
| Validar el contenido, no la extensión ni lo declarado | 5.8.3, paso 4. El `contentType` que se guarda es el detectado |
| Renombrar el fichero en disco | 5.8.1. La ruta sale del `fileId`; el nombre original es un dato, no una ruta. Cierra de una vez el path traversal, los bytes nulos y las dobles extensiones (`x.pdf.php`) |
| Guardar fuera del árbol web, sin permiso de ejecución | 5.8.1. Volumen propio con `noexec` |
| Limitar tamaño y frecuencia | 25 MB por fichero, 1 GB por hogar (4.1.1) y límite de frecuencia por identidad en el endpoint de subida |
| Neutralizar el contenido activo | 5.8.3. SVG excluido, imágenes recodificadas, PDF servido siempre como adjunto |
| Servir desde otro origen y como adjunto | 5.8.4. Dominio distinto, `Content-Disposition: attachment`, `nosniff` y CSP restrictiva |
| Identificadores no adivinables | UUID v4 en la ruta y en la API, nunca un contador |
| Autorizar el acceso a cada fichero | 5.8.4. En un documento, comprobando el hogar **en cada petición**; en una imagen, **al emitir** la URL firmada, que caduca en quince minutos |
| No exponer credenciales de sesión en la URL (ASVS) | La firma **no es un token de sesión**: es una autorización de solo lectura sobre un único objeto, con caducidad, que no da acceso a la cuenta ni a ningún otro fichero. Aun así no se registra en ningún log, ni se filtra por `Referer` (5.8.4) |
| Registrar quién sube qué | `createdBy` y `sizeBytes` en la fila (4.1.1) |
| No registrar credenciales | 5.8.4. La cadena de consulta queda fuera del log de acceso de nginx y de las trazas de la aplicación |
