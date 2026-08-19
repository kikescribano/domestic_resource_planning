"""Genera `DRP-comercial.pptx`: qué es DRP para quien no lo conoce.

Este script es la fuente editable del .pptx. La presentación **no se retoca a
mano**: se corrige aquí y se vuelve a ejecutar. El procedimiento entero —de dónde
sale el look and feel, qué obliga la licencia y cómo se verifica— está en la
skill [`marketing-deck`](../../../../.claude/skills/marketing-deck/SKILL.md).

    python docs/common/marketing/assets/build-drp-comercial.py

**Refleja el estado del repositorio a 2026-08-19**: Fases 1 y 2 cerradas, cuatro
módulos construidos, cierre de huecos planificado y sin empezar, Fase 3 sin
planificar. Los cinco datos que caducan antes, y que hay que repasar siempre
antes de regenerar, son **la fase en curso, el número de operaciones del
contrato, el de tablas, el de ADR y cuántos módulos hay construidos**.
"""

from __future__ import annotations

import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(RAIZ / ".claude" / "skills" / "marketing-deck" / "scripts"))

from slidesgo_deck import Deck  # noqa: E402

SALIDA = RAIZ / "docs" / "common" / "marketing" / "presentations" / "DRP-comercial.pptx"


def seccion(deck: Deck, numero: str, titulo: str, subtitulo: str, nota: str):
    """Separador de sección: el «#0n», el título y su frase."""
    slide = deck.use(4)
    slide.text(204, numero)
    slide.text(206, titulo)
    slide.text(205, subtitulo)
    slide.notes(nota)
    return slide


