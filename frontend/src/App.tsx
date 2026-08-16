import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'

import { SessionProvider } from './auth/SessionProvider'
import {
  AcceptInvitationPage,
  CreateHouseholdPage,
  ForgotPasswordPage,
  LoginPage,
  ResendVerificationPage,
  ResetPasswordPage,
  VerifyEmailPage,
} from './routes/enrollment'
import { AssetDetailPage, AssetsPage, IntakePage, NewAssetPage } from './routes/assets'
import { CatalogPage } from './routes/catalog'
import { AccountPage, HomePage, RequireSession, UsersPage } from './routes/household'
import { ExternalLoanPage, LoansPage } from './routes/loans'
import { LocationsPage } from './routes/locations'
import { StoragePage } from './routes/storage'

/**
 * Las rutas del Hito 1.
 *
 * Las públicas llevan nombre en castellano porque **viajan dentro de un correo**
 * y las lee una persona: el enlace de verificación dice `/verificar-correo`, no
 * `/verify-email`. Es la misma frontera de siempre —identificador en inglés,
 * dato en castellano— aplicada a una URL que es, de hecho, texto para el
 * usuario. Los componentes que las sirven sí van en inglés.
 *
 * Esos nombres tienen que coincidir con los que construye `EnrollmentEmails` en
 * el backend: si uno cambia, el enlace del correo lleva a una pantalla que no
 * existe.
 */

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Un 401 o un 403 no se arreglan repitiendo la petición: reintentarlos
        // solo retrasa el mensaje de error y multiplica el ruido en el servidor.
        retry: (failureCount, error) => {
          const status = (error as { status?: number }).status
          if (status === 401 || status === 403 || status === 404) return false
          return failureCount < 2
        },
        staleTime: 30_000,
      },
    },
  })
}

export function App() {
  // Uno por montaje y no uno de módulo, que es lo que había. La diferencia no
  // se nota en el navegador --la aplicación se monta una vez-- pero sí en las
  // pruebas: con un cliente de módulo, la caché sobrevive de una prueba a la
  // siguiente y, con `staleTime` de 30 s, la segunda lee los datos que preparó
  // la primera. El síntoma es una prueba que pasa sola y falla acompañada, que
  // es de los más caros de diagnosticar.
  const [queryClient] = useState(createQueryClient)

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SessionProvider>
          <Routes>
            <Route path="/crear-hogar" element={<CreateHouseholdPage />} />
            <Route path="/verificar-correo" element={<VerifyEmailPage />} />
            <Route path="/reenviar-confirmacion" element={<ResendVerificationPage />} />
            <Route path="/entrar" element={<LoginPage />} />
            <Route path="/recuperar" element={<ForgotPasswordPage />} />
            <Route path="/restablecer-contrasena" element={<ResetPasswordPage />} />
            <Route path="/aceptar-invitacion" element={<AcceptInvitationPage />} />
            {/* La vista externa de un préstamo. Va aquí arriba, FUERA de
                `RequireSession`, y es la única pantalla del producto que se ve
                sin cuenta: la abre quien recibió el enlace por correo y su
                credencial es el token de la URL. */}
            <Route path="/prestamo" element={<ExternalLoanPage />} />

            <Route element={<RequireSession />}>
              <Route path="/" element={<HomePage />} />
              <Route path="/catalogo" element={<CatalogPage />} />
              <Route path="/ubicaciones" element={<LocationsPage />} />
              <Route path="/inventario" element={<AssetsPage />} />
              {/* Antes que `/inventario/:id`, o «nuevo» se leería como un
                  identificador y la ficha respondería 404. */}
              <Route path="/inventario/nuevo" element={<NewAssetPage />} />
              <Route path="/inventario/entrada" element={<IntakePage />} />
              <Route path="/inventario/:id" element={<AssetDetailPage />} />
              <Route path="/prestamos" element={<LoansPage />} />
              <Route path="/usuarios" element={<UsersPage />} />
              <Route path="/almacenamiento" element={<StoragePage />} />
              <Route path="/cuenta" element={<AccountPage />} />
            </Route>
          </Routes>
        </SessionProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
