plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    // Genera el constructor sin argumentos que JPA exige de toda entidad y que
    // Kotlin no produce por su cuenta.
    kotlin("plugin.jpa") version "2.1.20"
    // **Peldano hacia la linea 4.x, no destino.** La 3.5 esta fuera de soporte
    // OSS desde el 2026-06-30 y la 3.5.16 es su ultimo parche publicado; se para
    // aqui porque es lo que la guia de migracion de Spring exige antes de saltar
    // a la 4 --«upgrade to the latest available 3.5.x version» -- y porque este
    // salto ya cierra por si solo las CVE que la auditoria localizo en Spring
    // Security, Tomcat y Spring Framework. El salto a la 4.1 va aparte: arrastra
    // Jackson 3 con cambio de groupId, Kotlin 2.2+, Spring Security 7 y
    // Hibernate 7, y mezclarlo con esto haria imposible saber que rompio que.
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.drp"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Argon2id (README 4.1.4). El Argon2PasswordEncoder de Spring Security
    // delega en BouncyCastle, que no entra con el starter ni lo gestiona el BOM
    // de Spring Boot: de ahi la version explicita.
    // La 1.80 estaba en el rango de CVE-2025-8885 y CVE-2025-8916 --denegacion de
    // servicio parseando ASN.1 manipulado--, que aqui no eran alcanzables porque
    // BC solo respalda a Argon2 y el codigo no parsea ASN.1 de entrada ajena. Se
    // sube igual: el hallazgo era de severidad baja por el vector, no por la
    // vulnerabilidad, y mantener una version en rango obliga a repetir ese
    // razonamiento cada vez que alguien lea el informe.
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    // Firma y verificacion del JWT (README 5.4.1). Se toma por esta via, y no
    // declarando nimbus-jose-jwt a pelo, porque asi la version la fija Spring
    // Security y no hay que mantenerla a mano.
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // Swagger UI - documentacion interactiva de la API (README 5.4.2).
    //
    // Acompana a Spring Boot y no se elige aparte: la 2.8.5 salio para la linea
    // 3.4 y el soporte de la 3.5 llega despues, asi que subir Boot sin subir esto
    // es la forma habitual de que Swagger deje de responder. La linea 3.x de
    // springdoc es la del Boot 4 y entra con el, no antes.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.9.0")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // WebP, que la JVM no sabe ni leer ni escribir (ADR-005). Hacen falta las
    // dos direcciones: `image/webp` esta en la lista blanca de subida --asi que
    // hay que decodificarlo para recodificarlo-- y la miniatura de 320 px se
    // escribe en WebP.
    //
    // Se enchufa como plugin de ImageIO, de modo que el codigo de recodificacion
    // es el mismo para los tres formatos de imagen y no hay un camino aparte.
    // Trae los binarios nativos dentro del jar y los extrae al arrancar.
    implementation("org.sejda.imageio:webp-imageio:0.1.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    // Las fronteras de modulo de la ADR-010, comprobadas por la construccion y no
    // por un acuerdo: ningun modulo puede referenciar a otro, el core no puede
    // referenciar a ninguno y plataforma no se apoya en el core. Una regla que
    // solo se escribe en una ADR se incumple al tercer hito sin que nadie lo note.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * Copia el openapi.yaml de la raiz del repositorio al classpath, para que
 * Swagger UI lo sirva (ver OpenApiController). No se versiona una copia en
 * src/main/resources porque el contrato tiene una unica fuente de verdad
 * (CLAUDE.md): esta tarea la reconstruye en cada build.
 */
tasks.named<ProcessResources>("processResources") {
    from(rootDir.parentFile) {
        include("openapi.yaml")
    }
}

/**
 * `bootRun` es **el arranque local**, asi que declara el perfil `dev`.
 *
 * Es la otra mitad de lo que hace la tarea de pruebas, y por el mismo motivo:
 * los secretos de ejemplo del `application.yml` solo se toleran con un perfil de
 * desarrollo declarado, de modo que un despliegue que no declare ninguno falla
 * al arrancar en lugar de firmar con una clave publicada en el repositorio.
 *
 * Cubre tambien el recorrido vertical, que arranca el backend por aqui
 * (`frontend/e2e/start-backend.mjs`).
 */
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("spring.profiles.active", "dev")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }

    // **Las pruebas declaran su perfil, y no es cosmetico.**
    //
    // Los secretos de despliegue --la clave del JWT y la que firma las URL de
    // ficheros-- traen en el `application.yml` un valor de ejemplo que solo vale
    // para desarrollo, y el arranque lo rechaza **salvo** con un perfil de
    // desarrollo declarado. Antes la ausencia de perfil se tomaba por
    // desarrollo, y eso hacia que la comprobacion no mordiera justo donde tenia
    // que morder: en un despliegue que olvidara declararlo.
    //
    // Invertido el criterio, quien tiene que declararse es el que usa los
    // valores de ejemplo. Va aqui y no clase a clase porque son **47 clases**
    // con `@SpringBootTest`: anotarlas una a una convierte el olvido de la
    // proxima en un fallo de arranque sin relacion aparente con la causa.
    systemProperty("spring.profiles.active", "test")

    // El cliente de Docker que arrastra Testcontainers negocia por defecto la
    // version 1.32 de la API, y Docker 29 rechaza cualquiera anterior a la 1.40
    // con "client version 1.32 is too old". Sin esto, toda prueba con
    // Testcontainers falla al buscar el entorno de Docker en una maquina con
    // Docker reciente, y el sintoma --"Could not find a valid Docker
    // environment"-- no se parece en nada a la causa.
    //
    // Se fija 1.41 y no una mas nueva a proposito: es la minima que Docker 29
    // acepta y la soporta cualquier motor desde 2020, asi que no deja fuera a
    // ninguna maquina por ir por detras.
    //
    // La propiedad se llama "api.version" --asi la lee docker-java-- y no
    // DOCKER_API_VERSION, que es el nombre de la variable equivalente del CLI y
    // que aqui no tiene ningun efecto.
    systemProperty("api.version", "1.41")
}

/**
 * La medicion de capacidad no es una prueba: no afirma nada y no puede romper la
 * construccion porque la maquina vaya lenta ese dia. Queda fuera del `test` de
 * siempre y se pide por su nombre.
 *
 * Va sobre `named("test")` y **no** sobre `withType<Test>`, que era la primera
 * version y no funcionaba: `withType` alcanza tambien a la tarea de mas abajo, de
 * modo que la exclusion y la inclusion de la misma etiqueta se anulaban y la
 * medicion ejecutaba cero pruebas sin decir nada.
 */
tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("capacity") }
}

/**
 * La medicion que decide el VPS al cerrar la Fase 1 (ver
 * `docs/backend/operations/capacity-measurements.md`).
 *
 * Es una tarea aparte y no un `test` mas por dos motivos: **tarda mucho**
 * --siembra veinticinco hogares completos-- y **no afirma nada**, asi que
 * mezclarla con la suite convertiria una medicion en una fuente de fallos
 * intermitentes. Su salida se lee; no se compara con un umbral.
 */
tasks.register<Test>("capacityMeasurement") {
    description = "Mide bytes por hogar y coste de CPU de las operaciones caras del core"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform { includeTags("capacity") }
    systemProperty("api.version", "1.41")

    // Sin cache: una medicion que se saltase por estar "al dia" no mediria nada.
    outputs.upToDateWhen { false }

    testLogging {
        events("passed", "failed")
        showStandardStreams = true
    }
}
