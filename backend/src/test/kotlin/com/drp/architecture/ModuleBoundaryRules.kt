package com.drp.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition

/**
 * Las cuatro fronteras de la ADR-010, escritas una sola vez.
 *
 * Estan parametrizadas por la raiz de paquete a proposito, y no es una floritura:
 * es lo que permite aplicar **la misma regla** al arbol de verdad --donde tiene
 * que pasar-- y a un arbol de mentira que la incumple --donde tiene que fallar--.
 * Sin eso, una regla verde no distingue «el codigo cumple» de «el patron de
 * paquete no casa con nada».
 */
object ModuleBoundaryRules {

    /**
     * **Ningun modulo referencia a otro.** Es lo que hace que apagar uno no pueda
     * romper al de al lado: si Compras importara una clase de Warehouse, el hogar
     * que tiene Compras encendido y Warehouse apagado se llevaria un fallo de
     * clase que no se carga. Lo que si pueden hacer es hablarse por el bus, que
     * es asincrono y no crea dependencia de codigo.
     */
    fun modulesDoNotReferenceEachOther(root: String): ArchRule =
        SlicesRuleDefinition.slices()
            .matching("$root.module.(*)..")
            .should().notDependOnEachOther()
            .because("un modulo que importa de otro deja de poder apagarse por separado (ADR-010)")

    /**
     * **El core no referencia a ningun modulo.** Es la regla que de verdad
     * protege: es la que impide que el core acabe sabiendo quien le escucha, que
     * es la propiedad entera del event bus (README 5.2). Las demas evitan
     * acoplamientos incomodos; esta evita que el monolito modular deje de serlo.
     */
    fun coreDoesNotReferenceModules(root: String): ArchRule =
        noClasses()
            .that().resideInAPackage("$root.core..")
            .should().dependOnClassesThat().resideInAPackage("$root.module..")
            .because("el core publica sin saber quien escucha (README 5.2, ADR-010)")

    /**
     * **Plataforma no se apoya en el core**, salvo por las clases que se nombran
     * aqui una a una.
     *
     * La lista de excepciones vive en la regla y no en un comentario porque asi
     * ampliarla es un cambio visible en la revision. Hoy tiene un solo nombre,
     * `SessionClaims`: la sesion es del core porque el core fue lo primero que
     * existio, y de ella sale la autoria de una activacion. El dia que un modulo
     * necesite la sesion sin el core, esa clase se muda a plataforma con
     * `MemberRole` detras y la lista se queda vacia.
     */
    fun platformDoesNotLeanOnCore(root: String, allowed: Set<String>): ArchRule =
        noClasses()
            .that().resideInAPackage("$root.platform..")
            .should().dependOnClassesThat(
                inPackage("$root.core..").and(DescribedPredicate.not(named(allowed))),
            )
            .because("lo compartido no puede depender de lo que comparte (ADR-010)")

    /** **Plataforma tampoco conoce a los modulos**, que son quienes se apoyan en ella. */
    fun platformDoesNotReferenceModules(root: String): ArchRule =
        noClasses()
            .that().resideInAPackage("$root.platform..")
            .should().dependOnClassesThat().resideInAPackage("$root.module..")
            .because("plataforma ofrece el mecanismo y no conoce a sus usuarios (ADR-010)")

    private fun inPackage(pattern: String): DescribedPredicate<JavaClass> =
        object : DescribedPredicate<JavaClass>("residen en $pattern") {
            override fun test(input: JavaClass): Boolean = input.packageName.matchesPackage(pattern)
        }

    private fun named(names: Set<String>): DescribedPredicate<JavaClass> =
        object : DescribedPredicate<JavaClass>("se llaman $names") {
            override fun test(input: JavaClass): Boolean = input.simpleName in names
        }

    /** `com.drp.core..` casa con `com.drp.core` y con todo lo que cuelga de el. */
    private fun String.matchesPackage(pattern: String): Boolean {
        val prefix = pattern.removeSuffix("..")
        return this == prefix || startsWith("$prefix.")
    }
}
