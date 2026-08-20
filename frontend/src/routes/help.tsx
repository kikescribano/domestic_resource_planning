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
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'

import { EmptyState, Field, PageHeading } from '../ui/primitives'

/**
 * La guía de la herramienta, pantalla a pantalla.
 *
 * Cada pantalla es una **sección** con su cabecera —icono, nombre y el enlace
 * para ir— y debajo sus **tarjetas**: una con la explicación general —qué es y
 * cómo se usa— y una por cada caso de uso, con su ejemplo práctico en
 * situación doméstica. El contenido es estático a propósito —no pide nada a la
 * API—, así que la pantalla sirve igual con el hogar recién creado que con
 * años de histórico.
 *
 * El buscador filtra **tarjeta a tarjeta**, no secciones enteras: escribir
 * «caducidad» deja a la vista solo las tarjetas que la mencionan, cada una
 * bajo la cabecera de su pantalla; una sección sin ninguna tarjeta que
 * coincida desaparece. El nombre de la pantalla cuenta para todas sus
 * tarjetas, así que buscar «préstamos» enseña la sección entera. La
 * comparación es la misma que la del catálogo —sin mayúsculas ni acentos—,
 * para que «prestamo» encuentre «Préstamos».
 */

interface HelpUseCase {
  /** El nombre de la funcionalidad, tal y como se contaría a alguien. */
  title: string
  /** Qué hace y cómo se usa. */
  description: string
  /** Un ejemplo práctico, en situación doméstica reconocible. */
  example: string
}

interface HelpTopic {
  title: string
  path: string
  icon: LucideIcon
  /** La explicación general: qué es la pantalla y qué papel juega. */
  overview: string
  /** Un caso por funcionalidad que la pantalla cubre. */
  useCases: HelpUseCase[]
}

