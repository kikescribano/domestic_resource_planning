"""Rasteriza un .pptx a un PNG por diapositiva, para mirarlas.

El QA de texto (`qa-deck.py`) dice si algo se sale de su caja; esto dice si la
diapositiva *se ve bien*, que no es lo mismo y no se deduce leyendo el generador.

    pip install pypdfium2 Pillow
    python render-deck.py deck.pptx qa/            # un PNG por diapositiva
    python render-deck.py deck.pptx qa/ --hojas    # además, hojas de contactos

Convierte con LibreOffice en modo headless y rasteriza el PDF con pypdfium2.
Dos avisos que ahorran un diagnóstico:

- **LibreOffice no siempre usa las tipografías incrustadas** en el .pptx, así que
  puede sustituir Montserrat o Roboto por lo que tenga a mano. Los cortes de
  línea del render pueden no ser los de PowerPoint: sirve para ver composición,
  no para medir al píxel.
- Se le pasa un **perfil de usuario propio** (`-env:UserInstallation`) para no
  tocar el del sistema ni chocar con una instancia abierta. El proceso muere al
  terminar la conversión; si queda vivo, no es de este script.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

SOFFICE_CANDIDATES = (
    os.environ.get("SOFFICE"),
    r"C:\Program Files\LibreOffice\program\soffice.exe",
    r"C:\Program Files (x86)\LibreOffice\program\soffice.exe",
    "/usr/bin/soffice",
    "/usr/local/bin/soffice",
)


def soffice() -> str:
    for candidato in SOFFICE_CANDIDATES:
        if candidato and Path(candidato).exists():
            return candidato
    encontrado = shutil.which("soffice") or shutil.which("libreoffice")
    if encontrado:
        return encontrado
    raise SystemExit("no encuentro LibreOffice; instálalo o exporta SOFFICE=<ruta>")


def to_pdf(pptx: Path, destino: Path) -> Path:
    perfil = Path(tempfile.gettempdir()) / "lo-profile-marketing-deck"
    subprocess.run(
        [soffice(), "--headless", "--norestore",
         f"-env:UserInstallation=file:///{perfil.as_posix().lstrip('/')}",
         "--convert-to", "pdf", "--outdir", str(destino), str(pptx)],
        check=True, capture_output=True, timeout=600)
    pdf = destino / (pptx.stem + ".pdf")
    if not pdf.exists():
        raise SystemExit(f"LibreOffice no generó {pdf}")
    return pdf


def to_png(pdf: Path, destino: Path, escala: float = 1.4) -> list[Path]:
    import pypdfium2

    documento = pypdfium2.PdfDocument(str(pdf))
    salidas = []
    for indice in range(len(documento)):
        imagen = documento[indice].render(scale=escala).to_pil()
        ruta = destino / f"slide-{indice + 1:02d}.png"
        imagen.save(ruta)
        salidas.append(ruta)
    return salidas


def contact_sheets(pngs: list[Path], destino: Path, por_hoja: int = 6) -> list[Path]:
    from PIL import Image, ImageDraw

    ancho = 620
    hojas = []
    for inicio in range(0, len(pngs), por_hoja):
        lote = pngs[inicio:inicio + por_hoja]
        imagenes = [Image.open(p) for p in lote]
        alto = int(ancho * imagenes[0].height / imagenes[0].width)
        filas = (len(imagenes) + 1) // 2
        hoja = Image.new("RGB", (2 * ancho + 24, filas * (alto + 22) + 8), "#d9d9d9")
        lapiz = ImageDraw.Draw(hoja)
        for posicion, imagen in enumerate(imagenes):
            fila, columna = divmod(posicion, 2)
            x = 8 + columna * (ancho + 8)
            y = 8 + fila * (alto + 22)
            hoja.paste(imagen.resize((ancho, alto)), (x, y))
            lapiz.text((x + 4, y + alto + 4), lote[posicion].name, fill="#333333")
        ruta = destino / f"hoja-{inicio // por_hoja + 1:02d}.png"
        hoja.save(ruta)
        hojas.append(ruta)
    return hojas


def main() -> None:
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    pptx = Path(sys.argv[1]).resolve()
    destino = Path(sys.argv[2]).resolve()
    destino.mkdir(parents=True, exist_ok=True)

    pngs = to_png(to_pdf(pptx, destino), destino)
    print(f"{len(pngs)} diapositivas rasterizadas en {destino}")
    if "--hojas" in sys.argv:
        for hoja in contact_sheets(pngs, destino):
            print(hoja)


if __name__ == "__main__":
    main()
