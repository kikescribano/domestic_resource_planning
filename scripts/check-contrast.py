#!/usr/bin/env python3
"""Comprueba el contraste de los tokens de color contra WCAG 2.2 nivel AA.

La direccion visual dice que el contraste esta "comprobado, no afirmado"
(docs/frontend/product-design/look-and-feel.md), y esto es lo que lo hace cierto:
lee los valores oklch reales de frontend/src/index.css, los convierte a sRGB,
calcula el ratio de cada par que acaba en pantalla y falla si alguno se queda
corto.

Sin esto, la tabla de ratios de docs/frontend/accessibility/README.md seria una
foto que envejece en cuanto alguien retoque un token, y nadie se enteraria hasta
una auditoria.

Se ejecuta desde la raiz del repositorio y no necesita dependencias:

    python scripts/check-contrast.py
"""
from __future__ import annotations

import math
import re
import sys
from pathlib import Path

TOKENS_FILE = Path("frontend/src/index.css")

# Minimos de WCAG 2.2 AA: 4.5:1 para texto normal, 3:1 para texto grande y para
# los elementos no textuales que transmiten informacion (1.4.11), que es el caso
# de los bordes de control y del anillo de foco.
TEXT_MINIMUM = 4.5
NON_TEXT_MINIMUM = 3.0

# Los pares que de verdad acaban juntos en pantalla. La lista se mantiene a mano
# a proposito: medir todas las combinaciones posibles daria cientos de numeros
# sin significado, y lo que importa es que este cubierto lo que se usa.
PAIRS: list[tuple[str, str, float, str]] = [
    ("color-ink", "color-surface", TEXT_MINIMUM, "texto normal sobre el papel"),
    ("color-ink", "color-surface-raised", TEXT_MINIMUM, "texto en tarjeta"),
    ("color-ink", "color-surface-sunken", TEXT_MINIMUM, "texto sobre superficie hundida"),
    ("color-ink", "color-surface-hover", TEXT_MINIMUM, "texto en fila al pasar el puntero"),
    ("color-ink-muted", "color-surface", TEXT_MINIMUM, "texto secundario"),
    ("color-ink-muted", "color-surface-raised", TEXT_MINIMUM, "texto secundario en tarjeta"),
    ("color-ink-subtle", "color-surface", TEXT_MINIMUM, "marcador de posicion"),
    ("color-ink-subtle", "color-surface-raised", TEXT_MINIMUM, "marcador en tarjeta"),
    ("color-accent", "color-surface", NON_TEXT_MINIMUM, "relleno del boton principal"),
    ("color-accent-hover", "color-surface", NON_TEXT_MINIMUM, "boton principal al pasar"),
    ("color-accent-ink", "color-surface", TEXT_MINIMUM, "enlace sobre el papel"),
    ("color-accent-ink", "color-surface-raised", TEXT_MINIMUM, "enlace en tarjeta"),
    ("color-ink-inverse", "color-accent", TEXT_MINIMUM, "texto dentro del boton principal"),
    ("color-focus", "color-surface", NON_TEXT_MINIMUM, "anillo de foco sobre el papel"),
    ("color-focus", "color-surface-raised", NON_TEXT_MINIMUM, "anillo de foco en tarjeta"),
    ("color-border", "color-surface", NON_TEXT_MINIMUM, "borde de control (1.4.11)"),
    ("color-border", "color-surface-raised", NON_TEXT_MINIMUM, "borde de control en tarjeta"),
    ("color-border-strong", "color-surface", NON_TEXT_MINIMUM, "borde reforzado"),
    ("color-success", "color-surface", TEXT_MINIMUM, "texto de exito"),
    ("color-warning", "color-surface", TEXT_MINIMUM, "texto de aviso"),
    ("color-danger", "color-surface", TEXT_MINIMUM, "texto de error"),
    ("color-info", "color-surface", TEXT_MINIMUM, "texto informativo"),
    ("color-success", "color-success-soft", TEXT_MINIMUM, "distintivo de exito"),
    ("color-warning", "color-warning-soft", TEXT_MINIMUM, "distintivo de aviso"),
    ("color-danger", "color-danger-soft", TEXT_MINIMUM, "distintivo de error"),
    ("color-info", "color-info-soft", TEXT_MINIMUM, "distintivo informativo"),
    ("color-state-available", "color-state-available-soft", TEXT_MINIMUM, "distintivo DISPONIBLE"),
    ("color-state-lent", "color-state-lent-soft", TEXT_MINIMUM, "distintivo PRESTADO"),
    ("color-state-overdue", "color-state-overdue-soft", TEXT_MINIMUM, "distintivo VENCIDO"),
    ("color-state-decommissioned", "color-state-decommissioned-soft", TEXT_MINIMUM, "distintivo DADO DE BAJA"),
    ("color-state-out-of-stock", "color-state-out-of-stock-soft", TEXT_MINIMUM, "distintivo SIN EXISTENCIAS"),
    ("color-state-available", "color-surface", TEXT_MINIMUM, "estado DISPONIBLE sobre el papel"),
    ("color-state-lent", "color-surface", TEXT_MINIMUM, "estado PRESTADO sobre el papel"),
    ("color-state-overdue", "color-surface", TEXT_MINIMUM, "estado VENCIDO sobre el papel"),
    ("color-state-decommissioned", "color-surface", TEXT_MINIMUM, "estado DADO DE BAJA sobre el papel"),
    ("color-state-out-of-stock", "color-surface", TEXT_MINIMUM, "estado SIN EXISTENCIAS sobre el papel"),
]