/**
 * Una sección por pantalla, en el orden de la navegación —Tu hogar, Datos
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
    overview:
      'La portada tras entrar. Recuerda por dónde empezar, enseña tu papel en el hogar y cómo se llama, y mientras dure una baja en gracia, el aviso con su fecha se ve aquí y en todas las demás pantallas.',
    useCases: [
      {
        title: 'Arrancar con buen pie',
        description:
          'La portada encadena los tres primeros pasos en su orden: primero Ubicaciones —la vivienda y lo que hay dentro—, luego el Catálogo de lo que sueles tener, y con eso dar de alta en el Inventario es elegir de una lista.',
        example:
          'Acabas de crear el hogar y no sabes por dónde empezar: sigues los tres enlaces en orden y el inventario nace ya ordenado.',
      },
      {
        title: 'Saber cuál es tu papel',
        description:
          'Dice si administras el hogar o eres miembro, que es lo que decide qué puedes tocar: encender módulos, invitar o la baja del hogar son de administración.',
        example:
          'No encuentras «General» en el menú: aquí ves que eres miembro, y ya sabes que eso es de quien administra.',
      },
    ],
  },
  {
    title: 'Avisos',
    path: '/avisos',
    icon: Bell,
    overview:
      'La bandeja de lo que el sistema encuentra por su cuenta en su pasada diaria: préstamos vencidos, caducidades próximas, mínimos por debajo, revisiones que tocan. Los avisos son del hogar, no de cada persona.',
    useCases: [
      {
        title: 'Ponerse al día',
        description:
          'La bandeja arranca en «Sin leer»: lo que la pasada diaria ha encontrado y nadie ha atendido todavía, sin tener que filtrar nada.',
        example:
          'Entras con el café en la mano y ves de un vistazo qué encontró el sistema esta noche: un préstamo vencido y dos caducidades.',
      },
      {
        title: 'Dar un aviso por atendido',
        description:
          'Marcar uno como leído lo tacha para todo el hogar, no solo para ti: atendido por uno es atendido por todos.',
        example:
          'Tiraste el yogur caducado y marcas su aviso: a tu pareja ya no le aparece pendiente esta tarde.',
      },
      {
        title: 'Repasar el histórico',
        description: 'El filtro «Todos» enseña también lo ya leído, con quién lo leyó.',
        example:
          'Dudáis de si lo de la caldera avisó el mes pasado: el histórico lo resuelve sin discusión.',
      },
      {
        title: 'Enterarse sin entrar',
        description:
          'El día que la pasada encuentra algo nuevo llega además un resumen por correo — ninguno los días en que no hay nada.',
        example:
          'Sin abrir la aplicación en toda la semana, el correo del jueves te avisa de que algo caduca: los otros días, silencio.',
      },
    ],
  },
  {
    title: 'Inventario',
    path: '/inventario',
    icon: Boxes,
    overview:
      'Todo lo material del hogar. Hay dos naturalezas que se comportan distinto: los duraderos (una ficha por unidad: el taladro, la bici) son los únicos que se prestan o que hacen de sitio para otras cosas; los consumibles (lo que se agota y se repone: azúcar, pilas) se llevan por cantidad en cada ubicación.',
    useCases: [
      {
        title: 'Encontrar algo',
        description: 'El listado filtra por tipo, estado de conservación y etiqueta.',
        example:
          'La víspera de la acampada filtras por la etiqueta «camping» y sale todo el equipo, esté en el trastero o en el altillo.',
      },
      {
        title: 'Dar de alta un duradero',
        description:
          'Una ficha por unidad física, con su ubicación y su propietario; después se completa con número de serie, fecha de compra y estado de conservación.',
        example:
          'Estrenas taladro: lo das de alta en el garaje y apuntas el número de serie por si la garantía lo pide.',
      },
      {
        title: 'Dar entrada a un consumible',
        description:
          'Traer más de algo no crea nada: la entrada resuelve el artículo —creándolo si hace falta— y suma sobre la cantidad que ya haya en esa ubicación.',
        example:
          'Vuelves de la compra con otro paquete de azúcar: le das entrada y la despensa pasa de uno a dos, sin fichas duplicadas.',
      },
      {
        title: 'Documentar una cosa',
        description: 'La ficha guarda fotos y documentos adjuntos, que descuentan del espacio del hogar.',
        example:
          'Al taladro le adjuntas la factura y una foto: cuando falle en garantía, todo está en su ficha y no en un cajón.',
      },
      {
        title: 'Mantener el orden al día',
        description:
          'Desde la ficha se mueve de sitio, se corrige una cantidad tras un recuento —la corrección sustituye, no suma— y dos existencias del mismo artículo se unen en una, eligiendo qué ubicación y qué propietario sobreviven.',
        example:
          'Aparecen dos paquetes de arroz abiertos en dos baldas: los unes y queda una sola existencia con la cantidad real.',
      },
      {
        title: 'Dar de baja',
        description: 'Lo roto, regalado o gastado se da de baja; la ficha no se borra, deja de contar.',
        example:
          'La tostadora se quema: la das de baja y deja de aparecer, pero su histórico —cuándo llegó, qué costó— se conserva.',
      },
    ],
  },
  {
    title: 'Préstamos',
    path: '/prestamos',
    icon: Handshake,
    overview:
      'A quién le has dejado qué y para cuándo lo tiene que devolver. Solo se prestan los duraderos: ceder un consumible es un ajuste de cantidad, no un préstamo. Cada préstamo pasa por Prestado, Vencido y Devuelto.',
    useCases: [
      {
        title: 'Registrar un préstamo',
        description: 'Qué se presta, a quién y hasta cuándo; desde entonces consta y deja de depender de la memoria.',
        example:
          'Le dejas la hidrolimpiadora a un vecino: queda apuntado el día y la fecha de vuelta, y no habrá que reconstruirlo de memoria en marzo.',
      },
      {
        title: 'Darle su copia a quien recibe',
        description:
          'Quien recibe el préstamo no necesita cuenta: le llega un enlace por correo con su copia de las condiciones, que puede consultar cuando quiera.',
        example:
          'El vecino no se acuerda de la fecha: abre su enlace y la ve, sin llamarte y sin instalarse nada.',
      },
      {
        title: 'Detectar vencidos sin vigilar',
        description:
          'La pasada diaria del sistema marca Vencido lo que pasa de fecha y lo convierte en aviso: nadie repasa fechas a mano.',
        example:
          'A los tres meses nadie se acordaba de la hidrolimpiadora: el aviso de vencimiento salta solo.',
      },
      {
        title: 'Confirmar la devolución',
        description:
          'Un préstamo sigue contando como prestado hasta que confirmas la devolución; al confirmarla, la cosa queda libre para prestarse otra vez.',
        example:
          'Vuelve la escalera: confirmas la devolución y desaparece de los pendientes — hasta el siguiente vecino.',
      },
    ],
  },
  {
    title: 'Mantenimiento',
    path: '/mantenimiento',
    icon: Wrench,
    overview:
      'Qué hay que revisar, cada cuánto y qué se hizo la última vez. Es un módulo: si no está en el menú, se enciende en «Módulos del hogar», dentro de Configuración.',
    useCases: [
      {
        title: 'Planificar una revisión periódica',
        description: 'Un plan con su intervalo y su próxima fecha, atado a lo que se revisa.',
        example:
          'La revisión anual de la caldera se olvida todos los años hasta que hace frío: con el plan, la fecha la vigila el sistema y no tu memoria.',
      },
      {
        title: 'Apuntar una intervención',
        description: 'Qué se hizo, cuándo y cuánto costó; el plan recalcula su próxima fecha a partir de ahí.',
        example:
          'Pasa el técnico del coche: apuntas la intervención con su coste y la siguiente queda para dentro de un año.',
      },
      {
        title: 'Saber qué toca, y que avise',
        description:
          'La pantalla ordena por próxima fecha, y lo que vence aparece en Avisos sin que nadie mire el calendario.',
        example:
          'A primeros de mes echas un vistazo a lo que viene; y si no lo echas, el aviso de la caldera salta igual.',
      },
    ],
  },
  {
    title: 'Compras',
    path: '/compras',
    icon: ShoppingCart,
    overview:
      'Qué falta, qué está pedido y qué acaba de entrar en casa. No enseña existencias —eso es del Almacén—. Es un módulo: si no está en el menú, se enciende en «Módulos del hogar», dentro de Configuración.',
    useCases: [
      {
        title: 'Apuntar lo que falta',
        description: 'La lista compartida del hogar, alimentada a mano en el momento en que algo se acaba.',
        example: 'Gastas el aceite cocinando: lo apuntas ahí mismo y quien pase por la tienda lo ve.',
      },
      {
        title: 'Alimentarse de los mínimos',
        description: 'Lo que el Almacén detecta bajo mínimo puede pasar a la lista sin teclearlo.',
        example: 'El café baja de su mínimo: aparece en la lista de la compra sin que nadie lo apunte.',
      },
      {
        title: 'No comprar dos veces',
        description: 'Marcar algo como pedido lo saca de «falta» sin sacarlo de la vista.',
        example:
          'Dos personas compran por separado el mismo día: el segundo ve que los botes ya están pedidos y no vuelve con otros dos.',
      },
      {
        title: 'Recibir la compra',
        description:
          'Recibir da entrada en el inventario: las cantidades suben en su ubicación sin apuntar nada dos veces.',
        example:
          'Vuelves del supermercado, confirmas la recepción y la despensa queda al día sin pasar por el inventario a mano.',
      },
    ],
  },
  {
    title: 'Almacén',
    path: '/almacen',
    icon: Warehouse,
    overview:
      'El control fino de los consumibles: cuánto queda, qué está bajo mínimo y qué caduca pronto. La cantidad es la del inventario —no hay dos contadores—. Es un módulo: si no está en el menú, se enciende en «Módulos del hogar», dentro de Configuración.',
    useCases: [
      {
        title: 'Apuntar un consumo',
        description: 'Gastar algo se apunta aquí y descuenta del contador real del inventario.',
        example: 'Cocinas un paquete de pasta: lo apuntas y la despensa dice la verdad esta misma noche.',
      },
      {
        title: 'Poner mínimos',
        description:
          'Cada artículo puede llevar su mínimo; al bajar de él salta un aviso, y con Compras encendido, pasa a la lista.',
        example:
          'Pones el café a un mínimo de diez cápsulas: el desayuno sin café deja de pasar, porque el aviso llega antes.',
      },
      {
        title: 'Vigilar caducidades',
        description: 'Lo que caduca pronto se ve junto, y la pasada diaria lo convierte en aviso a tiempo.',
        example:
          'Los yogures de la segunda balda caducan esta semana: lo dice el almacén el lunes, no la nariz el domingo.',
      },
    ],
  },
  {
    title: 'Personas',
    path: '/usuarios',
    icon: Users,
    overview:
      'Quién forma parte del hogar y con qué papel: administradores que gestionan y miembros que usan. Cada persona entra con su propia cuenta; lo compartido es el hogar, no las credenciales.',
    useCases: [
      {
        title: 'Invitar a alguien',
        description:
          'La invitación sale por correo con el papel ya elegido —administración o miembro—, y quien la acepta entra con su propia cuenta.',
        example:
          'Tu pareja quiere apuntar la compra sin pasar por ti: la invitas como miembro y en dos minutos entra con su correo.',
      },
      {
        title: 'Ver quién es qué',
        description: 'El listado enseña a cada miembro con su papel a la vista, sin adivinar.',
        example: 'Hay que encender un módulo y no sabes a quién pedírselo: el distintivo de administración lo dice.',
      },
      {
        title: 'Seguir una invitación pendiente',
        description: 'Las invitaciones enviadas y aún no aceptadas se ven con su papel, para saber qué falta.',
        example: 'Invitaste a tu hija el martes y no aparece: la invitación sigue pendiente, y lo ves sin preguntarle.',
      },
    ],
  },
  {
    title: 'Catálogo',
    path: '/catalogo',
    icon: BookOpen,
    overview:
      'El dato maestro del hogar: las categorías y los artículos, que son la ficha de qué es algo. Un artículo no ocupa sitio ni tiene cantidad —eso es del inventario—: definirlo una vez evita repetir sus datos en cada existencia.',
    useCases: [
      {
        title: 'Dar identidad a las categorías',
        description:
          'Cada categoría lleva icono y color elegidos dentro de un juego con contraste garantizado: se distingue de un vistazo en cualquier listado, también en modo oscuro.',
        example:
          'Pones la limpieza en verde con su icono: en un inventario de cientos de filas, lo suyo se localiza sin leer.',
      },
      {
        title: 'Definir un artículo una vez',
        description:
          'Nombre, categoría, unidad y, si los hay, marca y código de barras. La unidad la fija el artículo: todas sus existencias se llevan en ella.',
        example:
          'Defines «Detergente» con su marca una vez: cada compra posterior lo reutiliza y no vuelves a teclearlo.',
      },
      {
        title: 'Encontrarlo entre cientos',
        description: 'El buscador de artículos no distingue mayúsculas ni acentos.',
        example: 'Escribes «cafe» sin tilde y salen el molido, las cápsulas y el de tu suegra.',
      },
      {
        title: 'Retirar lo que ya no se compra',
        description: 'Un artículo retirado deja de ofrecerse, pero el histórico que lo menciona se conserva.',
        example:
          'Aquel suavizante descatalogado: lo retiras y no vuelve a aparecer al dar entradas, sin romper lo ya registrado.',
      },
    ],
  },
  {
    title: 'Ubicaciones',
    path: '/ubicaciones',
    icon: MapPin,
    overview:
      'El árbol de sitios del hogar: viviendas, habitaciones, muebles, cajas. Cada cosa del inventario cuelga de un sitio, así que este árbol es lo que responde a «¿dónde está?».',
    useCases: [
      {
        title: 'Levantar el mapa de la casa',
        description:
          'Se construye el árbol con el tipo y el icono de cada sitio, y admite varias viviendas como raíces.',
        example:
          'Vivienda, cocina, despensa, tercera balda — y el trastero del garaje como segunda raíz, porque no cuelga del piso.',
      },
      {
        title: 'Reorganizar sin perder nada',
        description:
          'Un sitio se edita y se mueve a otro padre; lo que contiene viaja con él, y el árbol impide moverlo dentro de sí mismo.',
        example:
          'Reordenas el trastero y mueves la estantería entera bajo la pared del fondo: sus cajas y lo de dentro se van con ella.',
      },
      {
        title: 'Un mueble que guarda',
        description: 'Un duradero puede actuar él mismo de ubicación y tener cosas dentro.',
        example: 'El armario ropero es un asset y a la vez el sitio donde vive la ropa de esquí.',
      },
      {
        title: 'Declarar cuánto cabe',
        description:
          'Un sitio puede declarar su capacidad máxima; al moverle algo que lo llena, la aplicación avisa sin impedirlo.',
        example:
          'La caja de decoración tiene un máximo declarado: al meter una guirnalda más, el aviso te lo dice antes de que no cierre.',
      },
    ],
  },
  {
    title: 'Proveedores',
    path: '/proveedores',
    icon: Store,
    overview:
      'La agenda de a quién se llama: fontanero, electricista, seguro, cerrajero. Es un módulo: si no está en el menú, se enciende en «Módulos del hogar», dentro de Configuración.',
    useCases: [
      {
        title: 'Tener el contacto a mano',
        description: 'Cada contacto con su categoría y sus datos, del hogar y no del móvil de una sola persona.',
        example:
          'Revienta una tubería un domingo: el fontanero que ya conoce la casa está aquí, aunque quien lo llamó la otra vez esté de viaje.',
      },
      {
        title: 'Encontrarlo rápido',
        description: 'El buscador filtra la agenda al teclear.',
        example: 'Tecleas «cerra» y sale el cerrajero antes de terminar la palabra.',
      },
      {
        title: 'Retirar sin borrar',
        description: 'Un contacto que ya no se usa se retira: deja de estorbar y el histórico se conserva.',
        example:
          'Cambias de compañía de seguros: la antigua queda retirada por si hay que reclamar algo del año pasado.',
      },
    ],
  },
  {
    title: 'Archivo',
    path: '/almacenamiento',
    icon: HardDrive,
    overview:
      'El espacio de ficheros del hogar: el gigabyte compartido donde viven las fotos y los documentos de las fichas, y lo que se suba directamente.',
    useCases: [
      {
        title: 'Saber cuánto queda',
        description: 'El medidor de cuota dice cuánto se ha usado y cuánto queda del espacio del hogar.',
        example: 'Antes de subir el vídeo del contador del agua, el medidor te dice si cabe.',
      },
      {
        title: 'Guardar un fichero suelto',
        description: 'Se puede subir directamente, sin pasar por la ficha de ninguna cosa.',
        example: 'El manual de la caldera en PDF, subido una vez y localizable para todos.',
      },
      {
        title: 'Liberar espacio con criterio',
        description:
          'La rejilla ordena por tamaño y un filtro deja solo los ficheros que no cuelgan de nada: los primeros candidatos a borrar.',
        example:
          'El medidor se acerca al tope: dos vídeos huérfanos al principio de la lista liberan más que cien fotos pequeñas.',
      },
    ],
  },
  {
    title: 'General',
    path: '/configuracion',
    icon: Settings,
    overview:
      'La configuración que afecta al hogar entero, y solo para quien administra: quien no administra ni la ve en el menú. Hoy contiene la baja del hogar.',
    useCases: [
      {
        title: 'Pedir la baja del hogar',
        description:
          'Exige confirmación escrita y abre treinta días de gracia en los que todo sigue funcionando; el aviso con la fecha se ve en todas las pantallas y lo ven todos los miembros. Pasada la gracia, el borrado es definitivo.',
        example:
          'El hogar se disuelve por una mudanza: pides la baja sabiendo que hay un mes entero de margen y que nadie se enterará por sorpresa.',
      },
      {
        title: 'Arrepentirse a tiempo',
        description:
          'Cancelar la baja durante la gracia es un botón normal, sin fricción: deshacer algo destructivo no se castiga.',
        example:
          'A los diez días la mudanza se cae: cancelas la baja y el hogar sigue como si nada, con todo dentro.',
      },
    ],
  },
  {
    title: 'Módulos del hogar',
    path: '/modulos',
    icon: Blocks,
    overview:
      'El interruptor de las piezas opcionales: Proveedores, Almacén, Compras y Mantenimiento. Cada hogar enciende solo lo que le sirve, y las pantallas encendidas aparecen en el menú, cada una en el grupo al que su contenido pertenece. Encender y apagar es cosa de administradores.',
    useCases: [
      {
        title: 'Encender lo que hace falta',
        description: 'Un interruptor por módulo; al encenderlo, su pantalla aparece en la navegación de todos.',
        example:
          'El hogar empezó con el core pelado y la despensa ha crecido: enciendes Almacén y aparece en «Tu hogar».',
      },
      {
        title: 'Apagar sin miedo',
        description: 'Apagar no borra nada: la pantalla desaparece del menú y los datos esperan a la vuelta.',
        example:
          'Probasteis Compras un mes y no cuajó: lo apagas, y si en Navidad vuelve a hacer falta, la lista sigue donde estaba.',
      },
    ],
  },
  {
    title: 'Cuenta',
    path: '/cuenta',
    icon: CircleUserRound,
    overview:
      'Lo tuyo, no lo del hogar: tus datos, tu avatar, tu contraseña y, si llega el caso, el cierre de tu cuenta. No es una parada del menú: se llega desde «Cuenta», junto a la marca —en móvil, en el apartado que cierra «Más»—.',
    useCases: [
      {
        title: 'Poner cara a tu nombre',
        description:
          'El avatar se sube aquí y te acompaña por toda la aplicación; admite también las fotos HEIC del iPhone, que se convierten solas.',
        example: 'Subes la foto tal cual sale del móvil y en Personas ya se te distingue de un vistazo.',
      },
      {
        title: 'Cambiar la contraseña',
        description: 'Al cambiarla se cierran tus otras sesiones; la que estás usando se queda abierta.',
        example:
          'Sospechas que tu contraseña anda comprometida: la cambias y cualquier sesión abierta en otro sitio deja de valer.',
      },
      {
        title: 'Cerrar tu cuenta',
        description:
          'El cierre borra lo que te identifica; lo que hiciste en el hogar se conserva, atribuido al hogar y no a ti.',
        example:
          'Dejas el piso compartido: cierras tu cuenta y el inventario común sigue entero para los que se quedan.',
      },
    ],
  },
]

// La misma comparación que el catálogo: sin mayúsculas ni acentos, para que
// «prestamo» encuentre «Préstamos» y «almacen» encuentre «Almacén».
const normalized = (text: string) => text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

/**
 * Una tarjeta ya aplanada para pintar: la general lleva `example` a null. El
 * `id` ancla el `aria-labelledby` del `article` y tiene que ser único en la
 * página, así que sale de la ruta más el índice del caso.
 */
