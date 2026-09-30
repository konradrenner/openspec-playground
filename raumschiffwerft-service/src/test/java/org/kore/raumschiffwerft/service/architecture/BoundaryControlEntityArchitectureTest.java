package org.kore.raumschiffwerft.service.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Erzwingt die interne Boundary-Control-Entity-Struktur aller Module:
 * entity kennt nur entity, control kennt entity, boundary kennt control und entity.
 */
@AnalyzeClasses(packages = "org.kore.raumschiffwerft", importOptions = ImportOption.DoNotIncludeTests.class)
class BoundaryControlEntityArchitectureTest {

    @ArchTest
    static final ArchRule entityDependsOnNothingOutward = noClasses()
            .that().resideInAPackage("..raumschiffwerft..entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..raumschiffwerft..control..",
                    "..raumschiffwerft..boundary..");

    @ArchTest
    static final ArchRule controlDoesNotDependOnBoundary = noClasses()
            .that().resideInAPackage("..raumschiffwerft..control..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..raumschiffwerft..boundary..");
}
