/**
 * Genera DRP-resumen.pptx, el resumen del README principal.
 *
 * Este script es la fuente editable del .pptx: la presentación no se retoca a
 * mano, se regenera. Procedimiento y verificación en
 * ../../skills/SKILL-001-readme-to-deck.md.
 *
 *   npm install pptxgenjs
 *   node build-drp-resumen.js ../DRP-resumen.pptx
 *
 * Refleja el estado del README a 2026-08-19: **Fase 1 (core) y Fase 2 (módulos
 * activables) cerradas**. Al cambiar el README de forma sustantiva, actualiza el
 * contenido de aquí y vuelve a ejecutarlo.
 *
 * Ojo con una trampa de este fichero: no falla ni avisa cuando se queda atrás,
 * simplemente sigue generando un deck que ya no es cierto. Pasó: al cerrar la
 * Fase 2 seguía diciendo «Fase 1 en curso», nueve ADR y cuatro módulos por
 * diseñar, nueve días después de que las tres cosas dejaran de ser verdad. Los
 * cuatro datos que más rápido caducan, y que hay que repasar siempre, son **la
 * fase, el número de ADR, el de operaciones del contrato y cuántos módulos hay
 * construidos**.
 */
const pptxgen = require("pptxgenjs");

// ── Paleta ────────────────────────────────────────────────────────────────────
const INK    = "12312F"; // pino profundo (fondos oscuros, titulares)
const INK2   = "1C4644"; // pino medio
const TEAL   = "2E7B72"; // primario
const TEALLT = "7FC8BE"; // teal claro (sobre oscuro)
const TERRA  = "C25A32"; // acento terracota
const TINT   = "EFF4F2"; // relleno de tarjeta claro
const TINT2  = "E3EDEA"; // relleno de tarjeta claro alternativo
const BODY   = "24302F";
const MUTED  = "5F706E";
const WHITE  = "FFFFFF";

const SERIF = "Cambria";
const SANS  = "Calibri";
const MONO  = "Courier New";

const W = 13.333, H = 7.5, M = 0.6, CW = W - 2 * M; // 12.133

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE";
pres.author = "DRP";
pres.title = "DRP · Domestic Resource Planning — resumen del README";

// ── Helpers ───────────────────────────────────────────────────────────────────
const sh = (o = {}) => ({ type: "outer", color: "0B1F1E", blur: 10, offset: 2, angle: 90, opacity: 0.10, ...o });

function card(slide, o) {
  slide.addShape(pres.ShapeType.roundRect, {
    x: o.x, y: o.y, w: o.w, h: o.h,
    fill: { color: o.fill || TINT },
    line: o.line ? { color: o.line, width: o.lineW || 1 } : { color: o.fill || TINT, width: 0.5 },
    rectRadius: o.r === undefined ? 0.09 : o.r,
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

// ═══ 1 · Portada ══════════════════════════════════════════════════════════════
{
  const s = newSlide(true);

  s.addText("DRP", { x: 0.9, y: 1.35, w: 6.2, h: 1.65, margin: 0, valign: "bottom", fontFace: SERIF, fontSize: 92, bold: true, color: WHITE });
  s.addText("Domestic Resource Planning", { x: 0.95, y: 3.02, w: 6.2, h: 0.45, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 24, color: TEALLT });
  s.addText("Un ERP para el hogar: un core mínimo que resuelve lo esencial y módulos que se activan cuando aparece la necesidad.", {
    x: 0.95, y: 3.6, w: 5.9, h: 1.0, margin: 0, valign: "top", fontFace: SANS, fontSize: 15, color: "C4D4D1", lineSpacing: 22,
  });

  const chips = [
    ["Fases 1 y 2 cerradas", TEAL],
    ["Core + 4 módulos", "24534F"],
    ["2026-08-19", "24534F"],
  ];
  let cx = 0.95;
  chips.forEach(([t, c]) => {
    const cwid = 0.28 + t.length * 0.095;
    s.addShape(pres.ShapeType.roundRect, { x: cx, y: 4.95, w: cwid, h: 0.42, fill: { color: c }, line: { color: c, width: 0.5 }, rectRadius: 0.2 });
    s.addText(t, { x: cx, y: 4.95, w: cwid, h: 0.42, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 11.5, bold: true, color: WHITE });
    cx += cwid + 0.18;
  });

  s.addText("Resumen del README.md — documento de diseño del core", {
    x: 0.95, y: 5.62, w: 6, h: 0.3, margin: 0, valign: "middle", fontFace: SANS, fontSize: 11, color: "7E9895",
  });

  // Motivo: jerarquía anidada (ubicación polimórfica)
  const nest = [
    { x: 7.75, y: 1.35, w: 4.95, h: 4.75, t: 92, label: "Ubicación · Vivienda" },
    { x: 8.15, y: 2.0,  w: 4.15, h: 3.6,  t: 86, label: "Ubicación · Planta baja" },
    { x: 8.55, y: 2.62, w: 3.35, h: 2.42, t: 78, label: "Asset · Trastero" },
    { x: 8.95, y: 3.24, w: 2.55, h: 1.24, t: 68, label: "Asset · Estantería" },
  ];
  nest.forEach((n, i) => {
    s.addShape(pres.ShapeType.roundRect, {
      x: n.x, y: n.y, w: n.w, h: n.h, rectRadius: 0.1,
      fill: { color: i > 1 ? TERRA : TEALLT, transparency: n.t },
      line: { color: i > 1 ? TERRA : TEALLT, width: 1 },
    });
    s.addText(n.label, {
      x: n.x + 0.16, y: n.y + 0.1, w: n.w - 0.32, h: 0.28, margin: 0, valign: "middle",
      fontFace: SANS, fontSize: 10.5, bold: true, color: i > 1 ? "F0C3B0" : "CFE7E2",
    });
  });
  s.addText("La ubicación es polimórfica: un asset puede estar en una ubicación o dentro de otro asset.", {
    x: 7.75, y: 6.25, w: 4.95, h: 0.72, margin: 0, valign: "top", fontFace: SANS, fontSize: 10.5, italic: true, color: "7E9895", lineSpacing: 15,
  });

  s.addNotes("Portada. Resumen del README.md de DRP (estado 2026-08-19): Fases 0, 1 y 2 cerradas. El core completo y los cuatro módulos de prioridad alta, activables hogar por hogar.");
}

// ═══ 2 · Problema y visión ════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "OBJETIVO DEL PROYECTO", "La información del hogar vive dispersa");
  s.addText("Hojas de cálculo, carpetas de papeles, recordatorios sueltos del móvil y la memoria de quien gestiona la casa. No hay punto único de verdad, y mucho menos algo que crezca con las necesidades de cada hogar.", {
    x: M, y: 1.45, w: CW, h: 0.62, margin: 0, valign: "top", fontFace: SANS, fontSize: 14, color: MUTED, lineSpacing: 20,
  });

  const cw = 5.85, cy = 2.28, chh = 3.5;
  card(s, { x: M, y: cy, w: cw, h: chh, fill: TINT });
  badge(s, M + 0.42, cy + 0.4, 0.6, "–", TERRA, WHITE, 24);
  s.addText("Sin DRP", { x: M + 1.18, y: cy + 0.42, w: 3.5, h: 0.56, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 21, bold: true, color: INK });
  s.addText(lines([
    "La fecha de la ITV, en el calendario del móvil",
    "El manual de la caldera, en un cajón",
    "El inventario del garaje, «en la cabeza»",
    "El mantenimiento, recordado por costumbre",
  ], { bullet: true }), {
    x: M + 0.45, y: cy + 1.25, w: cw - 0.9, h: 1.95, margin: 0, valign: "top",
    fontFace: SANS, fontSize: 14, color: BODY, paraSpaceAfter: 8, lineSpacing: 20,
  });

  const rx = M + cw + 0.43;
  card(s, { x: rx, y: cy, w: cw, h: chh, fill: TEAL });
  badge(s, rx + 0.42, cy + 0.4, 0.6, "+", WHITE, TEAL, 22);
  s.addText("Con DRP", { x: rx + 1.18, y: cy + 0.42, w: 3.5, h: 0.56, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 21, bold: true, color: WHITE });
  s.addText(lines([
    "Todos los assets dados de alta, con su documentación",
    "Ubicación, propietario y estado en un único sitio",
    "Al activar el módulo CMMS, los planes de revisión se generan solos",
    "Nada de lo ya cargado hay que rehacerlo",
  ], { bullet: true }), {
    x: rx + 0.45, y: cy + 1.25, w: cw - 0.9, h: 1.95, margin: 0, valign: "top",
    fontFace: SANS, fontSize: 14, color: WHITE, paraSpaceAfter: 8, lineSpacing: 20,
  });

  s.addText("Visión: aplicar al hogar la disciplina de gestión de un ERP, sin obligar a implementar de golpe lo que ese hogar concreto no necesita.", {
    x: M, y: 6.0, w: CW, h: 0.5, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, italic: true, color: TEAL,
  });
  foot(s);
  s.addNotes("README §1 y §2: resumen ejecutivo y objetivo del proyecto.");
}

// ═══ 3 · Analogía ERP → DRP ═══════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "ANALOGÍA", "Del ERP empresarial al hogar");

  const rows = [
    ["Activos productivos (maquinaria, líneas)", "Electrodomésticos, vehículos, mobiliario, herramientas"],
    ["Mantenimiento preventivo/correctivo (CMMS)", "Revisión de caldera, ITV, cambio de filtros, garantías"],
    ["Gestión de almacén / inventario", "Despensa, garaje, trastero, botiquín"],
    ["Compras y aprovisionamiento", "Lista de la compra, reposición de lo que se agota"],
    ["Maestro de proveedores", "Fontanero, servicio técnico de la caldera, taller"],
    ["Planificación de producción / tareas", "Tareas domésticas, turnos, rutinas familiares, menú semanal"],
    ["Gestión de proyectos / eventos puntuales", "Mudanzas, reformas, celebraciones, viajes"],
  ];
  const lw = 5.1, rw = 5.95, ax = M + lw + 0.35, rx = ax + 0.68, rh = 0.6;
  let y = 1.78;
  s.addText("ERP EMPRESARIAL", { x: M, y: 1.46, w: lw, h: 0.26, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10, bold: true, charSpacing: 1.5, color: MUTED });
  s.addText("DRP · HOGAR", { x: rx, y: 1.46, w: rw, h: 0.26, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10, bold: true, charSpacing: 1.5, color: TEAL });
  rows.forEach(([a, b]) => {
    card(s, { x: M, y, w: lw, h: rh, fill: TINT, shadow: false });
    s.addText(a, { x: M + 0.28, y, w: lw - 0.56, h: rh, margin: 0, valign: "middle", fontFace: SANS, fontSize: 12.5, color: BODY });
    s.addShape(pres.ShapeType.rightArrow, { x: ax, y: y + 0.16, w: 0.5, h: 0.28, fill: { color: "C3D2CF" }, line: { color: "C3D2CF", width: 0.5 } });
    card(s, { x: rx, y, w: rw, h: rh, fill: TINT2, shadow: false });
    s.addText(b, { x: rx + 0.28, y, w: rw - 0.56, h: rh, margin: 0, valign: "middle", fontFace: SANS, fontSize: 12.5, bold: true, color: INK2 });
    y += 0.68;
  });
  foot(s);
  s.addNotes("README §3: analogía ERP → DRP.");
}

// ═══ 4 · Core mínimo ══════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "ALCANCE FUNCIONAL", "El core mínimo, obligatorio");
  s.addText("Seis piezas que todo hogar necesita antes de activar ningún módulo.", {
    x: M, y: 1.42, w: CW, h: 0.32, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, color: MUTED,
  });

  const items = [
    ["Gestión del hogar", "Unidad de aislamiento multi-tenant: agrupa usuarios, assets, ubicaciones y préstamos de una misma vivienda."],
    ["Recursos y assets", "Alta, baja, modificación, categorización, ubicación jerárquica, propietario y documentación asociada."],
    ["Ubicaciones", "Estructura jerárquica de espacios físicos, con capacidad y condiciones de almacenaje."],
    ["Usuarios y roles", "Autenticación y roles, incluidos accesos acotados para los préstamos entre personas."],
    ["Event bus interno", "Canal entre módulos: el core funciona igual esté activo el módulo o no."],
    ["API REST autenticada", "Único canal de comunicación entre el backend y el frontend."],
  ];
  const cw = 3.83, ch = 1.98, gap = 0.32;
  items.forEach((it, i) => {
    const col = i % 3, row = Math.floor(i / 3);
    const x = M + col * (cw + gap), y = 1.95 + row * (ch + 0.32);
    card(s, { x, y, w: cw, h: ch, fill: TINT });
    badge(s, x + 0.35, y + 0.32, 0.52, String(i + 1), i < 3 ? TEAL : INK2);
    s.addText(it[0], { x: x + 1.02, y: y + 0.33, w: cw - 1.35, h: 0.5, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 16, bold: true, color: INK });
    s.addText(it[1], { x: x + 0.35, y: y + 1.0, w: cw - 0.7, h: 1.0, margin: 0, valign: "top", fontFace: SANS, fontSize: 12, color: MUTED, lineSpacing: 17 });
  });
  foot(s);
  s.addNotes("README §4.1: core mínimo obligatorio.");
}

