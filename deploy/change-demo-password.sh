#!/bin/sh
# Cambia la contrasena de las cuatro cuentas del hogar de demostracion a la de
# produccion, que vive en DRP_DEMO_PASSWORD del .env y nunca en el repositorio.
#
# Existe porque el script de la demo es recargable y publico: cada carga deja
# las cuatro cuentas con la contrasena escrita en el repositorio, y en
# produccion eso es una puerta abierta al hogar Serrano para cualquiera que
# lea el codigo. Este script se ejecuta DESPUES de cada carga del seed
# (deployment.md), y lo hace por la puerta de la aplicacion --login y
# ChangePassword--, no tocando hashes: asi la contrasena la procesa el mismo
# Argon2id con los mismos parametros que cualquier otra.
#
# Es idempotente: si ya se cambio, entra con la nueva y no toca nada. El unico
# efecto colateral es el propio de ChangePassword: revoca las demas sesiones
# de cada cuenta.
#
#   ./change-demo-password.sh [base-url]     # por omision, http://localhost
set -e

BASE="${1:-http://localhost}"
ENV_FILE="$(dirname "$0")/.env"

# La del script de la demo, publica en el repositorio (demo-dataset.md).
SEED_PASSWORD="DemoDRP2026Local"

DEMO_PASSWORD="$(grep '^DRP_DEMO_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)"
if [ -z "$DEMO_PASSWORD" ]; then
    echo "Falta DRP_DEMO_PASSWORD en $ENV_FILE (sin comillas ni barras invertidas)" >&2
    exit 1
fi

BODY=$(mktemp)
trap 'rm -f "$BODY"' EXIT

for who in marta javier lucia hugo; do
    email="$who@hogar-serrano.test"

    token=""
    current=""
    # La que funcione de las dos: la del seed (recien cargado) o la de
    # produccion (reejecucion sobre una demo ya cambiada).
    for pw in "$SEED_PASSWORD" "$DEMO_PASSWORD"; do
        code=$(curl -s -o "$BODY" -w "%{http_code}" -X POST "$BASE/api/v1/auth/login" \
            -H 'Content-Type: application/json' \
            --data "{\"email\":\"$email\",\"password\":\"$pw\"}")
        if [ "$code" = "200" ]; then
            token=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['accessToken'])" "$BODY")
            current="$pw"
            break
        fi
    done

    if [ -z "$token" ]; then
        echo "$email: no entra ni con la del seed ni con la de produccion" >&2
        exit 1
    fi

    if [ "$current" = "$DEMO_PASSWORD" ]; then
        echo "$email: ya estaba cambiada"
        continue
    fi

    code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/password" \
        -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' \
        --data "{\"currentPassword\":\"$current\",\"newPassword\":\"$DEMO_PASSWORD\"}")
    if [ "$code" != "204" ]; then
        echo "$email: el cambio respondio $code" >&2
        exit 1
    fi
    echo "$email: cambiada"
done
