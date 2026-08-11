import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'

import { ApiError, api } from '../api/client'
import { useSession } from '../auth/SessionProvider'
import { AuthCard, Button, Field, Notice, Spinner } from '../ui/primitives'

/**
 * Los flujos de enrolamiento: todo lo que ocurre antes de tener sesión.
 *
 * Comparten una decisión que viene del backend y conviene no deshacer en la
 * interfaz: **los endpoints anónimos responden siempre igual, exista o no el
 * correo**. Así que estas pantallas tampoco pueden decir «ese correo no está
 * registrado». Cuando eso se olvida, el frontend reintroduce por su cuenta la
 * fuga que el backend evitaba.
 */

const MIN_PASSWORD_LENGTH = 12

/** El huso del navegador, que acierta casi siempre y ahorra un desplegable de 400 entradas. */
function detectedTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/Madrid'
  } catch {
    return 'Europe/Madrid'
  }
}

/** Traduce un error de la API a algo que se pueda leer. */
function messageFor(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'No se ha podido conectar. Comprueba tu conexión e inténtalo otra vez.'
  }
  switch (error.code) {
    case 'RATE_LIMITED':
      return error.retryAfterSeconds
        ? `Demasiados intentos. Vuelve a probar en ${error.retryAfterSeconds} segundos.`
        : 'Demasiados intentos. Espera un poco antes de volver a probar.'
    case 'EMAIL_NOT_VERIFIED':
      return 'Tu correo todavía no está confirmado. Mira tu bandeja de entrada.'
    case 'VERIFICATION_TOKEN_INVALID':
      return 'Este enlace ya no vale: puede haber caducado o haberse usado antes.'
    case 'RESET_TOKEN_INVALID':
      return 'Este enlace de recuperación ya no vale. Pide uno nuevo.'
    case 'INVITATION_TOKEN_INVALID':
      return 'Esta invitación ya no vale: puede haber caducado, haberse usado o haberse retirado.'
    case 'IDENTITY_ALREADY_MEMBER':
      return 'Esa cuenta ya pertenece a otro hogar.'
    case 'UNAUTHORIZED':
      return 'El correo o la contraseña no son correctos.'
    default:
      return error.message
  }
}

export function CreateHouseholdPage() {
  const [householdName, setHouseholdName] = useState('')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | Error | null>(null)
  const [sent, setSent] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.createHousehold({
        name: householdName,
        timeZone: detectedTimeZone(),
        admin: { name, email, password },
      })
      setSent(true)
    } catch (failure) {
      setError(failure as Error)
    } finally {
      setBusy(false)
    }
  }

  if (sent) {
    return (
      <AuthCard title="Mira tu correo" subtitle={`Hemos escrito a ${email}.`}>
        <Notice tone="success" title="Ya casi está">
          Abre el enlace que te hemos enviado para confirmar la dirección. Hasta
          entonces el hogar no se puede usar, y si no lo confirmas se borra solo a
          los siete días.
        </Notice>
        <p className="text-body-sm text-ink-muted">
          ¿No te ha llegado? Mira en la carpeta de correo no deseado, o{' '}
          <Link to="/reenviar-confirmacion" className="text-accent-ink underline">
            pídelo otra vez
          </Link>
          .
        </p>
      </AuthCard>
    )
  }

  const fieldError = error instanceof ApiError ? error : null

  return (
    <AuthCard title="Crea tu hogar" subtitle="Un sitio para saber qué tienes y dónde está.">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && !fieldError?.details && (
          <Notice tone="danger" title="No se ha podido crear">
            {messageFor(error)}
          </Notice>
        )}

        <Field
          label="Nombre del hogar"
          value={householdName}
          onChange={(event) => setHouseholdName(event.target.value)}
          placeholder="Casa del Pinar"
          autoComplete="off"
          required
        />
        <Field
          label="Tu nombre"
          value={name}
          onChange={(event) => setName(event.target.value)}
          autoComplete="name"
          required
        />
        <Field
          label="Tu correo"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          autoComplete="email"
          error={fieldError?.fieldError('admin.email')}
          required
        />
        <Field
          label="Contraseña"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="new-password"
          hint={`Mínimo ${MIN_PASSWORD_LENGTH} caracteres. Una frase que recuerdes vale más que un jeroglífico.`}
          error={fieldError?.fieldError('admin.password') ?? fieldError?.fieldError('password')}
          required
        />

        <Button type="submit" variant="primary" busy={busy} busyLabel="Creando…">
          Crear el hogar
        </Button>
      </form>

      <p className="text-body-sm text-ink-muted">
        ¿Ya tienes cuenta?{' '}
        <Link to="/entrar" className="text-accent-ink underline">
          Entra
        </Link>
        .
      </p>
    </AuthCard>
  )
}

export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const { signIn } = useSession()
  const navigate = useNavigate()
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    if (!token) {
      setError(new Error('falta el token'))
      return
    }

    let cancelled = false
    api
      .verifyEmail(token)
      .then((tokens) => {
        if (cancelled) return
        signIn(tokens)
        navigate('/', { replace: true })
      })
      .catch((failure: Error) => {
        if (!cancelled) setError(failure)
      })

    // El token es de un solo uso: si React vuelve a montar el efecto —en modo
    // estricto lo hace— la segunda llamada fallaría y borraría el éxito de la
    // primera. De ahí la bandera.
    return () => {
      cancelled = true
    }
  }, [token, signIn, navigate])

  if (error) {
    return (
      <AuthCard title="Este enlace ya no vale">
        <Notice tone="danger">{messageFor(error)}</Notice>
        <p className="text-body-sm text-ink-muted">
          Puedes{' '}
          <Link to="/reenviar-confirmacion" className="text-accent-ink underline">
            pedir uno nuevo
          </Link>{' '}
          con el mismo correo.
        </p>
      </AuthCard>
    )
  }

  return (
    <AuthCard title="Confirmando tu correo">
      <Spinner label="Un momento…" />
    </AuthCard>
  )
}