// ═══ 5 · Asset: duradero vs consumible ════════════════════════════════════════
{
  const s = newSlide();
  head(s, "MODELO DE DOMINIO", "Un asset es todo el material del hogar");
  s.addText("Desde una caldera hasta un paquete de harina. Restringir el concepto a los bienes «importantes» dejaría fuera la mayor parte de lo que un hogar realmente gestiona. Pero no todo se comporta igual:", {
    x: M, y: 1.4, w: CW, h: 0.62, margin: 0, valign: "top", fontFace: SANS, fontSize: 14, color: MUTED, lineSpacing: 20,
  });

  const cw = 5.85, cy = 2.18, chh = 3.65;
  const cols = [
    { x: M, fill: TINT, head: "DURABLE", chip: TEAL, rows: [
      ["Qué es", "Identidad propia; se usa de forma repetida sin agotarse"],
      ["Cómo se cuenta", "Una fila por unidad física"],
      ["Ejemplos", "Caldera, taladro, sofá, coche, cuadro"],
    ], note: "Único que puede ubicar a otros assets y único que se presta." },
    { x: M + cw + 0.43, fill: TINT, head: "CONSUMABLE", chip: TERRA, rows: [
      ["Qué es", "Se agota o se repone; las unidades son intercambiables"],
      ["Cómo se cuenta", "Una fila por existencia —un artículo en una ubicación—, con cantidad"],
      ["Ejemplos", "Harina, detergente, pilas, bombillas"],
    ], note: "Nunca está LENT; llegar a cantidad 0 no lo da de baja." },
  ];
  cols.forEach((c) => {
    card(s, { x: c.x, y: cy, w: cw, h: chh, fill: c.fill });
    s.addShape(pres.ShapeType.roundRect, { x: c.x + 0.4, y: cy + 0.35, w: 2.1, h: 0.44, fill: { color: c.chip }, line: { color: c.chip, width: 0.5 }, rectRadius: 0.22 });
    s.addText(c.head, { x: c.x + 0.4, y: cy + 0.35, w: 2.1, h: 0.44, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 13, bold: true, charSpacing: 1, color: WHITE });
    let ry = cy + 1.0;
    c.rows.forEach(([k, v]) => {
      s.addText(k.toUpperCase(), { x: c.x + 0.4, y: ry, w: cw - 0.8, h: 0.22, margin: 0, fontFace: SANS, fontSize: 9.5, bold: true, charSpacing: 1, color: MUTED });
      s.addText(v, { x: c.x + 0.4, y: ry + 0.22, w: cw - 0.8, h: 0.5, margin: 0, valign: "top", fontFace: SANS, fontSize: 13.5, color: BODY, lineSpacing: 18 });
      ry += 0.76;
    });
    s.addText(c.note, { x: c.x + 0.4, y: cy + 3.18, w: cw - 0.8, h: 0.42, margin: 0, valign: "top", fontFace: SANS, fontSize: 12, italic: true, color: c.chip, lineSpacing: 16 });
  });

  s.addText([
    { text: "El tipo se fija en el alta y no es modificable: ", options: { bold: true, color: INK } },
    { text: "cambiar la naturaleza de un asset equivale a darlo de baja y crear otro. El core solo mantiene un contador — consumos, mínimos, caducidad y lotes son del módulo Warehouse.", options: { color: MUTED } },
  ], { x: M, y: 6.02, w: CW, h: 0.62, margin: 0, valign: "top", fontFace: SANS, fontSize: 13, lineSpacing: 19 });
  foot(s);
  s.addNotes("README §4.1.1: naturaleza DURABLE / CONSUMABLE y alcance deliberado del core.");
}

