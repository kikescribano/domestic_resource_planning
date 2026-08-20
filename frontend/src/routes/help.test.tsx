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

  it('tiene una sección por pantalla, cada una con su enlace y sus tarjetas', async () => {
    await openHelp()

    // Una muestra de cada grupo de la navegación —Tu hogar, Datos maestros,
    // Configuración— y un módulo. Si la lista de pantallas crece, la sección
    // nueva se añade a `HELP_TOPICS` y esta prueba no se toca.
    for (const title of ['Inventario', 'Catálogo', 'General', 'Mantenimiento']) {
      expect(screen.getByRole('region', { name: title })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: `Ir a ${title}` })).toBeInTheDocument()
    }

    expect(screen.getByRole('link', { name: 'Ir a Almacén' })).toHaveAttribute('href', '/almacen')

    // Dentro de una sección: la tarjeta de la explicación general y una por
    // caso de uso, cada caso con su ejemplo práctico rotulado.
    const loans = within(screen.getByRole('region', { name: 'Préstamos' }))
    expect(loans.getByRole('article', { name: 'Explicación general' })).toBeInTheDocument()
    const firstCase = within(loans.getByRole('article', { name: 'Registrar un préstamo' }))
    expect(firstCase.getByText('Ejemplo:')).toBeInTheDocument()
  })

  it('el buscador filtra tarjeta a tarjeta y no distingue acentos', async () => {
    await openHelp()

    // «prestamo» sin tilde tiene que encontrar «Préstamos»: es la promesa que
    // la pista del campo deja escrita, la misma comparación del catálogo.
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar' }), 'prestamo')

    expect(screen.getByRole('region', { name: 'Préstamos' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Proveedores' })).not.toBeInTheDocument()
  })

  it('una palabra que solo vive en un caso deja esa tarjeta sola bajo su sección', async () => {
    await openHelp()

    // La granularidad es el motivo del reparto en tarjetas: «guirnalda» solo
    // aparece en el ejemplo de un caso de Ubicaciones, así que sobrevive esa
    // tarjeta, con la cabecera de su sección encima y sin la explicación
    // general al lado.
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar' }), 'guirnalda')

    const section = within(screen.getByRole('region', { name: 'Ubicaciones' }))
    expect(section.getAllByRole('article')).toHaveLength(1)
    expect(section.getByRole('article', { name: 'Declarar cuánto cabe' })).toBeInTheDocument()
    expect(screen.getAllByRole('region')).toHaveLength(1)
  })

  it('resalta la coincidencia dentro de la tarjeta, con sus acentos', async () => {
    await openHelp()

    // La aguja va sin tilde y el resaltado recorta del texto ORIGINAL: lo que
    // queda dentro del `<mark>` es «préstamo» con su tilde, no la forma
    // normalizada que usó la comparación.
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar' }), 'prestamo')

    const general = within(screen.getByRole('region', { name: 'Préstamos' })).getByRole('article', {
      name: 'Explicación general',
    })
    expect(within(general).getAllByText('préstamo', { selector: 'mark' }).length).toBeGreaterThan(0)
  })

  it('cuando nada coincide lo dice, en lugar de dejar la lista vacía', async () => {
    await openHelp()

    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar' }), 'zzzz')

    expect(screen.getByText('Ninguna tarjeta coincide')).toBeInTheDocument()
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
