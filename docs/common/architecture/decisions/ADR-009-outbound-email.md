# ADR-009: Envío de correo saliente

- Estado: accepted
- Fecha: 2026-08-10
- Responsables: Equipo DRP
- Ámbito: backend
- Sustituye: Ninguna

## Contexto

El enrolamiento definido en la Fase 0 descansa entero sobre el correo, y sin
embargo la definición no dice nada de cómo sale. Son tres flujos y tres tokens
con caducidades ya fijadas:

- **Verificación de correo**, obligatoria: hasta que no se consume el token no
  hay sesión, y `PurgeUnverifiedHouseholds` borra a los siete días el hogar que
  no se verificó.
- **Invitación** a un hogar existente, con token de siete días. Es la única vía
  de alta desde que se descartó el alta directa.
- **Restablecimiento de contraseña**, con token de una hora.

Sin envío no hay alta, no hay invitación y no hay recuperación: el recorrido
vertical de la [ADR-001](ADR-001-solution-architecture-baseline.md) no llega ni a
empezar. Y un correo que no sale no produce ningún error visible en la
aplicación, porque las respuestas de esos tres endpoints son **constantes por
diseño** —siempre `202`, exista el destinatario o no— para no revelar qué correos
están registrados.

## Decisión

Un puerto **`EmailSender`** en la capa de aplicación, con **adaptador SMTP**
sobre Spring Mail. Es deliberadamente la misma jugada que la
[ADR-005](ADR-005-local-file-storage.md) hizo con `FileStorage`: la decisión que
se aplaza —qué proveedor— queda detrás de una frontera, y cambiarla es escribir
un segundo adaptador, no tocar los casos de uso.

- En **desarrollo y en las pruebas**, **Mailpit** levantado por `compose.yaml`:
  captura todo lo que se envía y lo expone por API, que es como el recorrido
  vertical lee el enlace de verificación.
- En **producción**, el SMTP del proveedor que se elija al desplegar. La elección
  no exige tocar código ni volver a esta decisión.
- El envío ocurre **fuera de la transacción** del caso de uso. Un fallo al
  entregar el correo no deshace el alta de un hogar ni la creación de una
  invitación: el token ya está persistido y sigue siendo válido.
- La respuesta al cliente **no depende del resultado del envío**, para no
  reintroducir por el lado del tiempo de respuesta la fuga de información que las
  respuestas constantes evitan.

## Alternativas consideradas

- **Proveedor SaaS por API HTTP (Resend, Brevo, Mailgun):** mejor entregabilidad,
  métricas de apertura y rebote, y menos configuración de servidor. Se descarta
  como punto de partida porque ata la Fase 1 a una cuenta externa y a un dominio
  verificado antes de poder probar un simple alta de hogar, y porque el puerto
  deja esa puerta abierta: cuando la entregabilidad importe, es un adaptador más.
- **Servidor SMTP propio:** control total y ningún tercero. Se descarta por la
  cantidad de trabajo de reputación —SPF, DKIM, DMARC, IP limpia— que exige antes
  de que un correo llegue de forma fiable a una bandeja de entrada.
- **Aplazar el envío real:** registrar los tokens en el log y exponerlos en un
  endpoint solo de desarrollo. Desbloquearía el enrolamiento sin infraestructura,
  pero deja fuera del recorrido vertical un paso obligatorio del flujo real, y el
  endpoint de conveniencia es exactamente la clase de atajo que acaba desplegado.

## Consecuencias

### Positivas

- El enrolamiento se puede desarrollar y probar de punta a punta desde el primer
  día, sin cuentas externas ni dominios verificados.
- Elegir proveedor deja de bloquear la Fase 1 y pasa a ser configuración de
  despliegue, junto a la decisión del VPS.
- SMTP es el mínimo común denominador: prácticamente cualquier proveedor lo
  ofrece, así que la elección posterior no queda estrechada.

### Costes y riesgos

- SMTP no da métricas de entregabilidad ni de rebote. Mientras no haya adaptador
  de API, no se sabrá si un correo llegó, solo si se entregó al servidor.
- Un correo perdido se traduce en un hogar que se purga a los siete días sin que
  nadie se entere. `ResendVerification` existe precisamente para eso, y conviene
  que la interfaz lo ofrezca de forma visible.
- Enviar fuera de la transacción implica que puede haber token persistido sin
  correo enviado. Es el compromiso correcto —lo contrario sería perder el alta por
  un fallo de red ajeno— pero hay que registrarlo para poder diagnosticarlo.

## Validación o reversión

Se considera validada cuando el recorrido vertical cree un hogar, lea el correo
de verificación desde Mailpit, consuma el token e inicie sesión, sin ningún paso
manual; y cuando una prueba confirme que `POST /api/v1/households` responde igual
—mismo código, mismo cuerpo y sin diferencia de tiempo apreciable— con un correo
nuevo y con uno ya registrado.

Revisar cuando la entregabilidad pase a importar, es decir, en el primer
despliegue con usuarios reales: ahí toca elegir proveedor y, si se quiere
telemetría de entrega, escribir el adaptador de API que esta decisión deja
preparado.