def build() -> Path:
    deck = Deck(titulo="DRP · presentación comercial")

    # 1 · Portada -------------------------------------------------------------
    portada = deck.use(1)
    for identificador in (161, 162, 165):  # las tres capas del rótulo
        portada.text(identificador, "DRP")
    portada.text(163, "Domestic Resource Planning")
    portada.drop(166)  # el «#01» decorativo: en la portada no numera nada
    portada.notes("README, cabecera y sección 1.")

    # 2 · Qué es --------------------------------------------------------------
    que_es = deck.use(2)
    que_es.text(171, "QUÉ ES DRP")
    que_es.text(
        172,
        "DRP lleva al hogar el enfoque de un ERP: saber qué hay, dónde está, "
        "quién responde de ello y cuándo toca revisarlo.",
        "Un core mínimo obligatorio: assets, ubicaciones, artículos, "
        "documentos, usuarios y préstamos.",
        "Módulos que cada hogar enciende cuando le hacen falta, sin rehacer "
        "nada de lo que ya tenía cargado.",
        "Cabe todo el material de la casa, desde la caldera hasta el paquete "
        "de harina, y no solo lo que vale dinero.",
        "Cada cosa con su ubicación, su responsable y su documentación al "
        "lado: manuales, facturas y garantías.",
        "Varios hogares comparten instalación sin verse entre ellos, con dos "
        "capas de aislamiento por debajo.",
        "Avisos que llegan solos: un resumen diario por hogar, y solo cuando "
        "hay algo que contar.",
        "Aplicación web responsive, del móvil de 375 px a una pantalla "
        "ultrawide, y accesible según WCAG 2.2 AA.")
    que_es.notes("README, secciones 1 y 2.")

    # 3 · Índice --------------------------------------------------------------
    indice = deck.use(3)
    indice.text(181, "ÍNDICE")
    indice.text(182, "PROBLEMA")
    indice.text(183, "Dónde está hoy la información de una casa")
    indice.text(184, "SOLUCIÓN")
    indice.text(185, "Un core mínimo y módulos que se encienden")
    indice.text(186, "MÓDULOS")
    indice.text(187, "Trece capacidades, cuatro ya construidas")
    indice.text(188, "ESTADO")
    indice.text(189, "Qué hay hecho y qué viene detrás")
    indice.notes("Recorrido de la presentación.")

    # 4 · Sección 1 -----------------------------------------------------------
    seccion(deck, "#01", "EL PROBLEMA",
            "La información de la casa no vive en ningún sitio",
            "README, sección 2.")

    # 5 · El problema ---------------------------------------------------------
    problema = deck.use(5)
    problema.text(213, "EL PROBLEMA")
    problema.text(
        214,
        "¿Dónde está la factura de la caldera?",
        "Hoy se reparte así:",
        "En hojas de cálculo",
        "En un cajón con papeles",
        "En la cabeza de alguien",
        "Y el día que esa persona no está, el hogar deja de saber lo que tiene.")
    problema.notes("README, sección 2: el problema y el ejemplo ilustrativo.")

    # 6 · Antes y después -----------------------------------------------------
    comparativa = deck.use(6)
    comparativa.text(268, "SIN DRP Y CON DRP")
    comparativa.text(266, "HOY")
    comparativa.text(267, "CON DRP")
    comparativa.text(265, "La ITV en el calendario del móvil, el manual de la "
                          "caldera en un cajón y el garaje en la cabeza de "
                          "alguien.")
    comparativa.text(270, "Todo dado de alta con su ubicación, su documentación "
                          "y su responsable, y los avisos llegan solos.")
    comparativa.text(264, "#01")
    comparativa.notes("README, sección 2: ejemplo ilustrativo.")

    # 7 · Sección 2 -----------------------------------------------------------
    seccion(deck, "#02", "LA SOLUCIÓN",
            "Un core mínimo y módulos que se encienden",
            "README, secciones 1 y 4.")

    # 8 · La idea en tres palabras -------------------------------------------
    frase = deck.use(12)
    for identificador in (438, 439, 440):  # las tres capas del rótulo
        frase.text(identificador, "CRECE CONTIGO")
    frase.notes("README, sección 2: visión.")

    # 9 · Qué trae el core ----------------------------------------------------
    core = deck.use(9)
    core.text(402, "QUÉ TRAE EL CORE")
    core.text(397, "ASSETS")
    core.text(404, "Todo el material de la casa, con su ficha")
    core.text(398, "LUGARES")
    core.text(405, "Casa, habitación, mueble y estante")
    core.text(409, "PAPELES")
    core.text(407, "Manuales, facturas y garantías, con su foto")
    core.text(399, "USUARIOS")
    core.text(403, "Roles del hogar, invitación y verificación")
    core.text(400, "PRÉSTAMOS")
    core.text(406, "Quién se llevó qué y cuándo lo devuelve")
    core.text(401, "AVISOS")
    core.text(408, "Un resumen diario, y solo cuando hay algo")
    core.notes("README, sección 4.1.")

    # 10 · Cómo se activa un módulo ------------------------------------------
    activacion = deck.use(19)
    activacion.text(677, "CÓMO ENTRA UN MÓDULO")
    activacion.text(692, "Un hogar enciende lo que necesita")
    activacion.text(686, "El core funciona solo, sin ningún módulo")
    activacion.text(687, "Cada módulo se activa por hogar, cuando hace falta")
    activacion.text(688, "Los módulos se hablan por eventos, no se dependen")
    activacion.text(689, "Apagar uno no rompe nada de lo demás")
    activacion.notes("README, secciones 4.2 y 5.2; ADR-010.")

    # 11 · Sección 3 ----------------------------------------------------------
    seccion(deck, "#03", "LOS MÓDULOS",
            "Trece capacidades previstas, cuatro construidas",
            "README, sección 4.2.")

    # 12 · Los cuatro construidos --------------------------------------------
    modulos = deck.use(8)
    modulos.text(352, "LOS CUATRO ACTIVOS")
    modulos.text(348, "PROVEEDORES")
    modulos.text(349, "Quién arregla, quién cobra, quién responde")
    modulos.text(350, "WAREHOUSE")
    modulos.text(351, "Despensa y garaje: stock, mínimos, caducidad")
    modulos.text(353, "COMPRAS")
    modulos.text(354, "Qué falta, qué reponer y qué está pedido")
    modulos.text(355, "MANTENIMIENTO")
    modulos.text(356, "Revisiones recurrentes y su histórico")
    modulos.notes("README, sección 4.2: los cuatro de prioridad alta.")

    # 13 · El catálogo entero -------------------------------------------------
    catalogo = deck.use(17)
    catalogo.text(629, "EL CATÁLOGO ENTERO")
    catalogo.table(630, [
        ["PRIORIDAD", "MÓDULOS", "ESTADO", "FASE"],
        ["Alta", "4", "Construidos", "Fase 2"],
        ["Media", "3", "Por diseñar", "Fase 3"],
        ["Baja", "6", "Por diseñar", "Fase 3"],
    ])
    catalogo.notes("README, sección 4.2: la tabla de módulos y su estado.")

    # 14 · Sección 4 ----------------------------------------------------------
    seccion(deck, "#04", "EL ESTADO",
            "Qué hay construido a agosto de 2026",
            "README, sección 8.")

    # 15 · Cifras -------------------------------------------------------------
    cifras = deck.use(10)
    cifras.drop(415)  # duplicado que la plantilla dejó debajo del 419
    cifras.text(425, "13")
    cifras.text(426, "módulos previstos, cuatro construidos")
    cifras.text(420, "98")
    cifras.text(419, "operaciones disponibles en la API")
    cifras.text(421, "1 GB")
    cifras.text(424, "de documentos por hogar")
    cifras.text(423, "375 px")
    cifras.text(422, "de ancho mínimo, hasta ultrawide")
    cifras.notes("README, secciones 4.2, 5.5, 5.8 y cabecera.")

    # 16 · La cifra que importa ----------------------------------------------
    aislamiento = deck.use(11)
    aislamiento.text(433, "2")
    aislamiento.text(432, "capas de aislamiento: nadie ve lo que no es suyo")
    aislamiento.notes("README, sección 5.6; ADR-003.")

    # 17 · El camino ----------------------------------------------------------
    camino = deck.use(20)
    camino.text(704, "EL CAMINO")
    camino.text(706, "F0")
    camino.text(707, "F1")
    camino.text(709, "F2")
    camino.text(711, "F3")
    camino.text(714, "HOY")
    camino.text(703, "CORE")
    camino.text(716, "El core completo y su cliente web")
    camino.text(701, "MÓDULO")
    camino.text(717, "Activación por hogar y cuatro módulos")
    camino.text(702, "FUTURO")
    camino.text(718, "Los nueve módulos que faltan")
    camino.notes("README, sección 8: fases y estado.")

    # 18 · Gracias ------------------------------------------------------------
    gracias = deck.use(21)
    for identificador in (726, 727, 729):  # las tres capas del rótulo
        gracias.text(identificador, "GRACIAS")
    gracias.text(730, "¿Alguna pregunta?", "DRP · Domestic Resource Planning", "", "")
    gracias.text(731, "Se conserva por atribución a Slidesgo")
    gracias.notes("Diapositiva obligatoria: la plantilla se descargó con cuenta "
                  "gratuita y su licencia exige conservar la atribución.")

    return deck.save(SALIDA)


if __name__ == "__main__":
    print(build())