// ═══ 6 · Artículo y existencia ════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "CATÁLOGO Y EXISTENCIAS", "Definición y existencia van separadas");
  s.addText("Un artículo no es un asset: no ocupa sitio, no tiene cantidad y no se presta. Es obligatorio en un CONSUMABLE y opcional en un DURABLE, donde permite compartir modelo y documentación entre unidades idénticas.", {
    x: M, y: 1.38, w: CW, h: 0.6, margin: 0, valign: "top", fontFace: SANS, fontSize: 14, color: MUTED, lineSpacing: 20,
  });

  const ay = 2.08, ah = 2.6;
  card(s, { x: M, y: ay, w: 4.2, h: ah, fill: TINT, line: TEAL, lineW: 1.25 });
  s.addText("Artículo", { x: M + 0.35, y: ay + 0.28, w: 3.5, h: 0.4, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 19, bold: true, color: INK });
  s.addText("tabla articles", { x: M + 0.35, y: ay + 0.66, w: 3.5, h: 0.26, margin: 0, fontFace: MONO, fontSize: 10.5, color: TEAL });
  // El nombre de la categoría es dato del hogar y se muestra al usuario: va en
  // castellano, a diferencia de la unidad, que sí es un enumerado.
  s.addText(lines([
    "name · Azúcar",
    "categoryId · Alimentación",
    "unit · GRAM",
    "brand, barcode (opcionales)",
  ]), { x: M + 0.35, y: ay + 1.05, w: 3.5, h: 1.35, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, color: BODY, lineSpacing: 19 });

  s.addShape(pres.ShapeType.rightArrow, { x: 4.95, y: ay + 1.05, w: 0.62, h: 0.32, fill: { color: TEAL }, line: { color: TEAL, width: 0.5 } });
  s.addText("define", { x: 4.85, y: ay + 1.42, w: 0.85, h: 0.24, margin: 0, align: "center", fontFace: SANS, fontSize: 10, color: MUTED });

  const ex = [
    { x: 5.78, loc: "Despensa", qty: "1.300", u: "g" },
    { x: 9.43, loc: "Trastero", qty: "500", u: "g" },
  ];
  ex.forEach((e) => {
    card(s, { x: e.x, y: ay, w: 3.3, h: ah, fill: TINT2 });
    s.addText("Asset CONSUMABLE", { x: e.x + 0.32, y: ay + 0.28, w: 2.7, h: 0.26, margin: 0, fontFace: SANS, fontSize: 10, bold: true, charSpacing: 1, color: TERRA });
    s.addText("Existencia · " + e.loc, { x: e.x + 0.32, y: ay + 0.56, w: 2.7, h: 0.36, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 16, bold: true, color: INK });
    s.addText([
      { text: e.qty, options: { fontSize: 34, bold: true, color: INK2, fontFace: SERIF } },
      { text: " " + e.u, options: { fontSize: 15, color: MUTED, fontFace: SANS } },
    ], { x: e.x + 0.32, y: ay + 1.02, w: 2.7, h: 0.7, margin: 0, valign: "middle" });
    s.addText("ubicación · propietario · estado", { x: e.x + 0.32, y: ay + 1.82, w: 2.7, h: 0.5, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, color: MUTED, lineSpacing: 16 });
  });

  const rules = [
    ["La unidad la fija el artículo", "Todas sus existencias van en la misma unidad; convertir es cosa de Warehouse."],
    ["Una existencia viva por artículo y ubicación", "Índice único parcial con NULLS NOT DISTINCT que excluye status = DECOMMISSIONED."],
    ["La entrada suma; el PATCH sustituye", "RegisterConsumableIntake acumula; la cantidad del PATCH es absoluta."],
  ];
  const rw = 3.83;
  rules.forEach((r, i) => {
    const x = M + i * (rw + 0.32);
    card(s, { x, y: 5.0, w: rw, h: 1.45, fill: WHITE, line: "D3DFDC", shadow: false });
    s.addText(r[0], { x: x + 0.28, y: 5.16, w: rw - 0.56, h: 0.5, margin: 0, valign: "top", fontFace: SANS, fontSize: 13, bold: true, color: INK, lineSpacing: 17 });
    s.addText(r[1], { x: x + 0.28, y: 5.7, w: rw - 0.56, h: 0.62, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, color: MUTED, lineSpacing: 16 });
  });
  foot(s);
  s.addNotes("README §4.1.1: artículo y existencia; RegisterConsumableIntake y MergeStockItems.");
}

// ═══ 7 · Reglas de negocio ════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "REGLAS MÍNIMAS DE NEGOCIO", "Lo que el core no deja hacer");

  const rules = [
    ["Sin ciclos en la jerarquía", "Ningún asset ni ubicación puede ser su propio ancestro."],
    ["Una ubicación, nunca dos", "O un Asset o una Location, jamás las dos a la vez."],
    ["La baja es siempre lógica", "status = DECOMMISSIONED; nada se borra. No se da de baja con hijos o préstamo abierto."],
    ["Solo un DURABLE ubica y se presta", "Una estantería contiene cosas; un paquete de harina, no."],
    ["Llegar a cero no da de baja nada", "Un consumible agotado sigue existiendo, pendiente de reposición."],
    ["El artículo se retira, no se borra", "retired_at cuando no le queda existencia viva: las bajas siguen apuntando a él."],
  ];
  const cw = 5.85, ch = 1.24;
  rules.forEach((r, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = M + col * (cw + 0.43), y = 1.72 + row * (ch + 0.24);
    card(s, { x, y, w: cw, h: ch, fill: TINT, shadow: false });
    badge(s, x + 0.32, y + 0.3, 0.44, "×", col === 0 ? TEAL : TERRA, WHITE, 16);
    s.addText(r[0], { x: x + 0.94, y: y + 0.2, w: cw - 1.3, h: 0.4, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14.5, bold: true, color: INK });
    s.addText(r[1], { x: x + 0.94, y: y + 0.62, w: cw - 1.3, h: 0.55, margin: 0, valign: "top", fontFace: SANS, fontSize: 12, color: MUTED, lineSpacing: 16 });
  });

  s.addText("Juntar dos existencias del mismo artículo creadas por separado es MergeStockItems, no un MoveAsset: la fusión decide qué ubicación y qué propietario sobreviven, y eso lo elige el usuario.", {
    x: M, y: 6.16, w: CW, h: 0.56, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, italic: true, color: TEAL, lineSpacing: 18,
  });
  foot(s);
  s.addNotes("README §4.1.1 y §4.1.7: reglas mínimas de negocio y decisiones validadas.");
}

