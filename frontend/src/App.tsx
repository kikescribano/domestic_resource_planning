import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
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
import { AccountPage, HomePage, RequireSession, UsersPage } from './routes/household'

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

const queryClient = new QueryClient({
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

export function App() {
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

            <Route element={<RequireSession />}>
              <Route path="/" element={<HomePage />} />
              <Route path="/usuarios" element={<UsersPage />} />
              <Route path="/cuenta" element={<AccountPage />} />
            </Route>
          </Routes>
        </SessionProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
