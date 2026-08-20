/**
 * Genera DRP-comercial.pptx, la presentación comercial del proyecto.
 *
 * Este script es la fuente editable del .pptx: la presentación no se retoca a
 * mano, se regenera. Procedimiento y verificación en
 * ../../skills/SKILL-001-readme-to-deck.md.
 *
 *   npm install pptxgenjs
 *   node build-drp-comercial.js ../presentations/DRP-comercial.pptx
 *
 * Es la hermana comercial de build-drp-resumen.js y comparte con ella paleta,
 * tipografías y lienzo, para que las dos piezas se reconozcan como del mismo
 * producto. Cambian el destinatario y, con él, la selección: aquí no entra nada
 * de las secciones 5, 6 y 7 del README —arquitectura, stack y testing— salvo la
 * diapositiva de confianza, escrita en llano. Va dirigida a quien no conoce el
 * proyecto, no lee el repositorio y no quiere tecnicismos: inversión,
 * colaboradores y primeros hogares piloto.
 *
 * PROCEDENCIA: refleja el estado del README a **2026-08-20**, verificado contra
 * el repositorio ese mismo día:
 *
 *   - Fase: **Fases 1 y 2 completadas** (2026-08-17 y 2026-08-19) y el **cierre
 *     de huecos completado** (2026-08-20); la Fase 3 está pendiente y **sin
 *     planificar**.
 *   - ADR: **quince** (ADR-001 a ADR-015).
 *   - Operaciones del contrato: **106** (`grep -c operationId: openapi.yaml`).
 *   - Módulos construidos: **cuatro de trece** (proveedores, warehouse, compras
 *     y mantenimiento), todos en estado «En desarrollo» porque no hay despliegue.
 *
 * El cierre de huecos **no entra en el deck**, y la decisión se tomó cuando esa
 * fila apareció en la sección 8 y se mantiene ahora que está completada: no es
 * una fase —lo dice ella misma, y por eso no renumera nada—, de modo que las
 * fases siguen siendo cuatro con tres cerradas y las diapositivas de estado
 * siguen siendo ciertas tal cual. Es saldo de deuda interna, no avance de
 * producto, y queda por debajo de la altura a la que habla esta presentación.
 *
 * Esos cuatro son los datos que más rápido caducan y **hay que repasarlos cada
 * vez**: este fichero no falla ni avisa cuando se queda atrás, simplemente sigue
 * generando un deck impecable que ya no es cierto. Le pasó a su hermano durante
 * nueve días.
 *
 * RESTRICCIÓN DE HONESTIDAD, que aquí no es un matiz de estilo: **no hay ningún
 * despliegue, ni hogares reales, ni nadie usando esto**. La presentación no
 * puede decir que está en producción, ni inventar clientes, precios, cuota de
 * mercado, testimonios ni métricas de negocio. Si una cifra no está en el
 * repositorio, no aparece. La escena de la diapositiva «Un día con DRP» va
 * marcada como ilustrativa por ese motivo.
 *
 * LICENCIA DE LA DIRECCIÓN VISUAL: la retícula, la jerarquía tipográfica, el
 * ritmo de portada y cierre, la declaración grande y la línea de tiempo están
 * tomadas como **decisiones** de la plantilla «Pitch Deck Minitheme» de Slidesgo
 * (references/pitch-deck-minitheme-slidesgo.pptx), no copiadas: su lienzo es de
 * 10 × 5,62" contra los 13,3" de este, así que lo que se pegara tal cual se
 * escribiría fuera de pantalla. Se descargó con cuenta gratuita, y esa licencia
 * **exige conservar la diapositiva de agradecimiento**: es la número 16 y **no
 * se retira** mientras la presentación salga fuera.
 *
 * CONTACTO: la última diapositiva deja el bloque de contacto sin rellenar a
 * propósito. Se completa **aquí**, en el script, antes de cada envío.
 */
const pptxgen = require("pptxgenjs");

// ── Paleta: la misma que build-drp-resumen.js ─────────────────────────────────
const INK    = "12312F"; // pino profundo (fondos oscuros, titulares)
const INK2   = "1C4644"; // pino medio
const TEAL   = "2E7B72"; // primario
const TEALLT = "7FC8BE"; // teal claro (sobre oscuro)
const TERRA  = "C25A32"; // acento terracota
const TINT   = "EFF4F2"; // relleno de tarjeta claro
const TINT2  = "E3EDEA"; // relleno de tarjeta claro alternativo
const SAND   = "F7E9E2"; // relleno cálido, para lo que hay que leer sí o sí
const BODY   = "24302F";
const MUTED  = "5F706E";
const WHITE  = "FFFFFF";
const DEEP   = "163A38"; // tarjeta sobre fondo oscuro
const DEEP2  = "1B4B48"; // tarjeta sobre fondo oscuro, un punto más clara
const EDGE   = "2F5754"; // borde sobre fondo oscuro
const PALE   = "C4D4D1"; // texto secundario sobre fondo oscuro
const FAINT  = "7E9895"; // texto terciario sobre fondo oscuro
const ONTEAL = "EDF5F3"; // texto sobre relleno TEAL: 4,52:1, el minimo de AA

// Aquí no hay código en ninguna diapositiva, así que no hay tipografía
// monoespaciada: es la única pieza del sistema del deck de resumen que no viaja.
const SERIF = "Cambria";
const SANS  = "Calibri";

const W = 13.333, H = 7.5, M = 0.6, CW = W - 2 * M; // 12.133

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE";
pres.author = "DRP";
pres.title = "DRP · Domestic Resource Planning — presentación comercial";

// ── Helpers ───────────────────────────────────────────────────────────────────
// pptxgenjs muta los objetos de opciones al usarlos, así que cada helper
// construye el suyo y nunca se comparte uno entre dos llamadas.
const sh = (o = {}) => ({ type: "outer", color: "0B1F1E", blur: 10, offset: 2, angle: 90, opacity: 0.10, ...o });

function card(slide, o) {
  slide.addShape(pres.ShapeType.roundRect, {
    x: o.x, y: o.y, w: o.w, h: o.h,
    fill: { color: o.fill || TINT },
    line: o.line ? { color: o.line, width: o.lineW || 1 } : { color: o.fill || TINT, width: 0.5 },
    rectRadius: o.r === undefined ? 0.09 : o.r,
    rotate: o.rotate,
    shadow: o.shadow === false ? undefined : sh(o.shadowOpts),
  });
}

function circle(slide, x, y, d, fill) {
  slide.addShape(pres.ShapeType.ellipse, { x, y, w: d, h: d, fill: { color: fill }, line: { color: fill, width: 0.5 } });
}

function badge(slide, x, y, d, label, fill, color, size) {
  circle(slide, x, y, d, fill);
  slide.addText(label, {
    x, y, w: d, h: d, align: "center", valign: "middle", margin: 0,
    fontFace: SANS, fontSize: size || (d >= 0.55 ? 15 : 12), bold: true, color: color || WHITE,
  });
}

// Etiqueta corta con forma de píldora. Es el motivo que se repite en todo el
// deck: estado de un módulo, oleada, distintivo de una capacidad.
function pill(slide, o) {
  slide.addShape(pres.ShapeType.roundRect, {
    x: o.x, y: o.y, w: o.w, h: o.h, rectRadius: o.h / 2,
    fill: { color: o.fill }, line: { color: o.line || o.fill, width: 0.75 },
  });
  slide.addText(o.text, {
    x: o.x, y: o.y, w: o.w, h: o.h, margin: 0, align: "center", valign: "middle",
    fontFace: SANS, fontSize: o.size || 10, bold: true,
    charSpacing: o.spacing === undefined ? 0.8 : o.spacing,
    color: o.color || WHITE,
  });
}