// ═══ 8 · Ubicaciones y jerarquía ══════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "UBICACIONES", "Jerarquía y ubicación polimórfica");

  // leyenda
  const leg = [["Location", TEAL], ["Asset", TERRA]];
  let lx = M;
  leg.forEach(([t, c]) => {
    circle(s, lx, 1.46, 0.2, c);
    s.addText(t, { x: lx + 0.28, y: 1.4, w: 1.2, h: 0.32, margin: 0, valign: "middle", fontFace: SANS, fontSize: 11.5, color: MUTED });
    lx += 1.35;
  });

  const tree = [
    { x: 0.6,  y: 1.95, w: 3.4, t: "Vivienda", c: TEAL },
    { x: 1.15, y: 2.85, w: 3.4, t: "Planta baja", c: TEAL },
    { x: 1.7,  y: 3.75, w: 3.4, t: "Trastero", c: TERRA },
    { x: 2.25, y: 4.65, w: 3.4, t: "Estantería de trastero", c: TERRA },
    { x: 2.25, y: 5.55, w: 3.4, t: "Mesa de trabajo", c: TERRA },
  ];
  tree.forEach((n, i) => {
    if (i > 0) {
      const p = tree[i === 4 ? 2 : i - 1];
      const vx = p.x + 0.3;
      const top = (i === 4 ? tree[2].y + 0.68 : p.y + 0.68);
      s.addShape(pres.ShapeType.line, { x: vx, y: top, w: 0, h: n.y + 0.34 - top, line: { color: "C3D2CF", width: 1.25 } });
      s.addShape(pres.ShapeType.line, { x: vx, y: n.y + 0.34, w: n.x - vx, h: 0, line: { color: "C3D2CF", width: 1.25 } });
    }
    card(s, { x: n.x, y: n.y, w: n.w, h: 0.68, fill: n.c === TEAL ? TINT2 : "F7E9E2", shadow: false });
    circle(s, n.x + 0.26, n.y + 0.24, 0.2, n.c);
    s.addText(n.t, { x: n.x + 0.6, y: n.y, w: n.w - 0.8, h: 0.68, margin: 0, valign: "middle", fontFace: SANS, fontSize: 13.5, bold: true, color: INK });
  });

  const rx = 6.6, rw = W - M - rx;
  card(s, { x: rx, y: 1.95, w: rw, h: 4.55, fill: TINT });
  s.addText("Atributos mínimos de una Location", { x: rx + 0.4, y: 2.2, w: rw - 0.8, h: 0.36, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 17, bold: true, color: INK });
  s.addText(lines([
    "Identificador y nombre",
    "Ubicación padre (opcional, para la jerarquía)",
    "Capacidad: volumen, peso máximo o nº de unidades",
    "Condiciones ambientales: temperatura, humedad, luz",
    "Notas y observaciones libres",
  ], { bullet: true }), {
    x: rx + 0.45, y: 2.68, w: rw - 0.9, h: 1.7, margin: 0, valign: "top", fontFace: SANS, fontSize: 13, color: BODY, paraSpaceAfter: 6, lineSpacing: 19,
  });
  s.addText("Reglas", { x: rx + 0.4, y: 4.55, w: rw - 0.8, h: 0.32, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 17, bold: true, color: INK });
  s.addText(lines([
    "Una ubicación no puede ser su propia ancestra",
    "Si se informa capacidad, el sistema debería avisar al superarla (bloquear o no, por definir)",
    "Una Location no es un recurso del hogar: es el contenedor físico",
  ], { bullet: true }), {
    x: rx + 0.45, y: 4.97, w: rw - 0.9, h: 1.4, margin: 0, valign: "top", fontFace: SANS, fontSize: 13, color: BODY, paraSpaceAfter: 6, lineSpacing: 19,
  });
  foot(s);
  s.addNotes("README §4.1.2: ubicaciones, jerarquía y atributos.");
}

// ═══ 9 · Usuarios y roles ═════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "USUARIOS", "Cuatro roles, dos alcances");
  s.addText("Los roles estructurales pertenecen a usuarios del hogar con cuenta completa; los contextuales van ligados a un préstamo concreto y pueden recaer en personas externas.", {
    x: M, y: 1.42, w: CW, h: 0.5, margin: 0, valign: "top", fontFace: SANS, fontSize: 14, color: MUTED, lineSpacing: 19,
  });

  const roles = [
    ["Administrador del hogar", "Estructural", "Todo el hogar", "CRUD completo de assets, ubicaciones y usuarios; gestión de roles; activar y desactivar módulos.", TEAL],
    ["Miembro del hogar", "Estructural", "Todo el hogar", "CRUD de assets y ubicaciones; inicia y gestiona préstamos. Sin gestión de usuarios ni módulos.", TEAL],
    ["Prestador", "Contextual", "Un préstamo concreto", "Consulta el estado del préstamo y confirma la entrega del asset.", TERRA],
    ["Receptor del préstamo", "Contextual", "Un préstamo concreto", "Consulta el estado y la fecha prevista; confirma la devolución.", TERRA],
  ];
  const cw = 2.9, gap = 0.18, cy = 2.12, chh = 3.15;
  roles.forEach((r, i) => {
    const x = M + i * (cw + gap);
    card(s, { x, y: cy, w: cw, h: chh, fill: TINT });
    s.addShape(pres.ShapeType.roundRect, { x: x + 0.28, y: cy + 0.3, w: 1.36, h: 0.34, fill: { color: r[4] }, line: { color: r[4], width: 0.5 }, rectRadius: 0.17 });
    s.addText(r[1], { x: x + 0.28, y: cy + 0.3, w: 1.36, h: 0.34, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 10, bold: true, color: WHITE });
    s.addText(r[0], { x: x + 0.28, y: cy + 0.78, w: cw - 0.56, h: 0.85, margin: 0, valign: "top", fontFace: SERIF, fontSize: 17, bold: true, color: INK, lineSpacing: 22 });
    s.addText(r[2].toUpperCase(), { x: x + 0.28, y: cy + 1.66, w: cw - 0.56, h: 0.24, margin: 0, fontFace: SANS, fontSize: 9.5, bold: true, charSpacing: 1, color: MUTED });
    s.addText(r[3], { x: x + 0.28, y: cy + 1.98, w: cw - 0.56, h: 1.2, margin: 0, valign: "top", fontFace: SANS, fontSize: 12, color: BODY, lineSpacing: 17 });
  });

  s.addText("El acceso acotado por token (JWT con loanId y rol, sin sub de usuario) se aplica únicamente cuando la persona no tiene cuenta completa en el sistema.", {
    x: M, y: 5.55, w: CW, h: 0.5, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, italic: true, color: TEAL, lineSpacing: 18,
  });
  foot(s);
  s.addNotes("README §4.1.4 y §5.4.1: roles y tokens acotados.");
}

// ═══ 10 · Préstamos ═══════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "PRÉSTAMOS", "Un concepto mínimo dentro del core");

  // Son los estados del préstamo, no los del asset: el asset solo acompaña, y
  // mezclar ambas máquinas es lo que hacía esta diapositiva antes.
  const st = [
    { x: 0.9, t: "ACTIVE", c: TEAL },
    { x: 5.05, t: "OVERDUE", c: TERRA },
    { x: 9.2, t: "RETURNED", c: INK2 },
  ];
  st.forEach((n) => {
    card(s, { x: n.x, y: 1.72, w: 3.2, h: 0.95, fill: n.c });
    s.addText(n.t, { x: n.x, y: 1.72, w: 3.2, h: 0.95, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 16, bold: true, charSpacing: 1, color: WHITE });
  });
  [[4.25, "el proceso diario ve la fecha superada"], [8.4, "devolución confirmada"]].forEach(([ax, lbl]) => {
    s.addShape(pres.ShapeType.rightArrow, { x: ax, y: 2.05, w: 0.62, h: 0.3, fill: { color: "AFC2BF" }, line: { color: "AFC2BF", width: 0.5 } });
    s.addText(lbl, { x: ax - 1.0, y: 2.7, w: 2.6, h: 0.42, margin: 0, align: "center", valign: "top", fontFace: SANS, fontSize: 10, color: MUTED, lineSpacing: 13 });
  });
  card(s, { x: 0.9, y: 3.16, w: 11.5, h: 0.42, fill: TINT2, line: "C3D2CF", shadow: false });
  s.addText("Un ACTIVE también se devuelve sin pasar por OVERDUE · el asset acompaña: LENT mientras el préstamo está abierto, AVAILABLE al devolverlo", { x: 1.0, y: 3.16, w: 11.3, h: 0.42, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 11.5, bold: true, color: INK2 });

  const cw = 5.85, cy = 3.92, chh = 2.35;
  card(s, { x: M, y: cy, w: cw, h: chh, fill: TINT });
  s.addText("Atributos mínimos", { x: M + 0.4, y: cy + 0.28, w: cw - 0.8, h: 0.36, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 17, bold: true, color: INK });
  s.addText(lines([
    "Identificador y asset prestado",
    "Prestador y receptor: del hogar o personas externas",
    "Fecha de inicio, de devolución prevista y real",
    "Estado: ACTIVE, RETURNED u OVERDUE",
  ], { bullet: true }), {
    x: M + 0.45, y: cy + 0.78, w: cw - 0.9, h: 1.45, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, color: BODY, paraSpaceAfter: 6, lineSpacing: 18,
  });

  const rx = M + cw + 0.43;
  card(s, { x: rx, y: cy, w: cw, h: chh, fill: TINT2 });
  s.addText("Reglas y alcance", { x: rx + 0.4, y: cy + 0.28, w: cw - 0.8, h: 0.36, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 17, bold: true, color: INK });
  s.addText(lines([
    "Un solo préstamo abierto por asset: un OVERDUE sigue ocupándolo",
    "Solo se prestan assets DURABLE",
    "Ceder un consumible es un AdjustAssetQuantity, no un préstamo",
    "Si el préstamo crece, saldrá del core como módulo propio",
  ], { bullet: true }), {
    x: rx + 0.45, y: cy + 0.78, w: cw - 0.9, h: 1.45, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, color: BODY, paraSpaceAfter: 6, lineSpacing: 18,
  });
  foot(s);
  s.addNotes("README §4.1.5: préstamos, máquina de estados y alcance mínimo.");
}

