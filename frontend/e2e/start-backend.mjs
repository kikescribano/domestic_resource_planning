/**
 * Arranca el backend para Playwright, en las dos plataformas.
 *
 * Existe porque `webServer.command` lo interpreta el shell del sistema, y ahi
 * las dos no se parecen: `cmd` no entiende `./gradlew` y `sh` no ejecuta
 * `gradlew.bat`. Poner el condicional en la configuracion tampoco bastaba --el
 * `cwd` no llega a resolverse antes de buscar el ejecutable-- asi que se resuelve
 * aqui, con rutas absolutas y sin depender del shell.
 */
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const backend = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'backend')
const wrapper = join(backend, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')

// `shell: true` en Windows no es opcional: desde que Node cerro CVE-2024-27980,
// spawn se niega a ejecutar un .bat o un .cmd sin shell y falla con EINVAL, que
// no se parece a la causa. En Linux se deja en false, que es lo seguro.
const child = spawn(wrapper, ['bootRun'], {
  cwd: backend,
  stdio: 'inherit',
  shell: process.platform === 'win32',
})

child.on('exit', (code) => process.exit(code ?? 0))
process.on('SIGTERM', () => child.kill('SIGTERM'))
process.on('SIGINT', () => child.kill('SIGINT'))
