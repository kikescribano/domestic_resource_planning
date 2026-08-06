# Integración con la API

Este espacio explica cómo el frontend consume los
[`contratos compartidos`](../../common/contracts/README.md), sin redefinirlos.

## Contenido previsto

- Generación o mantenimiento del cliente HTTP.
- Adaptación de DTOs a modelos de dominio y aplicación.
- Autenticación, renovación y cierre de sesión desde el cliente.
- Caché, invalidación, reintentos y cancelación.
- Errores de red, contrato, autorización y validación.
- Paginación, filtrado y ordenación.
- Telemetría y correlación sin exponer datos sensibles.
- Mocks contractuales y estrategia de pruebas de integración.

La interfaz no debe depender directamente de formas de transporte fuera de los
adaptadores definidos por la arquitectura del frontend.