// ═══ 11 · Módulos futuros ═════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "MÓDULOS", "Trece, activables por hogar — cuatro ya construidos");
  s.addText("Ninguno es obligatorio: el core funciona con todos apagados. Los enciende el administrador de su hogar, y apagarlos conserva los datos.", {
    x: M, y: 1.42, w: CW, h: 0.32, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, color: MUTED,
  });

  // Cada banda es una prioridad de la sección 4.2. El detalle decrece con ella:
  // los de prioridad alta llevan descripción, los de baja solo el nombre.
  const bands = [
    {
      label: "ALTA · HECHOS", y: 1.9, h: 1.46, accent: TEAL,
      fill: TEAL, line: TEAL, title: WHITE, desc: "CFE4E0",
      mods: [
        ["Proveedores y contactos de servicio", "Quién arregla, quién cobra y quién responde de una garantía."],
        ["Compras y lista de la compra", "Qué falta, qué reponer y qué está pedido. Cierra el ciclo de Warehouse."],
        ["Warehouse", "Despensa, garaje y trastero: stock, consumo, mínimos y caducidad."],
        ["Mantenimiento (CMMS)", "Preventivo y correctivo: planes, avisos que se rearman e histórico."],
      ],
    },
    {
      label: "PRIORIDAD MEDIA", y: 3.52, h: 1.22, accent: INK2,
      fill: TINT2, line: "C3D2CF", title: INK, desc: MUTED,
      mods: [
        ["Planificador de tareas", "Rutinas, turnos entre miembros del hogar y recordatorios."],
        ["Gastos y presupuesto", "Lo que cuesta lo que entra en casa, y el presupuesto por periodo."],
        ["Gestión de eventos temporales", "Mudanzas, reformas, viajes y celebraciones, con inicio y fin."],
      ],
    },
    {
      label: "PRIORIDAD BAJA", y: 4.9, h: 0.86, accent: "9BAFAC",
      fill: WHITE, line: "D3DFDC", title: INK2, desc: null,
      mods: [
        ["Préstamos avanzados"], ["Recetas y menú semanal"], ["Reservas de uso"],
        ["Fin de vida"], ["Garantías y seguros"], ["Mascotas y plantas"],
      ],
    },
  ];

  const railW = 1.5, mx = M + railW + 0.15, maw = W - M - mx, mgap = 0.16;
  bands.forEach((b) => {
    s.addShape(pres.ShapeType.roundRect, { x: M, y: b.y, w: 0.07, h: b.h, fill: { color: b.accent }, line: { color: b.accent, width: 0.5 }, rectRadius: 0.03 });
    s.addText(b.label, {
      x: M + 0.2, y: b.y, w: railW - 0.2, h: b.h / 2, margin: 0, valign: "bottom",
      fontFace: SANS, fontSize: 10.5, bold: true, charSpacing: 1.2, color: b.accent,
    });
    s.addText(`${b.mods.length} módulos`, {
      x: M + 0.2, y: b.y + b.h / 2, w: railW - 0.2, h: b.h / 2, margin: 0, valign: "top",
      fontFace: SANS, fontSize: 11, color: MUTED,
    });

    const mw = (maw - (b.mods.length - 1) * mgap) / b.mods.length;
    b.mods.forEach((m, i) => {
      const x = mx + i * (mw + mgap);
      card(s, { x, y: b.y, w: mw, h: b.h, fill: b.fill, line: b.line, shadow: false });
      if (b.desc) {
        s.addText(m[0], {
          x: x + 0.2, y: b.y + 0.14, w: mw - 0.4, h: 0.44, margin: 0, valign: "top",
          fontFace: SANS, fontSize: 12.5, bold: true, color: b.title, lineSpacing: 16,
        });
        s.addText(m[1], {
          x: x + 0.2, y: b.y + 0.62, w: mw - 0.4, h: b.h - 0.76, margin: 0, valign: "top",
          fontFace: SANS, fontSize: 10.5, color: b.desc, lineSpacing: 14,
        });
      } else {
        s.addText(m[0], {
          x: x + 0.14, y: b.y, w: mw - 0.28, h: b.h, margin: 0, align: "center", valign: "middle",
          fontFace: SANS, fontSize: 11, bold: true, color: b.title, lineSpacing: 15,
        });
      }
    });
  });

  s.addText("Los cuatro de prioridad alta son el alcance entero de la Fase 2 y siguen en desarrollo: el código está, el despliegue todavía no. La lista no está cerrada, pero no hay cajón de sastre.", {
    x: M, y: 6.05, w: CW, h: 0.5, margin: 0, valign: "top", fontFace: SANS, fontSize: 13, italic: true, color: TEAL, lineSpacing: 19,
  });
  foot(s);
  s.addNotes("README §4.2: módulos activables, cada uno con estado y prioridad. Trece filas, sin fila de backlog. Los cuatro de prioridad alta se construyeron en la Fase 2 y están en desarrollo; los nueve restantes siguen por diseñar.");
}

// ═══ 12 · Arquitectura ════════════════════════════════════════════════════════
{
  const s = newSlide(true);
  head(s, "ARQUITECTURA", "Dos componentes, un monolito modular", true);

  card(s, { x: M, y: 1.5, w: CW, h: 0.8, fill: "1B4B48", line: "2F6360", shadow: false });
  s.addText("Frontend Web · TypeScript + React · mobile-first, de 375 px a ultrawide", {
    x: M, y: 1.5, w: CW, h: 0.8, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 15, bold: true, color: WHITE,
  });

  s.addShape(pres.ShapeType.downArrow, { x: 6.5, y: 2.42, w: 0.33, h: 0.42, fill: { color: TEALLT }, line: { color: TEALLT, width: 0.5 } });
  s.addText("HTTPS / REST + JWT", { x: 7.0, y: 2.45, w: 3, h: 0.36, margin: 0, valign: "middle", fontFace: SANS, fontSize: 11, color: TEALLT });

  card(s, { x: M, y: 2.95, w: CW, h: 0.8, fill: TEAL, shadow: false });
  s.addText("API REST autenticada · Spring Security + JWT · único canal backend ↔ frontend", {
    x: M, y: 2.95, w: CW, h: 0.8, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 15, bold: true, color: WHITE,
  });

  // Los trece módulos van agrupados por prioridad, no uno a uno: nombrarlos aquí
  // repetiría la diapositiva anterior y llenaría la banda de tarjetas ilegibles.
  // Es el mismo criterio con el que el README redibujó su diagrama de 5.1.
  const mods = [
    ["Core", "Recursos / Assets · obligatorio", true],
    ["Prioridad alta", "4 módulos · construidos", true],
    ["Prioridad media", "3 módulos · opcionales", false],
    ["Prioridad baja", "6 módulos · opcionales", false],
  ];
  const mgap = 0.22, mw = (CW - (mods.length - 1) * mgap) / mods.length;
  mods.forEach((m, i) => {
    const x = M + i * (mw + mgap);
    card(s, { x, y: 3.92, w: mw, h: 1.0, fill: m[2] ? INK2 : "163A38", line: m[2] ? TEALLT : "2F5754", shadow: false });
    s.addText(m[0], { x: x + 0.12, y: 4.08, w: mw - 0.24, h: 0.42, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 13.5, bold: true, color: m[2] ? WHITE : "BFD3D0" });
    s.addText(m[1], { x: x + 0.12, y: 4.48, w: mw - 0.24, h: 0.3, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 10.5, italic: !m[2], color: m[2] ? TEALLT : "7E9B98" });
    s.addShape(pres.ShapeType.line, { x: x + mw / 2, y: 4.92, w: 0, h: 0.2, line: { color: "3C6663", width: 1.25, dashType: m[2] ? "solid" : "dash" } });
  });

  card(s, { x: M, y: 5.12, w: CW, h: 0.66, fill: "1B4B48", line: TEALLT, shadow: false });
  s.addText("Event Bus interno (in-process) · publicación y suscripción entre módulos, sin que el core sepa quién escucha", {
    x: M, y: 5.12, w: CW, h: 0.66, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 13.5, bold: true, color: WHITE,
  });

  s.addText("El agrupamiento es la prioridad de la sección 4.2, no una dependencia: dentro de un grupo los módulos no se necesitan entre sí.", {
    x: M, y: 5.84, w: CW, h: 0.3, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 11.5, italic: true, color: "7E9B98",
  });

  card(s, { x: 4.4, y: 6.18, w: 4.53, h: 0.58, fill: "163A38", line: "2F5754", shadow: false });
  s.addText("PostgreSQL 16+ · RLS activado", { x: 4.4, y: 6.18, w: 4.53, h: 0.58, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 13, bold: true, color: "BFD3D0" });
  foot(s, true);
  s.addNotes("README §5.1: visión de componentes. Los módulos punteados siguen por diseñar; los de trazo continuo están construidos. El detalle de los trece está en la diapositiva anterior. La Fase 2 añadió las fronteras de paquete por módulo y la activación por hogar (ADR-010).");
}

