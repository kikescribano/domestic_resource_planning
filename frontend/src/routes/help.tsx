import {
  Bell,
  Blocks,
  BookOpen,
  Boxes,
  CircleHelp,
  CircleUserRound,
  HardDrive,
  Handshake,
  House,
  MapPin,
  Settings,
  ShoppingCart,
  Store,
  Users,
  Warehouse,
  Wrench,
  type LucideIcon,
} from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'

import { EmptyState, Field, PageHeading } from '../ui/primitives'

/**
 * La guía de la herramienta, pantalla a pantalla.
 *
 * Cada pantalla es un bloque con tres cosas: qué hace, un caso de uso que
 * enseña qué problema resuelve, y el enlace para ir. El contenido es estático
 * a propósito —no pide nada a la API—, así que la pantalla sirve igual con el
 * hogar recién creado que con años de histórico.
 *
 * El buscador filtra bloques enteros: escribir «caducidad» deja a la vista
 * solo las pantallas cuyo texto la menciona. La comparación es la misma que la
 * del catálogo —sin mayúsculas ni acentos—, para que «prestamo» encuentre
 * «Préstamos».
 */

interface HelpTopic {
  title: string
  path: string
  icon: LucideIcon
  /** Qué es y cómo se usa la pantalla. */
  description: string
  /** Una situación concreta que la pantalla resuelve. */
  useCase: string
}

/**
 * Un bloque por pantalla, en el orden de la navegación —Tu hogar, Datos
 * maestros y Configuración, con sus paradas tal y como se enseñan— y al final
 * «Cuenta», que no es una parada de la lista porque acompaña a la marca. Los
 * títulos y los iconos son los del menú: la ayuda no estrena nombres.
 *
 * Los cuatro módulos están siempre, aunque el hogar los tenga apagados: la
 * ayuda es también el escaparate de lo que se puede encender, y cada bloque de
 * módulo dice dónde se enciende.
 *
 * **La contrapartida de una guía escrita a mano es que caduca.** La regla de
 * alineación y su registro de deuda viven en la ficha de esta pantalla,
 * `docs/frontend/design-system/components/help-page.md`: un cambio sustantivo
 * en una pantalla actualiza su bloque aquí en el mismo cambio, o deja una fila
 * apuntada allí para alinear varios de una vez.
 */