function head(slide, kicker, title, dark) {
  slide.addText(kicker, {
    x: M, y: 0.42, w: CW, h: 0.26, margin: 0, align: "left", valign: "middle",
    fontFace: SANS, fontSize: 11, bold: true, charSpacing: 2, color: dark ? TEALLT : TEAL,
  });
  slide.addText(title, {
    x: M, y: 0.72, w: CW, h: 0.62, margin: 0, align: "left", valign: "middle",
    fontFace: SERIF, fontSize: 32, bold: true, color: dark ? WHITE : INK,
  });
}

let pageNo = 0;
function foot(slide, dark) {
  pageNo += 1;
  slide.addText("DRP · Domestic Resource Planning", {
    x: M, y: 6.98, w: 6, h: 0.28, margin: 0, valign: "middle",
    fontFace: SANS, fontSize: 9, color: dark ? "6E8C88" : "93A3A0",
  });
  slide.addText(String(pageNo), {
    x: W - M - 1.2, y: 6.98, w: 1.2, h: 0.28, margin: 0, align: "right", valign: "middle",
    fontFace: SANS, fontSize: 9, color: dark ? "6E8C88" : "93A3A0",
  });
}

function newSlide(dark) {
  const s = pres.addSlide();
  s.background = { color: dark ? INK : WHITE };
  return s;
}

function lines(items, o = {}) {
  return items.map((t, i) => ({
    text: t,
    options: { breakLine: i < items.length - 1, bullet: o.bullet ? { code: "2022" } : false, ...o.opts },
  }));
}

// Eco de la palabra: la plantilla de referencia apila el título repetido en
// bandas. Aquí se usa dos veces —portada y cierre— y en ningún sitio más, que
// es lo que lo mantiene como marca de apertura y no como adorno.
function echo(slide, x, y, w, text, size, color, align) {
  slide.addText(text, {
    x, y, w, h: 0.44, margin: 0, valign: "middle", align: align || "left",
    fontFace: SERIF, fontSize: size, bold: true, charSpacing: 3, color,
  });
}

// ═══ 1 · Portada ══════════════════════════════════════════════════════════════
{
  const s = newSlide(true);

  echo(s, 0.92, 0.86, 6.4, "DRP · DRP · DRP · DRP", 26, "1A423F");
  s.addText("DRP", { x: 0.9, y: 1.32, w: 6.2, h: 1.62, margin: 0, valign: "bottom", fontFace: SERIF, fontSize: 92, bold: true, color: WHITE });
  s.addText("Domestic Resource Planning", { x: 0.95, y: 2.98, w: 6.2, h: 0.45, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 24, color: TEALLT });
  s.addText("Un ERP para el hogar. Das de alta lo que tienes, enciendes solo lo que tu casa necesita y el sistema te avisa antes de que se te pase.", {
    x: 0.95, y: 3.56, w: 5.95, h: 1.05, margin: 0, valign: "top", fontFace: SANS, fontSize: 15, color: PALE, lineSpacing: 22,
  });

  const chips = [
    ["Fases 1 y 2 cerradas", TEAL],
    ["4 de 13 módulos construidos", "24534F"],
    ["Agosto de 2026", "24534F"],
  ];
  let cx = 0.95;
  chips.forEach(([t, c]) => {
    const cwid = 0.28 + t.length * 0.095;
    pill(s, { x: cx, y: 4.92, w: cwid, h: 0.42, text: t, fill: c, size: 11.5, spacing: 0 });
    cx += cwid + 0.18;
  });

  s.addText("Presentación comercial · generada desde el documento de diseño del proyecto", {
    x: 0.95, y: 5.6, w: 6.2, h: 0.3, margin: 0, valign: "middle", fontFace: SANS, fontSize: 11, color: FAINT,
  });

  // Motivo de portada: la casa como contenedor de todo lo que DRP gestiona. Es
  // la traducción comercial del diagrama de jerarquía que abre el deck de resumen.
  s.addShape(pres.ShapeType.triangle, {
    x: 7.72, y: 1.28, w: 5.0, h: 1.2, fill: { color: TEALLT, transparency: 72 }, line: { color: TEALLT, width: 1 },
  });
  card(s, { x: 8.14, y: 2.48, w: 4.16, h: 3.56, fill: DEEP, line: EDGE, shadow: false });

  const stuff = [
    "Caldera", "Los dos coches",
    "Lavavajillas", "Taladro",
    "Despensa", "Bicicletas",
    "Manuales", "Garantías",
  ];
  stuff.forEach((t, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = 8.36 + col * 1.92, y = 2.74 + row * 0.62;
    card(s, { x, y, w: 1.74, h: 0.48, fill: DEEP2, line: EDGE, r: 0.24, shadow: false });
    s.addText(t, { x, y, w: 1.74, h: 0.48, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 10.5, color: PALE });
  });
  s.addText("...y todo lo demás que hay en casa", {
    x: 8.36, y: 5.34, w: 3.72, h: 0.5, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 11, italic: true, color: FAINT,
  });

  s.addText("Un sitio donde está todo, y que además avisa.", {
    x: 7.72, y: 6.2, w: 5.0, h: 0.4, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 12, italic: true, color: TEALLT,
  });

  s.addNotes("Portada. Presentación comercial de DRP, dirigida a quien no conoce el proyecto: inversión, colaboradores y primeros hogares piloto. Procede del README §1 y refleja su estado a 2026-08-20: Fases 1 y 2 y el cierre de huecos cerrados, cuatro de trece módulos construidos y ningún despliegue todavía.");
}

// ═══ 2 · El problema ══════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "EL PROBLEMA", "Todo lo de casa está. Lo que no está es junto.");

  s.addText("La información sobre lo que hay en un hogar vive repartida en sitios que no se hablan entre sí, y el pegamento acaba siendo la memoria de quien lleva la casa.", {
    x: M, y: 1.42, w: 5.05, h: 0.9, margin: 0, valign: "top", fontFace: SANS, fontSize: 14, color: MUTED, lineSpacing: 20,
  });

  s.addText(lines([
    "La ITV, en el calendario del móvil",
    "El manual de la caldera, en un cajón",
    "El inventario del garaje, «en la cabeza»",
    "El mantenimiento, recordado por costumbre",
    "Las garantías, en el sobre de la compra",
  ], { bullet: true }), {
    x: M + 0.05, y: 2.5, w: 4.95, h: 2.3, margin: 0, valign: "top",
    fontFace: SANS, fontSize: 14, color: BODY, paraSpaceAfter: 9, lineSpacing: 20,
  });

  card(s, { x: M, y: 5.08, w: 5.05, h: 1.35, fill: SAND, shadow: false });
  s.addText("Y lo que se olvida no avisa: se descubre el día de la avería.", {
    x: M + 0.32, y: 5.08, w: 4.41, h: 1.35, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 17, bold: true, color: TERRA, lineSpacing: 24,
  });

  // Las tarjetas van giradas y desalineadas a propósito: es la dispersión
  // dibujada, no una retícula. Girar la caja obliga a girar su texto igual.
  const notes = [
    { x: 6.05, y: 1.72, w: 3.0,  h: 1.02, rot: -4, t: "Hoja de cálculo", d: "«inventario_v3_FINAL»" },
    { x: 9.55, y: 1.95, w: 3.05, h: 1.02, rot: 5,  t: "Carpeta de papeles", d: "facturas y manuales" },
    { x: 6.25, y: 3.30, w: 3.05, h: 1.02, rot: 3,  t: "Recordatorios sueltos", d: "los que no se silenciaron" },
    { x: 9.50, y: 3.62, w: 3.05, h: 1.02, rot: -5, t: "«Lo tengo en la cabeza»", d: "hasta que hace falta" },
  ];
  notes.forEach((n) => {
    card(s, { x: n.x, y: n.y, w: n.w, h: n.h, fill: TINT, line: "D3DFDC", rotate: n.rot });
    s.addText(n.t, { x: n.x + 0.24, y: n.y + 0.16, w: n.w - 0.48, h: 0.38, margin: 0, valign: "middle", rotate: n.rot, fontFace: SANS, fontSize: 13.5, bold: true, color: INK });
    s.addText(n.d, { x: n.x + 0.24, y: n.y + 0.52, w: n.w - 0.48, h: 0.34, margin: 0, valign: "middle", rotate: n.rot, fontFace: SANS, fontSize: 11, italic: true, color: MUTED });
  });

  card(s, { x: 6.05, y: 5.45, w: 6.68, h: 0.98, fill: TINT2, shadow: false });
  s.addText("Ninguno de estos sitios habla con los demás, y ninguno crece contigo: la hoja que servía para el garaje no sirve para la despensa.", {
    x: 6.35, y: 5.45, w: 6.08, h: 0.98, margin: 0, valign: "middle", fontFace: SANS, fontSize: 12.5, color: INK2, lineSpacing: 18,
  });

  foot(s);
  s.addNotes("README §2 (Objetivo del proyecto): el problema tal cual lo formula el documento —hojas de cálculo, carpetas de papeles, recordatorios sueltos y la memoria de quien gestiona la casa— y la ausencia de un punto único de verdad que además crezca con el hogar.");
}