// ═══ 13 · Event bus ═══════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "EVENT BUS INTERNO", "Un contrato y trece eventos");

  card(s, { x: M, y: 1.62, w: 4.6, h: 3.1, fill: TINT });
  s.addText("DomainEvent", { x: M + 0.32, y: 1.85, w: 4, h: 0.36, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 17, bold: true, color: INK });
  s.addText(lines([
    "eventId      UUID",
    "type         String",
    "occurredAt   Instant",
    "aggregateId  String",
    "version      Int",
    "payload      JSON",
  ]), { x: M + 0.32, y: 2.32, w: 4, h: 2.35, margin: 0, valign: "top", fontFace: MONO, fontSize: 12, color: INK2, lineSpacing: 22 });

  card(s, { x: M, y: 5.0, w: 4.6, h: 1.6, fill: WHITE, line: "D3DFDC", shadow: false });
  s.addText(lines([
    "In-process: monolito modular, pub/sub en memoria",
    "Entrega at-least-once: handlers idempotentes",
    "Un fallo del módulo no afecta a la transacción del core",
    "Evolución candidata: Transactional Outbox",
  ], { bullet: true }), {
    x: M + 0.3, y: 5.17, w: 4.0, h: 1.3, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, color: BODY, paraSpaceAfter: 4, lineSpacing: 16,
  });

  const rx = 5.55, rw = W - M - rx;
  card(s, { x: rx, y: 1.62, w: rw, h: 4.98, fill: TINT2, shadow: false });
  s.addText("Catálogo inicial de eventos del core", { x: rx + 0.35, y: 1.85, w: rw - 0.7, h: 0.36, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 17, bold: true, color: INK });

  const evs = [
    ["HouseholdCreated", "un hogar queda verificado y utilizable"],
    ["ArticleCreated", "se crea un artículo en el catálogo"],
    ["AssetCreated", "se da de alta un asset o la primera existencia"],
    ["AssetMoved", "cambia la ubicación de un asset"],
    ["AssetHierarchyChanged", "cambia el asset padre o la composición"],
    ["AssetQuantityChanged", "cambia la cantidad de un CONSUMABLE"],
    ["AssetDeactivated", "se da de baja un asset o se fusiona"],
    ["LocationCreated", "se crea una ubicación"],
    ["DocumentAttached", "se adjunta un documento a un asset o artículo"],
    ["UserDeactivated", "alguien deja el hogar, no su cuenta"],
    ["LoanStarted", "se inicia un préstamo"],
    ["LoanOverdue", "el proceso diario ve la fecha superada"],
    ["LoanReturned", "se confirma la devolución"],
  ];
  let ey = 2.34;
  evs.forEach((e) => {
    circle(s, rx + 0.38, ey + 0.09, 0.13, TEAL);
    s.addText(e[0], { x: rx + 0.64, y: ey, w: 2.35, h: 0.31, margin: 0, valign: "middle", fontFace: MONO, fontSize: 10.5, bold: true, color: INK });
    s.addText("· " + e[1], { x: rx + 3.02, y: ey, w: rw - 3.42, h: 0.31, margin: 0, valign: "middle", fontFace: SANS, fontSize: 10.5, color: MUTED });
    ey += 0.31;
  });
  foot(s);
  s.addNotes("README §5.2: contrato de evento, mecanismo interno y catálogo inicial.");
}

// ═══ 14 · Multi-tenant ════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "MODELO DE DATOS", "Aislamiento multi-tenant en dos capas");
  s.addText("Varios hogares comparten la misma base de datos. El aislamiento se defiende dos veces, de forma independiente.", {
    x: M, y: 1.4, w: CW, h: 0.34, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, color: MUTED,
  });

  const layers = [
    ["1", "Aplicación", "Todo caso de uso y todo repositorio filtra por el householdId del token. Nunca se confía en un householdId recibido del cliente.", TEAL],
    ["2", "Base de datos · RLS", "Cada tabla con household_id tiene Row-Level Security y una política que limita las filas al hogar de la sesión. Si un repositorio olvida el filtro, PostgreSQL sigue sin devolver filas ajenas.", INK2],
  ];
  const cw = 5.85, cy = 1.9, chh = 2.15;
  layers.forEach((l, i) => {
    const x = M + i * (cw + 0.43);
    card(s, { x, y: cy, w: cw, h: chh, fill: TINT });
    badge(s, x + 0.35, cy + 0.32, 0.56, l[0], l[3]);
    s.addText(l[1], { x: x + 1.08, y: cy + 0.34, w: cw - 1.4, h: 0.5, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 18, bold: true, color: INK });
    s.addText(l[2], { x: x + 0.38, y: cy + 1.0, w: cw - 0.76, h: 1.0, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, color: BODY, lineSpacing: 18 });
  });

  card(s, { x: M, y: 4.25, w: 7.55, h: 1.62, fill: INK, shadow: false });
  s.addText(lines([
    "ALTER TABLE assets ENABLE ROW LEVEL SECURITY;",
    "ALTER TABLE assets FORCE ROW LEVEL SECURITY;",
    "",
    "CREATE POLICY assets_household_isolation ON assets",
    "    USING (household_id = current_setting('app.household_id')::uuid);",
  ]), { x: M + 0.3, y: 4.42, w: 7.0, h: 1.3, margin: 0, valign: "top", fontFace: MONO, fontSize: 10.5, color: "9FD8D0", lineSpacing: 15 });

  card(s, { x: 8.4, y: 4.25, w: 4.33, h: 1.62, fill: "F7E9E2", shadow: false });
  s.addText("Dos condiciones fáciles de pasar por alto", { x: 8.7, y: 4.4, w: 3.75, h: 0.32, margin: 0, valign: "middle", fontFace: SANS, fontSize: 12.5, bold: true, color: TERRA });
  s.addText(lines([
    "El usuario de BD de la aplicación no puede ser superusuario ni tener BYPASSRLS",
    "Hace falta FORCE ROW LEVEL SECURITY para que la política se aplique al propietario de la tabla",
  ], { bullet: true }), {
    x: 8.72, y: 4.76, w: 3.75, h: 1.05, margin: 0, valign: "top", fontFace: SANS, fontSize: 11, color: BODY, paraSpaceAfter: 4, lineSpacing: 15,
  });

  s.addText("La aplicación fija SET LOCAL app.household_id al abrir cada transacción; las políticas se versionan como migraciones Flyway, igual que el esquema (ADR-003 y ADR-004).", {
    x: M, y: 6.08, w: CW, h: 0.5, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, italic: true, color: TEAL, lineSpacing: 18,
  });
  foot(s);
  s.addNotes("README §5.6: modelo de datos multi-tenant y Row-Level Security.");
}