const HELP_TOPICS: HelpTopic[] = [
  {
    title: 'Hogar',
    path: '/',
    icon: House,
    description:
      'La portada tras entrar. Recuerda por dónde empezar —primero Ubicaciones, con la vivienda y lo que hay dentro; luego el Catálogo de lo que sueles tener en casa; y con eso, dar de alta algo en el Inventario es elegir de una lista— y enseña tu papel en el hogar y cómo se llama.',
    useCase:
      'Acabas de crear el hogar y no sabes por dónde empezar: la portada te encadena los tres primeros pasos para que el inventario nazca ya ordenado.',
  },
  {
    title: 'Avisos',
    path: '/avisos',
    icon: Bell,
    description:
      'La bandeja de lo que el sistema encuentra por su cuenta en su pasada diaria: préstamos vencidos, caducidades próximas, mínimos por debajo, revisiones que tocan. Arranca en lo que falta por ver, con el histórico a un clic. Los avisos son del hogar, no de cada persona: marcar uno como leído lo marca para todos. Y el día que hay algo nuevo llega además un resumen por correo — ninguno los días en que no hay nada.',
    useCase:
      'Un yogur caduca pasado mañana y nadie ha abierto la nevera con esa pregunta en la cabeza: el aviso aparece solo, sin que nadie tenga que revisar fechas.',
  },
  {
    title: 'Inventario',
    path: '/inventario',
    icon: Boxes,
    description:
      'Todo lo material del hogar, con filtros por tipo, estado de conservación y etiqueta. Hay dos naturalezas: los duraderos (una ficha por unidad: el taladro, la bici) se dan de alta uno a uno, y son los únicos que se prestan o que hacen de sitio para otras cosas; los consumibles (lo que se agota y se repone: azúcar, pilas) no se dan de alta sino que se les da entrada, sumando a lo que ya haya en esa ubicación. La ficha de cada cosa guarda fotos, documentos y etiquetas —y en los duraderos, número de serie, fecha de compra y estado de conservación—, y desde ella se mueve, se corrige una cantidad o se da de baja.',
    useCase:
      'Vuelves de la compra con otro paquete de azúcar: en lugar de crear nada, le das entrada y la cantidad se suma a la que ya había en la despensa.',
  },
  {
    title: 'Préstamos',
    path: '/prestamos',
    icon: Handshake,
    description:
      'A quién le has dejado qué y para cuándo lo tiene que devolver. Solo se prestan los duraderos: ceder un consumible es un ajuste de cantidad, no un préstamo. Cada préstamo pasa por Prestado, Vencido —lo marca la pasada diaria del sistema, sin que nadie repase fechas— y Devuelto. Quien lo recibe no necesita cuenta: le llega un enlace por correo con su copia de las condiciones.',
    useCase:
      'Le dejas la hidrolimpiadora a un vecino y a los tres meses nadie se acuerda: aquí consta desde el primer día quién la tiene, y el vencimiento avisa solo.',
  },
  {
    title: 'Mantenimiento',
    path: '/mantenimiento',
    icon: Wrench,
    description:
      'Qué hay que revisar, cada cuánto y qué se hizo la última vez: planes con su próxima fecha (la caldera, el coche, los filtros) e intervenciones apuntadas con su coste. Cuando una revisión vence, aparece en Avisos. Es un módulo: si no está en el menú, se enciende en «Módulos del hogar», dentro de Configuración.',
    useCase:
      'La revisión anual de la caldera se olvida todos los años hasta que hace frío: con un plan anual, la fecha la vigila el sistema y no tu memoria.',
  },
  {
    title: 'Compras',
    path: '/compras',
    icon: ShoppingCart,
    description:
      'Qué falta, qué está pedido y qué acaba de entrar en casa. La lista se alimenta a mano o desde los mínimos del almacén, y recibir una compra da entrada en el inventario sin apuntar nada dos veces. Es un módulo: si no está en el menú, se enciende en «Módulos del hogar», dentro de Configuración.',
    useCase:
      'Dos personas van al supermercado por separado y vuelven con dos botes de lo mismo: con la lista compartida, lo pedido consta como pedido y no se duplica.',
  },
  {
    title: 'Almacén',
    path: '/almacen',
    icon: Warehouse,
    description:
      'El control fino de los consumibles: cuánto queda de cada cosa, qué está bajo mínimo y qué caduca pronto. Apuntar un consumo descuenta del contador real del inventario —no hay dos cantidades—. Es un módulo: si no está en el menú, se enciende en «Módulos del hogar», dentro de Configuración.',
    useCase:
      'Quedan dos cápsulas de café y nadie lo sabía hasta el desayuno: con un mínimo puesto, el almacén lo detecta antes y lo convierte en aviso.',
  },
  {
    title: 'Personas',
    path: '/usuarios',
    icon: Users,
    description:
      'Quién forma parte del hogar y con qué papel. Desde aquí se invita por correo a nuevos miembros, se cambian roles y cada cual pone su avatar. Los administradores gestionan; el resto usa.',
    useCase:
      'Tu pareja quiere apuntar la compra sin pasar por ti: la invitas por correo y entra con su propia cuenta, con lo del hogar compartido y sus credenciales suyas.',
  },
  {
    title: 'Catálogo',
    path: '/catalogo',
    icon: BookOpen,
    description:
      'El dato maestro del hogar: las categorías (con su icono y su color, elegidos dentro de un juego con contraste garantizado) y los artículos, que son la ficha de qué es algo —nombre, categoría, unidad, marca, código de barras—. Un artículo no ocupa sitio ni tiene cantidad: eso es del inventario. Definirlo una vez evita repetir sus datos en cada existencia.',
    useCase:
      'Compras siempre el mismo detergente: su artículo guarda una sola vez el nombre y la unidad, y cada entrada nueva en el inventario lo reutiliza sin reescribir nada.',
  },
  {
    title: 'Ubicaciones',
    path: '/ubicaciones',
    icon: MapPin,
    description:
      'El árbol de sitios del hogar: viviendas, habitaciones, muebles, cajas. Cada cosa del inventario cuelga de un sitio, así que este árbol es lo que responde a «¿dónde está?». Admite varias viviendas como raíces, y un duradero (un armario, un estuche) puede actuar él mismo de ubicación.',
    useCase:
      'Guardas los adornos de Navidad en una caja del trastero: cuando llegue diciembre no habrá que abrir cajas al azar, el inventario dice en cuál están.',
  },
  {
    title: 'Proveedores',
    path: '/proveedores',
    icon: Store,
    description:
      'La agenda de a quién se llama: fontanero, electricista, seguro, cerrajero. Cada contacto con su categoría y sus datos, con buscador, y los que ya no se usan se retiran sin perder el histórico. Es un módulo: si no está en el menú, se enciende en «Módulos del hogar», dentro de Configuración.',
    useCase:
      'Revienta una tubería un domingo: el teléfono del fontanero que ya conoce la casa está aquí, no en el móvil de quien no está.',
  },
  {
    title: 'Archivo',
    path: '/almacenamiento',
    icon: HardDrive,
    description:
      'El espacio de ficheros del hogar: cuánto se ha usado del gigabyte disponible, qué lo ocupa —ordenado por tamaño— y un filtro para ver solo los ficheros que ya no cuelgan de nada, que son los primeros candidatos a borrar.',
    useCase:
      'El medidor se acerca al tope y no sabes qué borrar: el filtro de huérfanos y el orden por tamaño te enseñan directamente lo que más libera con menos pérdida.',
  },
  {
    title: 'General',
    path: '/configuracion',
    icon: Settings,
    description:
      'La configuración que afecta al hogar entero, y solo para quien administra: quien no administra ni la ve en el menú. Hoy contiene la baja del hogar. Pedirla exige confirmación escrita y abre treinta días de gracia en los que todo sigue funcionando y un aviso con la fecha se ve en todas las pantallas; cancelarla es un botón sin fricción, porque arrepentirse no se castiga. Pasada la gracia, el borrado es definitivo.',
    useCase:
      'El hogar se disuelve —una mudanza, una separación—: pides la baja sabiendo que hay un mes entero para echarse atrás sin perder nada.',
  },
  {
    title: 'Módulos del hogar',
    path: '/modulos',
    icon: Blocks,
    description:
      'El interruptor de las piezas opcionales: cada hogar enciende solo los módulos que le sirven —Proveedores, Almacén, Compras, Mantenimiento— y sus pantallas aparecen en el menú, cada una en el grupo al que su contenido pertenece. Encender y apagar es cosa de administradores, y apagar no borra nada: los datos esperan a que se vuelva a encender.',
    useCase:
      'El hogar empieza pequeño y las pantallas de más estorban: se arranca con el core pelado y se enciende Almacén el día que la despensa lo pide.',
  },
  {
    title: 'Cuenta',
    path: '/cuenta',
    icon: CircleUserRound,
    description:
      'Lo tuyo, no lo del hogar: tus datos personales, tu contraseña y, si llega el caso, el cierre de tu cuenta. No es una parada del menú: se llega desde «Cuenta», junto a la marca —en móvil, en el apartado que cierra «Más»—. Lo que hiciste en el hogar se conserva; lo que te identifica, no.',
    useCase:
      'Sospechas que tu contraseña anda comprometida: la cambias aquí y las sesiones abiertas en otros sitios dejan de valer.',
  },
]

