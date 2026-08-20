# Seguridad transversal

Este directorio recoge la seguridad que **cruza componentes** —backend, frontend,
infraestructura y despliegue a la vez— y por eso no cabe en
[`docs/backend/security/`](../../backend/security/README.md), que documenta los
controles internos del backend.

## Documentos vigentes

- [`owasp-audit.md`](owasp-audit.md): la auditoría de seguridad previa al
  despliegue en una VPS, contrastando el código y la configuración reales contra
  las [OWASP Cheat Sheets](https://cheatsheetseries.owasp.org/). Es una foto del
  estado a 2026-08-20 (106 operaciones, 31 tablas): veredicto, hallazgos por
  severidad, requisitos del despliegue y fortalezas verificadas. Sin
  vulnerabilidades críticas en el código actual.

## Relación con la seguridad del backend

Cada componente documenta sus propios controles; este directorio conserva la
mirada de conjunto. Los controles de subida de ficheros, por ejemplo, viven en
[`backend/security/file-upload-controls.md`](../../backend/security/file-upload-controls.md);
la auditoría de más arriba los cita y señala dónde el código diverge de lo
documentado, sin duplicarlos.
