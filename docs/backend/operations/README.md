# Operación del backend

Este directorio documentará cómo ejecutar y mantener el backend en cada entorno.

## Documentos vigentes

- [`storage-sizing-and-backups.md`](storage-sizing-and-backups.md): por qué la
  cuota por hogar no protege al servidor y qué hace falta además —techo global y
  sobrecompromiso medido—, más el orden en que hay que copiar base de datos y
  ficheros para que una restauración no deje filas apuntando a bytes que no
  están. Trasladado desde las secciones 5.8.2 y 5.8.6 del
  [`README principal`](../../../README.md).

## Contenido previsto

- Requisitos y arranque local reproducible.
- Configuración por entorno y gestión de secretos.
- Build, empaquetado, despliegue y rollback.
- Health checks, logs estructurados, métricas y trazas.
- Migraciones durante despliegues.
- Objetivos de servicio y alertas.
- Runbooks para fallos conocidos, backup y restauración.

Los comandos deben ser ejecutables, indicar precondiciones y evitar valores
sensibles en ejemplos o salidas.