// La misma comparación que el catálogo: sin mayúsculas ni acentos, para que
// «prestamo» encuentre «Préstamos» y «almacen» encuentre «Almacén».
const normalized = (text: string) => text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

function matches(topic: HelpTopic, query: string) {
  const haystack = normalized([topic.title, topic.description, topic.useCase].join(' '))
  return haystack.includes(normalized(query.trim()))
}

export function HelpPage() {
  const [query, setQuery] = useState('')

  const visible = HELP_TOPICS.filter((topic) => matches(topic, query))

  return (
    <>
      <PageHeading title="Ayuda" icon={CircleHelp} />

      <div className="flex flex-col gap-6">
        <p className="max-w-prose text-body text-ink-muted">
          Qué hace cada pantalla de la herramienta y qué problema resuelve. Cada bloque lleva su enlace para ir
          directamente.
        </p>

        <Field
          label="Buscar"
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          hint="Filtra los bloques. No distingue mayúsculas ni acentos."
        />

        {visible.length === 0 ? (
          <EmptyState title="Ningún bloque coincide">Prueba con otras palabras.</EmptyState>
        ) : (
          <ul className="flex flex-col gap-3">
            {visible.map((topic) => (
              <HelpCard key={topic.path} topic={topic} />
            ))}
          </ul>
        )}
      </div>
    </>
  )
}

function HelpCard({ topic }: { topic: HelpTopic }) {
  const Icon = topic.icon

  return (
    <li>
      {/* `article` con su `aria-labelledby`: cada bloque es una pieza con
          nombre, y un lector de pantalla puede saltar de una a otra. */}
      <article
        aria-labelledby={`ayuda-${topic.path}`}
        className="flex flex-col gap-3 rounded-lg border border-border-subtle bg-surface-raised p-4"
      >
        <header className="flex flex-wrap items-center justify-between gap-3">
          <h2 id={`ayuda-${topic.path}`} className="flex items-center gap-2 text-title-sm text-ink">
            <Icon size={20} strokeWidth={1.75} aria-hidden="true" className="shrink-0 text-accent-ink" />
            {topic.title}
          </h2>
          {/* El nombre accesible dice adónde va: quince enlaces que digan
              solo «Abrir» son indistinguibles en una lista de enlaces. */}
          <Link
            to={topic.path}
            className="min-h-touch inline-flex items-center rounded-md px-2 text-body-sm font-medium text-accent-ink hover:underline"
          >
            Ir a {topic.title}
          </Link>
        </header>

        <p className="max-w-prose text-body-sm text-ink-muted">{topic.description}</p>

        <p className="max-w-prose text-body-sm text-ink-muted">
          <span className="font-medium text-ink">Cuándo te sirve: </span>
          {topic.useCase}
        </p>
      </article>
    </li>
  )
}
