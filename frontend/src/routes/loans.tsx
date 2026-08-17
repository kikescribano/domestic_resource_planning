import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'

import {
  ApiError,
  LOAN_STATUS_LABELS,
  api,
  formatDate,
  humanMessage,
  type Asset,
  type ExternalLoan,
  type Loan,
  type LoanStatus,
  type User,
} from '../api/client'
import { useAuthenticatedSession } from '../auth/SessionProvider'
import {
  AuthCard,
  Button,
  EmptyState,
  Field,
  Notice,
  PageHeading,
  SelectField,
  Spinner,
  StatusBadge,
} from '../ui/primitives'

/**
 * Las dos pantallas de préstamos, que no se parecen en nada.
 *
 * La del hogar vive dentro del shell y ve el préstamo entero. La externa se abre
 * **desde un correo, sin sesión y sin navegación**, y ve cinco campos: es la
 * única superficie del producto que se ve sin cuenta. Su ficha está en
 * `docs/frontend/design-system/components/loan-external-page.md`, escrita antes
 * que esta pantalla para guiarla.
 *
 * Comparten fichero porque comparten la forma de un préstamo y las etiquetas de
 * sus estados; si se separasen, la tentación sería duplicarlas.
 */

/** El tono del distintivo de cada estado. `OVERDUE` llama a la acción sin alarmar. */
function toneOf(status: LoanStatus): 'success' | 'warning' | 'neutral' {
  if (status === 'OVERDUE') return 'warning'
  if (status === 'RETURNED') return 'neutral'
  return 'success'
}

// --- La pantalla del hogar ---------------------------------------------------

export function LoansPage() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [showOpenOnly, setShowOpenOnly] = useState(true)
  const [isStarting, setIsStarting] = useState(false)

  const loans = useQuery({
    queryKey: ['loans', { open: showOpenOnly }],
    queryFn: () => api.listLoans(accessToken, showOpenOnly ? { open: true } : {}),
  })

  const confirmReturn = useMutation({
    mutationFn: (loanId: string) => api.confirmReturn(loanId, accessToken),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['loans'] })
      // El asset vuelve a AVAILABLE, así que el inventario también cambia.
      void queryClient.invalidateQueries({ queryKey: ['assets'] })
    },
  })

  return (
    <section className="space-y-6">
      <PageHeading
        title="Préstamos"
        action={
          <Button onClick={() => setIsStarting((open) => !open)}>
            {isStarting ? 'Cancelar' : 'Prestar algo'}
          </Button>
        }
      />

      {isStarting && <StartLoanForm onDone={() => setIsStarting(false)} />}

      <div className="flex gap-2">
        <Button variant={showOpenOnly ? 'primary' : 'secondary'} onClick={() => setShowOpenOnly(true)}>
          Fuera de casa
        </Button>
        <Button variant={showOpenOnly ? 'secondary' : 'primary'} onClick={() => setShowOpenOnly(false)}>
          Todos
        </Button>
      </div>

      {confirmReturn.isError && <Notice tone="danger">{humanMessage(confirmReturn.error)}</Notice>}

      {loans.isPending && <Spinner label="Cargando los préstamos" />}
      {loans.isError && <Notice tone="danger">{humanMessage(loans.error)}</Notice>}

      {loans.data?.items.length === 0 && (
        <EmptyState title={showOpenOnly ? 'No hay nada fuera de casa' : 'Todavía no has prestado nada'}>
          Cuando prestes algo, aquí verás qué es, a quién y para cuándo.
        </EmptyState>
      )}

      <ul className="space-y-3">
        {loans.data?.items.map((loan) => (
          <LoanCard
            key={loan.id}
            loan={loan}
            onReturn={() => confirmReturn.mutate(loan.id)}
            isReturning={confirmReturn.isPending && confirmReturn.variables === loan.id}
          />
        ))}
      </ul>
    </section>
  )
}

function LoanCard({
  loan,
  onReturn,
  isReturning,
}: {
  loan: Loan
  onReturn: () => void
  isReturning: boolean
}) {
  const isOpen = loan.status === 'ACTIVE' || loan.status === 'OVERDUE'

  return (
    <li className="rounded-lg border border-line bg-surface p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <p className="font-medium">{loan.assetName ?? 'Sin nombre'}</p>
          <p className="text-sm text-muted">
            {describeParticipant(loan, 'borrower')} · desde el {formatDate(loan.startedAt)}
          </p>
          {loan.dueAt && (
            <p className="text-sm text-muted">
              {loan.status === 'RETURNED' ? 'Vencía' : 'Vence'} el {formatDate(loan.dueAt)}
            </p>
          )}
          {loan.returnedAt && <p className="text-sm text-muted">Devuelto el {formatDate(loan.returnedAt)}</p>}
        </div>

        <div className="flex items-center gap-3">
          <StatusBadge tone={toneOf(loan.status)}>{LOAN_STATUS_LABELS[loan.status]}</StatusBadge>
          {isOpen && (
            <Button variant="secondary" onClick={onReturn} disabled={isReturning}>
              {isReturning ? 'Guardando…' : 'Ya lo tengo'}
            </Button>
          )}
        </div>
      </div>
    </li>
  )
}