// ═══ 3 · La idea, en una frase ════════════════════════════════════════════════
{
  const s = newSlide(true);

  s.addText("LA IDEA, EN UNA FRASE", {
    x: M, y: 1.15, w: CW, h: 0.3, margin: 0, align: "center", valign: "middle",
    fontFace: SANS, fontSize: 11, bold: true, charSpacing: 2.5, color: TEALLT,
  });
  s.addText("Un ERP para el hogar", {
    x: M, y: 1.62, w: CW, h: 1.35, margin: 0, align: "center", valign: "middle",
    fontFace: SERIF, fontSize: 62, bold: true, color: WHITE,
  });

  card(s, { x: 1.55, y: 3.28, w: 10.23, h: 1.38, fill: DEEP, line: TEALLT, shadow: false });
  s.addText("Un núcleo mínimo que resuelve lo esencial —dar de alta, ubicar y clasificar todo lo que hay en casa— y módulos que se van encendiendo a medida que aparece la necesidad.", {
    x: 1.95, y: 3.28, w: 9.43, h: 1.38, margin: 0, align: "center", valign: "middle",
    fontFace: SANS, fontSize: 16, color: PALE, lineSpacing: 25,
  });

  const props = [
    ["Lo esencial, siempre", "El núcleo es lo único obligatorio, y funciona con todos los módulos apagados."],
    ["Lo demás, cuando toque", "Enciendes solo lo que tu casa necesita, en el momento en que lo necesita."],
    ["Sin volver a empezar", "Nada de lo ya cargado hay que rehacerlo al encender un módulo nuevo."],
  ];
  const pw = 3.83;
  props.forEach((p, i) => {
    const x = M + i * (pw + 0.32);
    card(s, { x, y: 5.02, w: pw, h: 1.42, fill: DEEP2, line: EDGE, shadow: false });
    s.addText(p[0], { x: x + 0.3, y: 5.2, w: pw - 0.6, h: 0.38, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 15.5, bold: true, color: TEALLT });
    s.addText(p[1], { x: x + 0.3, y: 5.62, w: pw - 0.6, h: 0.68, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, color: PALE, lineSpacing: 16 });
  });

  foot(s, true);
  s.addNotes("README §1 (Resumen ejecutivo): el enfoque modular de los ERP empresariales trasladado al hogar, con un core mínimo y módulos activables a medida que aparece la necesidad.");
}

// ═══ 4 · Analogía ERP → DRP ═══════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "DE DÓNDE VIENE LA IDEA", "Lo que un ERP hace por una fábrica, en tu casa");
  s.addText("Cambian los nombres y el tamaño. La disciplina de gestión es exactamente la misma.", {
    x: M, y: 1.4, w: CW, h: 0.32, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, color: MUTED,
  });

  const rows = [
    ["Activos productivos: maquinaria, líneas", "Electrodomésticos, vehículos, mobiliario, herramientas"],
    ["Mantenimiento preventivo y correctivo", "Revisión de la caldera, ITV, cambio de filtros, garantías"],
    ["Almacén e inventario", "Despensa, garaje, trastero, botiquín"],
    ["Compras y aprovisionamiento", "Lista de la compra, reposición de lo que se agota"],
    ["Maestro de proveedores", "Fontanero, servicio técnico de la caldera, taller"],
    ["Planificación de la producción", "Tareas de casa, turnos, rutinas familiares, menú semanal"],
    ["Proyectos y eventos puntuales", "Mudanzas, reformas, celebraciones, viajes"],
  ];
  const lw = 4.75, rw = 6.3, ax = M + lw + 0.32, rx = ax + 0.66, rh = 0.55;
  s.addText("EN UNA EMPRESA", { x: M, y: 1.8, w: lw, h: 0.26, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10, bold: true, charSpacing: 1.5, color: MUTED });
  s.addText("EN TU CASA", { x: rx, y: 1.8, w: rw, h: 0.26, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10, bold: true, charSpacing: 1.5, color: TEAL });
  let y = 2.12;
  rows.forEach(([a, b]) => {
    card(s, { x: M, y, w: lw, h: rh, fill: TINT, shadow: false });
    s.addText(a, { x: M + 0.26, y, w: lw - 0.52, h: rh, margin: 0, valign: "middle", fontFace: SANS, fontSize: 12, color: MUTED });
    s.addShape(pres.ShapeType.rightArrow, { x: ax, y: y + 0.14, w: 0.48, h: 0.26, fill: { color: "C3D2CF" }, line: { color: "C3D2CF", width: 0.5 } });
    card(s, { x: rx, y, w: rw, h: rh, fill: TINT2, shadow: false });
    s.addText(b, { x: rx + 0.26, y, w: rw - 0.52, h: rh, margin: 0, valign: "middle", fontFace: SANS, fontSize: 12.5, bold: true, color: INK2 });
    y += 0.63;
  });

  foot(s);
  s.addNotes("README §3 (Analogía ERP → DRP), las siete filas de su tabla, con los nombres de la columna izquierda desprovistos de siglas para un público no técnico.");
}

// ═══ 5 · Cómo funciona, en tres pasos ═════════════════════════════════════════
{
  const s = newSlide();
  head(s, "CÓMO FUNCIONA", "Tres pasos, y solo el primero es obligatorio");

  const steps = [
    ["Das de alta lo que tienes", "Una vez, y con sus papeles: la factura, el manual, la garantía, la foto de la etiqueta. Dónde está, de quién es y en qué estado.", TEAL],
    ["Enciendes lo que te hace falta", "Solo lo que tu casa necesita, cuando lo necesita. Y sin rehacer nada de lo ya cargado: lo que diste de alta sigue exactamente donde estaba.", INK2],
    ["Y a partir de ahí, te avisa él", "La revisión de la caldera, la ITV, lo que caduca, lo que prestaste y no ha vuelto. Un aviso antes, no un disgusto después.", TERRA],
  ];
  const cw = 3.83, cy = 1.85, chh = 3.7;
  steps.forEach((p, i) => {
    const x = M + i * (cw + 0.32);
    card(s, { x, y: cy, w: cw, h: chh, fill: TINT });
    badge(s, x + 0.4, cy + 0.42, 0.72, String(i + 1), p[2], WHITE, 26);
    s.addText(p[0], { x: x + 0.4, y: cy + 1.35, w: cw - 0.8, h: 0.95, margin: 0, valign: "top", fontFace: SERIF, fontSize: 20, bold: true, color: INK, lineSpacing: 26 });
    s.addText(p[1], { x: x + 0.4, y: cy + 2.35, w: cw - 0.8, h: 1.2, margin: 0, valign: "top", fontFace: SANS, fontSize: 13, color: MUTED, lineSpacing: 19 });

    if (i < steps.length - 1) {
      s.addShape(pres.ShapeType.rightArrow, { x: x + cw + 0.03, y: cy + 1.62, w: 0.26, h: 0.26, fill: { color: "C3D2CF" }, line: { color: "C3D2CF", width: 0.5 } });
    }
  });

  card(s, { x: M, y: 5.78, w: CW, h: 0.72, fill: INK, shadow: false });
  s.addText("El paso 1 es lo único que hay que hacer para empezar. Los pasos 2 y 3 esperan a que el hogar los pida, y hasta entonces no estorban.", {
    x: M, y: 5.78, w: CW, h: 0.72, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 13.5, color: PALE,
  });

  foot(s);
  s.addNotes("README §1, §4.1 y §4.2: el core mínimo obligatorio, la activación progresiva de módulos sin rehacer lo cargado, y los avisos por fecha que la plataforma programa y entrega.");
}

