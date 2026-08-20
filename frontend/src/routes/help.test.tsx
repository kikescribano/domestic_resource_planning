import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import { SESSION_CLAIMS, fakeTokenPair, stubFetch } from '../test/http'

/**
 * La guía de la herramienta.
 *
 * Lo que se comprueba aquí es lo que solo se ve aquí: que hay un bloque por
 * pantalla de la navegación con su enlace, que el buscador deja a la vista
 * solo los bloques que coinciden —sin distinguir acentos, que es la promesa
 * escrita en la pista del campo— y que la parada «Ayuda» remata el grupo
 * «Configuración» también para quien no administra. El contenido es estático:
 * no hay ninguna petición a la API que preparar más allá de la sesión.
 */

async function openHelp(role = 'HOUSEHOLD_ADMIN') {
  localStorage.setItem('drp.refreshToken', 'refresh-de-mentira')
  window.history.pushState({}, '', '/ayuda')

  const stub = stubFetch({
    'POST /api/v1/auth/refresh': { status: 200, body: fakeTokenPair({ ...SESSION_CLAIMS, role }) },
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

    // Una muestra de cada grupo de la navegación —Tu hogar, Datos maestros,
    // Configuración— y un módulo. Si la lista de pantallas crece, el bloque
    // nuevo se añade a `HELP_TOPICS` y esta prueba no se toca.
    for (const title of ['Inventario', 'Catálogo', 'General', 'Mantenimiento']) {
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

  it('su parada remata el grupo «Configuración» de la navegación', async () => {
    await openHelp()

    // La última del grupo, que es la última de la navegación entera: la ayuda
    // cierra la lista, no se cuela entre las paradas de administración.
    const nav = within(screen.getByRole('navigation', { name: 'Principal' }))
    const config = within(nav.getByRole('list', { name: 'Configuración' }))
    const links = config.getAllByRole('link')

    expect(links.at(-1)).toHaveAccessibleName('Ayuda')
    expect(links.at(-1)).toHaveAttribute('href', '/ayuda')
  })

  it('quien no administra la ve igual, aunque el resto del grupo no exista para él', async () => {
    await openHelp('HOUSEHOLD_MEMBER')

    // El grupo «Configuración» era solo de administración hasta que «Ayuda» lo
    // remató: la guía es de quien usa la herramienta, no de quien la configura.
    const nav = within(screen.getByRole('navigation', { name: 'Principal' }))
    const config = within(nav.getByRole('list', { name: 'Configuración' }))

    expect(config.getByRole('link', { name: 'Ayuda' })).toBeInTheDocument()
    expect(config.queryByRole('link', { name: 'General' })).not.toBeInTheDocument()
    expect(config.queryByRole('link', { name: 'Módulos del hogar' })).not.toBeInTheDocument()
  })
})
