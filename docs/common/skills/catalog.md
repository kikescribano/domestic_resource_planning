# Catálogo de skills

| ID | Skill | Ámbito | Estado | Definición | Implementación |
|---|---|---|---|---|---|
| SKILL-001 | Presentaciones a partir del README | common | Vigente | [`SKILL-001-readme-to-deck.md`](SKILL-001-readme-to-deck.md) | [`../marketing/assets/build-drp-resumen.js`](../marketing/assets/build-drp-resumen.js), [`../marketing/assets/build-drp-comercial.js`](../marketing/assets/build-drp-comercial.js) y [`assets/preview-pptx.py`](assets/preview-pptx.py) |
| SKILL-002 | Presentaciones sobre la plantilla de Slidesgo | common | Vigente | [`.claude/skills/marketing-deck/SKILL.md`](../../../.claude/skills/marketing-deck/SKILL.md) | [`scripts/`](../../../.claude/skills/marketing-deck/scripts/slidesgo_deck.py) de esa misma skill, y los generadores [`build-drp-comercial-minitheme.py`](../marketing/assets/build-drp-comercial-minitheme.py) y [`build-drp-tecnico-minitheme.py`](../marketing/assets/build-drp-tecnico-minitheme.py) |

## Reglas del catálogo

- Usa un identificador estable con formato `SKILL-NNN`.
- Enlaza una única definición canónica.
- Declara `common`, `backend` o `frontend` como ámbito principal.
- Mantén el estado sincronizado con la disponibilidad real de la skill.