LIGHT_DARK = re.compile(r"--([a-z0-9-]+):\s*light-dark\(\s*(oklch\([^)]*\))\s*,\s*(oklch\([^)]*\))\s*\)")
OKLCH = re.compile(r"oklch\(\s*([\d.]+)%\s+([\d.]+)\s+([\d.]+)\s*(?:/\s*([\d.]+)\s*)?\)")

Rgb = tuple[float, float, float]


def oklch_to_linear_srgb(lightness: float, chroma: float, hue: float) -> Rgb:
    """OKLCH -> sRGB lineal, con los coeficientes de la especificacion de CSS Color 4."""
    radians = math.radians(hue)
    a = chroma * math.cos(radians)
    b = chroma * math.sin(radians)

    l_ = lightness + 0.3963377774 * a + 0.2158037573 * b
    m_ = lightness - 0.1055613458 * a - 0.0638541728 * b
    s_ = lightness - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_**3, m_**3, s_**3

    return (
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )


def parse_color(value: str) -> Rgb | None:
    match = OKLCH.match(value.strip())
    if not match or match.group(4) is not None:
        # Con alfa no se mide: el resultado depende de lo que haya debajo.
        return None
    return oklch_to_linear_srgb(float(match.group(1)) / 100, float(match.group(2)), float(match.group(3)))


def within_srgb(rgb: Rgb, tolerance: float = 0.001) -> bool:
    """Un color fuera del gamut lo recorta el navegador, y entonces el numero
    medido deja de ser el numero que se ve."""
    return all(-tolerance <= channel <= 1 + tolerance for channel in rgb)


def relative_luminance(rgb: Rgb) -> float:
    r, g, b = (min(max(channel, 0.0), 1.0) for channel in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast_ratio(first: Rgb, second: Rgb) -> float:
    a, b = relative_luminance(first), relative_luminance(second)
    lighter, darker = max(a, b), min(a, b)
    return (lighter + 0.05) / (darker + 0.05)


def main() -> int:
    if not TOKENS_FILE.is_file():
        print(f"No se encuentra {TOKENS_FILE}. Ejecuta el script desde la raiz del repositorio.")
        return 2

    text = TOKENS_FILE.read_text(encoding="utf-8")
    light: dict[str, Rgb] = {}
    dark: dict[str, Rgb] = {}
    for name, light_value, dark_value in LIGHT_DARK.findall(text):
        parsed_light, parsed_dark = parse_color(light_value), parse_color(dark_value)
        if parsed_light and parsed_dark:
            light[name], dark[name] = parsed_light, parsed_dark

    if not light:
        print(f"No se ha reconocido ningun token de color en {TOKENS_FILE}.")
        return 2

    problems: list[str] = []

    for mode, table in (("claro", light), ("oscuro", dark)):
        for name, rgb in sorted(table.items()):
            if not within_srgb(rgb):
                problems.append(f"{name} se sale del gamut sRGB en modo {mode}")

    rows = []
    for foreground, background, minimum, purpose in PAIRS:
        if foreground not in light or background not in light:
            problems.append(f"falta el token {foreground} o {background}, que la lista de pares necesita")
            continue

        ratio_light = contrast_ratio(light[foreground], light[background])
        ratio_dark = contrast_ratio(dark[foreground], dark[background])
        rows.append((purpose, ratio_light, ratio_dark, minimum))

        for mode, ratio in (("claro", ratio_light), ("oscuro", ratio_dark)):
            if ratio < minimum:
                problems.append(f"{purpose} en modo {mode}: {ratio:.2f}:1, por debajo de {minimum}:1")

    print(f"{'uso':46} {'claro':>7} {'oscuro':>7} {'minimo':>7}")
    for purpose, ratio_light, ratio_dark, minimum in rows:
        print(f"{purpose:46} {ratio_light:6.2f}:1 {ratio_dark:6.2f}:1 {minimum:6.1f}:1")

    worst_text = min(
        min(light_ratio, dark_ratio)
        for _, light_ratio, dark_ratio, minimum in rows
        if minimum == TEXT_MINIMUM
    )
    worst_non_text = min(
        min(light_ratio, dark_ratio)
        for _, light_ratio, dark_ratio, minimum in rows
        if minimum == NON_TEXT_MINIMUM
    )

    print()
    print(f"{len(rows)} pares medidos en los dos modos")
    print(f"Peor caso de texto:      {worst_text:.2f}:1 (minimo {TEXT_MINIMUM}:1)")
    print(f"Peor caso no textual:    {worst_non_text:.2f}:1 (minimo {NON_TEXT_MINIMUM}:1)")

    if problems:
        print()
        for problem in problems:
            print(f"  INCUMPLE: {problem}")
        return 1

    print("\nTodos los pares cumplen su minimo de WCAG 2.2 AA.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
