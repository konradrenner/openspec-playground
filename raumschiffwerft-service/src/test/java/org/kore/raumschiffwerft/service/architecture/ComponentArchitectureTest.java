package org.kore.raumschiffwerft.service.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Erzwingt die Komponentengrenze zwischen kaufauftrag und journal: Die
 * Komponente journal (Relay und Indexierung) haengt an keiner Klasse der
 * Komponente kaufauftrag und umgekehrt - die Kopplung laeuft
 * ausschliesslich ueber das Tabellenformat von journal_outbox.
 */
@AnalyzeClasses(packages = "org.kore.raumschiffwerft", importOptions = ImportOption.DoNotIncludeTests.class)
class ComponentArchitectureTest {

    @ArchTest
    static final ArchRule journalHaengtNichtAnKaufauftrag = noClasses()
            .that().resideInAPackage("..raumschiffwerft.service..journal..")
            .should().dependOnClassesThat(resideInAPackage("..raumschiffwerft.service..")
                    .and(not(resideInAPackage("..raumschiffwerft.service..journal.."))));

    @ArchTest
    static final ArchRule kaufauftragHaengtNichtAnJournal = noClasses()
            .that(resideInAPackage("..raumschiffwerft.service..")
                    .and(not(resideInAPackage("..raumschiffwerft.service..journal.."))))
            .should().dependOnClassesThat().resideInAPackage("..raumschiffwerft.service..journal..");
}
