#!/usr/bin/env python3
"""Comprueba que ningun enlace relativo de la documentacion esta roto.

Son alrededor de 170 y se rompen con facilidad al renumerar secciones o al mover
un fichero de sitio. El reparto del README a docs/, previsto para la Fase 1, es
justo la clase de cambio que los rompe en bloque.

Uso: python scripts/check-links.py [raiz]
"""

from __future__ import annotations

import pathlib
import re
import sys

# [texto](destino) -- se descartan los que empiezan por '#' (ancla en la misma
# pagina) porque no apuntan a ningun fichero.
LINK = re.compile(r"\]\(([^)#][^)]*)\)")
EXTERNAL = ("http://", "https://", "mailto:", "tel:")
SKIP_DIRS = {".git", "node_modules", "build", "dist", ".gradle", ".data"}


def main(argv: list[str]) -> int:
    root = pathlib.Path(argv[1]) if len(argv) > 1 else pathlib.Path(__file__).resolve().parent.parent

    broken: list[str] = []
    checked = 0

    for md in sorted(root.rglob("*.md")):
        if SKIP_DIRS & set(md.parts):
            continue

        for target in LINK.findall(md.read_text(encoding="utf-8")):
            if target.startswith(EXTERNAL):
                continue

            checked += 1
            # Se separa la ancla: el fichero es lo que se comprueba.
            destination = (md.parent / target.split("#")[0]).resolve()
            if not destination.exists():
                broken.append(f"  {md.relative_to(root).as_posix()} -> {target}")

    if broken:
        print(f"{len(broken)} de {checked} enlaces relativos rotos:\n", file=sys.stderr)
        print("\n".join(broken), file=sys.stderr)
        return 1

    print(f"{checked} enlaces relativos, ninguno roto")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
