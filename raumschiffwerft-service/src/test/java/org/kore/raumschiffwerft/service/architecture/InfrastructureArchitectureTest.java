package org.kore.raumschiffwerft.service.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Erzwingt die Infrastruktur-Grenzen des service: Das control ist frei von
 * Boundary- und Infrastruktur-Klassen (Dependency Inversion ueber Ports),
 * Datenbankzugriff liegt nur in boundary/persistence, Camel nur in
 * boundary/integration. Metriken laufen ausschliesslich ueber die
 * OpenTelemetry API, Logging nur ueber java.util.logging.
 */
@AnalyzeClasses(packages = "org.kore.raumschiffwerft", importOptions = ImportOption.DoNotIncludeTests.class)
class InfrastructureArchitectureTest {

    @ArchTest
    static final ArchRule controlIstInfrastrukturfrei = noClasses()
            .that().resideInAPackage("..raumschiffwerft.service.control..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..raumschiffwerft.service.boundary..",
                    "io.agroal..",
                    "org.apache.camel..",
                    "java.sql..",
                    "javax.sql..");

    @ArchTest
    static final ArchRule datenbankNurInBoundaryPersistence = noClasses()
            .that().resideOutsideOfPackage("..raumschiffwerft.service.boundary.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.agroal..",
                    "java.sql..",
                    "javax.sql..");

    @ArchTest
    static final ArchRule camelNurInBoundaryIntegration = noClasses()
            .that().resideOutsideOfPackage("..raumschiffwerft.service.boundary.integration..")
            .should().dependOnClassesThat().resideInAnyPackage("org.apache.camel..");

    @ArchTest
    static final ArchRule metrikeNurOpenTelemetry = noClasses()
            .that().resideInAPackage("..raumschiffwerft..")
            .should().dependOnClassesThat().resideInAnyPackage("io.micrometer..");

    @ArchTest
    static final ArchRule loggingNurJul = noClasses()
            .that().resideInAPackage("..raumschiffwerft.service..")
            .should().dependOnClassesThat().resideInAnyPackage("org.jboss.logging..");
}