interface HelpCardData {
  id: string
  title: string
  body: string
  example: string | null
}

/**
 * El nombre de la pantalla cuenta en el pajar de TODAS sus tarjetas: buscar
 * «préstamos» tiene que enseñar la sección entera, no obligar a que cada caso
 * repita la palabra. La aguja llega YA normalizada.
 */
function cardsOf(topic: HelpTopic, needle: string): HelpCardData[] {
  const cards: HelpCardData[] = [
    { id: `ayuda-${topic.path}-general`, title: 'Explicación general', body: topic.overview, example: null },
    ...topic.useCases.map((useCase, index) => ({
      id: `ayuda-${topic.path}-${index}`,
      title: useCase.title,
      body: useCase.description,
      example: useCase.example,
    })),
  ]
  if (!needle) return cards

  return cards.filter((card) =>
    normalized([topic.title, card.title, card.body, card.example ?? ''].join(' ')).includes(needle),
  )
}

/**
 * Envuelve en `<mark>` cada coincidencia con la aguja ya normalizada. La
 * coincidencia se busca sobre el texto normalizado y el tramo se recorta del
 * original —«prestamo» resalta «préstamo» entero, tilde incluida—, y para eso
 * hace falta el mapa de índices: normalizar puede comerse caracteres (los
 * diacríticos descompuestos), y sin mapa el recorte bailaría.
 *
 * El color fue primero la pareja del `::selection` —`accent-soft` con la
 * tinta normal— y no sobrevivió a mirarlo: sobre una tarjeta en modo oscuro
 * apenas se distinguía. El resalte usa la pareja ámbar `warning-soft` +
 * `warning` —la del distintivo de «Prestado», medida en check-contrast.py—,
 * que en los dos modos hace lo que un resalte tiene que hacer: verse antes de
 * leerse. El `font-medium` acompaña para que el color no sea el único
 * portador.
 */