// ═══ 6 · Qué resuelve hoy ═════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "QUÉ RESUELVE HOY", "Lo que ya está construido y funcionando");
  s.addText("Seis cosas que todo hogar necesita antes de encender ningún módulo. Están hechas, y son el suelo sobre el que se apoya todo lo demás.", {
    x: M, y: 1.4, w: CW, h: 0.32, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, color: MUTED,
  });

  const items = [
    ["TODO LO QUE HAY", "Desde la caldera hasta el paquete de harina. Lo que dura y lo que se gasta, cada uno contado a su manera.", TEAL],
    ["DÓNDE ESTÁ", "La casa por dentro: plantas, habitaciones, muebles, estantes. Y un mueble también guarda cosas, no solo una habitación.", TEAL],
    ["DE QUIÉN ES", "Propietario, responsable y estado de cada cosa. Nada se borra: lo que causa baja se marca, y su historia se conserva.", TEAL],
    ["LOS PAPELES", "Factura, manual, garantía y fotos, guardados junto al aparato al que pertenecen y no en un cajón aparte.", INK2],
    ["QUIÉN ENTRA", "Cada persona de la casa, con su cuenta. Se entra por invitación, y quien administra decide qué puede tocar cada cual.", INK2],
    ["LO QUE PRESTAS", "Quién se llevó el taladro y cuándo dijo que lo traía. Él confirma la devolución desde su propio enlace, sin darse de alta.", INK2],
  ];
  const cw = 3.83, ch = 2.0, gap = 0.32;
  items.forEach((it, i) => {
    const col = i % 3, row = Math.floor(i / 3);
    const x = M + col * (cw + gap), y = 1.9 + row * (ch + 0.3);
    card(s, { x, y, w: cw, h: ch, fill: TINT });
    pill(s, { x: x + 0.32, y: y + 0.3, w: 1.98, h: 0.38, text: it[0], fill: it[2], size: 9.5, spacing: 1 });
    s.addText(it[1], { x: x + 0.32, y: y + 0.86, w: cw - 0.64, h: 1.05, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, color: BODY, lineSpacing: 18 });
  });

  s.addText("Ninguna de las seis depende de que haya un módulo encendido: son del núcleo, y el núcleo está entero.", {
    x: M, y: 6.34, w: CW, h: 0.42, margin: 0, valign: "middle", fontFace: SANS, fontSize: 13, italic: true, color: TEAL,
  });

  foot(s);
  s.addNotes("README §4.1 (Core mínimo obligatorio) y sus subsecciones 4.1.1 a 4.1.5, contadas en lenguaje de casa: assets duraderos y consumibles, ubicaciones jerárquicas, propietario y estado, documentación asociada, usuarios por invitación y préstamos con acceso acotado.");
}

// ═══ 7 · Un día con DRP ═══════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "UN DÍA CON DRP", "Una familia, una caldera, dos coches y un garaje lleno");

  // Línea de tiempo: círculo de hora, conector y tarjeta debajo. Es la decisión
  // que la plantilla de referencia usa en su diapositiva de recorrido histórico.
  const moments = [
    ["08:10", "Toca la revisión de la caldera", "El aviso llegó el viernes, con margen para llamar. En su ficha están el teléfono del servicio técnico y la última intervención."],
    ["13:30", "¿Queda café?", "La despensa lo sabe sin abrir el armario, y lo que baja del mínimo aparece por sí solo en la lista de la compra."],
    ["18:00", "El vecino se lleva el taladro", "Queda anotado con su fecha de vuelta. Él confirma la devolución desde su propio enlace, sin darse de alta en nada."],
    ["21:45", "Se rompe el lavavajillas", "Modelo, factura, garantía y el taller que lo instaló, en la misma ficha. La llamada dura dos minutos."],
  ];
  const cw = 2.83, gap = 0.27, d = 1.05;
  moments.forEach((m, i) => {
    const x = M + i * (cw + gap), cx = x + cw / 2;
    if (i > 0) {
      s.addShape(pres.ShapeType.line, { x: x - gap - cw / 2 + d / 2, y: 2.16, w: cw + gap - d, h: 0, line: { color: "C3D2CF", width: 1.5 } });
    }
    circle(s, cx - d / 2, 1.64, d, i % 2 === 0 ? TEAL : INK2);
    s.addText(m[0], { x: cx - d / 2, y: 1.64, w: d, h: d, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 15, bold: true, color: WHITE });

    card(s, { x, y: 3.0, w: cw, h: 2.85, fill: TINT });
    s.addText(m[1], { x: x + 0.28, y: 3.24, w: cw - 0.56, h: 1.0, margin: 0, valign: "top", fontFace: SERIF, fontSize: 16, bold: true, color: INK, lineSpacing: 21 });
    s.addText(m[2], { x: x + 0.28, y: 4.3, w: cw - 0.56, h: 1.35, margin: 0, valign: "top", fontFace: SANS, fontSize: 12, color: MUTED, lineSpacing: 17 });
  });

  card(s, { x: M, y: 6.08, w: CW, h: 0.62, fill: SAND, shadow: false });
  s.addText("Escena ilustrativa: ningún hogar real usa todavía DRP. Todo lo que ocurre en ella está construido, pero aún no desplegado.", {
    x: M, y: 6.08, w: CW, h: 0.62, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 12, bold: true, color: TERRA,
  });

  foot(s);
  s.addNotes("README §2, ejemplo ilustrativo de la familia con caldera, dos coches, electrodomésticos y garaje. Las cuatro escenas se apoyan en el core (préstamos y documentos) y en los cuatro módulos construidos (mantenimiento, warehouse, compras y proveedores). Va marcada como ilustrativa porque §4.2 deja claro que no hay ningún hogar real usando el producto.");
}

