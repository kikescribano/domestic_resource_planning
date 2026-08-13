import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { api, formatBytes, humanMessage, type StoredFile } from '../api/client'
import { useAuthenticatedSession } from '../auth/SessionProvider'
import { FileGallery, QuotaMeter, UploadField, type GalleryItem } from '../ui/files'
import { Button, EmptyState, Notice, PageHeading, Spinner } from '../ui/primitives'

/**
 * El almacenamiento del hogar: cuánto ocupa, qué lo ocupa y qué se puede borrar.
 *
 * Es un **listado**, no una galería, y la diferencia no es de aspecto: la galería
 * pinta lo que cuelga de una cosa concreta, y esto pinta el gigabyte entero
 * ordenado por tamaño descendente, que es la pregunta real cuando la cuota se
 * agota —qué la está ocupando—. Comparten la celda, no la forma.
 */
export function StoragePage() {
  const { accessToken } = useAuthenticatedSession()
  const queryClient = useQueryClient()
  const [onlyUnattached, setOnlyUnattached] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)

  const usage = useQuery({
    queryKey: ['storage'],
    queryFn: () => api.getStorageUsage(accessToken),
  })

  const files = useQuery({
    queryKey: ['files', { onlyUnattached }],
    queryFn: () => api.listFiles(accessToken, onlyUnattached ? { attached: false } : {}),
  })

  const remove = useMutation({
    mutationFn: (id: string) => api.deleteFile(id, accessToken),
    onSuccess: () => {
      setProblem(null)
      // Las dos: la cuota se libera **en el acto**, así que el medidor tiene que
      // enterarse a la vez que el listado.
      void queryClient.invalidateQueries({ queryKey: ['files'] })
      void queryClient.invalidateQueries({ queryKey: ['storage'] })
    },
    onError: (error) => setProblem(humanMessage(error)),
  })

  const uploaded = useMutation({
    mutationFn: async () => undefined,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['files'] })
      void queryClient.invalidateQueries({ queryKey: ['storage'] })
    },
  })

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-col gap-6 p-4">
      <PageHeading title="Almacenamiento" />

      {usage.data && <QuotaMeter usedBytes={usage.data.usedBytes} quotaBytes={usage.data.quotaBytes} />}

      <UploadField
        label="Subir un fichero"
        accept="document"
        accessToken={accessToken}
        onUploaded={() => uploaded.mutate()}
      />

      {problem && <Notice tone="danger">{problem}</Notice>}

      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant={onlyUnattached ? 'primary' : 'secondary'}
          onClick={() => setOnlyUnattached((value) => !value)}
        >
          Solo los que no cuelgan de nada
        </Button>
      </div>

      {files.isPending && <Spinner label="Cargando los ficheros" />}

      {files.data && (
        <FileGallery
          label="Ficheros del hogar"
          items={files.data.items.map(toGalleryItem)}
          onOpen={(item) => window.open(`/api/v1/files/${item.id}/content`, '_blank', 'noopener')}
          onRemove={(item) => remove.mutate(item.id)}
          // Una miniatura que falla es una URL caducada: se vuelve a pedir el
          // listado, que devuelve URL frescas. Reintentar la misma daría 403
          // para siempre.
          onStale={() => void queryClient.invalidateQueries({ queryKey: ['files'] })}
          empty={
            <EmptyState title={onlyUnattached ? 'Todo lo subido cuelga de algo.' : 'Todavía no hay ficheros.'}>
              {onlyUnattached
                ? 'Nada que borrar sin perder nada.'
                : 'Las fotos y los documentos que subas aparecerán aquí.'}
            </EmptyState>
          }
        />
      )}
    </main>
  )
}

function toGalleryItem(file: StoredFile): GalleryItem {
  return {
    id: file.id,
    thumbnailUrl: file.thumbnailUrl,
    name: file.originalName,
    caption: formatBytes(file.sizeBytes),
  }
}
