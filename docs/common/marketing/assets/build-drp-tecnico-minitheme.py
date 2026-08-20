"""Genera `DRP-tecnico-minitheme.pptx`: cómo está construido DRP.

Este script es la fuente editable del .pptx. La presentación **no se retoca a
mano**: se corrige aquí y se vuelve a ejecutar. El procedimiento entero —de dónde
sale el look and feel, qué obliga la licencia y cómo se verifica— está en la
skill [`marketing-deck`](../../../../.claude/skills/marketing-deck/SKILL.md).

    python docs/common/marketing/assets/build-drp-tecnico-minitheme.py

**Refleja el estado del repositorio a 2026-08-20**: 106 operaciones en el
contrato, 31 tablas, quince ADR, Fases 1 y 2 cerradas más el cierre de huecos, y
cuatro módulos construidos. Los cinco datos que caducan antes, y que hay que
repasar siempre antes de regenerar, son **la fase en curso, el número de
operaciones del contrato, el de tablas, el de ADR y cuántos módulos hay
construidos**.
"""

from __future__ import annotations

import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(RAIZ / ".claude" / "skills" / "marketing-deck" / "scripts"))

from slidesgo_deck import Deck  # noqa: E402

SALIDA = RAIZ / "docs" / "common" / "marketing" / "presentations" / "DRP-tecnico-minitheme.pptx"


def seccion(deck: Deck, numero: str, titulo: str, subtitulo: str, nota: str):
    """Separador de sección: el «#0n», el título y su frase."""
    slide = deck.use(4)
    slide.text(204, numero)
    slide.text(206, titulo)
    slide.text(205, subtitulo)
    slide.notes(nota)
    return slide


