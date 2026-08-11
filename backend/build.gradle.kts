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
