package org.kore.durchlauferhitzer.service.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Erzwingt die Modul-Layering-Regeln:
 * model <- adapter (REST/SOAP) <- service (aggregiert als Anwendung).
 */
@AnalyzeClasses(packages = "org.kore.durchlauferhitzer", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule modelDependsOnNoOtherModule = noClasses()
            .that().resideInAPackage("..durchlauferhitzer.model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..durchlauferhitzer.adapter..",
                    "..durchlauferhitzer.service..");

    @ArchTest
    static final ArchRule adaptersDependOnlyOnModel = noClasses()
            .that().resideInAPackage("..durchlauferhitzer.adapter..")
            .should().dependOnClassesThat().resideInAPackage(
                    "..durchlauferhitzer.service..");
}