// ═══ 8 · Crece contigo ════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "CRECE CONTIGO", "Trece módulos. Enciendes los que quieras, cuando quieras.");
  s.addText("Ninguno es obligatorio y el núcleo funciona con todos apagados. Apagar uno tampoco borra nada: los datos siguen ahí si vuelves a encenderlo.", {
    x: M, y: 1.4, w: CW, h: 0.32, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, color: MUTED,
  });

  // Tres oleadas, con el detalle decreciendo con la lejanía: lo construido lleva
  // descripción larga, lo siguiente una línea, y lo de más adelante solo nombre.
  const waves = [
    {
      label: "YA CONSTRUIDO", count: "4 módulos", accent: TEAL, ly: 1.8, y: 2.1, h: 1.45, gap: 0.22,
      fill: TEAL, line: TEAL, title: WHITE, desc: ONTEAL,
      mods: [
        ["Quién arregla qué", "El fontanero, el servicio técnico, el taller. Quién viene, quién cobra y quién responde de una garantía."],
        ["La despensa y el trastero", "Qué hay, cuánto queda, qué está a punto de caducar y qué conviene reponer."],
        ["La lista de la compra", "Qué falta, qué hay que reponer y qué está ya pedido. Lo que se recibe entra por sí solo en la despensa."],
        ["El mantenimiento", "Revisiones que se repiten solas, cada una con su aviso y su histórico de intervenciones."],
      ],
    },
    {
      label: "LO SIGUIENTE", count: "3 módulos", accent: INK2, ly: 3.72, y: 4.02, h: 1.15, gap: 0.22,
      fill: TINT2, line: "C3D2CF", title: INK, desc: MUTED,
      mods: [
        ["Tareas y turnos de casa", "Rutinas, recordatorios y a quién le toca esta semana."],
        ["Gastos y presupuesto", "Lo que cuesta lo que entra en casa, y el presupuesto por periodo."],
        ["Mudanzas, reformas y viajes", "Proyectos con principio, fin y sus cosas asociadas."],
      ],
    },
    {
      label: "MÁS ADELANTE", count: "6 módulos", accent: "9BAFAC", ly: 5.32, y: 5.62, h: 0.74, gap: 0.18,
      fill: WHITE, line: "D3DFDC", title: INK2, desc: null,
      mods: [
        ["Préstamos avanzados"], ["Recetas y menú semanal"], ["Reservas de uso"],
        ["Fin de vida"], ["Garantías y seguros"], ["Mascotas y plantas"],
      ],
    },
  ];

  waves.forEach((b) => {
    s.addText([
      { text: b.label, options: { bold: true, charSpacing: 1.2, color: b.accent } },
      { text: "   ·   " + b.count, options: { color: MUTED } },
    ], { x: M, y: b.ly, w: CW, h: 0.26, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10.5 });

    const mw = (CW - (b.mods.length - 1) * b.gap) / b.mods.length;
    b.mods.forEach((m, i) => {
      const x = M + i * (mw + b.gap);
      card(s, { x, y: b.y, w: mw, h: b.h, fill: b.fill, line: b.line, shadow: false });
      if (b.desc) {
        s.addText(m[0], { x: x + 0.22, y: b.y + 0.14, w: mw - 0.44, h: 0.42, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, bold: true, color: b.title, lineSpacing: 16 });
        s.addText(m[1], { x: x + 0.22, y: b.y + 0.6, w: mw - 0.44, h: b.h - 0.74, margin: 0, valign: "top", fontFace: SANS, fontSize: 10.5, color: b.desc, lineSpacing: 14 });
      } else {
        s.addText(m[0], { x: x + 0.12, y: b.y, w: mw - 0.24, h: b.h, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 11, bold: true, color: b.title, lineSpacing: 15 });
      }
    });
  });

  s.addText("Los cuatro de arriba están construidos enteros. Los otros nueve tienen sitio reservado, no promesa de fecha.", {
    x: M, y: 6.48, w: CW, h: 0.42, margin: 0, valign: "middle", fontFace: SANS, fontSize: 12.5, italic: true, color: TEAL,
  });

  foot(s);
  s.addNotes("README §4.2 (Módulos futuros, activables progresivamente): trece filas repartidas en tres prioridades. Los cuatro de prioridad alta —proveedores, warehouse, compras y mantenimiento— fueron el alcance de la Fase 2 y están construidos; los nueve restantes siguen «Por diseñar».");
}

// ═══ 9 · Te avisa él ══════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "LO QUE MÁS SE AGRADECE", "No hay que acordarse: te avisa él");
  s.addText("Casi todo lo que se olvida en una casa se olvida por una fecha. Vigilar fechas es exactamente lo que una máquina hace mejor que una persona.", {
    x: M, y: 1.4, w: CW, h: 0.32, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, color: MUTED,
  });

  const alerts = [
    ["Lo que caduca", "El yogur del fondo, la medicina del botiquín, la pila del detector de humo.", true],
    ["La revisión que toca", "La caldera, el filtro del extractor, la ITV de cada coche.", true],
    ["Lo que prestaste", "Y la fecha en la que dijeron que lo devolvían.", true],
    ["La garantía que vence", "Avisada antes de vencer, que es cuando sirve de algo.", false],
    ["El perro y las plantas", "Vacuna, veterinario, desparasitación, riego y poda.", false],
  ];
  const cw = (CW - 4 * 0.22) / 5, cy = 1.88, chh = 3.34;
  alerts.forEach((a, i) => {
    const x = M + i * (cw + 0.22);
    card(s, { x, y: cy, w: cw, h: chh, fill: a[2] ? TINT : WHITE, line: a[2] ? undefined : "D3DFDC", shadow: a[2] ? undefined : false });
    circle(s, x + 0.3, cy + 0.32, 0.44, a[2] ? TEAL : "C3D2CF");
    s.addText(a[0], { x: x + 0.3, y: cy + 0.92, w: cw - 0.6, h: 0.88, margin: 0, valign: "top", fontFace: SERIF, fontSize: 16, bold: true, color: a[2] ? INK : MUTED, lineSpacing: 21 });
    s.addText(a[1], { x: x + 0.3, y: cy + 1.86, w: cw - 0.6, h: 1.05, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, color: MUTED, lineSpacing: 16 });
    pill(s, { x: x + 0.3, y: cy + 2.97, w: 1.28, h: 0.34, text: a[2] ? "CONSTRUIDO" : "PREVISTO", fill: a[2] ? TEAL : "E7EDEB", line: a[2] ? TEAL : "D3DFDC", color: a[2] ? WHITE : MUTED, size: 8.5 });
  });

  card(s, { x: M, y: 5.42, w: CW, h: 1.28, fill: INK, shadow: false });
  s.addText("Un solo resumen al día, con lo que haya.", {
    x: M + 0.5, y: 5.6, w: CW - 1.0, h: 0.42, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 20, bold: true, color: WHITE,
  });
  s.addText("Y ninguno los días en que no hay nada que contar, que es la diferencia entre un sistema que ayuda y uno que se acaba silenciando.", {
    x: M + 0.5, y: 6.06, w: CW - 1.0, h: 0.5, margin: 0, valign: "top", fontFace: SANS, fontSize: 13, color: PALE, lineSpacing: 18,
  });

  foot(s);
  s.addNotes("README §4.2, nota final sobre el patrón de aviso por fecha que aparece en cinco módulos —caducidad, revisión, vencimiento de garantía, devolución de préstamo y cuidados de mascotas y plantas—; programar la comprobación y entregar el aviso son capacidad de plataforma. El resumen diario único, y su ausencia cuando no hay nada, están en la ADR-011.");
}

