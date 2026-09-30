package org.kore.raumschiffwerft.service.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.quarkus.test.junit.QuarkusTest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Unit-Tests (*Test, Surefire) duerfen nicht von QuarkusTest abhaengen;
 * QuarkusTest ist nur in Integrationstests (*IT, Failsafe) erlaubt.
 */
@AnalyzeClasses(packages = "org.kore.raumschiffwerft")
class TestConventionArchitectureTest {

    @ArchTest
    static final ArchRule unitTestsMustNotUseQuarkusTest = noClasses()
            .that().haveNameMatching(".*Test")
            .should().beAnnotatedWith(QuarkusTest.class);
}
