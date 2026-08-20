# Seguridad del backend

Este directorio mantiene el modelo de amenazas y los controles aplicados dentro
del backend. Los aspectos observables por clientes se enlazan desde los contratos
comunes.

## Documentos vigentes

- [`file-upload-controls.md`](file-upload-controls.md): los controles de la OWASP
  File Upload Cheat Sheet que se aplican desde el primer día — lista blanca por
  contenido real, renombrado en disco, volumen `noexec` fuera del árbol web y
  recodificación de imágenes. Trasladado desde la sección 5.8.5 del
  [`README principal`](../../../README.md). El mecanismo que los rodea está en
  [`../architecture/file-storage.md`](../architecture/file-storage.md).

## Seguridad transversal

La auditoría OWASP que cruza backend, frontend, infraestructura y despliegue vive
en [`docs/common/security/owasp-audit.md`](../../common/security/owasp-audit.md),
porque no es solo de backend. Ahí están los hallazgos por severidad y los
requisitos del despliegue en la VPS; aquí, los controles internos.

## Contenido previsto

- Límites de confianza y superficies de ataque.
- Identidad, autenticación, autorización y sesiones o tokens.
- Validación de entrada y codificación de salida.
- Gestión de secretos y configuración sensible.
- Protección de datos en tránsito y en reposo.
- Auditoría, trazabilidad y respuesta a incidentes.
- Análisis de dependencias y proceso de actualización.
- Casos de abuso y pruebas de seguridad.

Nunca se almacenarán credenciales reales ni datos personales en esta documentación.