// ═══ 10 · Tu casa, tus datos ══════════════════════════════════════════════════
{
  const s = newSlide(true);
  head(s, "TU CASA, TUS DATOS", "Separados de los del resto, con dos barreras y no una", true);
  s.addText("Varios hogares comparten el mismo sistema, como comparten edificio varios vecinos. Lo que no comparten es nada de lo que hay dentro.", {
    x: M, y: 1.45, w: CW, h: 0.35, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, color: PALE,
  });

  const layers = [
    ["1", "El sistema solo pregunta por tu hogar", "Cada consulta lleva escrito de qué casa viene, y esa es la única que se atiende. Nunca la que llegue escrita desde fuera."],
    ["2", "Y la base de datos vuelve a comprobarlo", "Aunque la primera barrera fallara, por debajo hay una segunda que no entrega ni una sola fila que no sea de tu hogar."],
  ];
  const cw = 5.85, cy = 2.0, chh = 2.1;
  layers.forEach((l, i) => {
    const x = M + i * (cw + 0.43);
    card(s, { x, y: cy, w: cw, h: chh, fill: DEEP, line: EDGE, shadow: false });
    badge(s, x + 0.35, cy + 0.34, 0.6, l[0], i === 0 ? TEAL : TEALLT, i === 0 ? WHITE : INK, 22);
    s.addText(l[1], { x: x + 1.12, y: cy + 0.3, w: cw - 1.45, h: 0.7, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 17, bold: true, color: WHITE, lineSpacing: 22 });
    s.addText(l[2], { x: x + 0.38, y: cy + 1.12, w: cw - 0.76, h: 0.85, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, color: PALE, lineSpacing: 18 });
  });

  const extras = [
    ["El correo se verifica", "Un hogar no existe de verdad hasta que alguien confirma su correo, y sumar a una persona es siempre por invitación."],
    ["Los papeles viven en tu servidor", "Facturas, manuales y fotos se guardan en el servidor del hogar, con un giga de cuota por casa."],
    ["Nada se borra por accidente", "Dar de baja es marcar, no destruir. Lo que pasó en tu casa se puede seguir consultando después."],
  ];
  const ew = 3.83;
  extras.forEach((e, i) => {
    const x = M + i * (ew + 0.32);
    card(s, { x, y: 4.5, w: ew, h: 1.6, fill: DEEP2, line: EDGE, shadow: false });
    s.addText(e[0], { x: x + 0.3, y: 4.68, w: ew - 0.6, h: 0.42, margin: 0, valign: "middle", fontFace: SANS, fontSize: 13.5, bold: true, color: TEALLT });
    s.addText(e[1], { x: x + 0.3, y: 5.14, w: ew - 0.6, h: 0.85, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, color: PALE, lineSpacing: 16 });
  });

  s.addText("Es la misma protección que exige una empresa a su sistema de gestión, aplicada a una casa que no tiene departamento de informática.", {
    x: M, y: 6.26, w: CW, h: 0.42, margin: 0, valign: "middle", fontFace: SANS, fontSize: 12.5, italic: true, color: FAINT,
  });

  foot(s, true);
  s.addNotes("README §5.6 (Modelo de datos multi-tenant) y §5.8 (Almacenamiento de ficheros), traducidos a lenguaje llano: las dos capas independientes de aislamiento, la cuota de 1 GB por hogar y la baja lógica. Más §4.1.4: verificación de correo obligatoria y alta en un hogar existente siempre por invitación.");
}

// ═══ 11 · En el móvil, y para todo el mundo ═══════════════════════════════════
{
  const s = newSlide();
  head(s, "DÓNDE SE USA", "En el móvil, de pie en el trastero");

  s.addText("El inventario del garaje no se consulta sentado en un escritorio: se consulta con una mano, delante de la estantería. Por eso está diseñado primero para el móvil y después para la pantalla grande, y no al revés.", {
    x: M, y: 1.48, w: 5.5, h: 1.15, margin: 0, valign: "top", fontFace: SANS, fontSize: 14, color: MUTED, lineSpacing: 21,
  });

  const feats = [
    ["Funciona en el móvil", "Desde una pantalla pequeña de 375 píxeles hasta un monitor ultrapanorámico, sin perder nada por el camino."],
    ["Y lo puede usar todo el mundo", "Se maneja entero con el teclado, se lee con lector de pantalla y los colores tienen contraste suficiente para leerse de verdad."],
    ["Comprobado, no prometido", "La accesibilidad se verifica en un navegador real en cada entrega, contra el estándar WCAG 2.2 en su nivel AA."],
  ];
  let fy = 2.78;
  feats.forEach((f, i) => {
    card(s, { x: M, y: fy, w: 5.5, h: 1.16, fill: i === 0 ? TINT : WHITE, line: i === 0 ? undefined : "D3DFDC", shadow: i === 0 ? undefined : false });
    s.addText(f[0], { x: M + 0.3, y: fy + 0.16, w: 4.9, h: 0.36, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, bold: true, color: INK });
    s.addText(f[1], { x: M + 0.3, y: fy + 0.54, w: 4.9, h: 0.52, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, color: MUTED, lineSpacing: 16 });
    fy += 1.3;
  });

  // Dos dispositivos con el mismo contenido recolocado: el móvil en una columna
  // y la pantalla ancha en dos. Es la idea de «primero el móvil», dibujada.
  const phone = { x: 6.6, y: 1.62, w: 2.05, h: 4.3 };
  card(s, { x: phone.x, y: phone.y, w: phone.w, h: phone.h, fill: INK, r: 0.2 });
  card(s, { x: phone.x + 0.13, y: phone.y + 0.28, w: phone.w - 0.26, h: phone.h - 0.56, fill: WHITE, line: "D3DFDC", r: 0.05, shadow: false });
  s.addText("Trastero", { x: phone.x + 0.28, y: phone.y + 0.42, w: phone.w - 0.56, h: 0.3, margin: 0, valign: "middle", fontFace: SANS, fontSize: 11, bold: true, color: INK });
  ["Taladro", "Escalera", "Caja de tornillos", "Bicicleta", "Sombrilla"].forEach((t, i) => {
    const y = phone.y + 0.82 + i * 0.55;
    card(s, { x: phone.x + 0.26, y, w: phone.w - 0.52, h: 0.44, fill: TINT2, r: 0.06, shadow: false });
    s.addText(t, { x: phone.x + 0.38, y, w: phone.w - 0.76, h: 0.44, margin: 0, valign: "middle", fontFace: SANS, fontSize: 9.5, color: INK2 });
  });
  s.addText("375 px", { x: phone.x, y: phone.y + phone.h + 0.12, w: phone.w, h: 0.3, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 11, bold: true, color: MUTED });

  const desk = { x: 9.05, y: 1.62, w: 3.68, h: 2.9 };
  card(s, { x: desk.x, y: desk.y, w: desk.w, h: desk.h, fill: INK, r: 0.1 });
  card(s, { x: desk.x + 0.14, y: desk.y + 0.14, w: desk.w - 0.28, h: desk.h - 0.5, fill: WHITE, line: "D3DFDC", r: 0.04, shadow: false });
  s.addText("Trastero", { x: desk.x + 0.32, y: desk.y + 0.28, w: 1.6, h: 0.28, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10.5, bold: true, color: INK });
  ["Taladro", "Escalera", "Caja de tornillos", "Bicicleta", "Sombrilla", "Manguera"].forEach((t, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = desk.x + 0.32 + col * 1.56, y = desk.y + 0.68 + row * 0.55;
    card(s, { x, y, w: 1.44, h: 0.44, fill: TINT2, r: 0.06, shadow: false });
    s.addText(t, { x: x + 0.12, y, w: 1.2, h: 0.44, margin: 0, valign: "middle", fontFace: SANS, fontSize: 9, color: INK2 });
  });
  s.addShape(pres.ShapeType.rect, { x: desk.x + desk.w / 2 - 0.45, y: desk.y + desk.h, w: 0.9, h: 0.16, fill: { color: INK2 }, line: { color: INK2, width: 0.5 } });
  s.addText("hasta ultrapanorámico", { x: desk.x, y: desk.y + desk.h + 0.28, w: desk.w, h: 0.3, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 11, bold: true, color: MUTED });

  card(s, { x: 9.05, y: 5.2, w: 3.68, h: 1.2, fill: TINT, shadow: false });
  s.addText("El mismo contenido, ordenado de otra forma. No es una versión recortada del móvil ni una web grande apretada.", {
    x: 9.35, y: 5.2, w: 3.08, h: 1.2, margin: 0, valign: "middle", fontFace: SANS, fontSize: 11.5, color: INK2, lineSpacing: 17,
  });

  foot(s);
  s.addNotes("README §5.5 (Frontend responsive): enfoque mobile-first, de 375 px a ultrawide. Y §6, fila de accesibilidad: WCAG 2.2 nivel AA como objetivo normativo verificable, comprobado en navegador real dentro del recorrido vertical.");
}