export function ResendVerificationPage() {
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    // No hay rama de error visible a propósito: el endpoint responde igual
    // exista o no el correo, así que la interfaz tampoco puede distinguir.
    await api.resendVerification(email).catch(() => undefined)
    setBusy(false)
    setSent(true)
  }

  if (sent) {
    return (
      <AuthCard title="Enviado" subtitle={`Si hay una cuenta sin confirmar con ${email}, le llegará un enlace nuevo.`}>
        <Notice tone="info">El enlace anterior deja de valer en cuanto se envía este.</Notice>
      </AuthCard>
    )
  }

  return (
    <AuthCard title="Reenviar la confirmación">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <Field
          label="Tu correo"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          autoComplete="email"
          required
        />
        <Button type="submit" variant="primary" busy={busy} busyLabel="Enviando…">
          Enviar el enlace
        </Button>
      </form>
    </AuthCard>
  )
}

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  const { signIn } = useSession()
  const navigate = useNavigate()

  const needsVerification = error instanceof ApiError && error.code === 'EMAIL_NOT_VERIFIED'

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      signIn(await api.login(email, password))
      navigate('/', { replace: true })
    } catch (failure) {
      setError(failure as Error)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Entrar">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && (
          <Notice tone={needsVerification ? 'warning' : 'danger'}>
            {messageFor(error)}
            {needsVerification && (
              <>
                {' '}
                <Link to="/reenviar-confirmacion" className="text-accent-ink underline">
                  Reenviar el enlace
                </Link>
                .
              </>
            )}
          </Notice>
        )}

        <Field
          label="Correo"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          autoComplete="email"
          required
        />
        <Field
          label="Contraseña"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
          required
        />

        <Button type="submit" variant="primary" busy={busy} busyLabel="Entrando…">
          Entrar
        </Button>
      </form>

      <div className="flex flex-col gap-1 text-body-sm text-ink-muted">
        <Link to="/recuperar" className="text-accent-ink underline">
          He olvidado la contraseña
        </Link>
        <span>
          ¿Aún no tienes hogar?{' '}
          <Link to="/crear-hogar" className="text-accent-ink underline">
            Crea uno
          </Link>
          .
        </span>
      </div>
    </AuthCard>
  )
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    await api.requestPasswordReset(email).catch(() => undefined)
    setBusy(false)
    setSent(true)
  }

  if (sent) {
    return (
      <AuthCard title="Mira tu correo" subtitle={`Si hay una cuenta con ${email}, le llegará un enlace.`}>
        <Notice tone="info">
          El enlace caduca en una hora y solo se puede usar una vez. Al usarlo se
          cerrarán todas tus sesiones abiertas.
        </Notice>
      </AuthCard>
    )
  }

  return (
    <AuthCard title="Recuperar la contraseña" subtitle="Te mandamos un enlace para poner una nueva.">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <Field
          label="Tu correo"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          autoComplete="email"
          required
        />
        <Button type="submit" variant="primary" busy={busy} busyLabel="Enviando…">
          Enviar el enlace
        </Button>
      </form>

      <Link to="/entrar" className="text-body-sm text-accent-ink underline">
        Volver a entrar
      </Link>
    </AuthCard>
  )
}

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  const { signIn } = useSession()
  const navigate = useNavigate()

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      signIn(await api.resetPassword(token, password))
      navigate('/', { replace: true })
    } catch (failure) {
      setError(failure as Error)
    } finally {
      setBusy(false)
    }
  }

  const fieldError = error instanceof ApiError ? error.fieldError('password') : undefined

  return (
    <AuthCard title="Pon una contraseña nueva">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && !fieldError && <Notice tone="danger">{messageFor(error)}</Notice>}

        <Field
          label="Contraseña nueva"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="new-password"
          hint={`Mínimo ${MIN_PASSWORD_LENGTH} caracteres.`}
          error={fieldError}
          required
        />
        <Button type="submit" variant="primary" busy={busy} busyLabel="Guardando…">
          Guardar y entrar
        </Button>
      </form>
    </AuthCard>
  )
}

export function AcceptInvitationPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  const { signIn } = useSession()
  const navigate = useNavigate()

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      // El nombre y la contraseña solo hacen falta si la persona no tenía cuenta
      // todavía. Se mandan siempre y el backend los ignora si ya existe: la
      // interfaz no puede saber cuál es el caso sin preguntarle a la API si ese
      // correo está registrado, que es justo lo que no se puede responder.
      signIn(await api.acceptInvitation({ token, name, password }))
      navigate('/', { replace: true })
    } catch (failure) {
      setError(failure as Error)
    } finally {
      setBusy(false)
    }
  }

  const fieldError = error instanceof ApiError ? error : null

  return (
    <AuthCard title="Te han invitado" subtitle="Acepta para entrar en el hogar.">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && !fieldError?.details && <Notice tone="danger">{messageFor(error)}</Notice>}

        <Field
          label="Tu nombre"
          value={name}
          onChange={(event) => setName(event.target.value)}
          autoComplete="name"
          hint="Si ya tenías cuenta en DRP, estos dos campos se ignoran."
          error={fieldError?.fieldError('name')}
        />
        <Field
          label="Contraseña"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="new-password"
          hint={`Mínimo ${MIN_PASSWORD_LENGTH} caracteres.`}
          error={fieldError?.fieldError('password')}
        />

        <Button type="submit" variant="primary" busy={busy} busyLabel="Entrando…">
          Aceptar la invitación
        </Button>
      </form>
    </AuthCard>
  )
}
