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

/**
 * El limitador de frecuencia, holgado **solo aqui**.
 *
 * Por omision son 20 peticiones por IP cada 5 minutos sobre los endpoints sin
 * autenticar, y `/auth/refresh` es uno de ellos. La bateria entera sale de una
 * sola IP y **cada recarga de pagina reanuda la sesion con un refresco**, asi que
 * la auditoria sistematica --que recarga una vez por pantalla-- acaba gastando el
 * cupo y las ultimas pantallas aterrizan en la pantalla de entrar. El sintoma no
 * se parece a la causa: lo que falla es «el tabulador no encuentra el salto al
 * contenido», y quien lo lea buscara el defecto en la pantalla.
 *
 * Se descubrio al anadir dos pantallas a la lista en el Hito 0 del cierre de
 * huecos, que es cuando el cupo dejo de dar de si.
 *
 * Es exactamente lo que `SpringIntegrationTest` ya hace para la bateria de
 * integracion, y por el mismo motivo: **lo que se comprueba del limitador tiene
 * su propia prueba**, `RateLimitTest`, con sus propios valores. Aqui estorba sin
 * defender nada.
 *
 * Va por `SPRING_APPLICATION_JSON` y no por una variable suelta porque la forma
 * de entorno de `drp.rate-limit.per-ip` es adivinable pero no evidente, y una
 * variable mal escrita **no falla**: se ignora en silencio y volvemos a tener el
 * problema sin saberlo.
 */
const RATE_LIMITS = { drp: { 'rate-limit': { 'per-ip': 10_000, 'per-email': 10_000 } } }

// `shell: true` en Windows no es opcional: desde que Node cerro CVE-2024-27980,
// spawn se niega a ejecutar un .bat o un .cmd sin shell y falla con EINVAL, que
// no se parece a la causa. En Linux se deja en false, que es lo seguro.
const child = spawn(wrapper, ['bootRun'], {
  cwd: backend,
  stdio: 'inherit',
  shell: process.platform === 'win32',
  env: { ...process.env, SPRING_APPLICATION_JSON: JSON.stringify(RATE_LIMITS) },
})

child.on('exit', (code) => process.exit(code ?? 0))
process.on('SIGTERM', () => child.kill('SIGTERM'))
process.on('SIGINT', () => child.kill('SIGINT'))
