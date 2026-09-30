package org.kore.raumschiffwerft.service.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Erzwingt die Modul-Layering-Regeln (model <- adapter <- service) und die
 * Camel-Freiheit der Adapter (Camel ist allein Sache des service).
 */
@AnalyzeClasses(packages = "org.kore.raumschiffwerft", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule modelDependsOnNoOtherModule = noClasses()
            .that().resideInAPackage("..raumschiffwerft.model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..raumschiffwerft.adapter..",
                    "..raumschiffwerft.service..");

    @ArchTest
    static final ArchRule adaptersDependOnlyOnModel = noClasses()
            .that().resideInAPackage("..raumschiffwerft.adapter..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..raumschiffwerft.service..");

    @ArchTest
    static final ArchRule adaptersAreCamelFree = noClasses()
            .that().resideInAPackage("..raumschiffwerft.adapter..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.apache.camel",
                    "org.apache.camel..");
}
