package org.kore.durchlauferhitzer.service.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Erzwingt die interne Boundary-Control-Entity-Struktur aller Module:
 * entity kennt nur entity, control kennt entity, boundary kennt control und entity.
 */
@AnalyzeClasses(packages = "org.kore.durchlauferhitzer", importOptions = ImportOption.DoNotIncludeTests.class)
class BoundaryControlEntityArchitectureTest {

    @ArchTest
    static final ArchRule entityDependsOnNothingOutward = noClasses()
            .that().resideInAPackage("..durchlauferhitzer..entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..durchlauferhitzer..control..",
                    "..durchlauferhitzer..boundary..");

    @ArchTest
    static final ArchRule controlDoesNotDependOnBoundary = noClasses()
            .that().resideInAPackage("..durchlauferhitzer..control..")
            .should().dependOnClassesThat().resideInAPackage(
                    "..durchlauferhitzer..boundary..");
}