// ═══ 12 · Dónde está el proyecto ══════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "DÓNDE ESTÁ EL PROYECTO", "Tres fases cerradas, la cuarta sin empezar");

  const fases = [
    ["Fase 0", "Definición", "Qué es, qué alcance tiene y cómo se comprueba que funciona de verdad.", "Completada", true],
    ["Fase 1", "Lo esencial", "El núcleo entero, con pantallas para todos sus flujos. Cerrada el 17 de agosto de 2026.", "Completada", true],
    ["Fase 2", "Los primeros módulos", "Cuatro módulos y el mecanismo que los enciende hogar por hogar. Cerrada el 19 de agosto de 2026.", "Completada", true],
    ["Fase 3", "El resto de módulos", "Los nueve que faltan, por orden de prioridad. Todavía sin planificar.", "Pendiente", false],
  ];
  const cw = 2.83, gap = 0.27, d = 1.1;
  fases.forEach((f, i) => {
    const x = M + i * (cw + gap), cx = x + cw / 2, done = f[4];
    if (i > 0) {
      s.addShape(pres.ShapeType.line, { x: x - gap - cw / 2 + d / 2, y: 2.05, w: cw + gap - d, h: 0, line: { color: done ? "C3D2CF" : "E0E8E6", width: 1.5, dashType: done ? "solid" : "dash" } });
    }
    circle(s, cx - d / 2, 1.5, d, done ? TEAL : "E7EDEB");
    s.addText(f[0], { x: cx - d / 2, y: 1.5, w: d, h: d, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 14, bold: true, color: done ? WHITE : MUTED });

    card(s, { x, y: 2.86, w: cw, h: 2.5, fill: done ? TINT : WHITE, line: done ? undefined : "D3DFDC", shadow: done ? undefined : false });
    s.addText(f[1], { x: x + 0.28, y: 3.08, w: cw - 0.56, h: 0.85, margin: 0, valign: "top", fontFace: SERIF, fontSize: 17, bold: true, color: INK, lineSpacing: 22 });
    s.addText(f[2], { x: x + 0.28, y: 3.98, w: cw - 0.56, h: 1.0, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, color: MUTED, lineSpacing: 16 });
    pill(s, { x: x + 0.28, y: 4.94, w: 1.5, h: 0.34, text: f[3], fill: done ? TEAL : "E7EDEB", line: done ? TEAL : "D3DFDC", color: done ? WHITE : MUTED, size: 10 });
  });

  card(s, { x: M, y: 5.62, w: CW, h: 1.14, fill: SAND, shadow: false });
  s.addText([
    { text: "Lo que todavía no hay:  ", options: { bold: true, color: TERRA } },
    { text: "ningún servidor contratado, ningún hogar real dentro y nadie usando esto para saber dónde está el taladro. Por eso los cuatro módulos construidos siguen diciendo «en desarrollo» y no «en producción»: el código está entero, el despliegue no ha empezado.", options: { color: BODY } },
  ], { x: M + 0.4, y: 5.62, w: CW - 0.8, h: 1.14, margin: 0, valign: "middle", fontFace: SANS, fontSize: 13, lineSpacing: 19 });

  foot(s);
  s.addNotes("README §8 (Roadmap y estado actual) con el detalle de 8.2 y 8.3, y la nota de §4.2 que explica por qué los cuatro módulos construidos siguen en «En desarrollo»: no hay despliegue, ni hogar real, ni nadie usando el producto.");
}

// ═══ 13 · En números, sin maquillaje ══════════════════════════════════════════
{
  const s = newSlide(true);
  head(s, "EN NÚMEROS", "Lo que hay, contado sin adornos", true);

  s.addChart(pres.ChartType.doughnut, [{
    name: "Módulos",
    labels: ["Construidos", "Por construir"],
    values: [4, 9],
  }], {
    x: M, y: 1.75, w: 5.4, h: 4.5,
    holeSize: 58,
    chartColors: [TEALLT, "8FA6A3"],
    showTitle: true, title: "Los trece módulos previstos", titleFontFace: SANS, titleFontSize: 13, titleColor: WHITE,
    showLegend: true, legendPos: "b", legendFontFace: SANS, legendFontSize: 11, legendColor: PALE,
    showValue: true, dataLabelFontFace: SANS, dataLabelFontSize: 14, dataLabelColor: INK,
    showPercent: false,
  });

  const stats = [
    ["3 de 4", "FASES CERRADAS", "La cuarta ni siquiera está planificada todavía: convertirla en un plan es un trabajo en sí mismo.", TEALLT],
    ["4 de 13", "MÓDULOS CONSTRUIDOS", "Enteros, no maquetas: con sus pantallas, sus avisos y sus pruebas. Los otros nueve tienen sitio, no fecha.", TEALLT],
    ["0", "HOGARES USÁNDOLO", "No hay despliegue ni servidor contratado. Es exactamente lo siguiente que este proyecto necesita.", TERRA],
  ];
  const rx = 6.35, rw = W - M - rx, chh = 1.3;
  stats.forEach((t, i) => {
    const y = 1.9 + i * (chh + 0.22);
    card(s, { x: rx, y, w: rw, h: chh, fill: DEEP, line: EDGE, shadow: false });
    s.addText(t[0], { x: rx + 0.34, y: y + 0.22, w: 1.85, h: 0.98, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 34, bold: true, color: t[3] });
    s.addText(t[1], { x: rx + 2.3, y: y + 0.24, w: rw - 2.65, h: 0.3, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10, bold: true, charSpacing: 1.2, color: WHITE });
    s.addText(t[2], { x: rx + 2.3, y: y + 0.56, w: rw - 2.65, h: 0.66, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, color: PALE, lineSpacing: 16 });
  });

  s.addText("Ninguna de estas cifras está estimada: todas se pueden comprobar en el propio proyecto el día que se lea esta presentación.", {
    x: M, y: 6.44, w: CW, h: 0.4, margin: 0, valign: "middle", fontFace: SANS, fontSize: 12, italic: true, color: FAINT,
  });

  foot(s, true);
  s.addNotes("README §4.2 (trece módulos, cuatro construidos y en desarrollo) y §8 (cuatro fases, tres de ellas cerradas, y la Fase 3 pendiente sin planificar). El cero de hogares es literal: §4.2 declara que no hay despliegue, ni servidor contratado, ni nadie usando el producto.");
}

