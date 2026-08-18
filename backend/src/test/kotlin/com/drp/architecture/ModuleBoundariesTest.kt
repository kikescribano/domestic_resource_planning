package com.drp.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Las fronteras de modulo, comprobadas **en los dos sentidos**.
 *
 * Que las reglas pasen sobre el arbol de verdad es la mitad util y la mitad
 * enganosa: una regla cuyo patron de paquete no case con nada tambien pasa, y
 * entonces la construccion afirma una frontera que no esta vigilando. Por eso
 * cada regla se ejecuta dos veces: sobre `com.drp`, donde tiene que pasar, y
 * sobre `com.drp.archfixture`, un arbol de mentira con la dependencia prohibida
 * dentro, donde tiene que **fallar**.
 *
 * Las reglas gobiernan **lo que se despliega**, asi que el arbol de verdad se
 * importa sin las pruebas. No es un descuido: las pruebas de plataforma manejan
 * el bus contra el core a proposito --es lo unico que puede medir cuando corre un
 * handler respecto al commit-- y contarlas como violacion obligaria a escribirlas
 * peor para que una regla de arquitectura se pusiera verde. El arbol de mentira,
 * en cambio, se importa entero: vive en el arbol de pruebas y es justo lo que hay
 * que mirar.
 */
class ModuleBoundariesTest {

    private val real: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages(REAL)

    private val fixture: JavaClasses = ClassFileImporter().importPackages(FIXTURE)

    @Nested
    @DisplayName("sobre el arbol de verdad")
    inner class OverTheRealTree {

        @Test
        @DisplayName("ningun modulo referencia a otro")
        fun `modulos independientes`() =
            ModuleBoundaryRules.modulesDoNotReferenceEachOther(REAL).check(real)

        @Test
        @DisplayName("el core no referencia a ningun modulo")
        fun `el core no sabe quien le escucha`() =
            ModuleBoundaryRules.coreDoesNotReferenceModules(REAL).check(real)

        @Test
        @DisplayName("plataforma no se apoya en el core salvo por SessionClaims")
        fun `plataforma no se apoya en el core`() =
            ModuleBoundaryRules.platformDoesNotLeanOnCore(REAL, ALLOWED_FROM_CORE).check(real)

        @Test
        @DisplayName("plataforma no conoce a los modulos")
        fun `plataforma no conoce a sus usuarios`() =
            ModuleBoundaryRules.platformDoesNotReferenceModules(REAL).check(real)

        /**
         * La excepcion tiene que seguir siendo **una**. Sin esto, la regla de
         * arriba se puede desactivar en la practica anadiendo nombres a la lista
         * sin que nada lo delate.
         */
        @Test
        @DisplayName("la lista de excepciones de plataforma tiene un solo nombre")
        fun `la grieta es una y esta nombrada`() {
            ALLOWED_FROM_CORE.shouldBe(setOf("SessionClaims"))
        }
    }

    @Nested
    @DisplayName("sobre un arbol que las incumple, la regla falla")
    inner class OverATreeThatBreaksThem {

        @Test
        @DisplayName("un modulo que importa de otro rompe la construccion")
        fun `caza el modulo que mira al de al lado`() =
            ModuleBoundaryRules.modulesDoNotReferenceEachOther(FIXTURE)
                .shouldFailOn(fixture, "alpha")

        @Test
        @DisplayName("un core que importa de un modulo rompe la construccion")
        fun `caza el core que sabe quien le escucha`() =
            ModuleBoundaryRules.coreDoesNotReferenceModules(FIXTURE)
                .shouldFailOn(fixture, "CoreThatPeeks")

        @Test
        @DisplayName("plataforma apoyandose en el core rompe la construccion")
        fun `caza la plataforma que se apoya`() =
            ModuleBoundaryRules.platformDoesNotLeanOnCore(FIXTURE, ALLOWED_FROM_CORE)
                .shouldFailOn(fixture, "PlatformThatLeans")

        /**
         * Y la excepcion nombrada **excusa de verdad**: la misma dependencia deja
         * de violar la regla si la clase de destino esta en la lista. Sin esta
         * comprobacion, la lista podria no estar leyendose y nadie lo sabria.
         */
        @Test
        @DisplayName("la excepcion nombrada excusa esa dependencia y ninguna otra")
        fun `la lista de excepciones se lee`() =
            ModuleBoundaryRules.platformDoesNotLeanOnCore(FIXTURE, setOf("CoreThatPeeks")).check(fixture)
    }

    /** Ejecuta la regla esperando que falle, y que lo diga senalando al culpable. */
    private fun ArchRule.shouldFailOn(classes: JavaClasses, culprit: String) {
        val failure = runCatching { check(classes) }.exceptionOrNull()

        checkNotNull(failure) { "La regla «${this.description}» paso sobre un arbol que la incumple" }
        failure.message.orEmpty().shouldContain(culprit)
    }

    private companion object {
        const val REAL = "com.drp"
        const val FIXTURE = "com.drp.archfixture"

        /** Lo unico que plataforma toma del core. Ver [ModuleBoundaryRules.platformDoesNotLeanOnCore]. */
        val ALLOWED_FROM_CORE = setOf("SessionClaims")
    }
}
