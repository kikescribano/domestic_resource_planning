# Componentes

| Campo | Valor |
|---|---|
| Estado | Borrador |
| Responsable | Equipo DRP |
| Ámbito | frontend |
| Última revisión | 2026-08-12 |

## Propósito

Documentar los componentes reutilizables de la interfaz: qué hacen, qué API
exponen, cómo se comportan al estrecharse la pantalla y qué se les puede exigir
con el teclado y con un lector de pantalla.

Estas fichas **describen lo que el código hace hoy**, no lo que convendría que
hiciera. Cuando el Hito 2 necesita algo que la primitiva todavía no tiene, va en
un apartado propio de «Lo que falta» y se marca como previsto, en lugar de
escribirse como si existiera. Esa distinción es el único motivo por el que estas
fichas sirven de algo: la documentación del hito anterior falló exactamente por
ahí.

El índice de cada ficha es la **[ficha mínima de un componente](../README.md)**,
con sus siete puntos. No se reordena ni se recorta.

## Alcance

### Incluido

- Una ficha por componente que existe en
  [`frontend/src/ui/primitives.tsx`](../../../../frontend/src/ui/primitives.tsx).
- El estado de implementación de cada uno y el hueco que deja para el Hito 2.

### Fuera de alcance

- Las decisiones visuales que un componente consume sin decidir —color,
  tipografía, espacio, densidad, forma, iconografía y movimiento—, en
  [`foundations/`](../foundations/README.md).
- Los nombres y valores de los tokens, en [`tokens/`](../tokens/README.md).
- La composición de varios componentes en una vista —listado, jerarquía,
  formulario, feedback y navegación—, en [`patterns/`](../patterns/README.md).
- Terminología, voz y etiquetas, que irán en `content/`.

## Registro

| Componente | Ficha | Estado | Dónde vive |
|---|---|---|---|
| `Button` | [`button.md`](button.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `Field` | [`field.md`](field.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `Notice` | [`notice.md`](notice.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `StatusBadge` | [`status-badge.md`](status-badge.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `Spinner` | [`spinner.md`](spinner.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |
| `AuthCard` | [`card.md`](card.md) | **Implementado** | [`primitives.tsx`](../../../../frontend/src/ui/primitives.tsx) |

Los seis viven en un único fichero de 266 líneas. No hay un directorio por
componente, ni una carpeta de historias, ni una galería: mientras quepan en un
fichero que se lee de una sentada, partirlos añadiría estructura sin resolver
nada.

## Lo que el Hito 2 necesita y todavía no existe

Todos estos están **previstos**: ninguno tiene implementación hoy. Se anotan aquí
para que la ficha correspondiente no se escriba antes de tiempo.

| Componente previsto | Por qué lo pide el Hito 2 |
|---|---|
| `Select` | Categoría, unidad, tipo de ubicación y propietario son desplegables. Hoy el único `select` de la aplicación está escrito a mano dentro de [`household.tsx`](../../../../frontend/src/routes/household.tsx), repitiendo la maquetación de `Field` sin ser `Field` |
| `Combobox` | El artículo se busca antes de dar entrada a un consumible: `GET /articles` lleva un parámetro `q` que existe justamente para alimentar un autocompletado |
| `Skeleton` | La primera carga de una vista se pinta con la forma real del contenido, no con un `Spinner`. Es lo que fija [`look-and-feel.md`](../../product-design/look-and-feel.md) y lo que la primitiva de carga actual no puede hacer |
| `Toast` | El aviso efímero de esquina, con cierre y con deshacer. `Notice` es un aviso **en el sitio donde ocurrió**, que es otra cosa |
| `Dialog` y hoja inferior | La confirmación de baja y las operaciones de existencias, que en móvil son hoja y en escritorio diálogo |
| `EmptyState` | Los tres vacíos —inicial, por filtro y por permiso— con su ilustración y su acción |
| `Pagination` | Las colecciones del contrato paginan todas igual; hoy no hay ningún control que lo pinte |

Los patrones que los usan sí están escritos, y dicen en cada punto qué pieza
falta: ver [`patterns/`](../patterns/README.md).

## Reglas que ningún componente puede saltarse

Son las mismas de [`tokens/`](../tokens/README.md) y de
[`look-and-feel.md`](../../product-design/look-and-feel.md), recordadas aquí
porque es donde se incumplen:

1. **Nada de colores crudos.** Si falta un token, se añade antes de usarlo.
2. **El foco no se declara en un componente.** Está en la capa base de
   [`index.css`](../../../../frontend/src/index.css), para que ninguno pueda
   olvidarlo — ni quitarlo.
3. **Nada se dice solo con color.** Un estado lleva color, icono y etiqueta; un
   campo con error lleva borde, icono y mensaje.
4. **44 px de objetivo táctil** en todo lo pulsable (`min-h-touch`).
5. **Una acción principal por pantalla.** Solo `variant="primary"` pinta el
   relleno de acento.
6. **La serif no entra en una fila de listado.**

## Decisiones abiertas

- **`lucide-react` no está en
  [`frontend/package.json`](../../../../frontend/package.json).**
  [`iconography.md`](../foundations/iconography.md) adopta Lucide como juego de
  iconos, pero los cuatro iconos que hay hoy están dibujados a mano dentro de
  `primitives.tsx`, siguiendo su geometría —cuadrícula de 24, trazo 1,75— sin ser
  Lucide. Los cinco iconos de estado del dominio que el Hito 2 necesita son la
  ocasión de dar de alta la dependencia o de decidir que no se da.
- **Dónde vive el segundo componente.** Cuando `primitives.tsx` deje de leerse de
  una sentada habrá que partirlo, y conviene decidir el criterio antes de que la
  decisión la tome el tamaño del fichero.

## Referencias

- [`../README.md`](../README.md): la ficha mínima de un componente.
- [`foundations/`](../foundations/README.md) y [`tokens/`](../tokens/README.md).
- [`accessibility/`](../../accessibility/README.md): la auditoría de contraste y
  lo que todavía no está comprobado sobre pantallas reales.
- [`ADR-006`](../../../common/architecture/decisions/ADR-006-frontend-stack-and-design-system.md)

## Historial de cambios

| Fecha | Cambio | Autor |
|---|---|---|
| 2026-08-12 | Creación del directorio con las seis fichas de los componentes que existen, y el registro de los que el Hito 2 va a pedir. | Equipo DRP |
