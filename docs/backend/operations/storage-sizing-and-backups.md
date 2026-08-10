# 5.8.2 La cuota por hogar no protege al servidor

| Campo | Valor |
|---|---|
| Estado | Vigente |
| Responsable | Equipo DRP |
| Ámbito | Dimensionado del volumen y copias de seguridad |
| Última revisión | 2026-08-10 |

> Trasladado desde las secciones 5.8.2 y 5.8.6 del [`README principal`](../../../README.md) al iniciar la Fase 1. **Los números de sección se conservan**: hay más de cien referencias cruzadas del tipo «ver 4.1.1» repartidas por el repositorio, y renumerarlas las rompería todas.

El gigabyte por hogar impide que **uno** se quede el disco, que es para lo que está. No impide que se lo queden **todos**: con el alta en autoservicio abierto (4.1.7) el número de hogares no está acotado, y unas decenas llenando su cuota agotan cualquier VPS razonable. Hacen falta dos controles más, que son de operación y no de dominio:

- **Un techo global sobre el volumen**, que rechaza subidas nuevas al superar un umbral —del orden del 90 %— aunque al hogar le sobre cuota. Una subida rechazada siempre es mejor noticia que un volumen al 100 %.
- **Sobrecompromiso consciente y medido**: la suma de las cuotas puede superar el tamaño del volumen, apostando a que el uso real es una fracción, pero eso exige métrica de ocupación y alerta. Sin medirlo, no es una apuesta sino un descuido.

Hay un atenuante que ya estaba en el diseño: no se puede subir nada sin correo verificado, y los hogares sin verificar se purgan a los siete días (`PurgeUnverifiedHouseholds`), así que dar de alta hogares en masa no regala almacenamiento.


## 5.8.6 Copias de seguridad

Con los ficheros fuera de la base de datos, una restauración puede dejar filas apuntando a bytes que no están, o bytes que ya no referencia nadie. Lo segundo es inofensivo —el barrendero diario los recoge—; lo primero es un error duro, y el orden lo evita: **primero el volcado de la base de datos, después el árbol de ficheros**, con la ventana de purga configurada más larga que la ventana de copia. Como los bytes se escriben antes que la fila (5.8.3), todo lo que el volcado referencia ya estaba en disco.

Una instantánea del VPS completo resuelve el problema por otra vía, capturando ambos en el mismo instante, y es lo que da la copia diaria automática incluida en el plan. Conviene tener las dos: la instantánea protege del desastre y el volcado lógico, del error humano.