function highlightMatches(text: string, needle: string): ReactNode {
  if (!needle) return text

  const map: number[] = []
  let haystack = ''
  for (let i = 0; i < text.length; i++) {
    for (const piece of normalized(text.charAt(i))) {
      haystack += piece
      map.push(i)
    }
  }

  const parts: ReactNode[] = []
  let cursor = 0
  let at = haystack.indexOf(needle)
  while (at !== -1) {
    const start = map[at]
    const last = map[at + needle.length - 1]
    // No puede pasar --el pajar y el mapa se construyen a la vez--, pero el
    // acceso indexado no lo sabe, y romper el bucle es mejor que afirmarlo.
    if (start === undefined || last === undefined) break
    const end = last + 1
    if (start > cursor) parts.push(text.slice(cursor, start))
    parts.push(
      <mark key={`${start}-${end}`} className="rounded-sm bg-warning-soft font-medium text-warning">
        {text.slice(start, end)}
      </mark>,
    )
    cursor = end
    at = haystack.indexOf(needle, at + needle.length)
  }
  if (cursor < text.length) parts.push(text.slice(cursor))
  return parts
}

export function HelpPage() {
  const [query, setQuery] = useState('')

  const needle = normalized(query.trim())
  const sections = HELP_TOPICS.map((topic) => ({ topic, cards: cardsOf(topic, needle) })).filter(
    ({ cards }) => cards.length > 0,
  )

  return (
    <>
      <PageHeading title="Ayuda" icon={CircleHelp} />

      <div className="flex flex-col gap-8">
        <div className="flex flex-col gap-6">
          <p className="max-w-prose text-body text-ink-muted">
            Qué hace cada pantalla de la herramienta y qué problema resuelve, caso a caso y con su ejemplo. Cada
            sección lleva su enlace para ir directamente.
          </p>

          <Field
            label="Buscar"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            hint="Filtra tarjeta a tarjeta, casos de uso incluidos. No distingue mayúsculas ni acentos."
          />
        </div>

        {sections.length === 0 ? (
          <EmptyState title="Ninguna tarjeta coincide">Prueba con otras palabras.</EmptyState>
        ) : (
          sections.map(({ topic, cards }) => (
            <HelpSection key={topic.path} topic={topic} cards={cards} needle={needle} />
          ))
        )}
      </div>
    </>
  )
}

