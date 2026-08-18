#!/usr/bin/env python3
"""Saca el enlace del ultimo correo que Mailpit tiene para una direccion.

Existe porque **sin correo no se entra**: DRP no siembra ningun usuario, asi que
la primera sesion de cualquier arranque en limpio pasa por dar de alta un hogar y
pulsar el enlace de verificacion. Ese enlace solo esta en el correo, y leerlo a
mano --abrir la interfaz de Mailpit, buscar el mensaje, copiar la URL-- es lo que
convierte un arranque de dos minutos en uno de cinco.

Sirve igual para el resto de enlaces que el producto manda por correo: invitacion
a un hogar, restablecimiento de contrasena y la vista externa de un prestamo.

    python .claude/skills/run-local/scripts/mailpit-link.py kike@casa.test

Espera activamente unos segundos porque la entrega es sincrona pero no
instantanea: pedirlo justo despues de enviar el formulario suele llegar antes que
el correo.
"""
from __future__ import annotations

import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

MAILPIT = "http://localhost:8025"
TIMEOUT_SECONDS = 30


def latest_message_id(recipient: str) -> str | None:
    query = urllib.parse.quote(f"to:{recipient}")
    with urllib.request.urlopen(f"{MAILPIT}/api/v1/search?query={query}") as response:
        messages = json.load(response).get("messages") or []
    return messages[0]["ID"] if messages else None


def link_of(message_id: str) -> str | None:
    with urllib.request.urlopen(f"{MAILPIT}/api/v1/message/{message_id}") as response:
        body = json.load(response).get("Text") or ""
    found = re.search(r"https?://[^\s]+", body)
    return found.group(0) if found else None


def main() -> int:
    if len(sys.argv) != 2:
        print("Uso: mailpit-link.py <correo>", file=sys.stderr)
        return 2

    recipient = sys.argv[1]
    deadline = time.time() + TIMEOUT_SECONDS

    while time.time() < deadline:
        try:
            message_id = latest_message_id(recipient)
        except urllib.error.URLError:
            print(f"Mailpit no responde en {MAILPIT}: ¿está levantado el compose?", file=sys.stderr)
            return 1

        if message_id:
            link = link_of(message_id)
            if link:
                print(link)
                return 0

        time.sleep(0.5)

    print(f"No llegó ningún correo a {recipient} en {TIMEOUT_SECONDS} s", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
