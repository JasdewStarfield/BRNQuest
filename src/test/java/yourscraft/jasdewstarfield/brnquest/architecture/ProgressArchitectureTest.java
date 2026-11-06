package yourscraft.jasdewstarfield.brnquest.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Queries stay free of outward effects, and internal coordinators cannot capture the complete engine. */
@AnalyzeClasses(locations = ArchitectureBoundaryTest.ProductionModules.class)
public class ProgressArchitectureTest {
    @ArchTest static final ArchRule queriesDoNotPublishOrSynchronize = noClasses()
            .that().haveFullyQualifiedName("yourscraft.jasdewstarfield.brnquest.progress.ProgressQueries")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "yourscraft.jasdewstarfield.brnquest.event..", "yourscraft.jasdewstarfield.brnquest.network..",
                    "yourscraft.jasdewstarfield.brnquest.client..");

    @ArchTest static final ArchRule coordinatorsUseNarrowHostCommands = noClasses()
            .that().haveNameMatching("yourscraft\\.jasdewstarfield\\.brnquest\\.progress\\.Progress(Sampling|Rewards|Completion)(\\$.*)?")
            .should().dependOnClassesThat().haveFullyQualifiedName("yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine");
}