/** Quién es cada extremo, con el nombre del externo cuando lo hay. */
function describeParticipant(loan: Loan, end: 'lender' | 'borrower'): string {
  const participant = loan[end]
  if (participant.external) return participant.external.name
  return end === 'borrower' ? 'A alguien de casa' : 'De casa'
}

/**
 * El formulario de alta.
 *
 * Los dos extremos son **o un miembro del hogar o alguien de fuera**, y la
 * interfaz lo dice con un selector en el que «Otra persona» abre los campos del
 * externo. Así el estado imposible —los dos a la vez— no se puede ni teclear,
 * que es la misma decisión que toma el dominio con `LoanParticipant`.
 */
function StartLoanForm({ onDone }: { onDone: () => void }) {
  const { accessToken, claims } = useAuthenticatedSession()
  const queryClient = useQueryClient()

  const [assetId, setAssetId] = useState('')
  const [lenderId, setLenderId] = useState(claims.memberId)
  const [borrowerId, setBorrowerId] = useState('')
  const [externalName, setExternalName] = useState('')
  const [externalEmail, setExternalEmail] = useState('')
  const [dueAt, setDueAt] = useState('')
  const [notes, setNotes] = useState('')

  const isExternal = borrowerId === EXTERNAL

  // Solo lo que se puede prestar: DURABLE y disponible. Ofrecer lo prestado
  // solo sirve para que la API responda 409 a algo que ya se sabía.
  const lendable = useQuery({
    queryKey: ['assets', { type: 'DURABLE', status: 'AVAILABLE' }],
    queryFn: () => api.listAssets(accessToken, { type: 'DURABLE', status: 'AVAILABLE' }),
  })
  const members = useQuery({
    queryKey: ['users'],
    queryFn: () => api.listUsers(accessToken),
  })

  const start = useMutation({
    mutationFn: () =>
      api.startLoan(
        {
          assetId,
          lender: { userId: lenderId },
          borrower: isExternal
            ? { external: { name: externalName, email: externalEmail || undefined } }
            : { userId: borrowerId },
          dueAt: dueAt ? new Date(dueAt).toISOString() : undefined,
          notes: notes || undefined,
        },
        accessToken,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['loans'] })
      void queryClient.invalidateQueries({ queryKey: ['assets'] })
      onDone()
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    start.mutate()
  }

  const error = start.error instanceof ApiError ? start.error : null

  return (
    <form onSubmit={submit} className="space-y-4 rounded-lg border border-line bg-surface p-4">
      <SelectField
        label="Qué prestas"
        value={assetId}
        onChange={(event) => setAssetId(event.target.value)}
        required
      >
        <option value="">Elige una cosa</option>
        {lendable.data?.items.map((asset: Asset) => (
          <option key={asset.id} value={asset.id}>
            {asset.name}
          </option>
        ))}
      </SelectField>

      <SelectField
        label="Quién lo presta"
        value={lenderId}
        onChange={(event) => setLenderId(event.target.value)}
        required
      >
        {members.data?.items.map((user: User) => (
          <option key={user.id} value={user.id}>
            {user.name}
          </option>
        ))}
      </SelectField>

      <SelectField
        label="A quién"
        value={borrowerId}
        onChange={(event) => setBorrowerId(event.target.value)}
        required
        error={error?.fieldError('borrower')}
      >
        <option value="">Elige a quién</option>
        {members.data?.items.map((user: User) => (
          <option key={user.id} value={user.id}>
            {user.name}
          </option>
        ))}
        <option value={EXTERNAL}>Otra persona</option>
      </SelectField>

      {isExternal && (
        <>
          <Field
            label="Su nombre"
            value={externalName}
            onChange={(event) => setExternalName(event.target.value)}
            required
          />
          <Field
            label="Su correo"
            type="email"
            value={externalEmail}
            onChange={(event) => setExternalEmail(event.target.value)}
            hint="Le llegará un enlace para ver el préstamo y avisar cuando lo devuelva."
            error={error?.fieldError('borrower.external')}
            required
          />
        </>
      )}

      <Field
        label="Para cuándo (opcional)"
        type="date"
        value={dueAt}
        onChange={(event) => setDueAt(event.target.value)}
        hint="Sin fecha, el préstamo no vence nunca."
        error={error?.fieldError('dueAt')}
      />

      <Field label="Notas (opcional)" value={notes} onChange={(event) => setNotes(event.target.value)} />

      {start.isError && <Notice tone="danger">{humanMessage(start.error)}</Notice>}

      <Button type="submit" disabled={start.isPending}>
        {start.isPending ? 'Guardando…' : 'Prestar'}
      </Button>
    </form>
  )
}

/** Valor centinela del selector: no es un identificador, así que no puede chocar con uno. */
const EXTERNAL = 'external'

// --- La pantalla externa -----------------------------------------------------

