plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    // Genera el constructor sin argumentos que JPA exige de toda entidad y que
    // Kotlin no produce por su cuenta.
    kotlin("plugin.jpa") version "2.1.20"
    id("org.springframework.boot") version "3.4.5"
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
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    // Firma y verificacion del JWT (README 5.4.1). Se toma por esta via, y no
    // declarando nimbus-jose-jwt a pelo, porque asi la version la fija Spring
    // Security y no hay que mantenerla a mano.
    implementation("org.springframework.security:spring-security-oauth2-jose")

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
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }

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