function HelpSection({ topic, cards, needle }: { topic: HelpTopic; cards: HelpCardData[]; needle: string }) {
  const Icon = topic.icon

  return (
    <section aria-labelledby={`ayuda-${topic.path}`} className="flex flex-col gap-3">
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

      {/* Dos columnas desde `md`: con una tarjeta por caso, la columna única
          hacía la página un pergamino en escritorio. La general abre la
          sección a todo el ancho, que es su jerarquía. */}
      <ul className="grid gap-3 md:grid-cols-2">
        {cards.map((card) => (
          <HelpCard key={card.id} card={card} spanFull={card.example === null} needle={needle} />
        ))}
      </ul>
    </section>
  )
}

function HelpCard({ card, spanFull, needle }: { card: HelpCardData; spanFull: boolean; needle: string }) {
  return (
    <li className={spanFull ? 'md:col-span-2' : ''}>
      {/* `article` con su `aria-labelledby`: cada tarjeta es una pieza con
          nombre —el del caso de uso—, y un lector de pantalla salta de una a
          otra. El `<mark>` no cambia el nombre accesible: el texto es el
          mismo, resaltado. El `h-full` iguala las tarjetas de una misma fila
          de la rejilla. */}
      <article
        aria-labelledby={card.id}
        className="flex h-full flex-col gap-1.5 rounded-lg border border-border-subtle bg-surface-raised p-4"
      >
        <h3 id={card.id} className="text-body-sm font-medium text-ink">
          {highlightMatches(card.title, needle)}
        </h3>
        <p className="max-w-prose text-body-sm text-ink-muted">{highlightMatches(card.body, needle)}</p>
        {card.example && (
          <p className="max-w-prose text-body-sm text-ink-muted">
            <span className="italic">Ejemplo:</span> {highlightMatches(card.example, needle)}
          </p>
        )}
      </article>
    </li>
  )
}