/**
 * Lo que abre quien no tiene cuenta.
 *
 * **Sin sesión, sin shell y sin navegación**: es terminal en los dos sentidos, no
 * se llega desde dentro y no se va hacia dentro. Toda su credencial es el token
 * de la URL, y todo lo que puede hacer es mirar y confirmar la devolución.
 *
 * Pinta los campos de `ExternalLoan` y solo esos. Que sea un tipo distinto del
 * `Loan` completo no es ceremonia: es lo que impide que un descuido enseñe aquí
 * quién prestó.
 */
export function ExternalLoanPage() {
  const [params] = useSearchParams()
  const loanId = params.get('id') ?? ''
  const token = params.get('token') ?? ''

  const loan = useQuery({
    queryKey: ['external-loan', loanId],
    queryFn: () => api.getLoanWithToken(loanId, token),
    enabled: Boolean(loanId && token),
    retry: false,
  })

  const confirm = useMutation({
    mutationFn: () => api.confirmReturnWithToken(loanId, token),
    onSuccess: (updated) => loan.refetch().catch(() => updated),
  })

  if (!loanId || !token) return <BrokenLink />
  if (loan.isPending) {
    return (
      <div className="flex min-h-dvh items-center justify-center px-gutter">
        <Spinner label="Abriendo el préstamo" />
      </div>
    )
  }
  // Caducado, revocado o manipulado responden igual, y la pantalla también: no
  // puede decir si el préstamo existe sin convertir el enlace en un oráculo.
  if (loan.isError) return <BrokenLink />

  return <ExternalLoanView loan={loan.data} onConfirm={() => confirm.mutate()} confirming={confirm} />
}

function ExternalLoanView({
  loan,
  onConfirm,
  confirming,
}: {
  loan: ExternalLoan
  onConfirm: () => void
  confirming: { isPending: boolean; isError: boolean; error: unknown; isSuccess: boolean }
}) {
  const isOpen = loan.status === 'ACTIVE' || loan.status === 'OVERDUE'
  const isLender = loan.role === 'LENDER'

  return (
    <main className="flex min-h-dvh items-center justify-center px-gutter py-10">
      <AuthCard
        title={loan.assetName ?? 'Un préstamo'}
        subtitle={isLender ? 'Lo tienes prestado' : 'Lo tienes prestado a ti'}
      >
        <div className="space-y-4">
          <StatusBadge tone={toneOf(loan.status)}>{LOAN_STATUS_LABELS[loan.status]}</StatusBadge>

          <dl className="space-y-2 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-muted">Desde</dt>
              <dd>{formatDate(loan.startedAt)}</dd>
            </div>
            {loan.dueAt && (
              <div className="flex justify-between gap-4">
                <dt className="text-muted">{loan.status === 'RETURNED' ? 'Vencía' : 'Hasta'}</dt>
                <dd>{formatDate(loan.dueAt)}</dd>
              </div>
            )}
            {loan.returnedAt && (
              <div className="flex justify-between gap-4">
                <dt className="text-muted">Devuelto</dt>
                <dd>{formatDate(loan.returnedAt)}</dd>
              </div>
            )}
          </dl>

          {/* El anuncio del cambio va en el `Notice` de abajo y no en una región
              propia. Había las dos, y eso es justo lo que la ficha de esta
              pantalla prohíbe: `Notice` ya es `role="status"`, así que un lector
              de pantalla leía la misma noticia dos veces, primero «Préstamo
              devuelto» y después la frase entera. Se queda la que dice algo más
              —que no hay que hacer nada más—, y se anuncia porque aquí el foco no
              se mueve: al desaparecer el botón la pantalla se queda sin ninguna
              parada de tabulador, que es su estado final correcto. */}
          {isOpen ? (
            <>
              <p className="text-sm text-muted">
                {isLender
                  ? 'Cuando te lo devuelvan, avísalo aquí y se cerrará el préstamo.'
                  : 'Cuando se lo devuelvas, avísalo aquí y se cerrará el préstamo.'}
              </p>
              <Button onClick={onConfirm} disabled={confirming.isPending}>
                {confirming.isPending ? 'Guardando…' : isLender ? 'Ya me lo han devuelto' : 'Ya lo he devuelto'}
              </Button>
            </>
          ) : (
            <Notice tone="success">Este préstamo ya está cerrado. No hace falta que hagas nada más.</Notice>
          )}

          {confirming.isError && <Notice tone="danger">{humanMessage(confirming.error)}</Notice>}
        </div>
      </AuthCard>
    </main>
  )
}

/**
 * El enlace que no vale.
 *
 * **No dice por qué**, y eso es deliberado: distinguir «ha caducado» de «no
 * existe» convertiría el enlace en un oráculo con el que averiguar qué préstamos
 * hay. Lo que sí hace es decirle a una persona real qué puede hacer, que es
 * hablar con quien le prestó la cosa, porque desde aquí no hay nada más.
 */
function BrokenLink() {
  return (
    <main className="flex min-h-dvh items-center justify-center px-gutter py-10">
      <AuthCard title="Este enlace ya no vale">
        <p className="text-sm text-muted">
          Puede que haya caducado o que se haya sustituido por otro. Habla con la persona que te prestó la
          cosa: puede volver a enviártelo o apuntar la devolución por su cuenta.
        </p>
      </AuthCard>
    </main>
  )
}