// ═══ 14 · Qué falta y qué se busca ════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "QUÉ FALTA Y QUÉ SE BUSCA", "Tres huecos, y las tres cosas que los llenan");

  const cw = 5.85, cy = 1.62, chh = 4.5;

  card(s, { x: M, y: cy, w: cw, h: chh, fill: TINT });
  s.addText("QUÉ FALTA", { x: M + 0.38, y: cy + 0.3, w: cw - 0.76, h: 0.3, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10.5, bold: true, charSpacing: 1.5, color: MUTED });
  const gaps = [
    ["Un servidor donde vivir", "El tamaño está medido con consumo real y la máquina está elegida. Lo que no está es contratada."],
    ["Los primeros hogares", "Dos decisiones del producto están esperando a que haya casas de verdad para poder contestarse."],
    ["Los nueve módulos que quedan", "Ninguno pide inventar nada nuevo: el camino de un módulo ya está recorrido cuatro veces enteras."],
  ];
  let gy = cy + 0.78;
  gaps.forEach((g) => {
    badge(s, M + 0.38, gy + 0.04, 0.4, "–", TERRA, WHITE, 17);
    s.addText(g[0], { x: M + 0.95, y: gy, w: cw - 1.35, h: 0.4, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 16, bold: true, color: INK });
    s.addText(g[1], { x: M + 0.95, y: gy + 0.44, w: cw - 1.35, h: 0.75, margin: 0, valign: "top", fontFace: SANS, fontSize: 12, color: MUTED, lineSpacing: 17 });
    gy += 1.25;
  });

  const rx = M + cw + 0.43;
  card(s, { x: rx, y: cy, w: cw, h: chh, fill: TEAL });
  s.addText("QUÉ SE BUSCA", { x: rx + 0.38, y: cy + 0.3, w: cw - 0.76, h: 0.3, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10.5, bold: true, charSpacing: 1.5, color: ONTEAL });
  const asks = [
    ["Inversión", "Para el despliegue, para sostenerlo en marcha y para el tiempo que la Fase 3 necesita."],
    ["Colaboradores", "Quien quiera construir un módulo tiene cuatro ejemplos completos delante y un camino escrito."],
    ["Hogares piloto", "La pieza que ninguna cantidad de código sustituye: casas de verdad, con sus cosas de verdad dentro."],
  ];
  let ay = cy + 0.78;
  asks.forEach((a) => {
    badge(s, rx + 0.38, ay + 0.04, 0.4, "+", WHITE, TEAL, 17);
    s.addText(a[0], { x: rx + 0.95, y: ay, w: cw - 1.35, h: 0.4, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 16, bold: true, color: WHITE });
    s.addText(a[1], { x: rx + 0.95, y: ay + 0.44, w: cw - 1.35, h: 0.75, margin: 0, valign: "top", fontFace: SANS, fontSize: 12, color: ONTEAL, lineSpacing: 17 });
    ay += 1.25;
  });

  s.addText("Ninguna de las tres se puede comprar con las otras dos: sin hogares reales hay preguntas de producto que no tienen forma de contestarse.", {
    x: M, y: 6.28, w: CW, h: 0.44, margin: 0, valign: "middle", fontFace: SANS, fontSize: 13, italic: true, color: TEAL,
  });

  foot(s);
  s.addNotes("README §6 (fila de despliegue: VPS elegido con consumo medido, no contratado), §4.2 (las dos decisiones abiertas cuyo responsable pasó a ser «la primera revisión de operación con hogares reales dentro») y §8.3 (el camino de módulo recorrido cuatro veces).");
}

// ═══ 15 · Cierre y llamada a la acción ════════════════════════════════════════
{
  const s = newSlide(true);
  head(s, "EL SIGUIENTE PASO", "Lo construido funciona entero. Le falta una casa dentro.", true);
  s.addText("Un proyecto de software se demuestra ejecutándose en casa de alguien. Esa es, hoy, la única frontera que a DRP le queda por cruzar.", {
    x: M, y: 1.5, w: 9.6, h: 0.6, margin: 0, valign: "top", fontFace: SANS, fontSize: 15, color: PALE, lineSpacing: 21,
  });

  const ctas = [
    ["Abre tu hogar", "Sé uno de los primeros hogares piloto. Tus preguntas valen más que las respuestas que podamos inventarnos.", TEAL, WHITE],
    ["Construye un módulo", "Nueve tienen sitio reservado. Cuatro caminos completos están escritos para copiarlos.", TEALLT, INK],
    ["Financia el despliegue", "Poner esto en marcha y sostenerlo es lo que separa un producto construido de un producto usado.", TERRA, WHITE],
  ];
  const cw = 3.83, cy = 2.5, chh = 2.5;
  ctas.forEach((c, i) => {
    const x = M + i * (cw + 0.32);
    card(s, { x, y: cy, w: cw, h: chh, fill: DEEP, line: i === 0 ? TEALLT : EDGE, lineW: i === 0 ? 1.5 : 1, shadow: false });
    badge(s, x + 0.32, cy + 0.3, 0.5, String(i + 1), c[2], c[3], 17);
    s.addText(c[0], { x: x + 0.32, y: cy + 0.9, w: cw - 0.64, h: 0.5, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 21, bold: true, color: WHITE });
    s.addText(c[1], { x: x + 0.32, y: cy + 1.46, w: cw - 0.64, h: 0.85, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, color: PALE, lineSpacing: 18 });
  });

  // El bloque de contacto se deja vacío a propósito: inventar un correo o un
  // teléfono sería exactamente lo que la restricción de honestidad prohíbe. Se
  // rellena en este script, no sobre el .pptx, antes de cada envío.
  card(s, { x: M, y: 5.3, w: CW, h: 1.1, fill: DEEP2, line: EDGE, shadow: false });
  s.addText("CONTACTO", { x: M + 0.42, y: 5.48, w: 2.2, h: 0.3, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10, bold: true, charSpacing: 1.5, color: TEALLT });
  s.addText("Por completar antes de cada envío.", { x: M + 0.42, y: 5.8, w: 5.4, h: 0.34, margin: 0, valign: "middle", fontFace: SANS, fontSize: 13, italic: true, color: PALE });
  s.addText("Todo lo que afirma esta presentación se puede comprobar en el propio proyecto.", {
    x: 6.6, y: 5.3, w: 6.13, h: 1.1, margin: 0, align: "right", valign: "middle", fontFace: SANS, fontSize: 12, color: PALE,
  });

  foot(s, true);
  s.addNotes("README §8 y §4.2: cierre y llamada a la acción. Las tres peticiones —hogares piloto, colaboradores e inversión— se corresponden con los tres huecos declarados en la diapositiva anterior. El bloque de contacto se rellena en el generador antes de cada envío; no se inventa.");
}

// ═══ 16 · Agradecimientos y créditos ══════════════════════════════════════════
{
  // Esta diapositiva NO se retira. La plantilla de Slidesgo se descargó con
  // cuenta gratuita, y esa licencia permite modificarla y usarla con fines
  // comerciales a cambio de conservar la diapositiva de agradecimiento.
  const s = newSlide(true);

  echo(s, M, 1.5, CW, "GRACIAS · GRACIAS · GRACIAS · GRACIAS · GRACIAS", 22, "1A423F", "center");
  s.addText("Gracias", {
    x: M, y: 2.0, w: CW, h: 1.2, margin: 0, align: "center", valign: "middle",
    fontFace: SERIF, fontSize: 62, bold: true, color: WHITE,
  });
  s.addText("¿Alguna pregunta?", {
    x: M, y: 3.25, w: CW, h: 0.42, margin: 0, align: "center", valign: "middle",
    fontFace: SANS, fontSize: 16, color: TEALLT,
  });

  card(s, { x: 2.6, y: 4.1, w: 8.13, h: 1.72, fill: DEEP, line: EDGE, shadow: false });
  s.addText("CRÉDITOS", { x: 2.6, y: 4.3, w: 8.13, h: 0.28, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 10, bold: true, charSpacing: 1.5, color: TEALLT });
  s.addText("Plantilla de presentación: «Pitch Deck Minitheme», de Slidesgo.\nIconos y recursos gráficos: Flaticon y Freepik.", {
    x: 3.0, y: 4.62, w: 7.33, h: 0.7, margin: 0, align: "center", valign: "top", fontFace: SANS, fontSize: 13, color: PALE, lineSpacing: 21,
  });
  s.addText("Esta diapositiva se conserva por la licencia de la plantilla.", {
    x: 3.0, y: 5.36, w: 7.33, h: 0.32, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 11, italic: true, color: FAINT,
  });

  s.addText("Presentación generada desde un script, no montada a mano: su fuente es el documento de diseño del proyecto.", {
    x: M, y: 6.15, w: CW, h: 0.4, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 11, color: FAINT,
  });

  foot(s, true);
  s.addNotes("Diapositiva de agradecimiento y créditos. Es obligatoria: la plantilla «Pitch Deck Minitheme» de Slidesgo se descargó con cuenta gratuita, y su licencia exige conservar la diapositiva de agradecimiento en cualquier presentación derivada. No se retira mientras esta presentación salga fuera. Ver docs/common/marketing/README.md.");
}

const out = process.argv[2] || "DRP-comercial.pptx";
pres.writeFile({ fileName: out }).then(() => console.log("OK ->", out));
