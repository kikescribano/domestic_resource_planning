#!/usr/bin/env python3
"""Valida openapi.yaml contra el esquema de OpenAPI.

La ADR-007 hace del contrato la fuente de verdad de la API: el frontend genera
de el sus tipos y su cliente, y el backend se verifica contra el. Un contrato
que no valida rompe las dos cosas, asi que esto corre en cada integracion.

Requiere: pip install pyyaml openapi-spec-validator
Uso:      python scripts/validate-openapi.py [ruta]
"""

from __future__ import annotations

import pathlib
import sys

DEFAULT_SPEC = pathlib.Path(__file__).resolve().parent.parent / "openapi.yaml"


def main(argv: list[str]) -> int:
    spec_path = pathlib.Path(argv[1]) if len(argv) > 1 else DEFAULT_SPEC

    if not spec_path.is_file():
        print(f"No existe el contrato: {spec_path}", file=sys.stderr)
        return 2

    try:
        from openapi_spec_validator import validate
        from openapi_spec_validator.readers import read_from_filename
    except ImportError:
        print(
            "Faltan dependencias. Instalalas con:\n"
            "    pip install pyyaml openapi-spec-validator",
            file=sys.stderr,
        )
        return 2

    spec, _ = read_from_filename(str(spec_path))

    try:
        validate(spec)
    except Exception as error:  # noqa: BLE001 - el validador lanza varios tipos
        print(f"{spec_path.name} NO valida:\n\n{error}", file=sys.stderr)
        return 1

    paths = spec.get("paths", {})
    operations = [
        (path, method)
        for path, item in paths.items()
        for method in item
        if method in {"get", "put", "post", "delete", "patch", "head", "options", "trace"}
    ]
    schemas = spec.get("components", {}).get("schemas", {})

    print(
        f"{spec_path.name} OK "
        f"- OpenAPI {spec.get('openapi')}, version {spec.get('info', {}).get('version')}"
    )
    print(f"  {len(paths)} rutas, {len(operations)} operaciones, {len(schemas)} esquemas")

    # La ADR-007 exige operationId en todas: sin el, el cliente generado no tiene
    # nombres estables y las pruebas de adaptador no pueden nombrar la operacion.
    sin_id = [f"{m.upper()} {p}" for p, m in operations if "operationId" not in paths[p][m]]
    if sin_id:
        print(f"\n  {len(sin_id)} operaciones sin operationId:", file=sys.stderr)
        for item in sin_id:
            print(f"    {item}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