def build() -> Path:
    deck = Deck(titulo="DRP · documento técnico")

    # 1 · Portada -------------------------------------------------------------
    portada = deck.use(1)
    for identificador in (161, 162, 165):  # las tres capas del rótulo
        portada.text(identificador, "DRP")
    portada.text(163, "Documento técnico · agosto de 2026")
    portada.drop(166)  # el «#01» decorativo: en la portada no numera nada
    portada.notes("README, cabecera.")

    # 2 · Qué cubre -----------------------------------------------------------
    alcance = deck.use(2)
    alcance.text(171, "QUÉ CUBRE ESTE DOCUMENTO")
    alcance.text(
        172,
        "Cómo está construido DRP: arquitectura, datos, contrato y "
        "verificación. Refleja el repositorio a 20 de agosto de 2026.",
        "Backend en Kotlin sobre Spring Boot: monolito modular con Clean "
        "Architecture y un event bus in-process.",
        "PostgreSQL 16 o superior, con Row-Level Security y migraciones en SQL "
        "plano versionadas con Flyway.",
        "Frontend en TypeScript y React sobre Vite, mobile-first desde 375 px "
        "y accesible según WCAG 2.2 nivel AA.",
        "Contrato OpenAPI 3.0 en la raíz del repositorio, con 106 operaciones "
        "y cliente de TypeScript generado.",
        "Monorepo con backend, frontend y documentación, construido en GitHub "
        "Actions con cinco trabajos.",
        "Quince ADR recogen las decisiones estructurales, sus alternativas "
        "descartadas y el motivo de cada una.",
        "Ficheros en el disco del servidor tras un puerto propio, servidos por "
        "nginx con una cuota de 1 GB por hogar.")
    alcance.notes("README, secciones 5, 6, 7 y 9.")

    # 3 · Índice --------------------------------------------------------------
    indice = deck.use(3)
    indice.text(181, "ÍNDICE")
    indice.text(182, "CAPAS")
    indice.text(183, "Monolito modular y fronteras que fallan")
    indice.text(184, "DATOS")
    indice.text(185, "Dos capas para que un hogar no vea otro")
    indice.text(186, "CONTRATO")
    indice.text(187, "OpenAPI como fuente de verdad, y el stack")
    indice.text(188, "PRUEBAS")
    indice.text(189, "Qué se ejecuta y qué demuestra")
    indice.notes("Recorrido del documento.")

    # 4 · Sección 1 -----------------------------------------------------------
    seccion(deck, "#01", "ARQUITECTURA",
            "Un monolito modular, no un servicio por módulo",
            "README, sección 5; ADR-001 y ADR-002.")

    # 5 · Clean Architecture --------------------------------------------------
    capas = deck.use(5)
    capas.text(213, "LAS CAPAS")
    capas.text(
        214,
        "La regla de dependencia apunta siempre hacia dentro:",
        "De fuera adentro:",
        "Infraestructura",
        "Adaptadores",
        "Casos de uso y dominio",
        "El dominio no sabe que existe PostgreSQL: el adaptador es lo único "
        "que lo sabe.")
    capas.notes("README, sección 5.3.")

    # 6 · El backend por paquetes --------------------------------------------
    paquetes = deck.use(9)
    paquetes.text(402, "EL BACKEND POR PAQUETES")
    paquetes.text(397, "PLATFORM")
    paquetes.text(404, "Bus, inquilino, paginación, activación")
    paquetes.text(398, "CORE")
    paquetes.text(405, "Assets, ubicaciones, artículos, préstamos")
    paquetes.text(409, "MODULE")
    paquetes.text(407, "Un árbol por módulo, con sus capas")
    paquetes.text(399, "SCHEDULE")
    paquetes.text(403, "El barrido diario, hogar a hogar")
    paquetes.text(400, "MAIL")
    paquetes.text(406, "Correo saliente tras un puerto propio")
    paquetes.text(401, "CONFIG")
    paquetes.text(408, "Quien cablea todo: DrpApplication")
    paquetes.notes("CLAUDE.md, reparto por paquetes; ADR-010.")

    # 7 · Core y módulo -------------------------------------------------------
    fronteras = deck.use(6)
    fronteras.text(268, "QUÉ ES DE CADA UNO")
    fronteras.text(266, "CORE")
    fronteras.text(267, "MÓDULO")
    fronteras.text(265, "Assets, ubicaciones, artículos, documentos, usuarios "
                        "y préstamos. No sabe qué módulos existen.")
    fronteras.text(270, "Se activa por hogar, lee el estado del core y publica "
                        "lo suyo en el bus. Nunca referencia a otro módulo.")
    fronteras.text(264, "#01")
    fronteras.notes("README, secciones 4.1 y 4.2; ADR-010.")

    # 8 · Las cuatro reglas ---------------------------------------------------
    reglas = deck.use(8)
    reglas.text(352, "LAS CUATRO FRONTERAS")
    reglas.text(348, "ENTRE MÓDULOS")
    reglas.text(349, "Un módulo no referencia a otro, nunca")
    reglas.text(350, "DESDE EL CORE")
    reglas.text(351, "El core no referencia a ningún módulo")
    reglas.text(353, "PLATAFORMA")
    reglas.text(354, "No se apoya en el core, salvo SessionClaims")
    reglas.text(355, "PERMITIDA")
    reglas.text(356, "Un módulo sí lee el estado del core")
    reglas.notes("ADR-010: las cuatro reglas de ArchUnit fallan la construcción.")

    # 9 · Sección 2 -----------------------------------------------------------
    seccion(deck, "#02", "AISLAMIENTO",
            "Varios hogares comparten base de datos",
            "README, sección 5.6; ADR-003.")

    # 10 · La idea en dos palabras -------------------------------------------
    frase = deck.use(12)
    for identificador in (438, 439, 440):  # las tres capas del rótulo
        frase.text(identificador, "DOS CAPAS")
    frase.notes("README, sección 5.6: el invariante de aislamiento.")

    # 11 · Cómo se defiende ---------------------------------------------------
    aislamiento = deck.use(19)
    aislamiento.text(677, "CÓMO SE DEFIENDE")
    aislamiento.text(692, "Ninguna de las dos capas basta sola")
    aislamiento.text(686, "Todo caso de uso filtra por el hogar del token")
    aislamiento.text(687, "Row-Level Security debajo, con FORCE")
    aislamiento.text(688, "El usuario de la aplicación no tiene BYPASSRLS")
    aislamiento.text(689, "Los procesos diarios fijan el hogar uno a uno")
    aislamiento.notes("README, secciones 5.6 y 5.7; ADR-003 y ADR-011.")

    # 12 · Lo que ocupa un hogar ---------------------------------------------
    capacidad = deck.use(11)
    capacidad.text(433, "142 kB")
    capacidad.text(432, "es lo que crece la base de datos por hogar")
    capacidad.notes("docs/backend/operations/capacity-measurements.md, "
                    "medición del 2026-08-20.")

    # 13 · Sección 3 ----------------------------------------------------------
    seccion(deck, "#03", "CONTRATO Y STACK",
            "El contrato manda: de él sale el cliente",
            "README, secciones 5.4 y 6; ADR-007.")

    # 14 · Cifras -------------------------------------------------------------
    cifras = deck.use(10)
    cifras.drop(415)  # duplicado que la plantilla dejó debajo del 419
    cifras.text(425, "106")
    cifras.text(426, "operaciones en el contrato OpenAPI")
    cifras.text(420, "31")
    cifras.text(419, "tablas, con RLS y FORCE las del core")
    cifras.text(421, "15")
    cifras.text(424, "ADR, cada una con su porqué")
    cifras.text(423, "10")
    cifras.text(422, "recorridos en navegador real")
    cifras.notes("README, cabecera y sección 8.")

    # 15 · Stack --------------------------------------------------------------
    stack = deck.use(17)
    stack.text(629, "STACK TECNOLÓGICO")
    stack.table(630, [
        ["CAPA", "BASE", "DETALLE", "ADR"],
        ["Backend", "Kotlin", "Spring Boot", "ADR-002"],
        ["Datos", "PostgreSQL", "RLS y Flyway", "ADR-003"],
        ["Frontend", "React", "Vite y Tailwind", "ADR-006"],
    ])
    stack.notes("README, sección 6.")

    # 16 · Sección 4 ----------------------------------------------------------
    seccion(deck, "#04", "VERIFICACIÓN",
            "Lo que se ejecuta, no lo que se promete",
            "README, sección 7; ADR-001.")

    # 17 · Los tres niveles ---------------------------------------------------
    pruebas = deck.use(7)
    pruebas.text(286, "CÓMO SE VERIFICA")
    pruebas.text(282, "DOMINIO")
    pruebas.text(285, "60 % de la batería: entidades y reglas")
    pruebas.text(280, "CASOS DE USO")
    pruebas.text(283, "25 %: PostgreSQL real, sujeto a RLS")
    pruebas.text(281, "CONTRATO")
    pruebas.text(284, "15 %: adaptadores y navegador real")
    pruebas.notes("README, sección 7; ADR-001 y ADR-008.")

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
