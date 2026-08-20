import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { fakeTokenPair, stubFetch } from '../test/http'

/**
 * La guía de la herramienta.
 *
 * Lo que se comprueba aquí es lo que solo se ve aquí: que hay un bloque por
 * pantalla de la navegación con su enlace, y que el buscador deja a la vista
 * solo los bloques que coinciden —sin distinguir acentos, que es la promesa
 * escrita en la pista del campo—. El contenido es estático: no hay ninguna
 * petición a la API que preparar más allá de la sesión.
 */

async function openHelp() {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  window.history.pushState({}, '', '/ayuda')

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair() },
  })

  render(<App />)
  await screen.findByRole('heading', { level: 1, name: 'Ayuda' })
  return stub
}

describe('la guía de la herramienta', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => vi.unstubAllGlobals())

  it('tiene un bloque por pantalla, cada uno con su enlace', async () => {
    await openHelp()

    // Una muestra de cada tramo de la navegación: pulgar, resto del core y
    // módulos. Si la lista de pantallas crece, el bloque nuevo se añade a
    // `HELP_TOPICS` y esta prueba no se toca.
    for (const title of ['Inventario', 'Catálogo', 'Mantenimiento']) {
      expect(screen.getByRole('article', { name: title })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: `Ir a ${title}` })).toBeInTheDocument()
    }

    expect(screen.getByRole('link', { name: 'Ir a Almacén' })).toHaveAttribute('href', '/almacen')
  })

  it('el buscador filtra bloques enteros y no distingue acentos', async () => {
    await openHelp()

    // «prestamo» sin tilde tiene que encontrar «Préstamos»: es la promesa que
    // la pista del campo deja escrita, la misma comparación del catálogo.
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar' }), 'prestamo')

    expect(screen.getByRole('article', { name: 'Préstamos' })).toBeInTheDocument()
    expect(screen.queryByRole('article', { name: 'Proveedores' })).not.toBeInTheDocument()
  })

  it('cuando nada coincide lo dice, en lugar de dejar la lista vacía', async () => {
    await openHelp()

    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar' }), 'zzzz')

    expect(screen.getByText('Ningún bloque coincide')).toBeInTheDocument()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })
})