// ═══ 15 · Casos de uso ════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "CAPA DE APLICACIÓN", "Los casos de uso del core");

  card(s, { x: M, y: 1.62, w: 7.6, h: 4.75, fill: TINT });
  s.addText("Comandos · 30", { x: M + 0.38, y: 1.84, w: 7.0, h: 0.38, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 18, bold: true, color: INK });
  s.addText("Cada uno valida sus reglas y, cuando corresponde, publica su evento en el bus", {
    x: M + 0.38, y: 2.2, w: 7.0, h: 0.28, margin: 0, valign: "middle", fontFace: SANS, fontSize: 11.5, color: MUTED,
  });

  // Treinta nombres no caben legibles: van por área, con el recuento completo y
  // tres ejemplos de cada una. El catálogo entero está en el README §5.7.
  const areas = [
    ["Catálogo y assets", 11, ["CreateArticle", "RegisterConsumableIntake", "MergeStockItems"]],
    ["Hogar, identidad y acceso", 11, ["CreateHousehold", "VerifyEmail", "InviteUser"]],
    ["Documentos y ficheros", 5, ["AttachDocument", "UploadFile", "SetIdentityAvatar"]],
    ["Préstamos", 3, ["StartLoan", "ConfirmReturn", "GenerateExternalAccessToken"]],
  ];
  const aw = 3.38, ah = 1.5;
  areas.forEach((a, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = M + 0.38 + col * (aw + 0.24), y = 2.55 + row * (ah + 0.1);
    card(s, { x, y, w: aw, h: ah, fill: WHITE, line: "D3DFDC", shadow: false });
    s.addText(a[0].toUpperCase(), { x: x + 0.22, y: y + 0.16, w: aw - 0.9, h: 0.24, margin: 0, valign: "middle", fontFace: SANS, fontSize: 9, bold: true, charSpacing: 1, color: TEAL });
    s.addText(String(a[1]), { x: x + aw - 0.72, y: y + 0.12, w: 0.55, h: 0.32, margin: 0, align: "right", valign: "middle", fontFace: SERIF, fontSize: 18, bold: true, color: INK });
    s.addText(lines(a[2]), { x: x + 0.22, y: y + 0.48, w: aw - 0.44, h: 0.9, margin: 0, valign: "top", fontFace: MONO, fontSize: 9, color: MUTED, lineSpacing: 14 });
  });

  card(s, { x: M + 0.38, y: 5.78, w: 7.0 - 0.16, h: 0.5, fill: INK2, shadow: false });
  s.addText("Comandos de sistema · 3 — PurgeUnverifiedHouseholds · PurgeUnusedFiles · MarkOverdueLoans", {
    x: M + 0.38, y: 5.78, w: 7.0 - 0.16, h: 0.5, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 10, bold: true, color: "CFE1DE",
  });

  const rx = 8.5, rw = W - M - rx;
  card(s, { x: rx, y: 1.62, w: rw, h: 4.75, fill: TINT2 });
  s.addText("Consultas · 12", { x: rx + 0.38, y: 1.84, w: rw - 0.76, h: 0.38, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 18, bold: true, color: INK });
  s.addText("Lectura del catálogo, los assets y el hogar", {
    x: rx + 0.38, y: 2.2, w: rw - 0.76, h: 0.28, margin: 0, valign: "middle", fontFace: SANS, fontSize: 11.5, color: MUTED,
  });
  s.addText(lines([
    "ListArticles", "ListAssets", "GetAsset", "ListCategories",
    "ListLocations", "ListUsers", "ListInvitations", "GetLoan",
    "ListDocuments", "ListFiles", "DownloadFile", "GetStorageUsage",
  ], { bullet: true, opts: { fontFace: MONO } }), {
    x: rx + 0.42, y: 2.6, w: rw - 0.84, h: 2.6, margin: 0, valign: "top", fontFace: MONO, fontSize: 11, bold: true, color: INK, lineSpacing: 15,
  });
  s.addText("Toda consulta queda acotada al householdId del token. Los listados excluyen por defecto los assets dados de baja y los artículos retirados.", {
    x: rx + 0.42, y: 5.25, w: rw - 0.84, h: 1.0, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, italic: true, color: MUTED, lineSpacing: 17,
  });
  foot(s);
  s.addNotes("README §5.7: catálogo de comandos y queries de la capa de aplicación.");
}

// ═══ 16 · Stack ═══════════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "STACK TECNOLÓGICO", "Decisiones ya cerradas");

  const stack = [
    ["Backend", "Kotlin + Spring Boot", "Monolito modular, Clean Architecture"],
    ["Persistencia", "PostgreSQL 16+", "Row-Level Security como segunda capa"],
    ["Migraciones", "Flyway", "SQL plano versionado, esquema y políticas juntos"],
    ["Comunicación BE", "Event bus in-process", "Puerto propio sobre Spring, sin dependencia añadida"],
    ["Comunicación FE ↔ BE", "API REST + JWT", "Spring Security; tokens acotados de préstamo"],
    ["Contratos", "OpenAPI 3.0", "openapi.yaml, 98 operaciones, fuente de verdad"],
    ["Frontend", "React sobre Vite", "Mobile-first, de 375 px a ultrawide; WCAG 2.2 AA"],
    ["Testing", "JUnit 5 + Testcontainers", "Vitest y Playwright en el frontend"],
  ];
  const cw = 2.9, gap = 0.18, chh = 1.9;
  stack.forEach((t, i) => {
    const col = i % 4, row = Math.floor(i / 4);
    const x = M + col * (cw + gap), y = 1.75 + row * (chh + 0.3);
    card(s, { x, y, w: cw, h: chh, fill: TINT });
    s.addText(t[0].toUpperCase(), { x: x + 0.28, y: y + 0.26, w: cw - 0.56, h: 0.24, margin: 0, fontFace: SANS, fontSize: 9.5, bold: true, charSpacing: 1, color: TEAL });
    s.addText(t[1], { x: x + 0.28, y: y + 0.55, w: cw - 0.56, h: 0.62, margin: 0, valign: "top", fontFace: SERIF, fontSize: 15.5, bold: true, color: INK, lineSpacing: 20 });
    s.addText(t[2], { x: x + 0.28, y: y + 1.18, w: cw - 0.56, h: 0.6, margin: 0, valign: "top", fontFace: SANS, fontSize: 11, color: MUTED, lineSpacing: 15 });
  });

  card(s, { x: M, y: 6.05, w: CW, h: 0.62, fill: INK, shadow: false });
  s.addText("Once ADR recogen el porqué y lo descartado: monolito modular · Spring Boot · Row-Level Security · Flyway · ficheros en disco · React + Vite · contrato como fuente de verdad · monorepo · correo saliente · fronteras de módulo · comprobaciones periódicas y avisos", {
    x: M, y: 6.05, w: CW, h: 0.62, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 11, color: "CFE1DE",
  });
  foot(s);
  s.addNotes("README §6 y ADR-001 a ADR-011. Las dos últimas llegaron con la Fase 2: fronteras de módulo y activación por hogar, y programación de comprobaciones y entrega de avisos.");
}

// ═══ 17 · Testing ═════════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "CALIDAD", "Estrategia de testing, igual en BE y FE");

  s.addChart(pres.ChartType.doughnut, [{
    name: "Distribución",
    labels: ["Unitarios de dominio", "Integración de casos de uso", "Contrato de adaptadores / E2E"],
    values: [60, 25, 15],
  }], {
    x: 0.6, y: 1.75, w: 5.3, h: 4.5,
    holeSize: 55,
    chartColors: [TEAL, TERRA, INK2],
    showTitle: true, title: "Distribución de la batería de tests", titleFontFace: SANS, titleFontSize: 13, titleColor: INK,
    showLegend: true, legendPos: "b", legendFontFace: SANS, legendFontSize: 10, legendColor: MUTED,
    showValue: true, dataLabelFontFace: SANS, dataLabelFontSize: 12, dataLabelColor: WHITE, dataLabelFormatCode: '0"%"',
    showPercent: false,
  });

  const levels = [
    ["60 %", "Unitarios de dominio", "Entidades y reglas puras, sin dependencias externas. Ej.: un Asset no puede ser su propio ancestro.", TEAL],
    ["25 %", "Integración de casos de uso", "Orquestación completa con dependencias reales o en memoria. Ej.: CreateAsset persiste y publica AssetCreated.", TERRA],
    ["15 %", "Contrato de adaptadores y E2E", "El adaptador cumple el contrato externo. Ej.: POST /assets/intake responde 201 y luego 200 acumulando.", INK2],
  ];
  const rx = 6.35, rw = W - M - rx, chh = 1.4;
  levels.forEach((l, i) => {
    const y = 1.9 + i * (chh + 0.28);
    card(s, { x: rx, y, w: rw, h: chh, fill: TINT, shadow: false });
    s.addText(l[0], { x: rx + 0.3, y: y + 0.2, w: 1.0, h: 0.5, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 24, bold: true, color: l[3] });
    s.addText(l[1], { x: rx + 1.4, y: y + 0.22, w: rw - 1.75, h: 0.42, margin: 0, valign: "middle", fontFace: SANS, fontSize: 14, bold: true, color: INK });
    s.addText(l[2], { x: rx + 1.4, y: y + 0.66, w: rw - 1.75, h: 0.6, margin: 0, valign: "top", fontFace: SANS, fontSize: 11.5, color: MUTED, lineSpacing: 16 });
  });
  foot(s);
  s.addNotes("README §7: distribución 60/25/15 y ejemplos por nivel.");
}

// ═══ 18 · Roadmap ═════════════════════════════════════════════════════════════
{
  const s = newSlide();
  head(s, "ESTADO ACTUAL", "Roadmap por fases");

  // `done` decide el aspecto entero de la tarjeta. Antes lo decidía el índice
  // —«las dos primeras están hechas»— y eso hay que reescribirlo cada vez que
  // una fase se cierra; con un campo, cerrar una fase es cambiar dos valores.
  const fases = [
    ["Fase 0", "Definición", "Arquitectura, stack, alcance del core y estrategia de testing", "Completada", TEAL, WHITE, true],
    ["Fase 1", "Core MVP", "Assets, autenticación, API REST, event bus y cliente web completo del core", "Completada", TEAL, WHITE, true],
    ["Fase 2", "Módulos activables", "Activación por hogar, plataforma de avisos y los cuatro módulos de prioridad alta", "Completada", TEAL, WHITE, true],
    ["Fase 3", "Módulos adicionales", "Los nueve restantes, por orden de prioridad (sección 4.2)", "Pendiente", "C3D2CF", INK, false],
  ];
  const cw = 2.9, gap = 0.18, cy = 1.85, chh = 3.55;
  fases.forEach((f, i) => {
    const x = M + i * (cw + gap), done = f[6];
    card(s, { x, y: cy, w: cw, h: chh, fill: done ? TINT : WHITE, line: done ? undefined : "D3DFDC", shadow: done ? undefined : false });
    badge(s, x + 0.28, cy + 0.3, 0.56, String(i), f[4], f[5]);
    s.addText(f[0], { x: x + 1.0, y: cy + 0.32, w: cw - 1.3, h: 0.52, margin: 0, valign: "middle", fontFace: SANS, fontSize: 12, bold: true, charSpacing: 1, color: MUTED });
    s.addText(f[1], { x: x + 0.28, y: cy + 1.0, w: cw - 0.56, h: 0.8, margin: 0, valign: "top", fontFace: SERIF, fontSize: 18, bold: true, color: INK, lineSpacing: 23 });
    s.addText(f[2], { x: x + 0.28, y: cy + 1.85, w: cw - 0.56, h: 1.05, margin: 0, valign: "top", fontFace: SANS, fontSize: 12, color: MUTED, lineSpacing: 17 });
    s.addShape(pres.ShapeType.roundRect, { x: x + 0.28, y: cy + 2.95, w: 1.55, h: 0.36, fill: { color: done ? TEAL : "E7EDEB" }, line: { color: done ? TEAL : "D3DFDC", width: 0.5 }, rectRadius: 0.18 });
    s.addText(f[3], { x: x + 0.28, y: cy + 2.95, w: 1.55, h: 0.36, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 10.5, bold: true, color: done ? WHITE : MUTED });
  });

  s.addText("Criterio de validación (ADR-001), cumplido en las dos fases entregadas: un recorrido vertical que atraviesa frontend, API autenticada, aplicación, dominio y PostgreSQL. Hoy son siete, en un navegador de verdad.", {
    x: M, y: 5.65, w: CW, h: 0.55, margin: 0, valign: "top", fontFace: SANS, fontSize: 13, italic: true, color: TEAL, lineSpacing: 19,
  });
  foot(s);
  s.addNotes("README §8: roadmap y estado. Fase 0 cerrada el 2026-08-07, Fase 1 el 2026-08-17 y Fase 2 el 2026-08-19. El detalle de cada una vive en su propio roadmap, en docs/common/product/.");
}

// ═══ 19 · Cierre ══════════════════════════════════════════════════════════════
{
  const s = newSlide(true);
  head(s, "SIGUIENTE PASO", "El core y los cuatro primeros módulos, entregados", true);
  s.addText("Lo que queda es la Fase 3: los nueve módulos restantes de la sección 4.2, sobre un mecanismo de activación que ya existe y una plataforma de avisos que ya entrega.", {
    x: M, y: 1.45, w: 9.5, h: 0.62, margin: 0, valign: "top", fontFace: SANS, fontSize: 15, color: "C4D4D1", lineSpacing: 21,
  });

  s.addText("EL RECORRIDO VERTICAL QUE VALIDA CADA ENTREGA", {
    x: M, y: 2.35, w: CW, h: 0.3, margin: 0, valign: "middle", fontFace: SANS, fontSize: 11, bold: true, charSpacing: 2, color: TEALLT,
  });
  const chain = ["Frontend", "API autenticada", "Aplicación", "Dominio", "PostgreSQL"];
  const cwid = 2.13, cgap = 0.36;
  chain.forEach((t, i) => {
    const x = M + i * (cwid + cgap);
    card(s, { x, y: 2.8, w: cwid, h: 0.85, fill: i === 0 || i === 4 ? "1B4B48" : TEAL, line: TEALLT, shadow: false });
    s.addText(t, { x, y: 2.8, w: cwid, h: 0.85, margin: 0, align: "center", valign: "middle", fontFace: SANS, fontSize: 13, bold: true, color: WHITE });
    if (i < chain.length - 1) {
      s.addShape(pres.ShapeType.rightArrow, { x: x + cwid + 0.05, y: 3.09, w: 0.26, h: 0.26, fill: { color: TEALLT }, line: { color: TEALLT, width: 0.5 } });
    }
  });

  const closing = [
    ["Lo entregado", "El core completo y cuatro módulos, sobre activación por hogar y avisos programados. 98 operaciones en el contrato, 28 tablas con Row-Level Security y siete recorridos verticales."],
    ["Lo que queda", "Los nueve módulos restantes, por orden de prioridad. Ninguno pide arquitectura nueva: el camino de un módulo está recorrido cuatro veces."],
  ];
  closing.forEach((c, i) => {
    const x = M + i * (5.85 + 0.43);
    card(s, { x, y: 4.3, w: 5.85, h: 1.75, fill: "163A38", line: "2F5754", shadow: false });
    s.addText(c[0], { x: x + 0.38, y: 4.55, w: 5.1, h: 0.36, margin: 0, valign: "middle", fontFace: SERIF, fontSize: 17, bold: true, color: WHITE });
    s.addText(c[1], { x: x + 0.38, y: 5.0, w: 5.1, h: 0.9, margin: 0, valign: "top", fontFace: SANS, fontSize: 12.5, color: "BFD3D0", lineSpacing: 18 });
  });

  s.addText("Fuente: README.md · documento vivo, última actualización 2026-08-19", {
    x: M, y: 6.35, w: CW, h: 0.35, margin: 0, valign: "middle", fontFace: SANS, fontSize: 11, color: "7E9895",
  });
  foot(s, true);
  s.addNotes("README §8, §9 y §11: cierre de la Fase 2 y lo que queda para la Fase 3, que todavía no está planificada.");
}

const out = process.argv[2] || "DRP-resumen.pptx";
pres.writeFile({ fileName: out }).then(() => console.log("OK ->", out));
